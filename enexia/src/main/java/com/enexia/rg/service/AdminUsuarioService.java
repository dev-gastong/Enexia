package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.AdminCambioEstadoUsuarioRequest;
import com.enexia.rg.dto.AdminUsuarioResponse;
import com.enexia.rg.dto.AdminUsuarioResumenResponse;
import com.enexia.rg.dto.MensajeResponse;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.EstadoEventoOrganizadorNombre;
import com.enexia.rg.model.EstadoUsuarioNombre;
import com.enexia.rg.model.HistorialEstadoUsuario;
import com.enexia.rg.model.PersonaFisica;
import com.enexia.rg.model.RolNombre;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.UsuarioEstado;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.HistorialEstadoUsuarioRepository;
import com.enexia.rg.repository.UsuarioEstadoRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Modulo 6: gestion disciplinaria de cuentas de usuario (RF-6.2).
 *
 * El administrador pasa una cuenta a SUSPENDIDO y la devuelve a ACTIVO. El
 * efecto sobre el login no vive aca: AuthService ya rechaza, con la misma
 * respuesta silenciosa de siempre, cualquier cuenta cuyo estado no sea ACTIVO.
 *
 * REGLAS (todas se aplican en el servidor, el panel solo las refleja):
 *   - SUSPENDER parte de ACTIVO o BLOQUEADO. Suspender un BLOQUEADO importa:
 *     el titular puede reactivar un bloqueo con su enlace de recuperacion
 *     (RecuperacionCuentaService), pero NO una suspension, que es una sancion.
 *   - REACTIVAR parte de SUSPENDIDO o BLOQUEADO y deja intentos_fallidos en 0,
 *     si no la cuenta volveria a bloquearse al primer error de tipeo.
 *   - DE_BAJA es decision del propio titular (RF-1.6): el administrador no la
 *     revierte ni la sanciona.
 *   - Nunca sobre otro ADMINISTRADOR ni sobre la propia cuenta: una suspension
 *     por error del unico administrador dejaria el panel inaccesible.
 *
 * CONCURRENCIA: mismo patron que AdminEventoService. Bloqueo pesimista de la
 * fila ANTES de leer el estado, y READ_COMMITTED para que espere su turno en
 * vez de abortar por snapshot isolation. La cuenta puede estar siendo tocada
 * a la vez por el login (contador de intentos) o por otro administrador.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUsuarioService {

    public static final int TAMANO_PAGINA_MAXIMO = 100;

    static final String MENSAJE_SUSPENDIDA =
            "Cuenta de usuario suspendida. El inicio de sesión ha sido bloqueado.";
    static final String MENSAJE_REACTIVADA =
            "Cuenta de usuario reactivada. Permisos de acceso restaurados.";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioEstadoRepository usuarioEstadoRepository;
    private final HistorialEstadoUsuarioRepository historialRepository;
    private final EventoRepository eventoRepository;
    private final EventoService eventoService;
    private final AuditoriaService auditoriaService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public Page<AdminUsuarioResponse> listar(String emailAdmin, String texto, String estado, Pageable paginado) {

        String estadoFiltro = normalizarEstado(estado);

        Page<Long> ids = usuarioRepository.buscarIdsParaAdmin(
                eventoService.normalizarFiltro(texto), estadoFiltro, paginado);

        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), paginado, ids.getTotalElements());
        }

        // El IN no garantiza orden: se reordena segun la pagina de ids, que ya
        // viene con el criterio pedido por el cliente.
        Map<Long, Usuario> porId = usuarioRepository.cargarParaAdmin(ids.getContent()).stream()
                .collect(Collectors.toMap(Usuario::getIdUsuario, u -> u));

        Map<Long, Long> eventosPorUsuario = new HashMap<>();
        for (Object[] fila : eventoRepository.contarPorOrganizador(
                ids.getContent(), EstadoEventoOrganizadorNombre.DADO_DE_BAJA.name())) {
            eventosPorUsuario.put((Long) fila[0], (Long) fila[1]);
        }

        Usuario administrador = usuarioRepository.buscarActivoPorEmailConRoles(emailAdmin)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        List<AdminUsuarioResponse> contenido = ids.getContent().stream()
                .map(porId::get)
                .filter(u -> u != null)
                .map(u -> aRespuesta(u, administrador, eventosPorUsuario.getOrDefault(u.getIdUsuario(), 0L)))
                .toList();

        return new PageImpl<>(contenido, paginado, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminUsuarioResumenResponse resumen() {
        return new AdminUsuarioResumenResponse(
                usuarioRepository.count(),
                usuarioRepository.contarPorEstado(EstadoUsuarioNombre.SUSPENDIDO.name()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MensajeResponse cambiarEstado(String emailAdmin, Long idUsuario,
                                         AdminCambioEstadoUsuarioRequest datos,
                                         HttpServletRequest request) {

        Usuario administrador = usuarioRepository.buscarActivoPorEmailConRoles(emailAdmin)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        Usuario objetivo = usuarioRepository.bloquearParaActualizarSeguridad(idUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el usuario"));

        if (!esGestionable(objetivo, administrador)) {
            throw new OperacionNoPermitidaException(
                    "No se puede modificar el estado de esta cuenta");
        }

        String estadoActual = objetivo.getEstadoUsuario() == null
                ? null : objetivo.getEstadoUsuario().getEstadoUsuario();

        boolean suspender = "SUSPENDER".equals(datos.getAccion());
        EstadoUsuarioNombre destino = suspender
                ? EstadoUsuarioNombre.SUSPENDIDO
                : EstadoUsuarioNombre.ACTIVO;

        validarTransicion(estadoActual, destino);

        UsuarioEstado filaDestino = usuarioEstadoRepository
                .findByEstadoUsuarioIgnoreCase(destino.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo usuario_estado no tiene la fila " + destino.name()));

        objetivo.setEstadoUsuario(filaDestino);
        if (!suspender) {
            objetivo.setIntentosFallidos(0);
        }
        usuarioRepository.save(objetivo);

        HistorialEstadoUsuario historial = new HistorialEstadoUsuario();
        historial.setUsuario(objetivo);
        historial.setEstadoUsuario(filaDestino);
        historial.setEstadoUsuarioSistema(objetivo.getEstadoUsuarioSistema());
        historial.setFechaCambio(LocalDateTime.now());
        historialRepository.save(historial);

        auditoriaService.registrar(administrador,
                suspender ? AuditoriaService.ACCION_USUARIO_SUSPENDIDO_ADMIN
                          : AuditoriaService.ACCION_USUARIO_REACTIVADO_ADMIN,
                "Usuario " + idUsuario + ": " + estadoActual + " -> " + destino.name(),
                request);

        // El titular se entera por la unica casilla que controla; el login sigue
        // respondiendo igual para todos (ver AuthService). Sale tras el commit.
        if (suspender) {
            emailService.enviarCuentaSuspendida(objetivo.getEmail());
        }

        log.info("Cuenta {} pasada de {} a {} por el administrador {}",
                idUsuario, estadoActual, destino, administrador.getIdUsuario());

        return new MensajeResponse(suspender ? MENSAJE_SUSPENDIDA : MENSAJE_REACTIVADA);
    }

    /** Ya suspendida no admite suspenderse de nuevo; ya activa no admite reactivarse; DE_BAJA no se toca. */
    private void validarTransicion(String estadoActual, EstadoUsuarioNombre destino) {
        if (EstadoUsuarioNombre.DE_BAJA.name().equalsIgnoreCase(estadoActual)) {
            throw new OperacionNoPermitidaException(
                    "La cuenta fue dada de baja por su titular y no se puede modificar");
        }
        if (destino.name().equalsIgnoreCase(estadoActual)) {
            throw new OperacionNoPermitidaException(
                    destino == EstadoUsuarioNombre.SUSPENDIDO
                            ? "La cuenta ya esta suspendida"
                            : "La cuenta ya esta activa");
        }
    }

    /** Fuente unica de la regla "se puede gestionar", usada por el listado y por el cambio de estado. */
    private boolean esGestionable(Usuario objetivo, Usuario administrador) {
        if (objetivo.getIdUsuario().equals(administrador.getIdUsuario())) {
            return false;
        }
        if (objetivo.getRoles().contains(RolNombre.ADMINISTRADOR.name())) {
            return false;
        }
        String estado = objetivo.getEstadoUsuario() == null
                ? null : objetivo.getEstadoUsuario().getEstadoUsuario();
        return !EstadoUsuarioNombre.DE_BAJA.name().equalsIgnoreCase(estado);
    }

    private AdminUsuarioResponse aRespuesta(Usuario u, Usuario administrador, long cantidadEventos) {
        PersonaFisica pf = u.getPersonaFisica();
        String nombre = pf == null ? u.getNickname()
                : (pf.getNombre() + " " + pf.getApellido()).trim();
        LocalDateTime fechaRegistro = pf != null && pf.getPersona() != null
                ? pf.getPersona().getFechaRegistro() : null;

        return AdminUsuarioResponse.builder()
                .idUsuario(u.getIdUsuario())
                .nombreCompleto(nombre)
                .nickname(u.getNickname())
                .email(u.getEmail())
                .roles(u.getRoles())
                .fechaRegistro(fechaRegistro)
                .cantidadEventos(cantidadEventos)
                .estado(u.getEstadoUsuario() == null ? null : u.getEstadoUsuario().getEstadoUsuario())
                .gestionable(esGestionable(u, administrador))
                .build();
    }

    private String normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        try {
            return EstadoUsuarioNombre.valueOf(estado.trim().toUpperCase()).name();
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("El estado '" + estado + "' no es valido");
        }
    }
}
