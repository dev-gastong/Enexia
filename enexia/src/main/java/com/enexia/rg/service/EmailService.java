package com.enexia.rg.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Salida de correo de la plataforma (RF-1.5, RF-2.2, RF-7.2).
 *
 * DEGRADACION CONTROLADA: SMTP OPCIONAL
 * {@code JavaMailSender} solo se crea si hay {@code spring.mail.host}
 * configurado. Se inyecta por {@link ObjectProvider} en vez de directamente
 * porque eso permite que el bean NO exista sin romper el arranque: en
 * desarrollo, sin SMTP, el correo se escribe en el log con el mismo contenido
 * que se habria enviado. La logica de negocio no cambia y las pruebas no
 * necesitan un servidor de correo.
 *
 * Es deliberado que el log incluya el enlace: en desarrollo es la unica forma
 * de completar el flujo de recuperacion. En produccion, con SMTP configurado,
 * ese log no se emite (ver {@link #enviar}).
 *
 * ASINCRONO A PROPOSITO
 * {@code @Async} evita que la latencia del servidor SMTP se sume al tiempo de
 * respuesta del login. Es especialmente importante en el aviso de bloqueo: si
 * el envio fuera sincrono, el login tardaria notablemente MAS en el intento que
 * dispara el bloqueo que en los anteriores, y esa diferencia de tiempo seria
 * exactamente el oraculo que la politica de respuesta uniforme intenta cerrar.
 */
@Service
@Slf4j
public class EmailService {

    private final ObjectProvider<JavaMailSender> proveedorMailSender;
    private final String remitente;
    private final String urlBaseFrontend;

    public EmailService(
            ObjectProvider<JavaMailSender> proveedorMailSender,
            @Value("${enexia.email.remitente}") String remitente,
            @Value("${enexia.email.url-base-frontend}") String urlBaseFrontend) {
        this.proveedorMailSender = proveedorMailSender;
        this.remitente = remitente;
        this.urlBaseFrontend = urlBaseFrontend;
    }

    /**
     * Aviso de bloqueo automatico de cuenta con enlace de recuperacion (RF-1.4 + RF-1.5).
     *
     * Este correo es la CONTRAPARTE de la respuesta HTTP uniforme: como el login
     * ya no le dice a nadie que la cuenta quedo bloqueada, el email es el unico
     * canal por el que se entera el titular. El atacante no lo recibe porque no
     * controla la casilla.
     */
    @Async("ejecutorEnexia")
    public void enviarAvisoBloqueo(String destinatario, String tokenRecuperacion) {
        String enlace = urlBaseFrontend + "/pages/auth/recuperar.html?token=" + tokenRecuperacion;

        enviar(destinatario,
                "Enexia - Actividad inusual en tu cuenta",
                """
                Detectamos varios intentos fallidos de inicio de sesion en tu cuenta de Enexia.
                Por seguridad, la bloqueamos temporalmente.

                Si fuiste vos, podes recuperar el acceso definiendo una contrasena nueva desde
                este enlace (vence en 30 minutos y sirve una sola vez):

                %s

                Si NO fuiste vos, alguien esta intentando entrar a tu cuenta. Usa igualmente el
                enlace para cambiar tu contrasena por una que no uses en ningun otro sitio.

                No respondas a este correo.
                """.formatted(enlace));
    }

    /** Enlace de restablecimiento pedido explicitamente por el usuario (RF-1.5). */
    @Async("ejecutorEnexia")
    public void enviarRecuperacionPassword(String destinatario, String tokenRecuperacion) {
        String enlace = urlBaseFrontend + "/pages/auth/recuperar.html?token=" + tokenRecuperacion;

        enviar(destinatario,
                "Enexia - Restablecer tu contrasena",
                """
                Pediste restablecer la contrasena de tu cuenta de Enexia.

                Entra a este enlace para definir una nueva (vence en 30 minutos y sirve una
                sola vez):

                %s

                Si no fuiste vos, ignora este correo: tu contrasena actual sigue siendo valida.
                """.formatted(enlace));
    }

    /** Resultado de la moderacion automatica de un evento (RF-2.2, DFD 2.5B / 2.5C). */
    @Async("ejecutorEnexia")
    public void enviarResultadoModeracionEvento(String destinatario, String nombreEvento,
                                                boolean aprobado, String motivo) {
        String asunto = aprobado
                ? "Enexia - Tu evento ya esta publicado"
                : "Enexia - Tu evento no paso la moderacion";

        String cuerpo = aprobado
                ? """
                  Buenas noticias: "%s" supero la revision automatica de contenido y ya es
                  visible en el catalogo publico de Enexia.
                  """.formatted(nombreEvento)
                : """
                  Tu evento "%s" no supero la revision automatica de contenido.

                  Motivo: %s

                  Un administrador va a revisarlo manualmente. Si se trata de un error, el
                  evento se publicara sin que tengas que volver a cargarlo.
                  """.formatted(nombreEvento, motivo);

        enviar(destinatario, asunto, cuerpo);
    }

    /**
     * Envia, o deja constancia en el log si no hay SMTP.
     *
     * Nunca propaga la excepcion: que el correo falle no puede tumbar el login
     * ni la creacion de un evento. Un fallo de SMTP se registra como error y el
     * flujo principal continua.
     */
    private void enviar(String destinatario, String asunto, String cuerpo) {
        JavaMailSender emisor = proveedorMailSender.getIfAvailable();

        if (emisor == null) {
            // Modo desarrollo: sin SMTP configurado. Se vuelca el contenido para
            // poder completar el flujo (por ejemplo, copiar el enlace del token).
            log.info("[EMAIL SIMULADO -> {}] {}\n{}", destinatario, asunto, cuerpo);
            return;
        }

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
            mensaje.setTo(destinatario);
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            emisor.send(mensaje);
            // El cuerpo NO se loguea cuando el envio es real: contiene el token
            // de recuperacion, que en el log seria una credencial en claro.
            log.info("Correo '{}' enviado", asunto);

        } catch (RuntimeException ex) {
            log.error("No se pudo enviar el correo '{}'", asunto, ex);
        }
    }
}
