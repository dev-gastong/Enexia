package com.enexia.rg;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import com.enexia.rg.pages.EventoFormPage;
import com.enexia.rg.pages.EventoFormPage.DatosEvento;
import com.enexia.rg.pages.LoginPage;

/**
 * E2E: un ORGANIZADOR autenticado carga un evento completo (Modulo 2,
 * RF-2.1 a RF-2.6) desde el navegador real, igual de punta a punta que
 * {@link RegistroTest}.
 *
 * TOTALMENTE PARAMETRIZABLE POR CONSOLA. Ninguno de los valores de abajo esta
 * fijo en el codigo salvo la foto de portada (a proposito: ver
 * IMAGEN_PORTADA_PRUEBA). Cada "-D" tiene un default razonable, asi que la
 * prueba corre con solo pasar las credenciales:
 *
 *   .\gradlew test --tests "com.enexia.rg.CrearEventoTest" ^
 *       "-Dnickname=organizador_demo" "-Dpassword=Secreta123"
 *
 * O completamente parametrizada:
 *
 *   .\gradlew test --tests "com.enexia.rg.CrearEventoTest" ^
 *       "-Dnickname=organizador_demo" "-Dpassword=Secreta123" ^
 *       "-DeventoNombre=Torneo de Ajedrez" ^
 *       "-DeventoDescripcion=Torneo abierto a toda la comunidad de Ushuaia." ^
 *       "-DeventoCategoria=Deportivo" ^
 *       "-DeventoCalle=Av. San Martin" "-DeventoNumeroExterior=850" "-DeventoNumeroInterior=" ^
 *       "-DeventoCiudad=Ushuaia" "-DeventoLatitud=-54.8019" "-DeventoLongitud=-68.3030" ^
 *       "-DeventoFecha=2026-12-20" "-DeventoHoraInicio=10:00" "-DeventoHoraFin=13:00" ^
 *       "-DticketTipo=General" "-DticketPrecio=0" "-DticketCupoMaximo=200"
 *
 * La cuenta indicada en -Dnickname/-Dpassword tiene que existir de antemano y
 * tener el rol ORGANIZADOR (ver docs/requisitos, RF-1.1): esta prueba no la
 * crea, solo verifica que pueda operar como tal. El login se hace por NICKNAME
 * (no por email, ver UsuarioLoginRequest). {@code -Dnickname}/{@code
 * -Dpassword} sin valor hacen fallar el test con un mensaje explicito en vez
 * de un timeout de Playwright dificil de diagnosticar.
 *
 * A diferencia de {@link RegistroTest} y {@link LoginTest} (que llevan
 * {@code @SpringBootTest(webEnvironment = DEFINED_PORT)} y por lo tanto
 * levantan su PROPIA instancia de la app en el 8080), esta clase no arranca
 * ningun contexto de Spring: {@link BaseTest} solo abre Playwright, y las
 * paginas navegan contra {@code http://localhost:8080} ya hardcodeado en
 * {@link LoginPage#navegar()} / {@link EventoFormPage#URL}. Eso invierte el
 * requisito de las otras dos: en vez de exigir el puerto LIBRE, esta prueba
 * exige que la app ya este CORRIENDO ahi (por ejemplo con {@code gradle
 * bootRun}, el mismo origen que sirve el HTML estatico -- ver CLAUDE.md,
 * "NUNCA levantar un servidor de frontend aparte") antes de ejecutarla. Sin
 * nada escuchando en el 8080 el primer {@code navigate()} falla con
 * "ERR_CONNECTION_REFUSED" en vez de un BindException de arranque.
 */
@DisplayName("E2E - Alta de evento por un organizador (Modulo 2, RF-2.1 a RF-2.6)")
public class CrearEventoTest extends BaseTest {

    /** Margen para login + multipart con imagen + respuesta 202 del alta. */
    private static final double ESPERA_MS = 20_000;

    /**
     * Foto de portada de la prueba. A diferencia del resto del formulario,
     * esta ruta NO se parametriza: alcanza con una imagen valida y liviana
     * para ejercitar la carga multipart, y fijarla evita que cada corrida
     * dependa de que quien la ejecuta tenga a mano una foto real. Se genera
     * una sola vez junto con el repo (PNG solido de 400x250, ~0.5KB) y vive en
     * src/test/resources/fixtures -- no en src/main, para que nunca se
     * empaquete con la aplicacion real.
     */
    private static final Path IMAGEN_PORTADA_PRUEBA =
            Paths.get("src/test/resources/fixtures/evento-portada-prueba.png");

