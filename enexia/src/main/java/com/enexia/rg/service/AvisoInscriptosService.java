package com.enexia.rg.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.enexia.rg.repository.InscripcionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisa a los inscriptos de un evento cuando algo le pasa al evento entero.
 *
 * ES UN SERVICE APARTE PORQUE LO USAN TRES LUGARES
 * La baja del organizador ({@code EventoService}) y los DOS caminos que pasan un
 * evento a FINALIZADO (el scheduler de {@code FinalizacionEventoService} y el
 * barrido de {@code FinalizacionEventosService}, que ademas corre al listar el
 * panel del organizador). Resolver aca "quienes son los inscriptos" evita tres
 * copias de la misma consulta que se desalinean con el tiempo.
 *
 * NO ES TRANSACCIONAL
 * Se invoca desde dentro de la transaccion que cambia el estado del evento, y
 * {@link EmailService} difiere el envio real hasta despues del commit. Si esa
 * transaccion se revierte, no sale ningun correo.
 *
 * La idempotencia vive en {@link NotificacionService}: que dos caminos
 * finalicen el mismo evento no duplica la invitacion, porque la clave es
 * (tipo, usuario, evento).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvisoInscriptosService {

    private final InscripcionRepository inscripcionRepository;
    private final EmailService emailService;

    /** El organizador dio de baja un evento que tenia inscriptos confirmados. */
    public void avisarEventoCancelado(Long idEvento, String nombreEvento) {
        List<EmailService.Destinatario> destinatarios = destinatarios(idEvento);
        if (!destinatarios.isEmpty()) {
            log.info("Evento {} dado de baja: se avisara a {} inscripto(s)", idEvento, destinatarios.size());
            emailService.enviarEventoCancelado(destinatarios, idEvento, nombreEvento);
        }
    }

    /** El evento termino: se invita a valorarlo a quienes tenian lugar confirmado (RF-3.4). */
    public void invitarAValorar(Long idEvento, String nombreEvento) {
        List<EmailService.Destinatario> destinatarios = destinatarios(idEvento);
        if (!destinatarios.isEmpty()) {
            emailService.enviarInvitacionValorar(destinatarios, idEvento, nombreEvento);
        }
    }

    private List<EmailService.Destinatario> destinatarios(Long idEvento) {
        return inscripcionRepository.destinatariosConfirmadosDeEvento(idEvento).stream()
                .map(fila -> new EmailService.Destinatario((Long) fila[0], (String) fila[1]))
                .toList();
    }
}
