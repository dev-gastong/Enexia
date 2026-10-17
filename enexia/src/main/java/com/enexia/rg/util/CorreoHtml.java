package com.enexia.rg.util;

/**
 * Armado seguro del cuerpo HTML de los correos.
 *
 * POR QUE EXISTE
 * El proveedor de correo exige {@code htmlContent}. Los textos incluyen datos
 * que escribe un usuario (nombre de un evento, motivo de una suspension): sin
 * escapar, un nombre como {@code <a href="http://malo">Entrada gratis</a>}
 * llegaria a la casilla de cientos de inscriptos como un enlace clickeable
 * dentro de un correo legitimo de Enexia. Todo dato pasa por {@link #escapar}.
 *
 * NO SE CONVIERTEN URLS DEL TEXTO EN ENLACES
 * Seria lo cuco para el lector, pero un organizador podria escribir una URL en
 * el nombre del evento y Enexia la haria clickeable. El unico enlace del HTML
 * es el que arma el sistema (parametro {@code enlace}), nunca uno que aparezca
 * dentro de texto de un usuario.
 */
public final class CorreoHtml {

    private static final int LARGO_MAXIMO_ASUNTO = 150;

    private CorreoHtml() {
    }

    /** Escapa los cinco caracteres que permiten inyectar HTML o salirse de un atributo. */
    public static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder salida = new StringBuilder(valor.length() + 16);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '&' -> salida.append("&amp;");
                case '<' -> salida.append("&lt;");
                case '>' -> salida.append("&gt;");
                case '"' -> salida.append("&quot;");
                case '\'' -> salida.append("&#39;");
                default -> salida.append(c);
            }
        }
        return salida.toString();
    }

    /**
     * Convierte texto plano en HTML: parrafos por linea en blanco, saltos
     * simples como {@code <br>}, y un boton con el enlace del sistema si lo hay.
     */
    public static String aHtml(String textoPlano, String enlace) {
        StringBuilder html = new StringBuilder("<div style=\"font-family:Arial,sans-serif;font-size:15px;color:#222\">");

        String texto = textoPlano == null ? "" : textoPlano.strip();
        for (String parrafo : texto.split("\\R\\s*\\R")) {
            String limpio = parrafo.strip();
            if (limpio.isEmpty()) {
                continue;
            }
            html.append("<p>")
                    .append(escapar(limpio).replaceAll("\\R", "<br>"))
                    .append("</p>");
        }

        if (enlace != null && !enlace.isBlank()) {
            String seguro = escapar(enlace);
            html.append("<p><a href=\"").append(seguro).append("\" style=\"display:inline-block;")
                    .append("padding:10px 18px;background:#1b6ef3;color:#fff;text-decoration:none\">")
                    .append("Abrir enlace</a></p>")
                    .append("<p style=\"font-size:12px;color:#666\">Si el boton no funciona, copia esta direccion: ")
                    .append(seguro).append("</p>");
        }
        return html.append("</div>").toString();
    }

    /** El asunto no puede llevar saltos de linea ni ser kilometrico. */
    public static String sanearAsunto(String asunto) {
        if (asunto == null) {
            return "";
        }
        String limpio = asunto.replaceAll("[\\r\\n\\t]+", " ").strip();
        return limpio.length() > LARGO_MAXIMO_ASUNTO
                ? limpio.substring(0, LARGO_MAXIMO_ASUNTO) + "..."
                : limpio;
    }
}
