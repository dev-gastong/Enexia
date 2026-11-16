package com.enexia.rg;

import org.junit.jupiter.api.Test;

public class Ejercicio01 extends BaseTest {

    @Test
    public void abrirYNavegar() {

        page.navigate("http://localhost:8080/pages/auth/login-desktop-claro.html");
        System.out.println(page.title());

    }

}
