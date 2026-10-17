package com.enexia.rg.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.model.EstadoEventoOrganizadorNombre;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.Inscripcion;
import com.enexia.rg.model.NotificacionTipo;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.InscripcionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Recordatorio "tu evento empieza en 10 minutos" (DFD N.3, rama A).
 *
 * LA VENTANA, NO EL INSTANTE
 * Se avisa cuando {@code ahora < inicio <= ahora + 10 min}, no cuando falta
 * exactamente 10. Si el scheduler pierde un tick (reinicio, carga) un instante
 * exacto se perderia para siempre; con una ventana el aviso sale en el tick
 * siguiente, un poco tarde pero sale. El efecto es que cada inscripcion cae en
 * la ventana unas diez veces: lo que impide el correo repetido es el libro de
 * {@link NotificacionService} (clave usuario + inscripcion), no esta clase.
 *
 * POR FUNCION, NO POR EVENTO
 * Un evento con varias fechas tiene un cronograma por fecha, y la inscripcion
 * cuelga de un cronograma. Cada fecha avisa antes de empezar, no solo la primera.
 *
 * SOLO LO QUE SIGUE EN PIE
 * Inscripciones CONFIRMADAS de eventos PUBLICADOS y aprobados. Si el evento se
 * suspende o se da de baja dentro de la ventana, deja de cumplir el filtro y el
 * recordatorio ya no sale; quien cancelo su lugar tampoco lo recibe.
 *
 * EL ADELANTO SE CONFIGURA EN SEGUNDOS
 * Por defecto 600 (10 minutos). Para probar sin esperar se puede bajar a 30 o
 * a 1, junto con un {@code intervalo-ms} menor en el scheduler: con un tick por
 * minuto y una ventana de 30 s, una funcion puede empezar entre dos ticks y
 * no avisarse nunca.
 *
 * EL RELOJ ES UN PARAMETRO
 * {@code avisarProximos(ahora)} recibe la hora en vez de leerla, para poder
 * probar la ventana sin esperar. Fecha y hora del cronograma se guardan sin zona
 * (hora local de Tierra del Fuego), asi que el scheduler pasa la hora de esa
 * zona y no la de la JVM: en un servidor en UTC estaria corrida 3 horas.
 */
@Service
@Slf4j
public class RecordatorioEventoService {

    private final InscripcionRepository inscripcionRepository;
    private final EmailService emailService;
    private final long segundosAntes;

    public RecordatorioEventoService(
            InscripcionRepository inscripcionRepository,
            EmailService emailService,
            @Value("${enexia.notificaciones.recordatorio.segundos-antes:600}") long segundosAntes) {
        this.inscripcionRepository = inscripcionRepository;
        this.emailService = emailService;
        this.segundosAntes = segundosAntes;
    }

    /**
     * Encola los recordatorios de las funciones que empiezan dentro de la
     * ventana. Devuelve cuantos encolo en esta pasada (los ya avisados no se
     * cuentan porque la consulta los excluye).
     *
     * Transaccional de solo lectura para que el envio real quede diferido hasta
     * el cierre de la transaccion, igual que el resto de los avisos.
     */
    @Transactional(readOnly = true)
    public int avisarProximos(LocalDateTime ahora) {
        LocalDateTime limite = ahora.plusSeconds(segundosAntes);

        // La ventana puede cruzar la medianoche: se piden ambas fechas.
        List<LocalDate> fechas = ahora.toLocalDate().equals(limite.toLocalDate())
                ? List.of(ahora.toLocalDate())
                : List.of(ahora.toLocalDate(), limite.toLocalDate());

        List<Inscripcion> candidatas = inscripcionRepository.candidatasARecordatorio(
                fechas,
                EstadoEventoOrganizadorNombre.PUBLICADO.name(),
                EventoRepository.ESTADOS_VISIBLES,
                NotificacionTipo.RECORDATORIO_10MIN.name());

        int encolados = 0;
        for (Inscripcion inscripcion : candidatas) {
            EventoCronograma funcion = inscripcion.getCronogramaTicket().getCronograma();
            if (funcion.getFecha() == null || funcion.getHoraInicio() == null) {
                continue;
            }

            LocalDateTime inicio = LocalDateTime.of(funcion.getFecha(), funcion.getHoraInicio());
            if (!inicio.isAfter(ahora) || inicio.isAfter(limite)) {
                continue;
            }

            Usuario participante = inscripcion.getUsuario();
            emailService.enviarRecordatorio(
                    new EmailService.Destinatario(participante.getIdUsuario(), participante.getEmail()),
                    inscripcion.getIdInscripcion(),
                    funcion.getEvento().getNombre(),
                    funcion.getFecha(),
                    funcion.getHoraInicio());
            encolados++;
        }

        if (encolados > 0) {
            log.info("Recordatorio de inicio: {} aviso(s) encolado(s)", encolados);
        }
        return encolados;
    }
}
