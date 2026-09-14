package com.enexia.rg.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Clase LoginPage - Implementa el patrón Page Object Model (POM)
 * Define los elementos y acciones de la página de login.
 * Esta clase encapsula los locators y métodos relacionados con el formulario de
 * login.
 */
public class LoginPage {
    // Referencia a la página de Playwright
    private final Page page;

    // Locators para los elementos del formulario
    private final Locator email; // Campo de entrada de usuario
    private final Locator password; // Campo de entrada de contraseña
    private final Locator loginButton; // Botón para enviar el formulario
    private final Locator flashMessage; // Mensaje de resultado (éxito/error)

    /**
     * Constructor de LoginPage
     * Inicializa la página y localiza todos los elementos del formulario de login
     *
     * @param page - Objeto Page de Playwright que representa la página actual
     */
    public LoginPage(Page page) {
        this.page = page;
        // Localiza el campo de usuario usando su etiqueta asociada
        this.email = page.getByLabel("Usuario o correo electrónico");
        // Localiza el campo de contraseña usando su etiqueta asociada
        this.password = page.locator("#password");
        // Localiza el botón de login usando su rol y nombre
        this.loginButton = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Iniciar sesión"));
        // Localiza el mensaje de resultado por su ID HTML
        this.flashMessage = page.locator("#mensajeError");
    }

    /**
     * Navega a la URL de la página de login
     * Abre el entorno local de mi sistema
     */
    public void navegar() {
        page.navigate("http://localhost:8080/pages/auth/login-desktop-claro.html");
    }

    /**
     * Realiza el login ingresando usuario y contraseña
     *
     * @param user - Nombre de usuario a ingresar
     * @param pass - Contraseña a ingresar
     */
    public void login(String user, String pass) {
        // Completa el campo de usuario
        email.fill(user);
        // Completa el campo de contraseña
        password.fill(pass);
        // Hace clic en el botón de login
        loginButton.click();
    }

    /**
     * Retorna el locator del mensaje de resultado
     * Permite acceder al mensaje de éxito/error del login
     *
     * @return Locator del elemento que contiene el mensaje
     */
    public Locator mensaje() {
        return flashMessage;
    }
}