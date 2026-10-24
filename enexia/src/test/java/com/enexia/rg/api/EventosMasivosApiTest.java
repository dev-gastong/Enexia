package com.enexia.rg.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;

/**
 * Suite de creacion masiva: 30 eventos aleatorios para dentro de 10 dias.
 *
 * NO es una prueba de regresion: es un GENERADOR DE DATOS. Escribe en la base
 * del servidor al que apunta (el borrado es logico, no hay vuelta atras
 * limpia), por eso esta apagada por defecto y solo corre si se pide:
 *
 * <pre>
 *   gradle test --tests "*EventosMasivosApiTest" -Denexia.masivo=true
 *   # contra otra instancia:  -Denexia.baseUrl=http://localhost:8081
 *   # repetible:              -Denexia.semilla=12345
 * </pre>
 *
 * REQUIERE el backend CORRIENDO (no levanta uno propio): a diferencia de
 * EventoApiTest, que usa @SpringBootTest con puerto fijo, esta suite habla
 * con el servidor que ya tenes abierto para que los eventos queden en la base
 * que estas mirando en el navegador.
 *
 * QUE GENERA (cada evento, al azar):
 * - entre 1 y 3 fechas, todas a partir de hoy + 10 dias (la primera es
 * exactamente hoy + 10; las otras caen en los dias siguientes, sin repetir),
 * - entre 1 y 2 tipos de ticket por fecha, con precio y cupo propios,
 * - categoria, ciudad, direccion, horario e imagen de portada distintos.
 * Se reparten en 3 organizadores nuevos (10 eventos cada uno).
 *
 * Cada nombre lleva una etiqueta de corrida ("S10D-xxxx") para encontrar los
 * 30 juntos en el catalogo o en la base.
 *
 * IMAGENES: cada evento sube un PNG real de 640x360 con color propio. Si el
 * servidor tiene credenciales de Cloudinary, son 30 subidas reales.
 */
@EnabledIfSystemProperty(named = "enexia.masivo", matches = "true")
@DisplayName("Suite masiva - 30 eventos para dentro de 10 dias")
class EventosMasivosApiTest extends BaseApiTest {

    private static final int CANTIDAD_EVENTOS = 30;
    private static final int ORGANIZADORES = 3;
    private static final int DIAS_ADELANTE = 10;
    private static final Duration ESPERA_MODERACION = Duration.ofSeconds(45);

    private static final String[] PLANTILLAS_NOMBRE = {
            "Festival de Musica Austral", "Feria de Artesanos del Fin del Mundo", "Torneo Abierto de Ajedrez",
            "Maraton Costera", "Taller de Fotografia Patagonica", "Noche de Teatro Independiente",
            "Campeonato de Videojuegos", "Charla de Astronomia", "Cine al Aire Libre", "Encuentro de Cocina Fueguina",
            "Copa Interbarrios de Futbol", "Jornada de Ciencia para Chicos", "Recital de Folklore",
            "Expo de Emprendedores", "Torneo de Truco", "Caminata a la Laguna", "Clase Abierta de Yoga",
            "Muestra de Arte Joven", "Liga Local de Esports", "Ciclo de Conferencias del Sur"
    };

    private static final String[] TIPOS_TICKET = {
            "General", "VIP", "Early Bird", "Estudiante", "Jubilado", "Platea", "Campo", "Palco", "Familiar"
    };

    private static final String[] CALLES = {
            "San Martin", "Maipu", "Gobernador Paz", "Av. Hector Lopez", "Fadul", "Rosas", "Kuanip", "Belgrano"
    };

    private static final String[] HORAS_INICIO = { "09:00", "11:00", "15:00", "17:30", "19:00", "20:30" };
    private static final int[] DURACIONES_HORAS = { 2, 3, 4 };
    private static final int[] PRECIOS = { 0, 0, 1500, 3000, 5000, 8000, 12000, 20000 };
    private static final int[] CUPOS = { 20, 50, 80, 100, 150, 300 };

    private static Random azar;
    private static long semilla;
    private static String etiqueta;
    private static List<Long> idsCategorias;
    private static List<Long> idsCiudades;

    @BeforeAll
    static void prepararCatalogos() {
        semilla = Long.getLong("enexia.semilla", System.nanoTime());
        azar = new Random(semilla);
        etiqueta = "S10D-" + (1000 + azar.nextInt(9000));

        idsCategorias = ids(api.get("/api/publico/categorias"));
        assertThat(idsCategorias).as("categorias del catalogo").isNotEmpty();

        // Ciudades de TODAS las provincias que haya sembradas.
        idsCiudades = new ArrayList<>();
        for (Long idProvincia : ids(api.get("/api/publico/provincias"))) {
            idsCiudades.addAll(ids(api.get("/api/publico/provincias/" + idProvincia + "/ciudades")));
        }
        assertThat(idsCiudades).as("ciudades del catalogo").isNotEmpty();
    }

