package com.enexia.rg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import com.enexia.rg.pages.LoginPage;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class LoginTest extends BaseTest{

    /*
    * Credenciales de prueba: se inyectan desde variables de entorno, nunca
    * hardcodeadas (ver application.properties, seccion "Credenciales para
    * testing"). Antes de correr la suite:
    *
    *   set ENEXIA_TEST_EMAIL_EXITOSO=tu_email_valido@correo.com
    *   set ENEXIA_TEST_PASS_EXITOSO=tu_password_valida
    *   set ENEXIA_TEST_EMAIL_FALLIDO=un_email_que_no_exista@correo.com
    *   set ENEXIA_TEST_PASS_FALLIDO=cualquier_password
    *
    * Trace: npx playwright show-trace build/traces/trace.zip
    *
    * Test 1: .\gradlew test --tests "com.enexia.rg.LoginTest.ejecutarPrueba"
    * Test 2 : .\gradlew test --tests "com.enexia.rg.LoginTest.loginFallido" "-Demail=hola@hola.com" "-Dpass=Enexia2026"
    *
    * */

    @Value("${enexia.test.email.exitoso}")
    private String emailExitoso;

    @Value("${enexia.test.pass.exitoso}")
    private String passExitoso;

    @Value("${enexia.test.email.fallido}")
    private String emailFallido;

    @Value("${enexia.test.pass.fallido}")
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
        loginPage.login(emailExitoso, passExitoso);

        // Las aserciones van en el test
        assertThat(page).hasTitle(Pattern.compile("Enexia - Eventos en Tierra del Fuego"));
    }

    @Test
    public void loginFallido() {

        LoginPage loginPage = new LoginPage(page);
        loginPage.navegar();
        loginPage.login(emailFallido, passFallido);

        // Las aserciones van en el test
        assertThat(loginPage.mensaje()).hasText("Credenciales inválidas");
    }
}