    @Test
    @DisplayName("Login como ORGANIZADOR + alta completa de evento con portada")
    public void organizadorCreaEventoCompleto() {

        String nickname = propiedadObligatoria("nickname");
        String password = propiedadObligatoria("password");

        // --- 1. Login (por nickname, no por email: ver UsuarioLoginRequest).
        LoginPage login = new LoginPage(page);
        login.navegar();
        login.login(nickname, password);

        page.waitForURL("**/index.html", new Page.WaitForURLOptions().setTimeout(ESPERA_MS));

        // --- 2. Validar el rol ANTES de intentar crear el evento: si la
        // cuenta pasada por -Dnickname/-Dpassword no es ORGANIZADOR, el error
        // tiene que decirlo aca y no aparecer como un timeout confuso mas
        // adelante en el formulario.
        boolean esOrganizador = (Boolean) page.evaluate("() => Auth.hasRole('ORGANIZADOR')");
        assertTrue(esOrganizador,
                "La cuenta '" + nickname + "' inicio sesion pero no tiene el rol ORGANIZADOR, "
                + "asi que no puede crear eventos. Pasa las credenciales de una cuenta organizadora "
                + "con -Dnickname=... -Dpassword=...");

        // --- 3. Ir al alta de evento. evento-form.html vuelve a exigir el
        // rol por su cuenta (Auth.hasRole('ORGANIZADOR'), y si falla redirige
        // a login): quedarse en esta URL confirma el chequeo anterior.
        EventoFormPage form = new EventoFormPage(page);
        form.navegar();

        assertTrue(page.url().contains("evento-form.html"),
                "Se esperaba permanecer en el formulario de alta de evento, pero la pantalla "
                + "redirigio a: " + page.url());

        // --- 4. Completar el formulario entero, totalmente parametrizado.
        DatosEvento datos = DatosEvento.conValoresPorDefecto();
        datos.nombre = System.getProperty("eventoNombre", datos.nombre);
        datos.descripcion = System.getProperty("eventoDescripcion", datos.descripcion);
        datos.categoria = System.getProperty("eventoCategoria", datos.categoria);
        datos.calle = System.getProperty("eventoCalle", datos.calle);
        datos.numeroExterior = System.getProperty("eventoNumeroExterior", datos.numeroExterior);
        datos.numeroInterior = System.getProperty("eventoNumeroInterior", datos.numeroInterior);
        datos.ciudad = System.getProperty("eventoCiudad", datos.ciudad);
        datos.latitud = System.getProperty("eventoLatitud", datos.latitud);
        datos.longitud = System.getProperty("eventoLongitud", datos.longitud);
        datos.fecha = System.getProperty("eventoFecha", datos.fecha);
        datos.horaInicio = System.getProperty("eventoHoraInicio", datos.horaInicio);
        datos.horaFin = System.getProperty("eventoHoraFin", datos.horaFin);
        datos.tipoTicket = System.getProperty("ticketTipo", datos.tipoTicket);
        datos.precio = System.getProperty("ticketPrecio", datos.precio);
        datos.cupoMaximo = System.getProperty("ticketCupoMaximo", datos.cupoMaximo);

        form.completar(datos, IMAGEN_PORTADA_PRUEBA);
        form.publicar();

        // --- 5. El 202 dispara el toast y, 1.6s despues, la redireccion a
        // mis-eventos.html (ver evento-form.html); si en cambio hay un 400 de
        // validacion, la pantalla se queda quieta y el aviso lo explica.
        if (!esperarRedireccionAMisEventos()) {
            String motivo = form.aviso().isVisible() ? form.aviso().innerText() : "(sin aviso visible)";
            fail("El alta no redirigio a mis-eventos.html a tiempo. Aviso del formulario: " + motivo);
        }

        // --- 6. El evento recien creado aparece listado con su nombre.
        assertThat(page.locator(".col-info__nombre").filter(
                new Locator.FilterOptions().setHasText(datos.nombre))).isVisible();
    }

    /* --------------------------------------------------------- Helpers --- */

    private boolean esperarRedireccionAMisEventos() {
        try {
            page.waitForURL("**/mis-eventos.html", new Page.WaitForURLOptions().setTimeout(ESPERA_MS));
            return true;
        } catch (com.microsoft.playwright.TimeoutError tiempoAgotado) {
            return false;
        }
    }

    private static String propiedadObligatoria(String clave) {
        String valor = System.getProperty(clave, "");
        assertFalse(valor.isBlank(),
                "Falta el parametro obligatorio -D" + clave + "=... (ver el javadoc de esta clase)");
        return valor;
    }
}
