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
 * Pruebas de API de autenticacion (RF-1.1, RF-1.2, RF-1.4, RF-1.5, RF-1.6).
 *
 * QUE APORTAN SOBRE LAS UNITARIAS
 * AuthServiceLoginTest ya verifica que cada rama lance la excepcion correcta.
 * Lo que NO puede verificar es como sale eso por HTTP: que el handler global
 * traduzca las cinco ramas al MISMO status, el MISMO cuerpo y SIN cabeceras
 * extra. Un {@code @ExceptionHandler} mas especifico agregado por descuido
 * pasaria todas las pruebas unitarias y reabriria la fuga; estas lo detectan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DisplayName("API - Autenticacion y recuperacion de acceso")
class AuthApiTest extends BaseApiTest {

    @Nested
    @DisplayName("Registro de Persona Fisica (RF-1.1)")
    class Registro {

        @Test
        @DisplayName("201 con los roles asignados y la cuenta ACTIVA")
        void altaCorrecta() {
            String sufijo = sufijoUnico();
            APIResponse respuesta = api.post("/api/auth/registro", jsonCon(alta(sufijo, "ORGANIZADOR")));

            assertThat(respuesta.status()).isEqualTo(201);
            var json = cuerpo(respuesta);
            assertThat(json.get("estado").asText()).isEqualTo("ACTIVO");
            // Regla de CLAUDE.md: el ORGANIZADOR recibe TAMBIEN el rol
            // PARTICIPANTE, para no obligarlo a crear una segunda cuenta solo
            // para inscribirse a eventos de otros.
            assertThat(json.get("roles").toString()).contains("ORGANIZADOR").contains("PARTICIPANTE");
        }

        @Test
        @DisplayName("409 si el email ya esta tomado")
        void emailDuplicado() {
            String sufijo = sufijoUnico();
            api.post("/api/auth/registro", jsonCon(alta(sufijo, "PARTICIPANTE")));

            // Mismo email, distinto nickname y DNI: el conflicto tiene que ser
            // por el email y no por otra cosa.
            Map<String, Object> repetido = alta(sufijo, "PARTICIPANTE");
            repetido.put("nickname", "otro" + sufijoUnico());
            repetido.put("dni", sufijoUnico());

            APIResponse respuesta = api.post("/api/auth/registro", jsonCon(repetido));

            assertThat(respuesta.status()).isEqualTo(409);
            assertThat(cuerpo(respuesta).get("error").asText()).isEqualTo("RECURSO_DUPLICADO");
        }

        @Test
        @DisplayName("400 con el detalle campo por campo si falla la validacion")
        void validacionPorCampo() {
            Map<String, Object> invalida = alta(sufijoUnico(), "PARTICIPANTE");
            invalida.put("email", "no-es-un-email");
            invalida.put("password", "corta");

            APIResponse respuesta = api.post("/api/auth/registro", jsonCon(invalida));

            assertThat(respuesta.status()).isEqualTo(400);
            var json = cuerpo(respuesta);
            assertThat(json.get("error").asText()).isEqualTo("VALIDACION_FALLIDA");
            // El mapa por campo es lo que permite al formulario marcar cada
            // input equivocado en vez de mostrar un unico error global.
            assertThat(json.get("errores").has("email")).isTrue();
            assertThat(json.get("errores").has("password")).isTrue();
        }

