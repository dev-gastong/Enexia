package com.enexia.rg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.enexia.rg.pages.EventoFormPage;
import com.enexia.rg.pages.LoginPage;
import com.enexia.rg.pages.RegisterPage;

/**
 * Bot automatizado con Playwright que recorre las pantallas clave del sistema
 * y genera capturas de pantalla de página completa (full_page screenshot)
 * tanto en resolución Desktop estándar (1920x1080) como en modo Responsive (Mobile 390x844 y Tablet 768x1024),
 * además de las 5 secciones desglosadas de "Crear Evento".
 *
 * Utiliza por defecto el usuario organizador:
 *   - Usuario: tongashoy2
 *   - Contraseña: Contraseña1
 */
public class CapturasTest {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final Path CARPETA_CAPTURAS = Paths.get("capturas");

    private static final String DEFAULT_NICKNAME = "tongashoy2";
    private static final String DEFAULT_PASSWORD = "Contraseña1";

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(CARPETA_CAPTURAS);

        playwright = Playwright.create();
        boolean esCI = System.getenv("CI") != null || Boolean.getBoolean("headless");
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(esCI));

        // Inicializa con contexto desktop estándar 1920x1080
        configurarContexto(1920, 1080, false, 1.0);
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    private void configurarContexto(int ancho, int alto, boolean esMobile, double scaleFactor) {
        if (page != null) page.close();
        if (context != null) context.close();

        Browser.NewContextOptions opciones = new Browser.NewContextOptions()
                .setViewportSize(ancho, alto)
                .setIsMobile(esMobile)
                .setHasTouch(esMobile)
                .setDeviceScaleFactor(scaleFactor);

        context = browser.newContext(opciones);
        page = context.newPage();
    }

    private void sacarCapturaFullPage(Path carpetaDestino, String nombreArchivo) {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(4000));
        } catch (Exception ignored) {
            // Continúa si alguna petición estática se demora
        }
        page.waitForTimeout(1000);

        Path destino = carpetaDestino.resolve(nombreArchivo + ".png");
        page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(true)
                .setPath(destino));

        System.out.println("📸 Captura guardada: " + destino.toAbsolutePath());
    }

    private void iniciarSesion() {
        String nickname = System.getProperty("nickname", DEFAULT_NICKNAME);
        String password = System.getProperty("password", DEFAULT_PASSWORD);

        System.out.println("🔑 Iniciando sesión con usuario: " + nickname);
        LoginPage login = new LoginPage(page);
        login.navegar();
        login.login(nickname, password);

        try {
            page.waitForURL("**/index.html", new Page.WaitForURLOptions().setTimeout(15000));
            System.out.println("✅ Sesión iniciada con éxito (redirigido a index.html).");
        } catch (Exception e) {
            System.out.println("⚠️ Alerta en login: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Capturar todas las pantallas Desktop (1920x1080)")
    public void capturarTodasLasPantallas() {
        // 1. Login
        page.navigate(BASE_URL + "/pages/auth/login-desktop-claro.html");
        sacarCapturaFullPage(CARPETA_CAPTURAS, "01_login");

        // 2. Registro
        page.navigate(RegisterPage.URL);
        sacarCapturaFullPage(CARPETA_CAPTURAS, "02_registro");

        // 3. Login
        iniciarSesion();

        // 4. Crear Evento
        page.navigate(EventoFormPage.URL);
        sacarCapturaFullPage(CARPETA_CAPTURAS, "03_crear_evento");

        // 5. Dashboard
        page.navigate(BASE_URL + "/pages/organizador/dashboard.html");
        sacarCapturaFullPage(CARPETA_CAPTURAS, "04_dashboard");

        // 6. Detalle Evento
        String eventoId = System.getProperty("eventoId", "364");
        page.navigate(BASE_URL + "/evento-detalle.html?id=" + eventoId);
        sacarCapturaFullPage(CARPETA_CAPTURAS, "05_evento_detalle");

        System.out.println("🎉 ¡Capturas Desktop generadas con éxito!");
    }

    @Test
    @DisplayName("Capturar todas las pantallas en modo MOBILE (390x844)")
    public void capturarMobile() throws IOException {
        Path carpetaMobile = CARPETA_CAPTURAS.resolve("mobile");
        Files.createDirectories(carpetaMobile);

        // Dispositivo móvil típico (390x844, escala 2x retina)
        configurarContexto(390, 844, true, 2.0);

        System.out.println("📱 --- CAPTURANDO EN MODO MOBILE (390x844) ---");

        // 1. Login Mobile
        System.out.println("--> 1/5 Mobile Login...");
        page.navigate(BASE_URL + "/pages/auth/login-desktop-claro.html");
        sacarCapturaFullPage(carpetaMobile, "01_login_mobile");

        // 2. Registro Mobile
        System.out.println("--> 2/5 Mobile Registro...");
        page.navigate(RegisterPage.URL);
        sacarCapturaFullPage(carpetaMobile, "02_registro_mobile");

        // 3. Inicio de sesión en mobile
        iniciarSesion();

        // 4. Crear Evento Mobile
        System.out.println("--> 3/5 Mobile Crear Evento...");
        page.navigate(EventoFormPage.URL);
        sacarCapturaFullPage(carpetaMobile, "03_crear_evento_mobile");

        // 5. Dashboard Mobile
        System.out.println("--> 4/5 Mobile Dashboard...");
        page.navigate(BASE_URL + "/pages/organizador/dashboard.html");
        sacarCapturaFullPage(carpetaMobile, "04_dashboard_mobile");

        // 6. Detalle Evento Mobile
        String eventoId = System.getProperty("eventoId", "364");
        System.out.println("--> 5/5 Mobile Detalle Evento...");
        page.navigate(BASE_URL + "/evento-detalle.html?id=" + eventoId);
        sacarCapturaFullPage(carpetaMobile, "05_evento_detalle_mobile");

        System.out.println("🎉 ¡Todas las capturas MOBILE fueron generadas con éxito en capturas/mobile/!");
    }

    @Test
    @DisplayName("Capturar secciones individuales de Crear Evento (Paso 1 al 5)")
    public void capturarSeccionesCrearEvento() throws IOException {
        Path carpetaSecciones = CARPETA_CAPTURAS.resolve("secciones_crear_evento");
        Files.createDirectories(carpetaSecciones);

        iniciarSesion();
        page.navigate(EventoFormPage.URL);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        page.waitForTimeout(1000);

        // 1. Información Básica
        capturarElemento("#seccion-info", carpetaSecciones, "03_crear_evento_1");

        // 2. Cronograma Dinámico
        capturarElemento("#seccion-cronograma", carpetaSecciones, "03_crear_evento_2");

        // 3. Ubicación
        capturarElemento("#seccion-ubicacion", carpetaSecciones, "03_crear_evento_3");

        // 4. Tickets
        capturarElemento("#seccion-tickets", carpetaSecciones, "03_crear_evento_4");

        // 5. Galería y Portada
        capturarElemento("#seccion-galeria", carpetaSecciones, "03_crear_evento_5");

        System.out.println("🎉 ¡Las 5 secciones de Crear Evento fueron capturadas en capturas/secciones_crear_evento/!");
    }

    private void capturarElemento(String selector, Path carpeta, String nombre) {
        Locator loc = page.locator(selector);
        loc.scrollIntoViewIfNeeded();
        page.waitForTimeout(300);
        Path destino = carpeta.resolve(nombre + ".png");
        loc.screenshot(new Locator.ScreenshotOptions().setPath(destino));
        System.out.println("📸 Sección guardada: " + destino.toAbsolutePath());
    }
}
