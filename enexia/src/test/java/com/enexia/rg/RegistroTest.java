package com.enexia.rg;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.microsoft.playwright.options.WaitForSelectorState;

import pages.RegisterPage;
import pages.RegisterPage.DatosRegistro;

/**
 * Pruebas de extremo a extremo del registro de Persona Fisica (RF-1.1).
 *
 * A diferencia de AuthServiceRegistroTest, que aisla el service con mocks, aca
 * corre el sistema completo: navegador real -> HTML/JS -> Spring -> MariaDB.
 * Es la unica capa capaz de detectar los errores de integracion, que son
 * justamente los que mas cuestan encontrar a mano: un name de input que no
 * coincide con el campo del DTO, un CORS mal configurado o un mensaje de error
 * que el backend manda pero la pantalla nunca pinta.
 *
 * {@code DEFINED_PORT} levanta la aplicacion en el 8080 del
 * application.properties, que es el mismo origen desde donde Spring sirve el
 * HTML estatico. Requiere MariaDB corriendo y el puerto 8080 libre: si hay un
 * bootRun abierto, hay que cerrarlo antes.
 *
 * Cada prueba genera su propia identidad: el alta no se puede deshacer (el
 * sistema usa borrado logico, nunca DELETE), asi que reutilizar un email haria
 * que la suite pase una sola vez.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DisplayName("E2E - Registro de Persona Fisica (RF-1.1)")
public class RegistroTest extends BaseTest {

    /** Margen para el viaje de red completo, incluido el BCrypt del alta. */
    private static final double ESPERA_MS = 10_000;

    // =====================================================================
    // Estructura de la pantalla
    // =====================================================================

    @Test
    @DisplayName("La pantalla de registro carga con su titulo")
    public void laPantallaCarga() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        assertTrue(page.title().contains("Registro"),
                "El titulo deberia identificar la pantalla de registro, pero fue: " + page.title());
    }

    @Test
    @DisplayName("El bloque de domicilio personal ya no esta en el formulario")
    public void noPideDomicilioPersonal() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        // Se quito a proposito: el MER no modela una Ubicacion para
        // Persona_Fisica (solo Persona_Juridica tiene id_ubicacion), asi que
        // esos campos no tenian donde persistirse. Pedirle datos al usuario
        // que despues se tiran es peor que no pedirlos.
        assertThat(registro.seccionDomicilioPersonal()).hasCount(0);
        assertThat(page.locator("#calle")).hasCount(0);
        assertThat(page.locator("#provincia")).hasCount(0);
    }

    @Test
    @DisplayName("Persona Juridica avisa que aun no esta disponible y bloquea el envio")
    public void personaJuridicaTodaviaNoDisponible() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        registro.elegirPersonaJuridica();

        // Sprint 1 solo da de alta Personas Fisicas. Decirlo al elegir la
        // opcion evita que el usuario complete un formulario largo para
        // recibir un error recien al final.
        assertThat(registro.aviso()).containsText("organizaciones");
        assertThat(registro.botonEnviar()).isDisabled();
    }

    // =====================================================================
    // Camino feliz
    // =====================================================================

    @Test
    @DisplayName("Alta de PARTICIPANTE: llega a la pantalla de cuenta activa")
    public void registroExitosoDeParticipante() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        DatosRegistro datos = DatosRegistro.unica();
        registro.completarPersonaFisica(datos);
        registro.enviar();

        // El 201 dispara la redireccion desde JS; waitForURL espera a que
        // ocurra en lugar de dormir una cantidad fija de milisegundos.
        page.waitForURL("**/register-completado-pf.html",
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(ESPERA_MS));

        assertThat(page.locator("#chipRol")).hasText("Participante");
        assertThat(page.locator("#saludo")).containsText(datos.nickname);
    }

    @Test
    @DisplayName("Alta de ORGANIZADOR: la pantalla de exito refleja el rol elegido")
    public void registroExitosoDeOrganizador() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        DatosRegistro datos = DatosRegistro.unica();
        datos.perfil = "ORGANIZADOR";
        registro.completarPersonaFisica(datos);
        registro.enviar();

        page.waitForURL("**/register-completado-pf.html",
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(ESPERA_MS));

        // El backend le asigna ORGANIZADOR + PARTICIPANTE; la pantalla muestra
        // el principal. Que diga "Organizador" prueba que el rol viajo de
        // verdad y no es un texto fijo del HTML.
        assertThat(page.locator("#chipRol")).hasText("Organizador");
    }

    // =====================================================================
    // Errores del backend, vistos desde la pantalla
    // =====================================================================

    @Test
    @DisplayName("Email duplicado: el aviso lo explica y no se navega")
    public void emailDuplicadoMuestraElMotivo() {
        RegisterPage registro = new RegisterPage(page);

        // Primera alta: consume el email.
        registro.navegar();
        DatosRegistro primera = DatosRegistro.unica();
        registro.completarPersonaFisica(primera);
        registro.enviar();
        page.waitForURL("**/register-completado-pf.html",
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(ESPERA_MS));

        // Segunda alta: mismo email, todo lo demas distinto.
        registro.navegar();
        DatosRegistro segunda = DatosRegistro.unica();
        segunda.email = primera.email;
        registro.completarPersonaFisica(segunda);
        registro.enviar();

        assertThat(registro.aviso()).containsText("email");
        assertTrue(page.url().contains("register-paso2.html"),
                "Ante un 409 la pantalla debe quedarse en el formulario");
    }

    @Test
    @DisplayName("DNI duplicado: rechazado aunque cambien email y nickname (DFD 7.1.2)")
    public void dniDuplicadoMuestraElMotivo() {
        RegisterPage registro = new RegisterPage(page);

        registro.navegar();
        DatosRegistro primera = DatosRegistro.unica();
        registro.completarPersonaFisica(primera);
        registro.enviar();
        page.waitForURL("**/register-completado-pf.html",
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(ESPERA_MS));

        registro.navegar();
        DatosRegistro segunda = DatosRegistro.unica();
        segunda.dni = primera.dni;          // unico dato repetido
        registro.completarPersonaFisica(segunda);
        registro.enviar();

        assertThat(registro.aviso()).containsText("DNI");
    }

    @Test
    @DisplayName("Nickname ofensivo: la moderacion lo frena antes de crear la cuenta")
    public void nicknameOfensivoRechazado() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        DatosRegistro datos = DatosRegistro.unica();
        datos.nickname = "pelotudo_" + datos.dni;
        registro.completarPersonaFisica(datos);
        registro.enviar();

        // 422 CONTENIDO_INAPROPIADO, traducido por UI.mensajeDeError().
        assertThat(registro.aviso()).containsText("moderacion");
        assertTrue(page.url().contains("register-paso2.html"),
                "El alta rechazada no debe navegar a la pantalla de exito");
    }

    @Test
    @DisplayName("Contrasena debil: el 400 se pinta sobre el campo password")
    public void contrasenaDebilPintaElCampo() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        DatosRegistro datos = DatosRegistro.unica();
        datos.password = "todominuscula";              // sin mayuscula ni digito
        datos.passwordConfirmacion = "todominuscula";
        registro.completarPersonaFisica(datos);
        registro.enviar();

        // El handler global manda { campo: motivo } y UI.pintarErrores() lo
        // vuelca sobre el input cuyo id coincide con la clave.
        assertThat(registro.contenedorDeCampo("password")).hasClass(java.util.regex.Pattern.compile("campo--invalido"));
        assertThat(registro.errorDeCampo("password")).not().isEmpty();
    }

    @Test
    @DisplayName("Contrasenas distintas: se avisa sin ir al backend")
    public void contrasenasDistintasSeDetectanEnElCliente() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        DatosRegistro datos = DatosRegistro.unica();
        datos.passwordConfirmacion = "Diferente456";
        registro.completarPersonaFisica(datos);
        registro.enviar();

        assertThat(registro.aviso()).containsText("no coinciden");

        // El boton vuelve a quedar utilizable: el usuario tiene que poder
        // corregir y reintentar sin recargar la pagina.
        assertThat(registro.botonEnviar()).isEnabled();
    }

    @Test
    @DisplayName("El formulario nunca deja la contrasena en la URL")
    public void laContrasenaNoViajaEnLaUrl() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        DatosRegistro datos = DatosRegistro.unica();
        registro.completarPersonaFisica(datos);
        registro.enviar();

        page.waitForURL("**/register-completado-pf.html",
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(ESPERA_MS));

        // Un <form> sin action ni method hace GET y arrastra todos los campos
        // a la query string, donde quedarian en el historial del navegador y
        // en los logs del servidor. El submit se intercepta con
        // preventDefault() justamente para evitarlo.
        assertTrue(!page.url().contains("password") && !page.url().contains("Segura123"),
                "La URL no debe contener credenciales: " + page.url());
    }

    // =====================================================================
    // Continuidad con el login
    // =====================================================================

    @Test
    @DisplayName("La cuenta recien creada puede iniciar sesion")
    public void laCuentaCreadaPuedeIniciarSesion() {
        RegisterPage registro = new RegisterPage(page);
        registro.navegar();

        DatosRegistro datos = DatosRegistro.unica();
        registro.completarPersonaFisica(datos);
        registro.enviar();
        page.waitForURL("**/register-completado-pf.html",
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(ESPERA_MS));

        // Cierre del circuito: si el alta guardara la clave en claro o con otro
        // algoritmo, el BCrypt.matches() del login fallaria aca.
        pages.LoginPage login = new pages.LoginPage(page);
        login.navegar();
        login.login(datos.email, datos.password);

        page.waitForSelector("#mensajeError",
                new com.microsoft.playwright.Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.HIDDEN)
                        .setTimeout(ESPERA_MS));

        page.waitForURL("**/dashboard.html",
                new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(ESPERA_MS));

        assertTrue(page.url().contains("dashboard.html"),
                "Tras el login la sesion deberia llevar al dashboard, pero quedo en: " + page.url());
    }
}
