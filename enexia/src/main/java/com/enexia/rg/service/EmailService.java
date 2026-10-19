package com.enexia.rg.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.enexia.rg.model.NotificacionTipo;
import com.enexia.rg.util.CorreoHtml;

import lombok.extern.slf4j.Slf4j;

/**
 * Salida de correo de la plataforma: compone cada aviso y lo despacha (N.5 del
 * DFD de notificaciones). El envio fisico lo hace {@link BrevoClient}.
 *
 * LOS TRES PRINCIPIOS DE ESTA CLASE
 *
 * 1. SIEMPRE DESPUES DEL COMMIT. Si el metodo se invoca dentro de una
 *    transaccion, el envio se registra como {@code afterCommit}: un correo no
 *    puede salir por una operacion que luego se revierte ("tu evento fue
 *    suspendido" sobre una suspension que no ocurrio). Fuera de una
 *    transaccion se encola de inmediato.
 *
 * 2. SIEMPRE ASINCRONO, en un pool propio ({@code ejecutorCorreo}). Si el envio
 *    fuera sincrono, el intento de login que dispara el bloqueo tardaria mas que
 *    los anteriores y esa diferencia de tiempo seria el oraculo que la politica
 *    de respuesta uniforme intenta cerrar.
 *
 * 3. NUNCA PROPAGA UN FALLO. Que el proveedor este caido no puede tumbar el
 *    login, una inscripcion ni la creacion de un evento.
 *
 * IDEMPOTENCIA
 * Los avisos que un reloj o un reintento podrian repetir (recordatorio, evento
 * cancelado, invitacion a valorar, inscripcion confirmada) se reservan antes en
 * {@link NotificacionService}: la clave (tipo, usuario, referencia) es UNIQUE y
 * el segundo intento se descarta. Los que dispara una accion unica de una
 * persona no la necesitan.
 *
 * TEXTOS
 * Van sin tildes ni enie, igual que el resto del codigo, para no depender de la
 * codificacion de los archivos fuente.
 */
@Service
@Slf4j
public class EmailService {

    /** Quien recibe un aviso. {@code idUsuario} es la clave de idempotencia. */
    public record Destinatario(Long idUsuario, String email) {
    }

    private record Correo(String asunto, String texto, String enlace) {
        String textoCompleto() {
            return enlace == null ? texto : texto + "\n\n" + enlace;
        }
    }

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final BrevoClient brevo;
    private final NotificacionService notificaciones;
    private final Executor ejecutor;
    private final String urlBaseFrontend;
    private final long pausaMasivoMs;

    public EmailService(
            BrevoClient brevo,
            NotificacionService notificaciones,
            @Qualifier("ejecutorCorreo") Executor ejecutor,
            @Value("${enexia.email.url-base-frontend}") String urlBaseFrontend,
            @Value("${enexia.email.pausa-masivo-ms:200}") long pausaMasivoMs) {
        this.brevo = brevo;
        this.notificaciones = notificaciones;
        this.ejecutor = ejecutor;
        this.urlBaseFrontend = urlBaseFrontend;
        this.pausaMasivoMs = pausaMasivoMs;
    }

    // =====================================================================
    // N.1  SEGURIDAD DE CUENTA
    // =====================================================================

    /**
     * Aviso de bloqueo automatico de cuenta con enlace de recuperacion (RF-1.4 + RF-1.5).
     *
     * Es la CONTRAPARTE de la respuesta HTTP uniforme: como el login ya no le
     * dice a nadie que la cuenta quedo bloqueada, el email es el unico canal por
     * el que se entera el titular. El atacante no lo recibe porque no controla
     * la casilla.
     */
    public void enviarAvisoBloqueo(String destinatario, String tokenRecuperacion) {
        despachar(destinatario, new Correo(
                "Enexia - Actividad inusual en tu cuenta",
                """
                Detectamos varios intentos fallidos de inicio de sesion en tu cuenta de Enexia.
                Por seguridad, la bloqueamos.

                Solo un administrador de Enexia puede desbloquearla: comunicate con la
                administracion para pedirlo. Cambiar la contrasena NO desbloquea la cuenta.

                Si NO fuiste vos, alguien esta intentando entrar. Mientras tanto podes usar el
                enlace de abajo para definir una contrasena nueva que no uses en ningun otro
                sitio (vence en 30 minutos y sirve una sola vez).

                No respondas a este correo.""",
                enlaceRecuperacion(tokenRecuperacion)));
    }

