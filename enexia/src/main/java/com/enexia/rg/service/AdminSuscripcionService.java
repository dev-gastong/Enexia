package com.enexia.rg.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.AdminSuscripcionAccionRequest;
import com.enexia.rg.dto.AdminSuscripcionOperacionResponse;
import com.enexia.rg.dto.AdminSuscripcionesResumenResponse;
import com.enexia.rg.dto.AdminSuscriptorResponse;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.HistorialEstadoSuscripcion;
import com.enexia.rg.model.PersonaFisica;
import com.enexia.rg.model.Suscripcion;
import com.enexia.rg.model.SuscripcionEstado;
import com.enexia.rg.model.SuscripcionEstadoNombre;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.HistorialEstadoSuscripcionRepository;
import com.enexia.rg.repository.PagoRepository;
import com.enexia.rg.repository.SuscripcionEstadoRepository;
import com.enexia.rg.repository.SuscripcionRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Modulo 6: control de suscripciones de los organizadores (RF-6.4).
 *
 * LECTURA: el plan vigente de un organizador es el de su ULTIMA suscripcion;
 * sin suscripciones, es el plan gratuito. "Usuarios activos" de cada plan
 * cuenta organizadores: Pro = ultima suscripcion PRO en estado ACTIVA;
 * Gratuito = el resto.
 *
 * INTERVENCIONES (PATCH sobre una suscripcion, DFD 6.4):
 *   - REVOCAR: ACTIVA o VENCIDA -> CANCELADA. El organizador queda con los
 *     privilegios del plan gratuito.
 *   - CAMBIAR_PLAN: solo sobre una ACTIVA; conmuta tipo_plan (FREE/PRO).
 *   - RENOVAR: solo sobre una VENCIDA PRO; vuelve a ACTIVA con una cobertura
 *     nueva desde hoy, de la misma duracion que la anterior. Omite la pasarela
 *     de pagos A PROPOSITO (es una excepcion administrativa) y por lo tanto no
 *     genera un {@code Pago}: no es ingreso.
 * Toda intervencion deja una fila en el historial CON motivo y una auditoria.
 *
 * CONCURRENCIA: bloqueo pesimista de la fila antes de leer el estado y
 * READ_COMMITTED, el mismo patron del resto de las transiciones.
 */
@Service
@Slf4j
public class AdminSuscripcionService {

    public static final int TAMANO_PAGINA_MAXIMO = 100;

    static final String PLAN_GRATUITO = "GRATUITO";
    static final String PLAN_PRO = "PRO";
    static final String TIPO_FREE = "FREE";

    /** Un periodo de cobertura de al menos esto se informa como ANUAL; menos, MENSUAL. */
    private static final long DIAS_MINIMOS_ANUAL = 300;

    static final String MOTIVO_REVOCAR = "Revocación administrativa manual por excepción técnica";
    static final String MOTIVO_CAMBIAR_PLAN = "Cambio de plan manual por el administrador";
    static final String MOTIVO_RENOVAR = "Renovación manual por el administrador (sin pasarela de pagos)";

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final SuscripcionEstadoRepository estadoRepository;
    private final HistorialEstadoSuscripcionRepository historialRepository;
    private final PagoRepository pagoRepository;
    private final EventoService eventoService;
    private final AuditoriaService auditoriaService;

    private final int eventosFree;
    private final BigDecimal precioPro;

    public AdminSuscripcionService(
            UsuarioRepository usuarioRepository,
            SuscripcionRepository suscripcionRepository,
            SuscripcionEstadoRepository estadoRepository,
            HistorialEstadoSuscripcionRepository historialRepository,
            PagoRepository pagoRepository,
            EventoService eventoService,
            AuditoriaService auditoriaService,
            @Value("${enexia.planes.free.eventos-permitidos:2}") int eventosFree,
            @Value("${enexia.planes.pro.precio-mensual:9.99}") BigDecimal precioPro) {
        this.usuarioRepository = usuarioRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.estadoRepository = estadoRepository;
        this.historialRepository = historialRepository;
        this.pagoRepository = pagoRepository;
        this.eventoService = eventoService;
        this.auditoriaService = auditoriaService;
        this.eventosFree = eventosFree;
        this.precioPro = precioPro;
    }

    // ===================================================================
    // LECTURA
    // ===================================================================

