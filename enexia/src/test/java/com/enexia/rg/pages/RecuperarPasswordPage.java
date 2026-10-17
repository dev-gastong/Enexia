package com.enexia.rg.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Page Object de la pantalla "Recuperar contrasena" (password-reset.html), la
 * que pide el enlace de restablecimiento (RF-1.5, paso 1).
 */
public class RecuperarPasswordPage {

    private final Page page;

    private final Locator email;
    private final Locator botonEnviar;
    private final Locator tituloEnviado;

    public RecuperarPasswordPage(Page page) {
        this.page = page;
        this.email = page.getByLabel("Correo electrónico");
        this.botonEnviar = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Enviar enlace de recuperación"));
        this.tituloEnviado = page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("¡Correo enviado!"));
    }

    public void navegar() {
        page.navigate("http://localhost:8080/pages/auth/password-reset.html");
    }

    /** Escribe el correo y pide el enlace. */
    public void pedirEnlace(String correo) {
        email.fill(correo);
        botonEnviar.click();
    }

    /** El titulo del estado "correo enviado": aparece cuando el backend respondio 202. */
    public Locator tituloEnviado() {
        return tituloEnviado;
    }
}