    /** Enlace de restablecimiento pedido explicitamente por el usuario (RF-1.5). */
    public void enviarRecuperacionPassword(String destinatario, String tokenRecuperacion) {
        despachar(destinatario, new Correo(
                "Enexia - Restablecer tu contrasena",
                """
                Pediste restablecer la contrasena de tu cuenta de Enexia.

                Entra al enlace de abajo para definir una nueva (vence en 30 minutos y sirve una
                sola vez).

                Si no fuiste vos, ignora este correo: tu contrasena actual sigue siendo valida.""",
                enlaceRecuperacion(tokenRecuperacion)));
    }

    /**
     * Aviso de que la contrasena cambio. Es el que le permite al titular
     * enterarse si alguien mas tomo su cuenta: llega a la unica casilla que el
     * atacante no controla.
     */
    public void enviarPasswordCambiada(String destinatario) {
        despachar(destinatario, new Correo(
                "Enexia - Tu contrasena fue cambiada",
                """
                La contrasena de tu cuenta de Enexia acaba de cambiar.

                Si fuiste vos, no tenes que hacer nada.

                Si NO fuiste vos, tu cuenta puede estar comprometida: pedi un nuevo enlace de
                recuperacion desde la pantalla de inicio de sesion y elegi una contrasena que no
                uses en ningun otro sitio.""",
                null));
    }

    /** Una sancion administrativa sobre la cuenta (RF-6.2). */
    public void enviarCuentaSuspendida(String destinatario) {
        despachar(destinatario, new Correo(
                "Enexia - Tu cuenta fue suspendida",
                """
                Un administrador de Enexia suspendio tu cuenta. Mientras dure la suspension no
                vas a poder iniciar sesion.

                Si pensas que se trata de un error, comunicate con la administracion de Enexia.""",
                null));
    }

    // =====================================================================
    // N.2  MODERACION DE EVENTOS
    // =====================================================================

    /** Resultado de la moderacion automatica de un evento (RF-2.2, DFD 2.5B / 2.5C). */
    public void enviarResultadoModeracionEvento(String destinatario, String nombreEvento,
                                                boolean aprobado, String motivo) {
        String asunto = aprobado
                ? "Enexia - Tu evento ya esta publicado"
                : "Enexia - Tu evento no paso la moderacion";

        String texto = aprobado
                ? """
                  Buenas noticias: "%s" supero la revision automatica de contenido y ya es
                  visible en el catalogo publico de Enexia.""".formatted(nombreEvento)
                : """
                  Tu evento "%s" no supero la revision automatica de contenido.

                  Motivo: %s

                  Un administrador va a revisarlo manualmente. Si se trata de un error, el
                  evento se publicara sin que tengas que volver a cargarlo.""".formatted(nombreEvento, motivo);

        despachar(destinatario, new Correo(asunto, texto, null));
    }

    /** Un administrador retiro del catalogo un evento ya publicado (RF-6.1, escenario b). */
    public void enviarEventoSuspendido(String destinatario, String nombreEvento,
                                       String motivoCodigo, String observacion) {
        despachar(destinatario, new Correo(
                "Enexia - Tu evento fue suspendido",
                """
                Un administrador de Enexia suspendio tu evento "%s" y dejo de ser visible en el
                catalogo publico.

                Motivo: %s

                Observacion del administrador: %s

                Podes ver el detalle desde tu panel de organizador. Si pensas que se trata de un
                error, comunicate con la administracion de Enexia."""
                        .formatted(nombreEvento, etiquetaMotivoSuspension(motivoCodigo), observacion),
                null));
    }

    /** Un administrador revirtio una suspension: el evento vuelve al catalogo. */
    public void enviarEventoReactivado(String destinatario, String nombreEvento) {
        despachar(destinatario, new Correo(
                "Enexia - Tu evento volvio a estar publicado",
                """
                Un administrador de Enexia reviso la suspension de tu evento "%s" y la revirtio.

                El evento volvio a ser visible en el catalogo publico."""
                        .formatted(nombreEvento),
                null));
    }

