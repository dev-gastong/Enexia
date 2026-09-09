package com.enexia.rg.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.microsoft.playwright.APIResponse;

/**
 * Pruebas de API del alta de organizaciones (RF-7.2, RF-7.3; DFD 7.1/7.2 y 7.3).
 *
 * SE PRUEBAN LOS DOS CAMINOS, y no es redundancia: la documentacion describe dos
 * y ninguno invalida al otro.
 *
 *   - {@code POST /api/auth/registro/organizacion} (publico): la bifurcacion
 *     Fisica/Juridica que el DFD 7.1/7.2 dibuja DENTRO del formulario de
 *     registro. Crea cuenta personal + organizacion en una sola transaccion.
 *   - {@code POST /api/organizador/organizaciones} (autenticado): el "flujo
 *     separado, no como parte del registro inicial" que pide RF-7.2 al pie de
 *     la letra.
 *
 * Los dos terminan en el mismo metodo de servicio, asi que no puede haber dos
 * reglas de negocio que diverjan. Estas pruebas fijan justamente eso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DisplayName("API - Alta de organizaciones (RF-7.2)")
class OrganizacionApiTest extends BaseApiTest {

    /** CUIT real y valido: suma ponderada 198, resto 0, verificador 0. */
    private static final String CUIT_VALIDO = "30-71659554-0";

    @Nested
    @DisplayName("Camino publico: registro en un solo paso (DFD 7.1/7.2)")
    class RegistroConOrganizacion {

        @Test
        @DisplayName("201 con la cuenta ACTIVA y la organizacion APROBADA + ACTIVA")
        void altaCompleta() {
            APIResponse respuesta = api.post("/api/auth/registro/organizacion",
                    jsonCon(altaConOrganizacion(sufijoUnico(), cuitUnicoValido())));

            assertThat(respuesta.status()).isEqualTo(201);
            var json = cuerpo(respuesta);

            // La cuenta personal nace usable de inmediato...
            assertThat(json.get("cuenta").get("estado").asText()).isEqualTo("ACTIVO");
            assertThat(json.get("cuenta").get("roles").toString())
                    .contains("ORGANIZADOR").contains("PARTICIPANTE");

            // ...y la organizacion tambien: desde ADR-14 el alta se resuelve en
            // el acto con el modulo 11 como unico control, sin estado de
            // revision. Los estados siguen siendo DE LA ORGANIZACION y no del
            // usuario: son ejes distintos, y el de la cuenta se verifica arriba.
            assertThat(json.get("organizacion").get("estadoSistema").asText())
                    .isEqualTo("APROBADO");
            assertThat(json.get("organizacion").get("estado").asText()).isEqualTo("ACTIVO");
            assertThat(json.get("organizacion").get("rolEnEmpresa").asText())
                    .isEqualTo("ADMINISTRADOR");
        }

        @Test
        @DisplayName("400 si el CUIT no pasa el digito verificador (RF-7.3)")
        void cuitInvalido() {
            Map<String, Object> alta = altaConOrganizacion(sufijoUnico(), "30-71659554-1");

            APIResponse respuesta = api.post("/api/auth/registro/organizacion", jsonCon(alta));

            assertThat(respuesta.status()).isEqualTo(400);
            assertThat(cuerpo(respuesta).get("mensaje").asText()).contains("digito verificador");
        }

        @Test
        @DisplayName("400 si el CUIT no tiene 11 digitos: lo corta la validacion del DTO")
        void cuitMalFormado() {
            Map<String, Object> alta = altaConOrganizacion(sufijoUnico(), "3071");

            APIResponse respuesta = api.post("/api/auth/registro/organizacion", jsonCon(alta));

            // Este ni siquiera llega al service: lo frena el @Pattern del DTO,
            // que es la barrera mas barata de todas.
            assertThat(respuesta.status()).isEqualTo(400);
            assertThat(cuerpo(respuesta).get("error").asText()).isEqualTo("VALIDACION_FALLIDA");
        }

        @Test
        @DisplayName("409 si el CUIT ya esta registrado, aunque venga con otro formato")
        void cuitDuplicadoConOtroFormato() {
            String cuit = cuitUnicoValido();
            api.post("/api/auth/registro/organizacion",
                    jsonCon(altaConOrganizacion(sufijoUnico(), cuit)));

            // Mismo numero, escrito sin guiones. Sin la normalizacion a 11
            // digitos, esto crearia una segunda organizacion con el mismo CUIT.
            APIResponse respuesta = api.post("/api/auth/registro/organizacion",
                    jsonCon(altaConOrganizacion(sufijoUnico(), cuit.replace("-", ""))));

            assertThat(respuesta.status()).isEqualTo(409);
            assertThat(cuerpo(respuesta).get("mensaje").asText()).contains("CUIT");
        }

        @Test
        @DisplayName("400 si el perfil no es ORGANIZADOR")
        void perfilIncorrecto() {
            Map<String, Object> alta = altaConOrganizacion(sufijoUnico(), cuitUnicoValido());
            alta.put("perfil", "PARTICIPANTE");

            APIResponse respuesta = api.post("/api/auth/registro/organizacion", jsonCon(alta));

            // Se exige explicitamente en vez de corregirlo en silencio: recibir
            // PARTICIPANTE junto con datos fiscales significa que el cliente
            // esta armando mal la peticion.
            assertThat(respuesta.status()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 si falta el domicilio fiscal (validacion en cascada)")
        void faltaDomicilio() {
            Map<String, Object> alta = altaConOrganizacion(sufijoUnico(), cuitUnicoValido());
            @SuppressWarnings("unchecked")
            Map<String, Object> organizacion = (Map<String, Object>) alta.get("organizacion");
            organizacion.remove("domicilioFiscal");

            APIResponse respuesta = api.post("/api/auth/registro/organizacion", jsonCon(alta));

            assertThat(respuesta.status()).isEqualTo(400);
        }

        @Test
        @DisplayName("Alta fallida: NO queda la cuenta creada a medias")
        void transaccionalidad() {
            String sufijo = sufijoUnico();
            Map<String, Object> alta = altaConOrganizacion(sufijo, "30-71659554-1");

            assertThat(api.post("/api/auth/registro/organizacion", jsonCon(alta)).status())
                    .isEqualTo(400);

            // Si la cuenta hubiera quedado creada, este segundo intento con el
            // CUIT corregido chocaria con "ese email ya esta registrado" y el
            // usuario quedaria trabado sin entender por que.
            Map<String, Object> reintento = altaConOrganizacion(sufijo, cuitUnicoValido());
            APIResponse segundo = api.post("/api/auth/registro/organizacion", jsonCon(reintento));

            assertThat(segundo.status()).isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("Camino autenticado: organizacion sobre una cuenta ya existente (RF-7.2)")
    class AltaAutenticada {

        @Test
        @DisplayName("Un ORGANIZADOR ya registrado puede crear su organizacion despues")
        void altaPosterior() {
            String token = registrarYObtenerToken("ORGANIZADOR");

            APIResponse respuesta = api.post("/api/organizador/organizaciones",
                    jsonConToken(organizacion(cuitUnicoValido()), token));

            assertThat(respuesta.status()).isEqualTo(201);
            assertThat(cuerpo(respuesta).get("estadoSistema").asText())
                    .isEqualTo("APROBADO");
        }

        @Test
        @DisplayName("El listado devuelve las organizaciones del usuario con su estado")
        void listado() {
            String token = registrarYObtenerToken("ORGANIZADOR");
            api.post("/api/organizador/organizaciones",
                    jsonConToken(organizacion(cuitUnicoValido()), token));

            APIResponse listado = api.get("/api/organizador/organizaciones",
                    jsonConToken(null, token));

            assertThat(listado.status()).isEqualTo(200);
            var json = cuerpo(listado);
            assertThat(json.isArray()).isTrue();
            assertThat(json).hasSize(1);
            // El estado viaja igual en el listado: alimenta el selector
            // "publicar como..." del formulario de evento, y cuando el Modulo 6
            // pueda suspender una organizacion es lo que explicara por que
            // dejo de poder publicarse a nombre corporativo.
            assertThat(json.get(0).get("estadoSistema").asText()).isEqualTo("APROBADO");
        }

        @Test
        @DisplayName("403 si quien pide es PARTICIPANTE")
        void participanteNoPuede() {
            String token = registrarYObtenerToken("PARTICIPANTE");

            APIResponse respuesta = api.post("/api/organizador/organizaciones",
                    jsonConToken(organizacion(cuitUnicoValido()), token));

            assertThat(respuesta.status()).isEqualTo(403);
        }

        @Test
        @DisplayName("401 sin token")
        void sinToken() {
            APIResponse respuesta = api.post("/api/organizador/organizaciones",
                    jsonCon(organizacion(cuitUnicoValido())));

            assertThat(respuesta.status()).isEqualTo(401);
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    /**
     * Genera un CUIT distinto en cada llamada, con digito verificador correcto.
     *
     * Hace falta porque el CUIT es UNICO en la base y el sistema no borra filas:
     * usar siempre el mismo haria que la suite pase una sola vez.
     */
    private static String cuitUnicoValido() {
        String base = "30" + sufijoUnico();
        int[] multiplicadores = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

        int suma = 0;
        for (int i = 0; i < multiplicadores.length; i++) {
            suma += (base.charAt(i) - '0') * multiplicadores[i];
        }

        int verificador = 11 - (suma % 11);
        if (verificador == 11) {
            verificador = 0;
        } else if (verificador == 10) {
            verificador = 9;
        }
        return base + verificador;
    }

    private static Map<String, Object> organizacion(String cuit) {
        Map<String, Object> domicilio = new LinkedHashMap<>();
        domicilio.put("calle", "San Martin");
        domicilio.put("numeroExterior", "1234");
        // 13 = Ushuaia en el catalogo que siembra DatosInicialesConfig.
        domicilio.put("idCiudad", 13);

        Map<String, Object> organizacion = new LinkedHashMap<>();
        organizacion.put("razonSocial", "Cultural Fueguina SRL");
        organizacion.put("nombreFantasia", "Fueguina Eventos");
        organizacion.put("cuit", cuit);
        organizacion.put("emailCorporativo", "contacto" + sufijoUnico() + "@fueguina.test");
        organizacion.put("telefonoContacto", "+54 2901 123456");
        organizacion.put("domicilioFiscal", domicilio);
        return organizacion;
    }

    private static Map<String, Object> altaConOrganizacion(String sufijo, String cuit) {
        Map<String, Object> alta = new LinkedHashMap<>();
        alta.put("email", "org" + sufijo + "@enexia.test");
        alta.put("nickname", "org" + sufijo);
        alta.put("password", "Segura123");
        alta.put("passwordConfirmacion", "Segura123");
        alta.put("nombre", "Ana");
        alta.put("apellido", "Gomez");
        alta.put("dni", sufijo);
        alta.put("fechaNacimiento", "1990-05-10");
        alta.put("perfil", "ORGANIZADOR");
        alta.put("organizacion", organizacion(cuit));
        return alta;
    }
}
