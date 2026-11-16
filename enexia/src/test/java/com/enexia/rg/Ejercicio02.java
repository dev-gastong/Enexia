package com.enexia.rg;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class Ejercicio02 extends BaseTest {

    @Test
    public void abrirYNavegar() {

        page.navigate("http://localhost:8080/pages/auth/login-desktop-claro.html");

        assertThat(page).hasTitle(Pattern.compile("Iniciar sesion"));
    }
}
