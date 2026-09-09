package com.enexia.rg.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.RequestOptions;

/**
 * Pruebas de API del Modulo 2 (eventos) y del Modulo 4 (catalogo publico).
 *
 * ES LA UNICA CAPA QUE PRUEBA EL PIPELINE ASINCRONO DE VERDAD.
 * Las unitarias verifican las decisiones del pipeline con mocks; aca se
 * comprueba que el recorrido completo funcione: multipart -> skeleton
 * EN_PROCESO -> commit -> evento de aplicacion -> hilo del pool -> moderacion ->
 * persistencia -> catalogo publico. Ese encadenamiento es donde aparecio la
 * condicion de carrera con el commit, y ninguna prueba unitaria podia verla.
 *
 * ESPERA ACTIVA, NUNCA Thread.sleep FIJO
 * La moderacion tarda lo que tarde. Un sleep de 2 segundos seria a la vez lento
 * (casi siempre termina antes) y fragil (a veces no alcanza). Awaitility
 * consulta cada 200ms hasta que el estado cambia o vence el limite: la prueba
 * corre rapido cuando el sistema responde rapido y solo falla si de verdad se
 * colgo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DisplayName("API - Eventos, moderacion asincrona y catalogo publico")
class EventoApiTest extends BaseApiTest {

    /** Techo generoso: si en 20s no hay veredicto, algo se colgo de verdad. */
    private static final Duration ESPERA_MODERACION = Duration.ofSeconds(20);

    private static Path imagenValida;
    private static Path imagenRechazable;

    @BeforeAll
    static void prepararImagenes() throws IOException {
        imagenValida = crearImagenTemporal("portada.png");
        // El nombre activa el rechazo simulado de CloudinaryService: es la unica
        // forma de probar la rama "todas las imagenes rechazadas" sin una cuenta
        // real de Cloudinary con complemento de moderacion habilitado.
        imagenRechazable = crearImagenTemporal("foto-a-rechazar.png");
    }

    // =====================================================================
    // Creacion y pipeline
    // =====================================================================

    @Nested
    @DisplayName("Creacion de eventos (RF-2.1 a RF-2.6)")
    class Creacion {

        @Test
        @DisplayName("202 ACCEPTED con el evento EN_PROCESO y sin contenido persistido")
        void creaSkeleton() {
            String token = registrarYObtenerToken("ORGANIZADOR");

            APIResponse respuesta = crearEvento(token, datosEvento("Festival de Invierno Fueguino"),
                    imagenValida);

            // 202 y no 201: el recurso quedo ACEPTADO pero todavia no publicado.
            // Un 201 le diria al cliente que ya esta completo, y no lo esta.
            assertThat(respuesta.status()).isEqualTo(202);
            JsonNode json = cuerpo(respuesta);
            assertThat(json.get("estadoSistema").asText()).isEqualTo("EN_PROCESO");
            // RF-2.2: el skeleton NO guarda el titulo. Se devuelve un texto
            // neutro para que la tarjeta del dashboard no quede vacia.
            assertThat(json.get("nombre").asText()).isEqualTo("(Evento en validacion)");
        }

        @Test
        @DisplayName("El pipeline aprueba y el evento aparece en el catalogo publico")
        void moderacionAprueba() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            String nombre = "Festival Unico " + sufijoUnico();

            long idEvento = crearEventoYObtenerId(token, datosEvento(nombre), imagenValida);

            esperarEstado(token, idEvento, "APROBADO_SISTEMA");

            // La prueba de fuego: el evento tiene que estar en el catalogo
            // publico, con su categoria, su ciudad y su portada resueltas.
            APIResponse catalogo = api.get("/api/publico/eventos?texto=" + nombre.replace(" ", "%20"));
            assertThat(catalogo.status()).isEqualTo(200);

            JsonNode contenido = cuerpo(catalogo).get("content");
            assertThat(contenido).hasSize(1);
            assertThat(contenido.get(0).get("nombre").asText()).isEqualTo(nombre);
            assertThat(contenido.get(0).get("categoria").asText()).isEqualTo("Cultural");
            assertThat(contenido.get(0).get("ciudad").asText()).isEqualTo("Ushuaia");
            assertThat(contenido.get(0).get("urlPortada").asText()).isNotBlank();
        }

        @Test
        @DisplayName("Imagen rechazada: el evento queda RECHAZADO_SISTEMA / MODERACION_IMAGEN")
        void moderacionRechazaImagen() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            String nombre = "Evento Rechazable " + sufijoUnico();

            long idEvento = crearEventoYObtenerId(token, datosEvento(nombre), imagenRechazable);

            JsonNode evento = esperarEstado(token, idEvento, "RECHAZADO_SISTEMA");
            assertThat(evento.get("motivoRechazo").asText()).isEqualTo("MODERACION_IMAGEN");
            // RF-2.2: en el rechazo NO se persiste contenido, ni siquiera el
            // titulo. Guardar el texto rechazado lo mete igual en la base.
            assertThat(evento.get("nombre").asText()).isEqualTo("(Evento en validacion)");

            // Y no puede aparecer en el catalogo publico.
            APIResponse catalogo = api.get("/api/publico/eventos?texto=" + nombre.replace(" ", "%20"));
            assertThat(cuerpo(catalogo).get("totalElements").asInt()).isZero();
        }

        @Test
        @DisplayName("400 sin imagenes (DFD 5.2.0 exige entre 1 y 3)")
        void sinImagenes() {
            String token = registrarYObtenerToken("ORGANIZADOR");

            APIResponse respuesta = api.post("/api/organizador/eventos", RequestOptions.create()
                    .setHeader("Authorization", "Bearer " + token)
                    .setMultipart(FormData.create()
                            .set("datos", parteJson(datosEvento("Evento Sin Imagenes")))));

            assertThat(respuesta.status()).isEqualTo(400);
            assertThat(cuerpo(respuesta).get("mensaje").asText()).contains("al menos una imagen");
        }

        @Test
        @DisplayName("400 si la hora de fin no es posterior a la de inicio")
        void horariosIncoherentes() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            Map<String, Object> datos = datosEvento("Evento Mal Horario");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cronogramas =
                    (List<Map<String, Object>>) datos.get("cronogramas");
            cronogramas.get(0).put("horaInicio", "22:00");
            cronogramas.get(0).put("horaFin", "20:00");

            APIResponse respuesta = crearEvento(token, datos, imagenValida);

            assertThat(respuesta.status()).isEqualTo(400);
            assertThat(cuerpo(respuesta).get("mensaje").asText()).contains("posterior");
        }

        @Test
        @DisplayName("400 si la fecha ya paso (paso 2.2: fechas futuras)")
        void fechaPasada() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            Map<String, Object> datos = datosEvento("Evento del Pasado");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cronogramas =
                    (List<Map<String, Object>>) datos.get("cronogramas");
            cronogramas.get(0).put("fecha", LocalDate.now().minusDays(1).toString());

            APIResponse respuesta = crearEvento(token, datos, imagenValida);

            // Lo corta el @Future del DTO: no tiene sentido publicar un evento
            // al que ya nadie puede inscribirse.
            assertThat(respuesta.status()).isEqualTo(400);
        }

        @Test
        @DisplayName("403 si quien crea es PARTICIPANTE")
        void participanteNoCrea() {
            String token = registrarYObtenerToken("PARTICIPANTE");

            APIResponse respuesta = crearEvento(token, datosEvento("Evento Prohibido"), imagenValida);

            assertThat(respuesta.status()).isEqualTo(403);
        }
    }

    // =====================================================================
    // Catalogo publico
    // =====================================================================

    @Nested
    @DisplayName("Catalogo publico (RF-4.1 a RF-4.5)")
    class CatalogoPublico {

        @Test
        @DisplayName("Accesible sin token: es un catalogo publico e indexable")
        void accesoAnonimo() {
            assertThat(api.get("/api/publico/eventos").status()).isEqualTo(200);
            assertThat(api.get("/api/publico/categorias").status()).isEqualTo(200);
            assertThat(api.get("/api/publico/provincias").status()).isEqualTo(200);
        }

        @Test
        @DisplayName("Los selectores en cascada devuelven provincias y sus ciudades (RF-4.3)")
        void cascadaGeografica() {
            JsonNode provincias = cuerpo(api.get("/api/publico/provincias"));
            assertThat(provincias.isArray()).isTrue();
            assertThat(provincias.size()).isPositive();

            long idProvincia = provincias.get(0).get("id").asLong();
            JsonNode ciudades = cuerpo(api.get("/api/publico/provincias/" + idProvincia + "/ciudades"));

            assertThat(ciudades.isArray()).isTrue();
            assertThat(ciudades.size()).isPositive();
        }

        @Test
        @DisplayName("La ficha tecnica trae agenda, tickets y direccion resuelta (RF-4.4)")
        void fichaCompleta() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            String nombre = "Festival Ficha " + sufijoUnico();
            long idEvento = crearEventoYObtenerId(token, datosEvento(nombre), imagenValida);
            esperarEstado(token, idEvento, "APROBADO_SISTEMA");

            JsonNode ficha = cuerpo(api.get("/api/publico/eventos/" + idEvento));

            assertThat(ficha.get("nombre").asText()).isEqualTo(nombre);
            assertThat(ficha.get("descripcion").asText()).isNotBlank();
            // Direccion resuelta hasta el pais: el DFD 4.4 pide la jerarquia
            // completa, no solo el id de ciudad.
            assertThat(ficha.get("ciudad").asText()).isEqualTo("Ushuaia");
            assertThat(ficha.get("provincia").asText()).isEqualTo("Tierra del Fuego");
            assertThat(ficha.get("pais").asText()).isEqualTo("Argentina");
            // Firma del organizador ya resuelta (RF-7.4): a titulo personal.
            assertThat(ficha.get("organizadorEsOrganizacion").asBoolean()).isFalse();
            assertThat(ficha.get("organizador").asText()).isEqualTo("Ana Gomez");

            JsonNode agenda = ficha.get("cronogramas");
            assertThat(agenda).hasSize(1);
            JsonNode tickets = agenda.get(0).get("tickets");
            assertThat(tickets).hasSize(2);
            // El ticket gratuito se marca como tal y con su cupo completo.
            assertThat(tickets.get(0).get("gratuito").asBoolean()).isTrue();
            assertThat(tickets.get(0).get("cupoDisponible").asInt()).isEqualTo(200);
        }

        @Test
        @DisplayName("404 al pedir un evento que no esta publicado")
        void eventoNoPublicoDaCuatrocientosCuatro() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            long idEvento = crearEventoYObtenerId(token,
                    datosEvento("Rechazado " + sufijoUnico()), imagenRechazable);
            esperarEstado(token, idEvento, "RECHAZADO_SISTEMA");

            APIResponse respuesta = api.get("/api/publico/eventos/" + idEvento);

            // 404 y no 403: distinguirlos permitiria descubrir que eventos
            // fueron rechazados por moderacion probando ids.
            assertThat(respuesta.status()).isEqualTo(404);
        }

        @Test
        @DisplayName("La visita queda registrada y se refleja en las estadisticas (RF-4.5, RF-2.10)")
        void registraVisitas() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            long idEvento = crearEventoYObtenerId(token,
                    datosEvento("Festival Visitas " + sufijoUnico()), imagenValida);
            esperarEstado(token, idEvento, "APROBADO_SISTEMA");

            // Una visita anonima y una autenticada.
            api.get("/api/publico/eventos/" + idEvento);
            api.get("/api/publico/eventos/" + idEvento, jsonConToken(null, token));

            JsonNode stats = cuerpo(api.get(
                    "/api/organizador/eventos/" + idEvento + "/estadisticas",
                    jsonConToken(null, token)));

            // Totales cuenta las dos; unicas solo la identificada, porque de una
            // visita anonima no hay forma de saber si es una persona o cien.
            assertThat(stats.get("visitasTotales").asLong()).isEqualTo(2);
            assertThat(stats.get("visitasUnicas").asLong()).isEqualTo(1);
            assertThat(stats.get("cupoTotalOfrecido").asLong()).isEqualTo(230);
        }

        @Test
        @DisplayName("400 si el rango de fechas esta invertido")
        void rangoInvertido() {
            APIResponse respuesta = api.get("/api/publico/eventos?desde=2026-12-01&hasta=2026-11-01");

            assertThat(respuesta.status()).isEqualTo(400);
        }

        @Test
        @DisplayName("El tamano de pagina tiene techo: no se puede pedir la tabla entera")
        void topeDePaginacion() {
            JsonNode pagina = cuerpo(api.get("/api/publico/eventos?tamano=100000"));

            // Sin este techo, un solo parametro bastaria para traer toda la
            // tabla a memoria en el endpoint mas expuesto del sistema.
            assertThat(pagina.get("size").asInt()).isLessThanOrEqualTo(50);
        }
    }

    // =====================================================================
    // Dashboard y baja
    // =====================================================================

    @Nested
    @DisplayName("Dashboard del organizador (RF-2.8) y baja logica (RF-2.9)")
    class Dashboard {

        @Test
        @DisplayName("El dashboard muestra tambien los eventos EN_PROCESO y RECHAZADOS")
        void muestraTodosLosEstados() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            crearEventoYObtenerId(token, datosEvento("Aprobable " + sufijoUnico()), imagenValida);
            long idRechazado = crearEventoYObtenerId(token,
                    datosEvento("Rechazable " + sufijoUnico()), imagenRechazable);
            esperarEstado(token, idRechazado, "RECHAZADO_SISTEMA");

            JsonNode pagina = cuerpo(api.get("/api/organizador/eventos", jsonConToken(null, token)));

            // A diferencia del catalogo, aca NO se filtra por estado: los
            // rechazados son justamente los que requieren atencion.
            assertThat(pagina.get("totalElements").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("Un organizador no ve los eventos de otro")
        void aislamientoEntreOrganizadores() {
            String tokenA = registrarYObtenerToken("ORGANIZADOR");
            crearEventoYObtenerId(tokenA, datosEvento("Evento de A " + sufijoUnico()), imagenValida);

            String tokenB = registrarYObtenerToken("ORGANIZADOR");
            JsonNode paginaB = cuerpo(api.get("/api/organizador/eventos", jsonConToken(null, tokenB)));

            assertThat(paginaB.get("totalElements").asInt()).isZero();
        }

        @Test
        @DisplayName("La baja logica lo saca del catalogo pero conserva el registro")
        void bajaLogica() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            String nombre = "Festival A Cancelar " + sufijoUnico();
            long idEvento = crearEventoYObtenerId(token, datosEvento(nombre), imagenValida);
            esperarEstado(token, idEvento, "APROBADO_SISTEMA");

            APIResponse baja = api.delete("/api/organizador/eventos/" + idEvento,
                    jsonConToken(null, token));
            assertThat(baja.status()).isEqualTo(200);
            assertThat(cuerpo(baja).get("estadoOrganizador").asText()).isEqualTo("DADO_DE_BAJA");

            // Fuera del catalogo publico...
            APIResponse catalogo = api.get("/api/publico/eventos?texto=" + nombre.replace(" ", "%20"));
            assertThat(cuerpo(catalogo).get("totalElements").asInt()).isZero();

            // ...pero la fila sigue existiendo para su dueno. Borrar de verdad
            // romperia inscripciones, pagos y valoraciones asociadas.
            JsonNode dashboard = cuerpo(api.get("/api/organizador/eventos", jsonConToken(null, token)));
            assertThat(dashboard.get("totalElements").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("404 al dar de baja un evento ajeno")
        void bajaDeEventoAjeno() {
            String tokenA = registrarYObtenerToken("ORGANIZADOR");
            long idEvento = crearEventoYObtenerId(tokenA,
                    datosEvento("Evento Ajeno " + sufijoUnico()), imagenValida);

            String tokenB = registrarYObtenerToken("ORGANIZADOR");
            APIResponse respuesta = api.delete("/api/organizador/eventos/" + idEvento,
                    jsonConToken(null, tokenB));

            // 404 y no 403: un 403 confirmaria que ese id existe.
            assertThat(respuesta.status()).isEqualTo(404);
        }

        @Test
        @DisplayName("Dar de baja MIENTRAS modera: la moderacion no resucita el evento")
        void bajaDuranteModeracion() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            String nombre = "Baja Durante Moderacion " + sufijoUnico();

            // Se da de baja SIN esperar el veredicto: la baja y el pipeline
            // corren en paralelo, que es justo el escenario del bug.
            long idEvento = crearEventoYObtenerId(token, datosEvento(nombre), imagenValida);
            assertThat(api.delete("/api/organizador/eventos/" + idEvento, jsonConToken(null, token))
                    .status()).isEqualTo(200);

            // Se espera a que el pipeline termine de escribir lo suyo.
            esperarEstado(token, idEvento, "APROBADO_SISTEMA");

            // EL BUG: sin @DynamicUpdate, el UPDATE del pipeline reescribia TODAS
            // las columnas con los valores que habia cargado ANTES de la baja, y
            // devolvia el evento a PUBLICADO. Un organizador que se arrepentia
            // antes de que terminara la moderacion veia su evento publicado igual.
            JsonNode evento = buscarEnDashboard(token, idEvento);
            assertThat(evento.get("estadoOrganizador").asText())
                    .as("la moderacion no puede revertir una baja del organizador")
                    .isEqualTo("DADO_DE_BAJA");

            // Y por lo tanto tampoco puede aparecer en el catalogo publico.
            APIResponse catalogo = api.get("/api/publico/eventos?texto=" + nombre.replace(" ", "%20"));
            assertThat(cuerpo(catalogo).get("totalElements").asInt()).isZero();
        }

        @Test
        @DisplayName("409 al dar de baja dos veces el mismo evento")
        void bajaRepetida() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            long idEvento = crearEventoYObtenerId(token,
                    datosEvento("Doble Baja " + sufijoUnico()), imagenValida);

            // Se espera el veredicto ANTES de la primera baja para que esta
            // prueba mida una sola cosa: que la segunda baja de 409. La
            // interaccion entre baja y moderacion tiene su propia prueba, arriba.
            esperarEstado(token, idEvento, "APROBADO_SISTEMA");

            api.delete("/api/organizador/eventos/" + idEvento, jsonConToken(null, token));
            APIResponse segunda = api.delete("/api/organizador/eventos/" + idEvento,
                    jsonConToken(null, token));

            assertThat(segunda.status()).isEqualTo(409);
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private static APIResponse crearEvento(String token, Map<String, Object> datos, Path imagen) {
        try {
            FormData formulario = FormData.create()
                    .set("datos", parteJson(datos))
                    .set("imagenes", new FilePayload(
                            imagen.getFileName().toString(), "image/png", Files.readAllBytes(imagen)));

            return api.post("/api/organizador/eventos", RequestOptions.create()
                    .setHeader("Authorization", "Bearer " + token)
                    .setMultipart(formulario));

        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer la imagen de prueba", ex);
        }
    }

    private static long crearEventoYObtenerId(String token, Map<String, Object> datos, Path imagen) {
        APIResponse respuesta = crearEvento(token, datos, imagen);
        if (respuesta.status() != 202) {
            throw new IllegalStateException("No se pudo crear el evento: "
                    + respuesta.status() + " " + respuesta.text());
        }
        return cuerpo(respuesta).get("idEvento").asLong();
    }

    /**
     * Empaqueta el JSON como una parte con {@code Content-Type: application/json}.
     *
     * Es imprescindible: {@code @RequestPart} necesita el tipo para elegir el
     * conversor. Sin el, la parte llega como octet-stream y Spring responde 415.
     * Es exactamente lo que hace un navegador con
     * {@code new Blob([json], {type: 'application/json'})}.
     */
    private static FilePayload parteJson(Map<String, Object> datos) {
        try {
            return new FilePayload("datos.json", "application/json",
                    JSON.writeValueAsBytes(datos));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar el formulario", ex);
        }
    }

    /**
     * Espera a que el pipeline asincrono dictamine, consultando el dashboard.
     *
     * @return el evento ya con su estado final
     */
    private static JsonNode esperarEstado(String token, long idEvento, String estadoEsperado) {
        await().atMost(ESPERA_MODERACION)
               .pollInterval(Duration.ofMillis(200))
               .until(() -> estadoEsperado.equals(estadoDe(token, idEvento)));

        return buscarEnDashboard(token, idEvento);
    }

    private static String estadoDe(String token, long idEvento) {
        JsonNode evento = buscarEnDashboard(token, idEvento);
        return evento == null ? null : evento.get("estadoSistema").asText();
    }

    private static JsonNode buscarEnDashboard(String token, long idEvento) {
        JsonNode pagina = cuerpo(api.get("/api/organizador/eventos?tamano=50",
                jsonConToken(null, token)));

        for (JsonNode evento : pagina.get("content")) {
            if (evento.get("idEvento").asLong() == idEvento) {
                return evento;
            }
        }
        return null;
    }

    private static Map<String, Object> datosEvento(String nombre) {
        Map<String, Object> ubicacion = new LinkedHashMap<>();
        ubicacion.put("calle", "Maipu");
        ubicacion.put("numeroExterior", "505");
        // 13 = Ushuaia, sembrada por DatosInicialesConfig.
        ubicacion.put("idCiudad", 13);

        Map<String, Object> general = new LinkedHashMap<>();
        general.put("tipoTicket", "Inscripcion General");
        general.put("precio", 0);
        general.put("cupoMaximo", 200);

        Map<String, Object> vip = new LinkedHashMap<>();
        vip.put("tipoTicket", "Pase VIP");
        vip.put("precio", 15000.50);
        vip.put("cupoMaximo", 30);

        Map<String, Object> cronograma = new LinkedHashMap<>();
        cronograma.put("fecha", LocalDate.now().plusDays(30).toString());
        cronograma.put("horaInicio", "18:00");
        cronograma.put("horaFin", "23:30");
        cronograma.put("tickets", List.of(general, vip));

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", nombre);
        datos.put("descripcion",
                "Tres jornadas de musica en vivo, gastronomia regional y actividades "
                + "para toda la familia en el corazon de Ushuaia.");
        datos.put("idCategoria", 1);
        datos.put("ubicacion", ubicacion);
        datos.put("cronogramas", List.of(cronograma));
        return datos;
    }
}
