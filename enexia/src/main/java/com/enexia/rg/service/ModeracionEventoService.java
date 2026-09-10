package com.enexia.rg.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.enexia.rg.dto.EventoCrearRequest;
import com.enexia.rg.event.EventoCreadoEvent;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.MotivoModeracionEvento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline asincrono de moderacion de eventos (RF-2.2, RF-2.3; DFD 2.5, 5.1 y 5.2/5.3).
 *
 * SECUENCIAL Y EN DOS FASES, EN ESE ORDEN
 *   Fase 1 - Texto: titulo y descripcion.
 *   Fase 2 - Imagenes: solo si la Fase 1 aprobo.
 *
 * El orden NO es casual y RF-2.2 lo pide de forma explicita: si el texto se
 * rechaza, las imagenes se DESCARTAN SIN PROCESAR. Subir y analizar hasta 6MB
 * para un evento que ya esta rechazado es gastar ancho de banda, cuota de
 * Cloudinary y segundos de un hilo del pool en un resultado que no se va a usar.
 *
 * POR QUE ASINCRONO
 * La moderacion tarda entre 500ms y varios segundos. Hacerla dentro de la
 * peticion HTTP dejaria al organizador mirando un spinner y expondria el alta a
 * los timeouts de un tercero. Con el pipeline aparte, el evento se crea al
 * instante en estado EN_PROCESO, aparece de inmediato en su dashboard y el
 * veredicto llega despues por notificacion.
 *
 * ESTA CLASE NO ESCRIBE EN LA BASE NI ABRE TRANSACCIONES. Solo decide. Las
 * escrituras las hace PublicacionEventoService, cada una con su transaccion
 * propia: ver ahi el porque.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModeracionEventoService {

    private final ModeracionTextoService moderacionTextoService;
    private final CloudinaryService cloudinaryService;
    private final PublicacionEventoService publicacionService;
    private final EmailService emailService;

    /**
     * Arranca el pipeline cuando la creacion del evento quedo CONFIRMADA.
     *
     * LAS DOS ANOTACIONES SON IMPRESCINDIBLES Y HACEN COSAS DISTINTAS:
     *
     * {@code @TransactionalEventListener(AFTER_COMMIT)} decide CUANDO. Un
     * {@code @EventListener} comun se ejecutaria en el instante de publicar el
     * evento, es decir con la transaccion de creacion todavia abierta: el
     * pipeline buscaria el evento por id y no lo encontraria, porque la fila sin
     * confirmar es invisible fuera de su transaccion. Esperar al commit elimina
     * esa carrera. (Ver el javadoc de EventoCreadoEvent: el error se observo de
     * verdad antes de este cambio.)
     *
     * {@code @Async} decide EN QUE HILO. Sin ella, el listener correria en el
     * mismo hilo de la peticion HTTP, justo despues del commit: el organizador
     * esperaria toda la moderacion antes de recibir su respuesta y se perderia
     * el sentido del diseno asincrono de RF-2.2.
     *
     * {@code fallbackExecution = true} hace que el listener corra igual cuando
     * NO hay transaccion, algo que pasa en las pruebas unitarias. Sin esto, un
     * test que llame al service sin transaccion veria el evento publicado y
     * nunca procesado, y el fallo seria silencioso.
     *
     * NO PROPAGA EXCEPCIONES. En un metodo asincrono que devuelve void, una
     * excepcion no llega a ningun lado: no hay quien la reciba, el hilo del pool
     * la registra y sigue. El evento quedaria EN_PROCESO para siempre sin que
     * nadie se entere. Por eso todo esta envuelto y el fallo se traduce en un
     * rechazo con motivo ERROR_PIPELINE, que el organizador y el panel de
     * administracion si ven.
     */
    @Async("ejecutorEnexia")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCrearseElEvento(EventoCreadoEvent aviso) {
        procesar(aviso.idEvento(), aviso.datos(), aviso.imagenes(), aviso.emailOrganizador());
    }

    /**
     * Pipeline propiamente dicho.
     *
     * Publico y sin anotaciones para que las pruebas unitarias puedan invocarlo
     * directo, sin montar el contexto de Spring ni una transaccion.
     */
    public void procesar(Long idEvento, EventoCrearRequest datos,
                         List<ImagenPendiente> imagenes, String emailOrganizador) {

        log.info("Moderacion iniciada para el evento {} ({} imagen(es))", idEvento, imagenes.size());

        try {
            // ===== FASE 1: TEXTO (DFD 5.1) =====
            if (contieneTextoInapropiado(datos)) {
                // Las imagenes se descartan aca mismo, sin subirse (RF-2.2).
                rechazar(idEvento, datos, emailOrganizador, MotivoModeracionEvento.MODERACION_TEXTO,
                        "El titulo o la descripcion contienen lenguaje no permitido");
                return;
            }

            // ===== FASE 2: MULTIMEDIA (DFD 5.2 / 5.3) =====
            List<String> aprobadas = new ArrayList<>();
            int rechazadas = 0;

            for (ImagenPendiente imagen : imagenes) {
                CloudinaryService.ResultadoImagen resultado =
                        cloudinaryService.subirYModerar(imagen.contenido(), imagen.nombreArchivo());

                if (resultado.aprobada()) {
                    aprobadas.add(resultado.url());
                } else {
                    rechazadas++;
                    log.warn("Imagen '{}' del evento {} rechazada: {}",
                            imagen.nombreArchivo(), idEvento, resultado.motivo());
                }
            }

            // ===== VEREDICTO (DFD 5.4.0) =====
            // RF-2.3: alcanza con UNA imagen aprobada para publicar. La logica es
            // deliberadamente permisiva: castigar el evento entero porque una de
            // tres imagenes fallo penaliza al organizador por un error menor.
            if (aprobadas.isEmpty()) {
                MotivoModeracionEvento motivo = rechazadas > 0
                        ? MotivoModeracionEvento.MODERACION_IMAGEN
                        : MotivoModeracionEvento.SIN_IMAGENES_VALIDAS;

                rechazar(idEvento, datos, emailOrganizador, motivo,
                        "Ninguna de las imagenes cargadas pudo publicarse");
                return;
            }

            publicacionService.aprobarYPersistir(idEvento, datos, aprobadas);

            emailService.enviarResultadoModeracionEvento(
                    emailOrganizador, datos.getNombre(), true, null);

        } catch (RuntimeException ex) {
            // Red de contencion: cualquier fallo inesperado deja el evento en un
            // estado explicito y revisable, nunca colgado en EN_PROCESO.
            log.error("El pipeline de moderacion fallo para el evento {}", idEvento, ex);
            intentarRechazoPorError(idEvento, datos, emailOrganizador);
        }
    }

    /**
     * Version SINCRONICA de las dos fases, para EDITAR un evento ya aprobado
     * (RF-2.7).
     *
     * POR QUE SINCRONICA Y NO POR EL PIPELINE ASINCRONO DE ARRIBA
     * Crear es asincrono porque no hay nada que mostrar todavia: el organizador
     * puede esperar el veredicto mirando "Validando...". Editar es distinto: el
     * evento YA esta publicado y visible, y {@code EstadoEventoSistemaNombre.EN_REVISION}
     * documenta la intencion de que "la version anterior siga en el catalogo
     * mientras se remodera el borrador". Sin una tabla de borradores aparte (no
     * existe en el MER), la unica forma de sostener esa promesa sin arriesgar el
     * contenido ya publicado es no tocar ninguna fila hasta tener el veredicto:
     * si la edicion se rechaza, el metodo sale con una excepcion y listo, la
     * version vieja no se entero de que hubo un intento.
     *
     * El costo es bloquear la respuesta HTTP con la subida a Cloudinary, igual
     * que ya hace el registro con la moderacion de texto (AuthService). Encaja
     * en el presupuesto de la RNF de escritura (4s, ver CLAUDE.md): 1 a 3
     * imagenes, no las decenas que si justificarian un pipeline aparte.
     *
     * @return URLs aprobadas, listas para reemplazar la multimedia vigente
     * @throws ReglaNegocioException si el texto o todas las imagenes se rechazan
     */
    public List<String> moderarSincrono(EventoCrearRequest datos, List<ImagenPendiente> imagenes) {
        if (contieneTextoInapropiado(datos)) {
            throw new ReglaNegocioException(
                    "El titulo o la descripcion contienen lenguaje no permitido");
        }

        List<String> aprobadas = new ArrayList<>();
        for (ImagenPendiente imagen : imagenes) {
            CloudinaryService.ResultadoImagen resultado =
                    cloudinaryService.subirYModerar(imagen.contenido(), imagen.nombreArchivo());

            if (resultado.aprobada()) {
                aprobadas.add(resultado.url());
            } else {
                log.warn("Imagen '{}' rechazada durante la edicion: {}",
                        imagen.nombreArchivo(), resultado.motivo());
            }
        }

        if (aprobadas.isEmpty()) {
            throw new ReglaNegocioException("Ninguna de las imagenes cargadas pudo publicarse");
        }
        return aprobadas;
    }

    /**
     * Fase 1 (DFD 5.1.1 a 5.1.6).
     *
     * LIMITE CONOCIDO Y DOCUMENTADO: RF-2.2 menciona APIs externas de moderacion
     * (Perspective, OpenAI). Se usa el filtro local de ModeracionTextoService,
     * el mismo que ya modera el registro. Ventajas: sin costo, sin latencia de
     * red y sin dependencia de terceros. Desventaja: detecta terminos de una
     * lista, no intencion ni contexto, asi que se le escapa el discurso de odio
     * escrito con vocabulario neutro. La reemplazo natural es sustituir la
     * implementacion de ModeracionTextoService; ningun otro archivo cambia.
     */
    private boolean contieneTextoInapropiado(EventoCrearRequest datos) {
        return moderacionTextoService.contieneLenguajeOfensivo(datos.getNombre())
                || moderacionTextoService.contieneLenguajeOfensivo(datos.getDescripcion());
    }

    private void rechazar(Long idEvento, EventoCrearRequest datos, String emailOrganizador,
                          MotivoModeracionEvento motivo, String explicacion) {

        publicacionService.rechazar(idEvento, motivo);

        emailService.enviarResultadoModeracionEvento(
                emailOrganizador, datos.getNombre(), false, explicacion);
    }

    /**
     * Ultimo recurso: marcar el evento como rechazado por error tecnico.
     *
     * Si hasta esto falla (por ejemplo, la base caida), solo queda el log. Se
     * atrapa igual para que una segunda excepcion no oculte la primera, que es
     * la que explica el problema real.
     */
    private void intentarRechazoPorError(Long idEvento, EventoCrearRequest datos,
                                         String emailOrganizador) {
        try {
            publicacionService.rechazar(idEvento, MotivoModeracionEvento.ERROR_PIPELINE);
            emailService.enviarResultadoModeracionEvento(emailOrganizador, datos.getNombre(),
                    false, "No pudimos completar la validacion automatica. "
                    + "Un administrador va a revisarlo.");

        } catch (RuntimeException ex) {
            log.error("Tampoco se pudo marcar como rechazado el evento {}. "
                    + "Queda EN_PROCESO y requiere intervencion manual.", idEvento, ex);
        }
    }
}
