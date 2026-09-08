package com.enexia.rg;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Paths;

/**
 * Clase BaseTest - Clase base para todas las pruebas automatizadas
 * Configura la inicialización y limpieza del entorno de Playwright.
 * Proporciona acceso a los objetos necesarios (Playwright, Browser, Context, Page)
 * para las pruebas mediante herencia.
 */
public class BaseTest {
    // Instancia de Playwright que controla los navegadores
    protected Playwright playwright;
    // Instancia del navegador Chromium
    protected Browser browser;
    // Contexto del navegador (contiene configuraciones y datos de la sesión)
    protected BrowserContext context;
    // Página del navegador (representa una pestaña del navegador)
    protected Page page;

    /**
     * Extensión de JUnit que captura una captura de pantalla cuando falla una prueba
     * Guarda la evidencia en la carpeta "evidencias" con el nombre del test
     */
    @RegisterExtension
    AfterTestExecutionCallback screenshotOnFailure = extensionContext -> {
        // Verifica si hubo una excepción durante la ejecución del test
        if (extensionContext.getExecutionException().isPresent()) {
            // Captura una pantalla de la página actual
            page.screenshot(new Page.ScreenshotOptions()
                    // Guarda la screenshot con el nombre del test que falló
                    .setPath(Paths.get("evidencias/" + extensionContext.getDisplayName() + ".png")));
        }
    };

    /**
     * Método que se ejecuta ANTES de cada prueba
     * Inicializa Playwright, abre el navegador, crea un contexto y una página
     * Este método es llamado automáticamente por JUnit antes de cada @Test
     */
    @BeforeEach
    void setUp() {

        // Crea una instancia de Playwright
        playwright = Playwright.create();
        // Verifica si estamos ejecutando en un entorno CI (Continuous Integration)
        // Si existe la variable de entorno CI, el navegador se ejecutará en modo headless
        boolean esCI = System.getenv("CI") != null;
        // Lanza el navegador Chromium
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(esCI));
        // Crea un nuevo contexto (sesión) del navegador
        context = browser.newContext();
        // Crea una nueva página (pestaña) dentro del contexto
        page = context.newPage();


        // Iniciar grabación de trazabilidad
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

    }

    /**
     * Método que se ejecuta DESPUÉS de cada prueba
     * Cierra todos los recursos de Playwright para limpiar el entorno
     * Este método es llamado automáticamente por JUnit después de cada @Test
     */
    @AfterEach
    void tearDown() {

        context.tracing().stop(new Tracing.StopOptions()
                .setPath(Paths.get("build/traces/trace.zip")));

        // Cierra el contexto (sesión) del navegador
        context.close();
        // Cierra el navegador
        browser.close();
        // Cierra la instancia de Playwright
        playwright.close();



    }
}