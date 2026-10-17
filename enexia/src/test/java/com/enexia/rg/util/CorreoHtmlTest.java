package com.enexia.rg.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El HTML de los correos incluye texto que escribe un usuario (nombre de un
 * evento, observacion de un admin). Si llega sin escapar, un organizador puede
 * meter un enlace propio dentro de un correo legitimo de Enexia.
 */
@DisplayName("CorreoHtml - escapado del cuerpo y saneado del asunto")
class CorreoHtmlTest {

    @Test
    @DisplayName("Escapa los caracteres que permiten inyectar HTML")
    void escapaCaracteresPeligrosos() {
        assertThat(CorreoHtml.escapar("<img src=x onerror=\"alert('x')\">&"))
                .isEqualTo("&lt;img src=x onerror=&quot;alert(&#39;x&#39;)&quot;&gt;&amp;");
    }

    @Test
    @DisplayName("Un nombre de evento con HTML no produce etiquetas en el cuerpo")
    void nombreConHtmlQuedaInerte() {
        String html = CorreoHtml.aHtml("Tu evento \"<a href=\"http://malo\">Entrada gratis</a>\" fue aprobado", null);

        assertThat(html).doesNotContain("<a href=\"http://malo\"");
        assertThat(html).contains("&lt;a href=&quot;http://malo&quot;&gt;");
    }

    @Test
    @DisplayName("Una URL escrita por un usuario NO se convierte en enlace")
    void noConvierteUrlsDelTexto() {
        String html = CorreoHtml.aHtml("Mira http://malo.example/entradas", null);

        assertThat(html).doesNotContain("<a ");
    }

    @Test
    @DisplayName("El unico enlace es el que arma el sistema, y va escapado")
    void enlaceDelSistema() {
        String html = CorreoHtml.aHtml("Entra aca", "https://enexia.test/x?a=1&b=2");

        assertThat(html).contains("<a href=\"https://enexia.test/x?a=1&amp;b=2\"");
    }

    @Test
    @DisplayName("Parrafos por linea en blanco y saltos simples como <br>")
    void estructuraDeParrafos() {
        String html = CorreoHtml.aHtml("Primera linea\nsegunda\n\nOtro parrafo", null);

        assertThat(html).contains("<p>Primera linea<br>segunda</p>").contains("<p>Otro parrafo</p>");
    }

    @Test
    @DisplayName("El asunto pierde saltos de linea y se acota")
    void asuntoSaneado() {
        assertThat(CorreoHtml.sanearAsunto("Hola\r\nBcc: victima@x.com")).doesNotContain("\n").doesNotContain("\r");
        assertThat(CorreoHtml.sanearAsunto("x".repeat(500)).length()).isLessThanOrEqualTo(153);
        assertThat(CorreoHtml.sanearAsunto(null)).isEmpty();
    }

    @Test
    @DisplayName("Un valor nulo se escapa como vacio")
    void nuloEsVacio() {
        assertThat(CorreoHtml.escapar(null)).isEmpty();
    }
}
