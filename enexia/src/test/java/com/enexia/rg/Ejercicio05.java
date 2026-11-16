package com.enexia.rg;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@SpringBootTest
public class Ejercicio05 extends BaseTest {

    @Value("${enexia.test.nickname.exitoso}")
    private String nicknameExitoso;

    @Value("${enexia.test.password.exitoso}")
    private String passExitoso;

    @Value("${enexia.test.nickname.fallido}")
    private String nicknameFallido;

    @Value("${enexia.test.password.fallido}")
    private String passFallido;

    @Test
    public void loginExitoso() {

        page.navigate("http://localhost:8080/pages/auth/login-desktop-claro.html");

        page.getByLabel("Nombre de usuario",
                new Page.GetByLabelOptions()
                        .setExact(true))
                .fill(nicknameExitoso);

        page.getByLabel("Contraseña",
                new Page.GetByLabelOptions()
                        .setExact(true))
                .fill(passExitoso);

        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Iniciar sesión")).click();

        assertThat(page).hasURL("http://localhost:8080/index.html");

    }

    @Test
    public void loginFallido() {

        page.navigate("http://localhost:8080/pages/auth/login-desktop-claro.html");

        page.getByLabel("Nombre de usuario",
                new Page.GetByLabelOptions()
                        .setExact(true))
                .fill(nicknameFallido);

        page.getByLabel("Contraseña",
                new Page.GetByLabelOptions()
                        .setExact(true))
                .fill(passFallido);

        page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName("Iniciar sesión"))
                .click();

        assertThat(page).hasURL("http://localhost:8080/pages/auth/login-desktop-claro.html");
    }
}
