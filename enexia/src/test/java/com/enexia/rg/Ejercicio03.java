package com.enexia.rg;

import org.junit.jupiter.api.Test;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class Ejercicio03 extends BaseTest {

    @Test
    public void hacerClick() {

        page.navigate("http://localhost:8080/pages/auth/login-desktop-claro.html");

        page.getByAltText("Logo Enexia").click();

        assertThat(page).hasURL("http://localhost:8080/index.html");
    }
}