        @Test
        @DisplayName("400 si el perfil intenta ser ADMINISTRADOR")
        void noSePuedeAutoAsignarAdmin() {
            Map<String, Object> escalada = alta(sufijoUnico(), "ADMINISTRADOR");

            // El rol de administrador jamas puede salir del registro publico.
            APIResponse respuesta = api.post("/api/auth/registro", jsonCon(escalada));

            assertThat(respuesta.status()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("Login: respuesta uniforme (politica 2026-09-08)")
    class LoginUniforme {

        @Test
        @DisplayName("200 con JWT cuando las credenciales son correctas")
        void loginCorrecto() {
            String token = registrarYObtenerToken("PARTICIPANTE");

            assertThat(token).isNotBlank();
            // Un JWT son tres partes separadas por punto.
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("Email inexistente y contrasena incorrecta dan EXACTAMENTE la misma respuesta")
        void mismaRespuestaParaAmbosCasos() {
            String sufijo = sufijoUnico();
            api.post("/api/auth/registro", jsonCon(alta(sufijo, "PARTICIPANTE")));

            APIResponse passwordMala = api.post("/api/auth/login", jsonCon(Map.of(
                    "email", "api" + sufijo + "@enexia.test", "password", "ClaveEquivocada1")));

            APIResponse emailInexistente = api.post("/api/auth/login", jsonCon(Map.of(
                    "email", "noexiste" + sufijoUnico() + "@enexia.test", "password", "ClaveEquivocada1")));

            // Las tres comparaciones son la politica entera: si alguna difiere,
            // un atacante puede enumerar que correos estan registrados.
            assertThat(passwordMala.status()).isEqualTo(emailInexistente.status()).isEqualTo(401);
            assertThat(cuerpo(passwordMala).get("error").asText())
                    .isEqualTo(cuerpo(emailInexistente).get("error").asText())
                    .isEqualTo("CREDENCIALES_INVALIDAS");
            assertThat(cuerpo(passwordMala).get("mensaje").asText())
                    .isEqualTo(cuerpo(emailInexistente).get("mensaje").asText());
        }

        @Test
        @DisplayName("La respuesta no publica ninguna cabecera de diagnostico")
        void sinCabecerasDelatoras() {
            APIResponse respuesta = api.post("/api/auth/login", jsonCon(Map.of(
                    "email", "noexiste" + sufijoUnico() + "@enexia.test", "password", "Cualquiera1")));

            // X-Reintentar-Despues publicaba el fin del cooldown. Leida desde las
            // DevTools delataba que la cuenta existe y esta penalizada, que es
            // justo lo que la respuesta uniforme trata de ocultar.
            assertThat(respuesta.headers()).doesNotContainKey("x-reintentar-despues");
        }

        @Test
        @DisplayName("Tras varios fallos la respuesta SIGUE siendo la misma")
        void elCooldownNoSeNota() {
            String sufijo = sufijoUnico();
            api.post("/api/auth/registro", jsonCon(alta(sufijo, "PARTICIPANTE")));
            String email = "api" + sufijo + "@enexia.test";

            String primerCuerpo = null;
            for (int intento = 1; intento <= 4; intento++) {
                APIResponse fallo = api.post("/api/auth/login",
                        jsonCon(Map.of("email", email, "password", "Equivocada" + intento)));

                assertThat(fallo.status())
                        .as("el intento %d no debe distinguirse de los otros", intento)
                        .isEqualTo(401);

                String actual = fallo.text();
                if (primerCuerpo == null) {
                    primerCuerpo = sinTimestamp(actual);
                } else {
                    // Al tercer fallo se dispara el cooldown internamente. Antes
                    // eso se notaba: cambiaba el status a 403 y aparecia la
                    // cabecera. Ahora el cliente no ve absolutamente nada.
                    assertThat(sinTimestamp(actual)).isEqualTo(primerCuerpo);
                }
            }
        }

        /** El timestamp cambia en cada respuesta; se descarta para comparar el resto. */
        private String sinTimestamp(String json) {
            return json.replaceAll("\"timestamp\":\"[^\"]*\"", "\"timestamp\":\"-\"");
        }
    }

    @Nested
    @DisplayName("Recuperacion de acceso (RF-1.5)")
    class Recuperacion {

        @Test
        @DisplayName("202 con el mismo texto exista o no el email")
        void respuestaUniforme() {
            String sufijo = sufijoUnico();
            api.post("/api/auth/registro", jsonCon(alta(sufijo, "PARTICIPANTE")));

            APIResponse existente = api.post("/api/auth/recuperacion",
                    jsonCon(Map.of("email", "api" + sufijo + "@enexia.test")));
            APIResponse inexistente = api.post("/api/auth/recuperacion",
                    jsonCon(Map.of("email", "nadie" + sufijoUnico() + "@enexia.test")));

            // Si contestara 404 para un email desconocido, este endpoint publico
            // seria un enumerador de cuentas mas comodo todavia que el login,
            // porque ni siquiera hay que adivinar contrasenas.
            assertThat(existente.status()).isEqualTo(inexistente.status()).isEqualTo(202);
            assertThat(cuerpo(existente).get("mensaje").asText())
                    .isEqualTo(cuerpo(inexistente).get("mensaje").asText());
        }

        @Test
        @DisplayName("Un token inventado se rechaza con 400")
        void tokenInventado() {
            APIResponse respuesta = api.post("/api/auth/recuperacion/confirmar", jsonCon(Map.of(
                    "token", "tokenCompletamenteInventadoQueNoExiste",
                    "password", "NuevaClave123",
                    "passwordConfirmacion", "NuevaClave123")));

            assertThat(respuesta.status()).isEqualTo(400);
            assertThat(cuerpo(respuesta).get("mensaje").asText())
                    .contains("no es valido o ya fue utilizado");
        }

        @Test
        @DisplayName("La politica de contrasena se aplica igual que en el registro")
        void mismaPoliticaDePassword() {
            APIResponse respuesta = api.post("/api/auth/recuperacion/confirmar", jsonCon(Map.of(
                    "token", "tokenLargoSuficienteParaPasarElSize",
                    "password", "debil",
                    "passwordConfirmacion", "debil")));

            // Si aca fuera mas laxa, este endpoint seria el atajo para eludir la
            // politica del registro.
            assertThat(respuesta.status()).isEqualTo(400);
            assertThat(cuerpo(respuesta).get("error").asText()).isEqualTo("VALIDACION_FALLIDA");
        }
    }

    @Nested
    @DisplayName("Proteccion de endpoints (RF-1.3)")
    class Rbac {

        @Test
        @DisplayName("Sin token, /api/auth/me responde 401")
        void sinToken() {
            assertThat(api.get("/api/auth/me").status()).isEqualTo(401);
        }

        @Test
        @DisplayName("Con token invalido tambien 401")
        void tokenInvalido() {
            APIResponse respuesta = api.get("/api/auth/me", jsonConToken(null, "no.es.un.jwt"));

            assertThat(respuesta.status()).isEqualTo(401);
        }

        @Test
        @DisplayName("Un PARTICIPANTE no entra al area de organizador -> 403")
        void participanteNoEsOrganizador() {
            String token = registrarYObtenerToken("PARTICIPANTE");

            APIResponse respuesta = api.get("/api/organizador/organizaciones",
                    jsonConToken(null, token));

            // 403 y no 401: aca el usuario SI esta autenticado, lo que le falta
            // es el permiso. Distinguirlos permite al frontend reaccionar bien
            // (reintentar login vs mostrar "no tenes acceso").
            assertThat(respuesta.status()).isEqualTo(403);
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private static Map<String, Object> alta(String sufijo, String perfil) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", "api" + sufijo + "@enexia.test");
        datos.put("nickname", "api" + sufijo);
        datos.put("password", "Segura123");
        datos.put("passwordConfirmacion", "Segura123");
        datos.put("nombre", "Ana");
        datos.put("apellido", "Gomez");
        datos.put("dni", sufijo);
        datos.put("fechaNacimiento", "1990-05-10");
        datos.put("perfil", perfil);
        return datos;
    }
}