    @Test
    @DisplayName("Crea 30 eventos (1-3 fechas, 1-2 tickets por fecha) y todos quedan aprobados y publicados")
    void creaTreintaEventos() {
        System.out.println("[suite-masiva] etiqueta=" + etiqueta + " semilla=" + semilla
                + " base=" + BASE_URL);

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < ORGANIZADORES; i++) {
            tokens.add(registrarYObtenerToken("ORGANIZADOR"));
        }

        LocalDate primeraFecha = LocalDate.now().plusDays(DIAS_ADELANTE);
        List<Long> idsCreados = new ArrayList<>();
        Map<Long, Integer> fechasEsperadas = new LinkedHashMap<>();

        for (int n = 1; n <= CANTIDAD_EVENTOS; n++) {
            String token = tokens.get((n - 1) % ORGANIZADORES);
            Map<String, Object> datos = armarEvento(n, primeraFecha);

            int cantidadFechas = ((List<?>) datos.get("cronogramas")).size();
            APIResponse respuesta = crearEvento(token, datos, imagen(n));

            assertThat(respuesta.status())
                    .as("evento %d (%s): %s", n, datos.get("nombre"), respuesta.text())
                    .isEqualTo(202);

            long id = cuerpo(respuesta).get("idEvento").asLong();
            idsCreados.add(id);
            fechasEsperadas.put(id, cantidadFechas);
        }

        assertThat(idsCreados).hasSize(CANTIDAD_EVENTOS).doesNotHaveDuplicates();

        // La moderacion es asincrona: se espera a que los 30 queden visibles en el
        // catalogo publico (= aprobados). Un rechazo no aparece nunca y vence el
        // limite.
        await().alias("moderacion de los " + CANTIDAD_EVENTOS + " eventos")
                .atMost(ESPERA_MODERACION).pollInterval(Duration.ofMillis(500))
                .until(() -> idsCreados.stream().allMatch(id -> fichaPublica(id) != null));

        // Cada uno se verifica contra la ficha publica.
        List<String> problemas = new ArrayList<>();
        for (Long id : idsCreados) {
            JsonNode ficha = fichaPublica(id);
            if (ficha == null) {
                problemas.add("evento " + id + ": no esta en el catalogo publico (no quedo aprobado)");
                continue;
            }

            JsonNode cronogramas = ficha.get("cronogramas");
            int esperadas = fechasEsperadas.get(id);
            if (cronogramas.size() != esperadas) {
                problemas.add("evento " + id + ": " + cronogramas.size() + " fechas, esperaba " + esperadas);
            }

            TreeSet<LocalDate> fechas = new TreeSet<>();
            for (JsonNode cronograma : cronogramas) {
                fechas.add(LocalDate.parse(cronograma.get("fecha").asText()));
                int tickets = cronograma.get("tickets").size();
                if (tickets < 1 || tickets > 2) {
                    problemas.add("evento " + id + ": fecha con " + tickets + " tickets (debe ser 1 o 2)");
                }
            }
            if (!fechas.first().equals(primeraFecha)) {
                problemas.add("evento " + id + ": primera fecha " + fechas.first()
                        + ", esperaba " + primeraFecha);
            }
        }
        assertThat(problemas).as("inconsistencias en los eventos creados").isEmpty();

        // Y los 30 juntos aparecen en el catalogo buscando por la etiqueta.
        JsonNode catalogo = cuerpo(api.get("/api/publico/eventos?texto=" + etiqueta + "&tamano=100"));
        assertThat(catalogo.get("totalElements").asInt())
                .as("eventos de la corrida %s en el catalogo publico", etiqueta)
                .isEqualTo(CANTIDAD_EVENTOS);

