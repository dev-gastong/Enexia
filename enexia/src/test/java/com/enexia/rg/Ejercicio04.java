package com.enexia.rg;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

public class Ejercicio04 extends BaseTest {

    @Test
    public void llenarCampos() {

        page.navigate("http://localhost:8080/pages/auth/login-desktop-claro.html");

        page.getByLabel("Nombre de usuario",
                new Page.GetByLabelOptions()
                        .setExact(true))
                .fill("nombre_usuario");

        page.getByLabel("Contraseña",
                new Page.GetByLabelOptions()
                        .setExact(true))
                .fill("clave_secreta");

        System.out.println(page.getByLabel("Nombre de usuario",
                new Page.GetByLabelOptions()
                        .setExact(true))
                .inputValue());

        System.out.println(page.getByLabel("Contraseña",
                new Page.GetByLabelOptions()
                        .setExact(true))
                .inputValue());
    }
}