    // =====================================================================
    // N.3  INSCRIPCIONES Y CICLO DE VIDA DEL EVENTO
    // =====================================================================

    /** Confirmacion al participante de que su lugar quedo reservado (RF-3.1, RF-3.2). */
    public void enviarInscripcionConfirmada(Destinatario destinatario, Long idInscripcion,
                                            String nombreEvento, LocalDate fecha, LocalTime hora,
                                            String tipoTicket) {
        despacharUnico(NotificacionTipo.INSCRIPCION_CONFIRMADA, destinatario, idInscripcion, new Correo(
                "Enexia - Inscripcion confirmada",
                """
                Tu inscripcion a "%s" esta confirmada.

                Fecha: %s a las %s hs
                Entrada: %s

                Tu codigo de ingreso esta en la seccion "Mis inscripciones" de tu cuenta."""
                        .formatted(nombreEvento, formatear(fecha), formatear(hora), tipoTicket),
                null));
    }

    /** "Esta por comenzar" (10 minutos antes por defecto), para quienes tienen una inscripcion confirmada. */
    public void enviarRecordatorio(Destinatario destinatario, Long idInscripcion,
                                   String nombreEvento, LocalDate fecha, LocalTime hora) {
        despacharUnico(NotificacionTipo.RECORDATORIO_10MIN, destinatario, idInscripcion, new Correo(
                "Enexia - \"" + nombreEvento + "\" esta por comenzar",
                """
                "%s" esta por comenzar: empieza hoy a las %s hs (%s).

                Tene a mano tu codigo de ingreso, que esta en "Mis inscripciones"."""
                        .formatted(nombreEvento, formatear(hora), formatear(fecha)),
                null));
    }

    /**
     * El organizador dio de baja un evento con inscriptos. Es el unico caso en
     * que no avisar tiene un costo real para el participante.
     */
    public void enviarEventoCancelado(List<Destinatario> destinatarios, Long idEvento,
                                      String nombreEvento) {
        despacharMasivo(NotificacionTipo.EVENTO_CANCELADO, destinatarios, idEvento, new Correo(
                "Enexia - Se cancelo \"" + nombreEvento + "\"",
                """
                El organizador cancelo el evento "%s", al que estabas inscripto.

                Ya no se va a realizar. Lamentamos las molestias."""
                        .formatted(nombreEvento),
                null));
    }

    /** Invitacion a valorar un evento que ya termino (RF-3.4). */
    public void enviarInvitacionValorar(List<Destinatario> destinatarios, Long idEvento,
                                        String nombreEvento) {
        despacharMasivo(NotificacionTipo.INVITACION_VALORAR, destinatarios, idEvento, new Correo(
                "Enexia - Conta como te fue en \"" + nombreEvento + "\"",
                """
                "%s" ya termino. Tu opinion ayuda a otros participantes y al organizador.

                Podes dejar tu valoracion desde la pagina del evento."""
                        .formatted(nombreEvento),
                urlBaseFrontend + "/evento-detalle.html?id=" + idEvento));
    }

    // =====================================================================
    // N.4  EQUIPO DE ORGANIZACION
    // =====================================================================

    /** Aviso al usuario que fue sumado a una organizacion desde "Mi Equipo" (RF-7.2). */
    public void enviarAgregadoAEquipo(String destinatario, String organizacion, String rolEnEmpresa) {
        despachar(destinatario, new Correo(
                "Enexia - Te sumaron al equipo de " + organizacion,
                """
                Te agregaron como %s al equipo de la organizacion "%s" en Enexia.

                Desde tu panel de organizador ya podes ver el equipo y los eventos de la
                organizacion.

                Si no esperabas esta invitacion, comunicate con la administracion de Enexia."""
                        .formatted(rolEnEmpresa, organizacion),
                null));
    }

    // =====================================================================
    // N.5  DESPACHO
    // =====================================================================

    /** Aviso unico por accion de una persona: sin libro de idempotencia. */
    private void despachar(String destinatario, Correo correo) {
        programar(() -> entregar(destinatario, correo, null));
    }

