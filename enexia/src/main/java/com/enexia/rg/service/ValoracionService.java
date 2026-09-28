package com.enexia.rg.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.ValoracionRequest;
import com.enexia.rg.dto.ValoracionResponse;
import com.enexia.rg.exception.ContenidoInapropiadoException;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoDuplicadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.Valoracion;
import com.enexia.rg.repository.EventoCronogramaRepository;
import com.enexia.rg.repository.InscripcionRepository;
import com.enexia.rg.repository.UsuarioRepository;
import com.enexia.rg.repository.ValoracionRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Modulo 3: calificacion de un cronograma ya finalizado (RF-3.4, RF-3.5; DFD 3.4/3.5).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValoracionService {

    private final ValoracionRepository valoracionRepository;
    private final InscripcionRepository inscripcionRepository;
    private final EventoCronogramaRepository cronogramaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final ModeracionTextoService moderacionTextoService;

    /**
     * Registra una valoracion, en el orden exacto que pide el DFD 3.4/3.5:
     * elegibilidad (asistencia confirmada) -> finalizacion del cronograma ->
     * unicidad -> moderacion sincrona del comentario -> persistencia.
     *
     * El rango 1-5 del puntaje (RF-3.4) lo valida {@code @Valid} en el
     * controller antes de llegar aca (@Min/@Max en {@link ValoracionRequest}):
     * no se repite la comprobacion, evita que las dos reglas puedan divergir.
     */
    @Transactional
    public ValoracionResponse crear(String email, ValoracionRequest datos, HttpServletRequest request) {
        Usuario participante = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        EventoCronograma cronograma = cronogramaRepository.findById(datos.getIdCronograma())
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el cronograma indicado"));

        // --- DFD 3.4.1: solo quien tuvo una inscripcion CONFIRMADA puede valorar.
        boolean asistio = inscripcionRepository.existeConfirmadaDeUsuarioEnCronograma(
                participante.getIdUsuario(), cronograma.getIdCronograma());
        if (!asistio) {
            throw new OperacionNoPermitidaException(
                    "Solo pueden valorar el cronograma quienes tuvieron una inscripcion confirmada");
        }

        // --- DFD 3.4.2: el cronograma ya tiene que haber finalizado.
        LocalDateTime fin = LocalDateTime.of(cronograma.getFecha(), cronograma.getHoraFin());
        if (fin.isAfter(LocalDateTime.now())) {
            throw new OperacionNoPermitidaException("El cronograma aun no finalizo");
        }

        // --- RF-3.4: restriccion unica por usuario y cronograma.
        if (valoracionRepository.existsByUsuarioIdUsuarioAndCronogramaIdCronograma(
                participante.getIdUsuario(), cronograma.getIdCronograma())) {
            throw new RecursoDuplicadoException("Ya valoraste este cronograma");
        }

        // --- RF-3.5: moderacion sincrona del comentario (DFD 3.5.1/3.5.2).
        if (moderacionTextoService.contieneLenguajeOfensivo(datos.getComentario())) {
            // REQUIRES_NEW: el intento de infraccion tiene que sobrevivir aunque
            // la excepcion de abajo revierta esta transaccion. Es seguro porque
            // solo referencia al usuario (fila ya confirmada de antes), no a
            // ninguna fila que esta transaccion todavia no confirmo -- a
            // diferencia del pago rechazado de RF-3.2, ver el javadoc de
            // RegistroInscripcionService para el caso en el que SI haria falta
            // separar en otro bean.
            auditoriaService.registrarAparte(participante,
                    AuditoriaService.ACCION_VALORACION_RECHAZADA_MODERACION,
                    "Comentario rechazado por moderacion en el cronograma " + cronograma.getIdCronograma(),
                    request);
            throw new ContenidoInapropiadoException("comentario");
        }

        Valoracion valoracion = new Valoracion();
        valoracion.setCronograma(cronograma);
        valoracion.setUsuario(participante);
        valoracion.setValor(datos.getValor());
        valoracion.setComentario(datos.getComentario());
        valoracion.setFecha(LocalDate.now());
        valoracion = valoracionRepository.save(valoracion);

        auditoriaService.registrar(participante, AuditoriaService.ACCION_VALORACION_CREADA,
                "Valoracion registrada para el cronograma " + cronograma.getIdCronograma(), request);

        log.info("Valoracion {} registrada por el usuario {} para el cronograma {}",
                valoracion.getIdvaloracion(), participante.getIdUsuario(), cronograma.getIdCronograma());

        return mapear(valoracion, cronograma);
    }

    private ValoracionResponse mapear(Valoracion valoracion, EventoCronograma cronograma) {
        return ValoracionResponse.builder()
                .idValoracion(valoracion.getIdvaloracion())
                .idCronograma(cronograma.getIdCronograma())
                .eventoNombre(cronograma.getEvento() == null ? null : cronograma.getEvento().getNombre())
                .valor(valoracion.getValor())
                .comentario(valoracion.getComentario())
                .fecha(valoracion.getFecha())
                .build();
    }
}
