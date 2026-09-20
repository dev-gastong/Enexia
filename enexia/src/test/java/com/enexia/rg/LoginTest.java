package com.enexia.rg;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.enexia.rg.pages.LoginPage;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class LoginTest extends BaseTest{


    /*
    * Trace: npx playwright show-trace build/traces/trace.zip
    *
    * Test 1: .\gradlew test --tests "com.enexia.rg.LoginTest.ejecutarPrueba"
    * Test 2 : .\gradlew test --tests "com.enexia.rg.LoginTest.loginFallido" "-Demail=hola@hola.com" "-Dpass=Enexia2026"
    *
    * */

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
        loginPage.login("govino.fac@gmail.com", "Contraseña1");

        // Las aserciones van en el test
        assertThat(page).hasTitle(Pattern.compile("Enexia - Eventos en Tierra del Fuego"));
    }

    @Test
    public void loginFallido() {


        LoginPage loginPage = new LoginPage(page);
        loginPage.navegar();
        loginPage.login("govinooo.fac@gmail.com", "Contraseña1");

        // Las aserciones van en el test
        assertThat(loginPage.mensaje()).hasText("Credenciales inválidas");
    }
}