    /** Aviso que un reintento o un reloj podrian repetir: se reserva en el libro antes de mandar. */
    private void despacharUnico(NotificacionTipo tipo, Destinatario destinatario, Long referencia,
                                Correo correo) {
        programar(() -> enviarReservado(tipo, destinatario, referencia, correo));
    }

    /** Mismo aviso a muchas personas, de a uno y con una pausa corta para cuidar la cuota del proveedor. */
    private void despacharMasivo(NotificacionTipo tipo, List<Destinatario> destinatarios,
                                 Long referencia, Correo correo) {
        if (destinatarios == null || destinatarios.isEmpty()) {
            return;
        }
        List<Destinatario> copia = List.copyOf(destinatarios);
        programar(() -> {
            for (Destinatario destinatario : copia) {
                enviarReservado(tipo, destinatario, referencia, correo);
                if (!pausar()) {
                    return;
                }
            }
        });
    }

    private void enviarReservado(NotificacionTipo tipo, Destinatario destinatario, Long referencia,
                                 Correo correo) {
        Long idLibro;
        try {
            idLibro = notificaciones.reservar(tipo, destinatario.idUsuario(), referencia).orElse(null);
        } catch (DataIntegrityViolationException duplicadoConcurrente) {
            idLibro = null;
        } catch (RuntimeException ex) {
            // Si el libro falla se prefiere NO mandar: un reintento posterior lo
            // vuelve a intentar, mientras que mandar a ciegas puede duplicar.
            log.error("No se pudo reservar el aviso {} para el usuario {}",
                    tipo, destinatario.idUsuario(), ex);
            return;
        }
        if (idLibro == null) {
            log.debug("Aviso {} ya enviado al usuario {}: se descarta", tipo, destinatario.idUsuario());
            return;
        }
        entregar(destinatario.email(), correo, idLibro);
    }

    private void entregar(String email, Correo correo, Long idLibro) {
        boolean enviado;
        try {
            enviado = brevo.enviar(email, correo.asunto(), correo.textoCompleto(),
                    CorreoHtml.aHtml(correo.texto(), correo.enlace()));
        } catch (RuntimeException ex) {
            log.error("Fallo inesperado al enviar '{}'", correo.asunto(), ex);
            enviado = false;
        }
        if (idLibro != null) {
            try {
                notificaciones.registrarResultado(idLibro, enviado);
            } catch (RuntimeException ex) {
                log.warn("No se pudo registrar el resultado del aviso {}", idLibro);
            }
        }
    }

    /**
     * Registra el trabajo para despues del commit, o lo encola ya si no hay
     * transaccion. Toda excepcion queda contenida: un correo no tumba a nadie.
     */
    private void programar(Runnable tarea) {
        Runnable contenida = () -> {
            try {
                tarea.run();
            } catch (RuntimeException ex) {
                log.error("Fallo inesperado en una tarea de correo", ex);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    encolar(contenida);
                }
            });
        } else {
            encolar(contenida);
        }
    }

    private void encolar(Runnable tarea) {
        try {
            ejecutor.execute(tarea);
        } catch (RejectedExecutionException ex) {
            // Cola llena: se descarta con un log. Mejor perder un correo que
            // bloquear el hilo de una peticion o tirar un 500.
            log.warn("La cola de correo esta llena: se descarto un envio");
        }
    }

    private boolean pausar() {
        if (pausaMasivoMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(pausaMasivoMs);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // =====================================================================
    // AUXILIARES
    // =====================================================================

    private String enlaceRecuperacion(String token) {
        return urlBaseFrontend + "/pages/auth/recuperar.html?token=" + token;
    }

    private static String formatear(LocalDate fecha) {
        return fecha == null ? "-" : fecha.format(FORMATO_FECHA);
    }

    private static String formatear(LocalTime hora) {
        return hora == null ? "-" : hora.format(FORMATO_HORA);
    }

    private static String etiquetaMotivoSuspension(String codigo) {
        if (codigo == null) {
            return "-";
        }
        return switch (codigo) {
            case "DENUNCIA_FUNDADA" -> "una denuncia sobre el evento resulto fundada";
            case "FRAUDE_DETECTADO" -> "se detecto una irregularidad grave en el evento";
            case "INFRACCION_POST_PUBLICACION" -> "el evento infringe las normas de la plataforma";
            default -> codigo;
        };
    }
}
