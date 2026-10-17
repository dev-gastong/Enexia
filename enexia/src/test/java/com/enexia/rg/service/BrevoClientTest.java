package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * El contrato con Brevo (POST /v3/smtp/email, header api-key, sender/to/subject/
 * htmlContent/textContent) y la regla de que un fallo del proveedor NUNCA lanza.
 */
@DisplayName("BrevoClient - contrato con el proveedor y degradacion controlada")
class BrevoClientTest {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    private MockRestServiceServer servidor;
    private RestClient cliente;

    @BeforeEach
    void prepararServidor() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com/v3");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = builder.build();
    }

    @Test
    @DisplayName("Envia el POST con api-key y el cuerpo que exige la documentacion")
    void enviaConElContratoCorrecto() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "clave-secreta"))
                .andExpect(jsonPath("$.sender.email").value("avisos@enexia.test"))
                .andExpect(jsonPath("$.sender.name").value("Enexia"))
                .andExpect(jsonPath("$.to[0].email").value("ana@enexia.test"))
                .andExpect(jsonPath("$.subject").value("Asunto"))
                .andExpect(jsonPath("$.textContent").value("texto"))
                .andExpect(jsonPath("$.htmlContent").value("<p>texto</p>"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"messageId\":\"<1@x>\"}"));

        boolean enviado = clienteConClave().enviar("ana@enexia.test", "Asunto", "texto", "<p>texto</p>");

        assertThat(enviado).isTrue();
        servidor.verify();
    }

    @Test
    @DisplayName("Un 400 del proveedor devuelve false y no lanza")
    void rechazoDelProveedor() {
        servidor.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"invalid_parameter\",\"message\":\"sender no verificado\"}"));

        // Que el proveedor rechace un correo no puede tumbar el login ni la
        // creacion de un evento que lo origino.
        assertThat(clienteConClave().enviar("ana@enexia.test", "Asunto", "t", "<p>t</p>")).isFalse();
    }

    @Test
    @DisplayName("Un error 500 devuelve false y no lanza")
    void errorDelServidor() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(clienteConClave().enviar("ana@enexia.test", "Asunto", "t", "<p>t</p>")).isFalse();
    }

    @Test
    @DisplayName("Sin clave: modo simulado, no sale a la red y cuenta como exito")
    void modoSimulado() {
        BrevoClient simulado = new BrevoClient(cliente, "", "avisos@enexia.test", "Enexia");

        assertThat(simulado.estaConfigurado()).isFalse();
        assertThat(simulado.enviar("ana@enexia.test", "Asunto", "t", "<p>t</p>")).isTrue();

        // Ninguna peticion esperada: si hubiera salido una, el servidor simulado
        // habria fallado la verificacion.
        servidor.verify();
    }

    @Test
    @DisplayName("Con clave pero sin remitente verificado: no envia y devuelve false")
    void sinRemitente() {
        BrevoClient sinRemitente = new BrevoClient(cliente, "clave-secreta", "", "Enexia");

        assertThat(sinRemitente.enviar("ana@enexia.test", "Asunto", "t", "<p>t</p>")).isFalse();
        servidor.verify();
    }

    @Test
    @DisplayName("El asunto viaja sin saltos de linea (no se inyectan cabeceras)")
    void asuntoSaneado() {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.subject").value("Hola Bcc: victima@x.com"))
                .andRespond(withStatus(HttpStatus.CREATED));

        clienteConClave().enviar("ana@enexia.test", "Hola\r\nBcc: victima@x.com", "t", "<p>t</p>");

        servidor.verify();
    }

    private BrevoClient clienteConClave() {
        return new BrevoClient(cliente, "clave-secreta", "avisos@enexia.test", "Enexia");
    }
}
