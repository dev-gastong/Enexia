package com.enexia.rg.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;

/**
 * Base de las pruebas de API de extremo a extremo.
 *
 * POR QUE PLAYWRIGHT SIN NAVEGADOR
 * Las pantallas de Sprint 2 todavia no existen (el frontend se retoma cuando
 * lleguen los disenos de Figma), pero los endpoints si. Playwright ademas del
 * navegador ofrece {@link APIRequestContext}: un cliente HTTP con la misma API
 * de aserciones, que habla directo con el backend.
 *
 * Eso permite probar HOY el recorrido completo -- HTTP -> Spring -> MariaDB --
 * sin esperar a la interfaz, y cuando las pantallas existan estas pruebas siguen
 * valiendo como la capa de contrato de la API.
 *
 * {@code DEFINED_PORT} (en las subclases) levanta la aplicacion en el 8080 del
 * application.properties. REQUIERE MariaDB corriendo y el puerto 8080 LIBRE: si
 * hay un {@code bootRun} abierto, hay que cerrarlo antes.
 *
 * IDENTIDADES IRREPETIBLES: el sistema usa borrado logico, nunca DELETE, asi que
 * un email o un DNI no se pueden reutilizar entre corridas. Por eso
 * {@link #sufijoUnico()} genera uno nuevo en cada ejecucion.
 */
public abstract class BaseApiTest {

    protected static final String BASE_URL = "http://localhost:8080";

    protected static Playwright playwright;
    protected static APIRequestContext api;
    protected static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void abrirContextoApi() {
        playwright = Playwright.create();
        api = playwright.request().newContext(
                new APIRequest.NewContextOptions().setBaseURL(BASE_URL));
    }

    @AfterAll
    static void cerrarContextoApi() {
        if (api != null) {
            api.dispose();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    /** Sufijo aleatorio para construir emails, nicknames y DNI irrepetibles. */
    protected static String sufijoUnico() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    }

    protected static JsonNode cuerpo(APIResponse respuesta) {
        try {
            return JSON.readTree(respuesta.text());
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "La respuesta no era JSON valido: " + respuesta.text(), ex);
        }
    }

    protected static RequestOptions jsonCon(Object cuerpo) {
        return RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(cuerpo);
    }

    protected static RequestOptions jsonConToken(Object cuerpo, String token) {
        return jsonCon(cuerpo).setHeader("Authorization", "Bearer " + token);
    }

    /**
     * Da de alta una cuenta y devuelve su token de sesion.
     *
     * Registrar y loguear en un solo paso: casi todas las pruebas necesitan una
     * identidad valida y ninguna necesita repetir ese armado.
     */
    protected static String registrarYObtenerToken(String perfil) {
        String sufijo = sufijoUnico();
        String email = "api" + sufijo + "@enexia.test";
        String password = "Segura123";

        Map<String, Object> alta = new LinkedHashMap<>();
        alta.put("email", email);
        alta.put("nickname", "api" + sufijo);
        alta.put("password", password);
        alta.put("passwordConfirmacion", password);
        alta.put("nombre", "Ana");
        alta.put("apellido", "Gomez");
        // El DNI tiene 8 digitos y viene del mismo sufijo: unico por corrida.
        alta.put("dni", sufijo);
        alta.put("fechaNacimiento", "1990-05-10");
        alta.put("perfil", perfil);

        APIResponse registro = api.post("/api/auth/registro", jsonCon(alta));
        if (registro.status() != 201) {
            throw new IllegalStateException("No se pudo registrar la cuenta de prueba: "
                    + registro.status() + " " + registro.text());
        }

        Map<String, Object> credenciales = Map.of("email", email, "password", password);
        APIResponse login = api.post("/api/auth/login", jsonCon(credenciales));
        if (login.status() != 200) {
            throw new IllegalStateException("No se pudo iniciar sesion: " + login.text());
        }

        return cuerpo(login).get("token").asText();
    }

    /**
     * Crea un archivo PNG minimo en disco para las pruebas de multipart.
     *
     * Son los 8 bytes de la firma PNG. No hace falta una imagen real: Cloudinary
     * corre en modo simulado (sin credenciales) y solo se valida la extension y
     * el peso. Que el nombre contenga "rechazar" activa el rechazo simulado, que
     * es como se prueba esa rama sin cuenta de Cloudinary.
     */
    protected static Path crearImagenTemporal(String nombre) throws IOException {
        Path archivo = Files.createTempDirectory("enexia-api").resolve(nombre);
        Files.write(archivo, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        return archivo;
    }
}