    @Transactional(readOnly = true)
    public Page<AdminSuscriptorResponse> listar(String texto, Pageable paginado) {
        Page<Long> ids = usuarioRepository.buscarIdsOrganizadoresParaAdmin(
                eventoService.normalizarFiltro(texto), paginado);

        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), paginado, ids.getTotalElements());
        }

        Map<Long, Usuario> usuarios = usuarioRepository.cargarParaAdmin(ids.getContent()).stream()
                .collect(Collectors.toMap(Usuario::getIdUsuario, u -> u));

        // De la mas vieja a la mas nueva: al recorrer, la ultima pisa a las anteriores.
        Map<Long, Suscripcion> ultimas = new HashMap<>();
        for (Suscripcion s : suscripcionRepository.buscarPorUsuarios(ids.getContent())) {
            ultimas.put(s.getUsuario().getIdUsuario(), s);
        }

        List<AdminSuscriptorResponse> filas = ids.getContent().stream()
                .map(usuarios::get)
                .filter(u -> u != null)
                .map(u -> aFila(u, ultimas.get(u.getIdUsuario())))
                .toList();

        return new PageImpl<>(filas, paginado, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminSuscripcionesResumenResponse resumen() {
        String activa = SuscripcionEstadoNombre.ACTIVA.name();

        long proActivas = suscripcionRepository.contarPorEstadoYPlan(activa, PLAN_PRO);
        long vencidas = suscripcionRepository.contarPorEstado(SuscripcionEstadoNombre.VENCIDA.name());

        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioMes = hoy.withDayOfMonth(1).atStartOfDay();
        BigDecimal ingresos = pagoRepository.sumarIngresosPorSuscripciones(
                inicioMes, inicioMes.plusMonths(1));

        long organizadores = usuarioRepository.contarOrganizadores();

        return new AdminSuscripcionesResumenResponse(proActivas, vencidas, ingresos, List.of(
                new AdminSuscripcionesResumenResponse.Plan(
                        PLAN_GRATUITO, BigDecimal.ZERO, Math.max(0, organizadores - proActivas), eventosFree),
                new AdminSuscripcionesResumenResponse.Plan(
                        PLAN_PRO, precioPro, proActivas, null)));
    }

    // ===================================================================
    // INTERVENCIONES (RF-6.4)
    // ===================================================================

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminSuscripcionOperacionResponse intervenir(String emailAdmin, Long idSuscripcion,
                                                        AdminSuscripcionAccionRequest datos,
                                                        HttpServletRequest request) {

        Usuario administrador = usuarioRepository.buscarActivoPorEmailConRoles(emailAdmin)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        Suscripcion suscripcion = suscripcionRepository.bloquearParaActualizar(idSuscripcion)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la suscripcion"));

        String estadoActual = suscripcion.getEstadoSuscripcion().getNombreEstado();
        String planActual = esPro(suscripcion) ? PLAN_PRO : TIPO_FREE;

        String mensaje;
        String accionAuditada;
        String motivoPorDefecto;
        SuscripcionEstadoNombre estadoDestino = null;

        switch (datos.getAccion()) {
            case "REVOCAR" -> {
                if (!SuscripcionEstadoNombre.ACTIVA.name().equals(estadoActual)
                        && !SuscripcionEstadoNombre.VENCIDA.name().equals(estadoActual)) {
                    throw new OperacionNoPermitidaException(
                            "Solo se puede revocar una suscripcion activa o vencida");
                }
                estadoDestino = SuscripcionEstadoNombre.CANCELADA;
                mensaje = "Plan Pro revocado manualmente. Usuario degradado a cuenta Gratuita.";
                accionAuditada = AuditoriaService.ACCION_SUSCRIPCION_REVOCADA_ADMIN;
                motivoPorDefecto = MOTIVO_REVOCAR;
            }
            case "CAMBIAR_PLAN" -> {
                if (!SuscripcionEstadoNombre.ACTIVA.name().equals(estadoActual)) {
                    throw new OperacionNoPermitidaException(
                            "Solo se puede cambiar el plan de una suscripcion activa");
                }
                if (datos.getTipoPlan() == null || datos.getTipoPlan().isBlank()) {
                    throw new ReglaNegocioException("Indique el nuevo plan");
                }
                if (datos.getTipoPlan().equals(planActual)) {
                    throw new OperacionNoPermitidaException("La suscripcion ya tiene ese plan");
                }
                suscripcion.setTipoPlan(datos.getTipoPlan());
                mensaje = "Nivel de suscripción actualizado con éxito para el organizador.";
                accionAuditada = AuditoriaService.ACCION_SUSCRIPCION_PLAN_CAMBIADO_ADMIN;
                motivoPorDefecto = MOTIVO_CAMBIAR_PLAN + ": " + planActual + " -> " + datos.getTipoPlan();
            }
            case "RENOVAR" -> {
                if (!SuscripcionEstadoNombre.VENCIDA.name().equals(estadoActual)
                        || !PLAN_PRO.equals(planActual)) {
                    throw new OperacionNoPermitidaException(
                            "Solo se puede renovar una suscripcion Pro vencida");
                }
                estadoDestino = SuscripcionEstadoNombre.ACTIVA;
                renovarCobertura(suscripcion);
                mensaje = "Suscripción vencida renovada manualmente de forma excepcional.";
                accionAuditada = AuditoriaService.ACCION_SUSCRIPCION_RENOVADA_ADMIN;
                motivoPorDefecto = MOTIVO_RENOVAR;
            }
            default -> throw new ReglaNegocioException("Accion no soportada: " + datos.getAccion());
        }

        if (estadoDestino != null) {
            final String nombreDestino = estadoDestino.name();
            SuscripcionEstado fila = estadoRepository.findByNombreEstadoIgnoreCase(nombreDestino)
                    .orElseThrow(() -> new ReglaNegocioException(
                            "El catalogo suscripcion_estado no tiene la fila " + nombreDestino));
            suscripcion.setEstadoSuscripcion(fila);
        }
        suscripcionRepository.save(suscripcion);

        String motivo = datos.getMotivo() != null && !datos.getMotivo().isBlank()
                ? datos.getMotivo().trim() : motivoPorDefecto;

        HistorialEstadoSuscripcion historial = new HistorialEstadoSuscripcion();
        historial.setSuscripcion(suscripcion);
        historial.setEstadoSuscripcion(suscripcion.getEstadoSuscripcion());
        historial.setFechaCambio(LocalDateTime.now());
        historial.setMotivoCambio(motivo.length() > 255 ? motivo.substring(0, 255) : motivo);
        historialRepository.save(historial);

        auditoriaService.registrar(administrador, accionAuditada,
                "Suscripcion " + idSuscripcion + " (usuario " + suscripcion.getUsuario().getIdUsuario() + "): "
                + estadoActual + "/" + planActual + " -> "
                + suscripcion.getEstadoSuscripcion().getNombreEstado() + "/"
                + (esPro(suscripcion) ? PLAN_PRO : TIPO_FREE), request);

        log.info("Suscripcion {} intervenida por el administrador {}: {}",
                idSuscripcion, administrador.getIdUsuario(), datos.getAccion());

        return new AdminSuscripcionOperacionResponse(mensaje, aFila(suscripcion.getUsuario(), suscripcion));
    }

    /** Nueva cobertura desde hoy, de la misma duracion que la anterior (anual si duraba 300+ dias). */
    private void renovarCobertura(Suscripcion s) {
        boolean anual = s.getFechaInicio() != null && s.getFechaFin() != null
                && ChronoUnit.DAYS.between(s.getFechaInicio(), s.getFechaFin()) >= DIAS_MINIMOS_ANUAL;
        LocalDate hoy = LocalDate.now();
        s.setFechaInicio(hoy);
        s.setFechaFin(anual ? hoy.plusYears(1) : hoy.plusMonths(1));
    }

    // ------------------------------------------------------------------ util

    private static boolean esPro(Suscripcion s) {
        return PLAN_PRO.equalsIgnoreCase(s.getTipoPlan());
    }

    private AdminSuscriptorResponse aFila(Usuario u, Suscripcion s) {
        PersonaFisica pf = u.getPersonaFisica();
        String nombre = pf == null ? u.getNickname() : (pf.getNombre() + " " + pf.getApellido()).trim();

        if (s == null) {
            LocalDate alta = pf != null && pf.getPersona() != null && pf.getPersona().getFechaRegistro() != null
                    ? pf.getPersona().getFechaRegistro().toLocalDate() : null;
            return AdminSuscriptorResponse.builder()
                    .idUsuario(u.getIdUsuario())
                    .nombreCompleto(nombre)
                    .email(u.getEmail())
                    .plan(PLAN_GRATUITO)
                    .desde(alta)
                    .monto(BigDecimal.ZERO)
                    .estado(PLAN_GRATUITO)
                    .build();
        }

        boolean pro = esPro(s);

        return AdminSuscriptorResponse.builder()
                .idUsuario(u.getIdUsuario())
                .nombreCompleto(nombre)
                .email(u.getEmail())
                .plan(pro ? PLAN_PRO : PLAN_GRATUITO)
                .desde(s.getFechaInicio())
                .hasta(s.getFechaFin())
                .facturacion(pro ? facturacion(s) : null)
                .monto(pro ? precioPro : BigDecimal.ZERO)
                .estado(s.getEstadoSuscripcion().getNombreEstado())
                .idSuscripcion(s.getIdSuscripcion())
                .build();
    }

    /** La base no guarda el ciclo de cobro: se deduce de cuanto dura la cobertura. */
    private String facturacion(Suscripcion s) {
        if (s.getFechaInicio() == null || s.getFechaFin() == null) {
            return null;
        }
        long dias = ChronoUnit.DAYS.between(s.getFechaInicio(), s.getFechaFin());
        return dias >= DIAS_MINIMOS_ANUAL ? "ANUAL" : "MENSUAL";
    }
}
