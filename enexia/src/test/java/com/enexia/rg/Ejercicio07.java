package com.enexia.rg;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import java.nio.file.Paths;

public class Ejercicio07 extends BaseTest {

        @Test
        public void capturarPantallas() {

                page.navigate("http://localhost:8080/index.html");

                page.screenshot(
                                new Page.ScreenshotOptions()
                                                .setFullPage(true)
                                                .setPath(Paths.get("capturasParcial/index.png")));

                page.navigate("http://localhost:8080/pages/auth/login-desktop-claro.html");

                page.screenshot(
                                new Page.ScreenshotOptions()
                                                .setFullPage(true)
                                                .setPath(Paths.get("capturasParcial/login.png")));

                page.navigate("http://localhost:8080/pages/auth/register-paso2.html");

                page.screenshot(
                                new Page.ScreenshotOptions()
                                                .setFullPage(true)
                                                .setPath(Paths.get("capturasParcial/registro.png")));
        }
}
