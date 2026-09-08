package com.enexia.rg;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import pages.LoginPage;

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
    public void loginFallido() {

        // Navegar hacia el front e intentar iniciar sesion.
        LoginPage loginPage = new LoginPage(page);
        loginPage.navegar();

        // Captura de argumentos por consola.
        String email = System.getProperty("email", "ejemplo@gmail.com");
        String password = System.getProperty("pass", "password");

        loginPage.login(email, password);

        // Espera automáticamente hasta 5 segundos a que la API responda y el JS pinte el mensaje
        assertThat(loginPage.mensaje()).hasText("Credenciales inválidas");

    }


}
