package com.enexia.rg.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.enexia.rg.util.CorreoHtml;

import lombok.extern.slf4j.Slf4j;

/**
 * Unico punto de salida hacia el proveedor de correo (Brevo, API HTTPS).
 *
 * POR QUE API HTTP Y NO SMTP
 * Los planes gratuitos de hosting como Render bloquean el trafico SMTP saliente
 * (puertos 25, 465 y 587). La API va por 443 y funciona igual en desarrollo y en
 * produccion.
 *
 * CONTRATO (documentacion oficial, "Send a transactional email")
 *   POST https://api.brevo.com/v3/smtp/email
 *   Header  api-key: &lt;clave&gt;
 *   Body    sender{name,email}, to[{email}], subject, htmlContent, textContent
 *   Exito   201 {"messageId": "..."}  /  Error 400 {"code","message"}
 *
 * DEGRADACION CONTROLADA
 * Sin {@code enexia.email.brevo.api-key} el cliente funciona en MODO SIMULADO:
 * escribe el correo en el log (con el enlace, que en desarrollo es la unica
 * forma de completar un flujo de recuperacion) y no sale a la red. La logica de
 * negocio y las pruebas no cambian.
 *
 * SEGURIDAD
 *   - La clave llega solo por variable de entorno; nunca se loguea.
 *   - Cuando el envio es real NO se loguea el cuerpo: algunos correos llevan un
 *     token de recuperacion, que en un log seria una credencial en claro.
 *   - Del error del proveedor se registra solo el codigo HTTP, no su cuerpo (que
 *     puede eco-ar el contenido de la peticion).
 *   - Timeouts explicitos: sin ellos, un proveedor lento deja los hilos del pool
 *     colgados indefinidamente.
 *   - Nunca lanza: devuelve false. Que el correo falle no puede tumbar el login
 *     ni la creacion de un evento.
 */
@Component
@Slf4j
public class BrevoClient {

    private static final String URL_BASE = "https://api.brevo.com/v3";
    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(10);

    private final RestClient cliente;
    private final String apiKey;
    private final String remitente;
    private final String nombreRemitente;

    @Autowired
    public BrevoClient(
            @Value("${enexia.email.brevo.api-key:}") String apiKey,
            @Value("${enexia.email.remitente:}") String remitente,
            @Value("${enexia.email.nombre-remitente:Enexia}") String nombreRemitente) {
        this(construirCliente(), apiKey, remitente, nombreRemitente);
    }

    /** Visible para pruebas: permite inyectar un RestClient atado a un servidor simulado. */
    BrevoClient(RestClient cliente, String apiKey, String remitente, String nombreRemitente) {
        this.cliente = cliente;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remitente = remitente == null ? "" : remitente.trim();
        this.nombreRemitente = nombreRemitente;
    }

    private static RestClient construirCliente() {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXION).build());
        fabrica.setReadTimeout(TIMEOUT_LECTURA);
        return RestClient.builder().baseUrl(URL_BASE).requestFactory(fabrica).build();
    }

    /** True si hay clave: el envio es real. False: modo simulado. */
    public boolean estaConfigurado() {
        return !apiKey.isEmpty();
    }

    /**
     * Envia un correo. Devuelve true si el proveedor lo acepto (o si se simulo)
     * y false ante cualquier fallo, que queda registrado en el log.
     */
    public boolean enviar(String destinatario, String asunto, String textoPlano, String html) {
        String asuntoSeguro = CorreoHtml.sanearAsunto(asunto);

        if (!estaConfigurado()) {
            log.info("[EMAIL SIMULADO -> {}] {}\n{}", destinatario, asuntoSeguro, textoPlano);
            return true;
        }
        if (remitente.isEmpty()) {
            log.error("No se puede enviar '{}': falta enexia.email.remitente (EMAIL_REMITENTE), "
                    + "que debe ser un remitente verificado en el proveedor.", asuntoSeguro);
            return false;
        }

        Map<String, Object> cuerpo = Map.of(
                "sender", Map.of("name", nombreRemitente, "email", remitente),
                "to", List.of(Map.of("email", destinatario)),
                "subject", asuntoSeguro,
                "textContent", textoPlano,
                "htmlContent", html);

        try {
            cliente.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Correo '{}' enviado", asuntoSeguro);
            return true;

        } catch (RestClientResponseException ex) {
            log.error("El proveedor rechazo el correo '{}': HTTP {}",
                    asuntoSeguro, ex.getStatusCode().value());
            return false;
        } catch (RuntimeException ex) {
            // Timeout, DNS, conexion rechazada. Solo el tipo: el mensaje puede traer la URL.
            log.error("No se pudo contactar al proveedor de correo para '{}': {}",
                    asuntoSeguro, ex.getClass().getSimpleName());
            return false;
        }
    }
}
