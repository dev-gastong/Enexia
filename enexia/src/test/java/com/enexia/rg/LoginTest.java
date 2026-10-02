package com.enexia.rg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import com.enexia.rg.pages.LoginPage;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.nio.file.Paths;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class LoginTest extends BaseTest{

    /*
    * Credenciales de prueba: se inyectan desde variables de entorno, nunca
    * hardcodeadas (ver application.properties, seccion "Credenciales para
    * testing"). El login se identifica por NICKNAME, no por email (ver
    * UsuarioLoginRequest). Antes de correr la suite:
    *
    *   set ENEXIA_TEST_NICKNAME_EXITOSO=tu_nickname_valido
    *   set ENEXIA_TEST_PASS_EXITOSO=tu_password_valida
    *   set ENEXIA_TEST_NICKNAME_FALLIDO=un_nickname_que_no_exista
    *   set ENEXIA_TEST_PASS_FALLIDO=cualquier_password
    *
    * Trace: npx playwright show-trace build/traces/trace.zip
    *
    * Test 1: .\gradlew test --tests "com.enexia.rg.LoginTest.ejecutarPrueba"
    * Test 2 : .\gradlew test --tests "com.enexia.rg.LoginTest.loginFallido" "-Dnickname=noexiste" "-Dpass=Enexia2026"
    * Test 3 (Ejercicio 07 - screenshot): .\gradlew test --tests "com.enexia.rg.LoginTest.loginExitoso"
    * Test 4 (Ejercicio 08 - navegacion con links y boton Atras): .\gradlew test --tests "com.enexia.rg.LoginTest.navegarLinks"
    *
    * */

    @Value("${enexia.test.nickname.exitoso}")
    private String nicknameExitoso;

    @Value("${enexia.test.password.exitoso}")
    private String passExitoso;

    @Value("${enexia.test.nickname.fallido}")
    private String nicknameFallido;

    @Value("${enexia.test.password.fallido}")
    private String passFallido;

    @Test
    public void ejecutarPrueba(){

        // Navegar hacia el front y comprobar que el titulo coincida con el esperado.
        LoginPage loginPage = new LoginPage(page);
        loginPage.navegar();
        String titulo = System.getProperty("titulo", "Iniciar sesion");
        assertEquals(titulo, page.title());
    }

    @Test
    public void loginExitoso() {

        LoginPage loginPage = new LoginPage(page);
        loginPage.navegar();
        loginPage.login(nicknameExitoso, passExitoso);

        // Las aserciones van en el test
        assertThat(page).hasTitle(Pattern.compile("Enexia - Eventos en Tierra del Fuego"));

        // Ejercicio 07: captura de pantalla de la pantalla principal (index.html)
        // a la que redirige un login exitoso. Se guarda junto a las evidencias
        // de fallos (ver BaseTest) para revisarla despues sin volver a correr el test.
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("evidencias/login-exitoso-index.png")));
    }

    @Test
    public void loginFallido() {

        LoginPage loginPage = new LoginPage(page);
        loginPage.navegar();
        loginPage.login(nicknameFallido, passFallido);

        // Las aserciones van en el test
        assertThat(loginPage.mensaje()).hasText("Credenciales inválidas");
    }

    // Ejercicio 08: navegar entre paginas con links y boton "Atras".
    @Test
    public void navegarLinks() {

        LoginPage loginPage = new LoginPage(page);
        loginPage.navegar();
        loginPage.login(nicknameExitoso, passExitoso);

        // El login exitoso ya nos dejo en index.html (ver login-desktop-claro.html).
        assertThat(page).hasURL(Pattern.compile(".*index\\.html"));

        // Navegamos con el enlace/boton "Salir" del navbar: cierra la sesion
        // y recarga index.html, esta vez sin usuario autenticado.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Salir")).click();

        // Al no haber sesion, el navbar vuelve a mostrar el enlace "Iniciar Sesión".
        assertThat(page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Iniciar Sesión"))).isVisible();

        // Boton "Atras" del navegador: la recarga de "Salir" no agrega una
        // entrada nueva al historial, asi que retrocede hasta la pantalla de login.
        page.goBack();
        assertThat(page).hasURL(Pattern.compile(".*login-desktop-claro\\.html"));
    }
}