        System.out.println("[suite-masiva] OK: " + CANTIDAD_EVENTOS + " eventos para el " + primeraFecha
                + ", ids " + idsCreados.get(0) + ".." + idsCreados.get(idsCreados.size() - 1));
    }

    // =====================================================================
    // Armado de datos
    // =====================================================================

    private static Map<String, Object> armarEvento(int numero, LocalDate primeraFecha) {
        String plantilla = PLANTILLAS_NOMBRE[azar.nextInt(PLANTILLAS_NOMBRE.length)];

        Map<String, Object> ubicacion = new LinkedHashMap<>();
        ubicacion.put("calle", CALLES[azar.nextInt(CALLES.length)]);
        ubicacion.put("numeroExterior", String.valueOf(100 + azar.nextInt(2900)));
        ubicacion.put("idCiudad", idsCiudades.get(azar.nextInt(idsCiudades.size())));

        // 1 a 3 fechas distintas: la primera es hoy+10; el resto, dias consecutivos
        // elegidos al azar entre los 4 siguientes, sin repetir.
        int cantidadFechas = 1 + azar.nextInt(3);
        List<Integer> offsets = new ArrayList<>(List.of(0));
        List<Integer> candidatos = new ArrayList<>(List.of(1, 2, 3, 4));
        while (offsets.size() < cantidadFechas) {
            offsets.add(candidatos.remove(azar.nextInt(candidatos.size())));
        }
        offsets.sort(Integer::compare);

        List<Map<String, Object>> cronogramas = new ArrayList<>();
        for (int offset : offsets) {
            cronogramas.add(armarCronograma(primeraFecha.plusDays(offset)));
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", etiqueta + " " + plantilla + " " + numero);
        datos.put("descripcion", "Evento de prueba numero " + numero + " de la corrida " + etiqueta
                + ". " + plantilla + " con " + cantidadFechas + " fecha(s) programada(s), pensado "
                + "para probar el catalogo, la ficha tecnica y la inscripcion.");
        datos.put("idCategoria", idsCategorias.get(azar.nextInt(idsCategorias.size())));
        datos.put("ubicacion", ubicacion);
        datos.put("cronogramas", cronogramas);
        return datos;
    }

    private static Map<String, Object> armarCronograma(LocalDate fecha) {
        String inicio = HORAS_INICIO[azar.nextInt(HORAS_INICIO.length)];
        int horaInicio = Integer.parseInt(inicio.substring(0, 2));
        int fin = Math.min(23, horaInicio + DURACIONES_HORAS[azar.nextInt(DURACIONES_HORAS.length)]);

        // 1 o 2 tipos de ticket, nombres distintos dentro de la misma fecha.
        int cantidadTickets = 1 + azar.nextInt(2);
        List<String> nombres = new ArrayList<>(List.of(TIPOS_TICKET));
        List<Map<String, Object>> tickets = new ArrayList<>();
        for (int i = 0; i < cantidadTickets; i++) {
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("tipoTicket", nombres.remove(azar.nextInt(nombres.size())));
            ticket.put("precio", PRECIOS[azar.nextInt(PRECIOS.length)]);
            ticket.put("cupoMaximo", CUPOS[azar.nextInt(CUPOS.length)]);
            tickets.add(ticket);
        }

        Map<String, Object> cronograma = new LinkedHashMap<>();
        cronograma.put("fecha", fecha.toString());
        cronograma.put("horaInicio", inicio);
        cronograma.put("horaFin", String.format("%02d:00", fin));
        cronograma.put("tickets", tickets);
        return cronograma;
    }

    /**
     * PNG real de 640x360, de un color distinto por evento, con el numero dibujado.
     */
    private static byte[] imagen(int numero) {
        try {
            BufferedImage lienzo = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = lienzo.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.getHSBColor((numero * 0.137f) % 1f, 0.55f, 0.85f));
            g.fillRect(0, 0, 640, 360);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 120));
            g.drawString(String.valueOf(numero), 40, 220);
            g.setFont(new Font("SansSerif", Font.PLAIN, 28));
            g.drawString(etiqueta, 44, 300);
            g.dispose();

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo, "png", salida);
            return salida.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar la imagen de prueba", ex);
        }
    }

    // =====================================================================
    // HTTP
    // =====================================================================

    private static APIResponse crearEvento(String token, Map<String, Object> datos, byte[] png) {
        try {
            FormData formulario = FormData.create()
                    .set("datos", new FilePayload("datos.json", "application/json",
                            JSON.writeValueAsBytes(datos)))
                    .set("imagenes", new FilePayload("portada.png", "image/png", png));

            return api.post("/api/organizador/eventos", RequestOptions.create()
                    .setHeader("Authorization", "Bearer " + token)
                    .setMultipart(formulario));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo armar la peticion", ex);
        }
    }

    private static JsonNode fichaPublica(long id) {
        APIResponse respuesta = api.get("/api/publico/eventos/" + id);
        return respuesta.status() == 200 ? cuerpo(respuesta) : null;
    }

    private static List<Long> ids(APIResponse respuesta) {
        List<Long> resultado = new ArrayList<>();
        for (JsonNode fila : cuerpo(respuesta)) {
            resultado.add(fila.get("id").asLong());
        }
        return resultado;
    }
}
