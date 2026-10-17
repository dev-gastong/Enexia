package com.enexia.rg;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.enexia.rg.pages.LoginPage;
import com.enexia.rg.pages.RecuperarPasswordPage;
import com.enexia.rg.service.BrevoClient;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;

/**
 * Prueba de correos de extremo a extremo con la cuenta de pruebas {@code gaston_test}:
 * dispara por la interfaz (Playwright) el restablecimiento de contrasena y el
 * bloqueo por intentos fallidos, y comprueba que {@link BrevoClient} haya
 * mandado cada correo y que el proveedor lo haya aceptado.
 *
 * COMO SE VERIFICA EL ENVIO
 * {@code BrevoClient} se espia ({@code @MockitoSpyBean}): sigue llamando al
 * proveedor de verdad, pero se anota cada {@code enviar(...)} con su
 * destinatario, asunto y resultado. Un {@code true} significa "Brevo respondio
 * 2xx", no "llego a la bandeja": eso se mira en la casilla (y en la carpeta de
 * spam, si el remitente es nuevo).
 *
 * REQUISITOS
 *   - MariaDB corriendo y el puerto 8080 LIBRE (esta clase levanta la app; si hay
 *     un bootRun abierto, cerrarlo).
 *   - BREVO_API_KEY y EMAIL_REMITENTE en el entorno de QUIEN LANZA Gradle. Sin la
 *     clave el envio es simulado y la prueba falla a proposito: no tiene sentido
 *     que "pase" sin haber mandado nada.
 *
 * LA CUENTA gaston_test
 *   - Si no existe se crea por la API de registro (perfil PARTICIPANTE). Su email
 *     es un alias "+gaston_test" de {@value #CASILLA_DESTINO}: Gmail lo entrega
 *     en esa misma bandeja, y hace falta porque el email de una cuenta es unico
 *     y la direccion pelada ya pertenece a otra.
 *   - Cada prueba la deja ACTIVA y con el contador en cero, asi se puede repetir.
 *     Tambien limpia el limite de enlaces de recuperacion de esa cuenta (3 cada
 *     24 h y 2 min entre uno y otro), que si no frenaria la segunda corrida.
 *   - El enlace del correo de bloqueo NO se consume aca (el token viaja solo por
 *     el mail); se puede abrir a mano para ver recuperar.html funcionando.
 *
 * Ejecutar (PowerShell; las claves de Windows las tiene que ver el proceso):
 *   .\gradlew test --tests "com.enexia.rg.CorreosTest"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class CorreosTest extends BaseTest {

    /** Bandeja donde llegan los correos de prueba. */
    static final String CASILLA_DESTINO = "govino.fac@gmail.com";

    static final String NICKNAME = "gaston_test";
    static final String PASSWORD = System.getenv().getOrDefault("ENEXIA_TEST_GASTON_PASSWORD", "Contraseña1");

    static final String ASUNTO_RECUPERACION = "Enexia - Restablecer tu contrasena";
    static final String ASUNTO_BLOQUEO = "Enexia - Actividad inusual en tu cuenta";

    private static final String BASE_URL = "http://localhost:8080";

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private BrevoClient brevo;

    @Value("${enexia.email.brevo.api-key:}")
    private String claveBrevo;

    /** Cada llamada a BrevoClient.enviar(): {destinatario, asunto, resultado}. */
    private final List<Envio> envios = new CopyOnWriteArrayList<>();

    private record Envio(String destinatario, String asunto, boolean aceptado) { }

    private String emailCuenta;

    // =====================================================================
    // Preparacion y limpieza
    // =====================================================================

    @BeforeEach
    void prepararCuentaYEspia() {
        if (claveBrevo == null || claveBrevo.isBlank()) {
            fail("BREVO_API_KEY no llego a esta JVM: el envio seria SIMULADO y no saldria ningun "
                    + "correo. Lanza Gradle desde una terminal que vea las variables de entorno "
                    + "(BREVO_API_KEY y EMAIL_REMITENTE).");
        }

        // Pasa por el cliente real y solo anota el resultado.
        Mockito.doAnswer(invocacion -> {
            Object resultado = invocacion.callRealMethod();
            envios.add(new Envio(invocacion.getArgument(0), invocacion.getArgument(1),
                    Boolean.TRUE.equals(resultado)));
            return resultado;
        }).when(brevo).enviar(Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString());

        asegurarCuenta();
        restaurarCuenta();
    }

    @AfterEach
    void dejarCuentaActiva() {
        restaurarCuenta();
    }

    /** Crea gaston_test por la API de registro si todavia no existe. */
    private void asegurarCuenta() {
        Integer existe = jdbc.queryForObject(
                "select count(*) from usuario where nickname = ?", Integer.class, NICKNAME);

        if (existe == null || existe == 0) {
            String[] partes = CASILLA_DESTINO.split("@", 2);
            String alias = partes[0] + "+" + NICKNAME + "@" + partes[1];

            Map<String, Object> alta = new LinkedHashMap<>();
            alta.put("email", alias);
            alta.put("nickname", NICKNAME);
            alta.put("password", PASSWORD);
            alta.put("passwordConfirmacion", PASSWORD);
            alta.put("nombre", "Gaston");
            alta.put("apellido", "Prueba");
            // 8 digitos al azar: el DNI es unico y no se reutiliza (borrado logico).
            alta.put("dni", String.valueOf(ThreadLocalRandom.current().nextInt(20_000_000, 39_999_999)));
            alta.put("fechaNacimiento", "1990-05-10");
            alta.put("perfil", "PARTICIPANTE");

            APIResponse respuesta = context.request().post(BASE_URL + "/api/auth/registro",
                    RequestOptions.create().setHeader("Content-Type", "application/json").setData(alta));
            if (respuesta.status() != 201) {
                fail("No se pudo crear la cuenta " + NICKNAME + ": HTTP " + respuesta.status()
                        + " " + respuesta.text());
            }
        }

        emailCuenta = jdbc.queryForObject(
                "select email from usuario where nickname = ?", String.class, NICKNAME);
    }

    /** ACTIVA, contador en cero y sin limite de enlaces de recuperacion acumulado. */
    private void restaurarCuenta() {
        jdbc.update("""
                update usuario
                   set intentos_fallidos = 0,
                       id_estado_usuario = (select id_estado_usuario from usuario_estado
                                             where estado_usuario = 'ACTIVO')
                 where nickname = ?""", NICKNAME);
        jdbc.update("""
                delete from notificacion_enviada
                 where tipo = 'RECUPERACION'
                   and id_usuario = (select id_usuario from usuario where nickname = ?)""", NICKNAME);
    }

    private String estadoDeLaCuenta() {
        return jdbc.queryForObject("""
                select e.estado_usuario from usuario u
                  join usuario_estado e on e.id_estado_usuario = u.id_estado_usuario
                 where u.nickname = ?""", String.class, NICKNAME);
    }

    /** Espera a que BrevoClient haya procesado el asunto y devuelve ese envio. */
    private Envio esperarEnvio(String asunto) {
        await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(envios)
                        .as("correos procesados hasta ahora: %s", envios)
                        .anyMatch(e -> e.asunto().equals(asunto)));
        return envios.stream().filter(e -> e.asunto().equals(asunto)).findFirst().orElseThrow();
    }

    // =====================================================================
    // Pruebas
    // =====================================================================

    @Test
    @DisplayName("gaston_test inicia sesion con sus credenciales")
    void loginConCredencialesCorrectas() {
        LoginPage login = new LoginPage(page);
        login.navegar();
        login.login(NICKNAME, PASSWORD);

        assertThat(page).hasURL(Pattern.compile(".*index\\.html"));
    }

    @Test
    @DisplayName("Recuperar contrasena: la pantalla confirma y sale el correo con el enlace")
    void recuperarPasswordEnviaCorreo() {
        RecuperarPasswordPage recuperar = new RecuperarPasswordPage(page);
        recuperar.navegar();
        recuperar.pedirEnlace(emailCuenta);

        // El 202 es identico exista o no la cuenta (RF-1.5): la pantalla no prueba el envio.
        assertThat(recuperar.tituloEnviado()).isVisible();

        // El correo sale por otro hilo, despues de la respuesta.
        Envio envio = esperarEnvio(ASUNTO_RECUPERACION);
        assertThat(envio.destinatario()).isEqualTo(emailCuenta);
        assertThat(envio.aceptado()).as("Brevo debe aceptar el correo").isTrue();
    }

    @Test
    @DisplayName("Tres intentos fallidos bloquean la cuenta en silencio y avisan por correo")
    void tresFallosBloqueanYAvisanPorCorreo() {
        LoginPage login = new LoginPage(page);
        login.navegar();

        for (int intento = 1; intento <= 3; intento++) {
            final int n = intento;
            page.waitForResponse(r -> r.url().endsWith("/api/auth/login"),
                    () -> login.login(NICKNAME, "ClaveEquivocada" + n));
            assertThat(login.mensaje()).hasText("Credenciales inválidas");
        }

        // La cuenta quedo BLOQUEADA, pero la pantalla no lo dice: ni el cuerpo ni
        // el mensaje cambian (bloqueo silencioso, ADR-09).
        assertThat(estadoDeLaCuenta()).isEqualTo("BLOQUEADO");

        // Ni siquiera la contrasena correcta entra: misma respuesta que un fallo comun.
        page.waitForResponse(r -> r.url().endsWith("/api/auth/login"),
                () -> login.login(NICKNAME, PASSWORD));
        assertThat(login.mensaje()).hasText("Credenciales inválidas");
        assertThat(page).hasURL(Pattern.compile(".*login-desktop-claro\\.html"));

        // El unico canal que avisa es el correo, con el enlace de recuperacion.
        Envio envio = esperarEnvio(ASUNTO_BLOQUEO);
        assertThat(envio.destinatario()).isEqualTo(emailCuenta);
        assertThat(envio.aceptado()).as("Brevo debe aceptar el correo").isTrue();
    }
}
