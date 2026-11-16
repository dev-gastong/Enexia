package com.enexia.rg;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class Ejercicio08 extends BaseTest {

    @Test
    public void navegarEntrePaginas(){

        page.navigate("http://localhost:8080/index.html");

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Iniciar Sesión")).click();

        assertThat(page).hasURL("http://localhost:8080/pages/auth/login-desktop-claro.html");

        page.goBack();

        assertThat(page).hasURL("http://localhost:8080/index.html");

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Registrarse")).click();

        assertThat(page).hasURL("http://localhost:8080/pages/auth/register-paso2.html");

        page.reload();

        assertThat(page).hasURL("http://localhost:8080/pages/auth/register-paso2.html");




    }

}
