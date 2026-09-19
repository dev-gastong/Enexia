package com.enexia.rg.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.MiembroAltaRequest;
import com.enexia.rg.dto.MiembroResponse;
import com.enexia.rg.exception.RecursoDuplicadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.MiembrosOrganizacion;
import com.enexia.rg.model.PersonaFisica;
import com.enexia.rg.model.PersonaJuridica;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.MiembrosOrganizacionRepository;
import com.enexia.rg.repository.PersonaJuridicaRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Mi Equipo": listado y gestion de los miembros de una organizacion.
 *
 * QUE NO CUBRE ESTE SERVICIO (deuda de alcance conocida)
 * RF-7.2 deja la incorporacion de miembros diferida a un "Modulo 8" que, tal
 * como esta escrito hoy, es en realidad el modulo de planes/suscripcion
 * (RF-8.1 a RF-8.3) y no dice nada sobre membresias. No hay ninguna RF
 * formal para este flujo: las reglas de abajo son decisiones de producto
 * tomadas para poder completar el panel de organizador, documentadas aca a
 * falta de una RF que las fije.
 *
 * REGLAS DE NEGOCIO ADOPTADAS
 *   - Solo un ADMINISTRADOR de la organizacion puede agregar o quitar miembros.
 *   - No hay invitaciones por correo: agregar un miembro requiere que la
 *     persona YA tenga una cuenta activa en Enexia (se busca por email, igual
 *     que en el resto del sistema).
 *   - Una organizacion nunca puede quedar sin ningun ADMINISTRADOR: ni
 *     quitando al ultimo, ni agregando cero (eso ya lo garantiza el alta,
 *     ver PersonaJuridicaService).
 *   - No hay tabla de invitaciones ni de roles jerarquicos: rol_en_empresa
 *     es "ADMINISTRADOR" o "MIEMBRO", tal como ya lo modela el MER (columna
 *     de texto libre, sin catalogo propio).
 *
 * Separado de {@link PersonaJuridicaService} porque el alta de la organizacion
 * y la gestion de su equipo son operaciones con autorizacion distinta: crear
 * una organizacion no exige ser miembro de nada (todavia no existe); agregar
 * o quitar gente de una si exige ya ser ADMINISTRADOR de esa organizacion
 * puntual. Mezclar ambas responsabilidades en una sola clase las habria hecho
 * mas dificiles de razonar por separado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MiembrosOrganizacionService {

    private static final String ROL_ADMINISTRADOR = PersonaJuridicaService.ROL_ADMINISTRADOR;
    private static final String ROL_MIEMBRO = "MIEMBRO";

    private final MiembrosOrganizacionRepository miembrosRepository;
    private final PersonaJuridicaRepository personaJuridicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /**
     * Lista el equipo completo de una organizacion.
     *
     * Cualquier miembro puede ver la lista (no solo el ADMINISTRADOR): saber
     * quien mas administra la misma organizacion no es informacion sensible
     * dentro del equipo, y restringirlo solo complicaria la pantalla sin
     * ganar nada.
     */
    @Transactional(readOnly = true)
    public List<MiembroResponse> listar(String emailSolicitante, Long idPersonaJuridica) {
        Usuario solicitante = buscarUsuario(emailSolicitante);
        exigirMembresia(solicitante, idPersonaJuridica);

        return miembrosRepository.listarPorOrganizacion(idPersonaJuridica).stream()
                .map(this::mapear)
                .toList();
    }

    /**
     * Agrega a un usuario existente como miembro de la organizacion.
     *
     * ORDEN DE LOS CONTROLES
     *   1. El solicitante tiene que ser ADMINISTRADOR de esa organizacion.
     *   2. El email tiene que corresponder a una cuenta activa.
     *   3. Esa persona todavia no puede ser miembro (no se duplica la fila).
     */
    @Transactional
    public MiembroResponse agregar(String emailSolicitante, Long idPersonaJuridica,
                                   MiembroAltaRequest peticion, HttpServletRequest request) {

        Usuario solicitante = buscarUsuario(emailSolicitante);
        MiembrosOrganizacion membresiaSolicitante = exigirAdministrador(solicitante, idPersonaJuridica);
        PersonaJuridica organizacion = membresiaSolicitante.getPersonaJuridica();

        Usuario nuevoMiembro = usuarioRepository.buscarActivoPorEmailConRoles(peticion.getEmail().trim())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ninguna cuenta activa con ese email"));

        if (miembrosRepository.buscarMembresia(nuevoMiembro.getIdUsuario(), idPersonaJuridica).isPresent()) {
            throw new RecursoDuplicadoException("Esa persona ya es miembro de la organizacion");
        }

        String rol = peticion.getRolEnEmpresa() == null || peticion.getRolEnEmpresa().isBlank()
                ? ROL_MIEMBRO
                : peticion.getRolEnEmpresa();

        MiembrosOrganizacion membresia = new MiembrosOrganizacion();
        membresia.setUsuario(nuevoMiembro);
        membresia.setPersonaJuridica(organizacion);
        membresia.setRolEnEmpresa(rol);
        miembrosRepository.save(membresia);

        auditoriaService.registrar(solicitante, AuditoriaService.ACCION_MIEMBRO_AGREGADO,
                "Se agrego a " + nuevoMiembro.getEmail() + " como " + rol
                        + " de la organizacion " + organizacion.getRazonSocial(), request);

        log.info("Usuario {} agregado como {} de la organizacion {} por el usuario {}",
                nuevoMiembro.getIdUsuario(), rol, idPersonaJuridica, solicitante.getIdUsuario());

        return mapear(membresia);
    }

    /**
     * Quita a un miembro de la organizacion.
     *
     * NO SE PUEDE QUITAR AL ULTIMO ADMINISTRADOR: dejaria la organizacion sin
     * nadie que pueda operarla, exactamente el escenario que la atomicidad
     * del alta (ver PersonaJuridicaService) ya evita en el momento de crearla.
     * Un ADMINISTRADOR SI puede quitarse a si mismo si hay otro administrador
     * que se quede a cargo.
     */
    @Transactional
    public void quitar(String emailSolicitante, Long idPersonaJuridica, Long idUsuarioAEliminar,
                       HttpServletRequest request) {

        Usuario solicitante = buscarUsuario(emailSolicitante);
        MiembrosOrganizacion membresiaSolicitante = exigirAdministrador(solicitante, idPersonaJuridica);
        PersonaJuridica organizacion = membresiaSolicitante.getPersonaJuridica();

        MiembrosOrganizacion membresiaAEliminar = miembrosRepository
                .buscarMembresia(idUsuarioAEliminar, idPersonaJuridica)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Esa persona no es miembro de la organizacion"));

        if (ROL_ADMINISTRADOR.equals(membresiaAEliminar.getRolEnEmpresa())
                && miembrosRepository.contarAdministradores(idPersonaJuridica) <= 1) {
            throw new ReglaNegocioException(
                    "No se puede quitar al unico administrador de la organizacion. "
                    + "Asigna otro administrador antes de quitar a este.");
        }

        String emailEliminado = membresiaAEliminar.getUsuario().getEmail();
        miembrosRepository.delete(membresiaAEliminar);

        auditoriaService.registrar(solicitante, AuditoriaService.ACCION_MIEMBRO_ELIMINADO,
                "Se quito a " + emailEliminado + " de la organizacion " + organizacion.getRazonSocial(),
                request);

        log.info("Usuario {} quitado de la organizacion {} por el usuario {}",
                idUsuarioAEliminar, idPersonaJuridica, solicitante.getIdUsuario());
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));
    }

    /** Exige que el solicitante sea miembro (de cualquier rol) de la organizacion. */
    private MiembrosOrganizacion exigirMembresia(Usuario solicitante, Long idPersonaJuridica) {
        return miembrosRepository.buscarMembresia(solicitante.getIdUsuario(), idPersonaJuridica)
                // 404 y no 403: ver el javadoc de RecursoNoEncontradoException.
                // Confirmar o negar la existencia de una organizacion ajena no
                // es informacion que un usuario cualquiera deba poder sondear.
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la organizacion indicada entre las tuyas"));
    }

    /** Exige que el solicitante sea, especificamente, ADMINISTRADOR de la organizacion. */
    private MiembrosOrganizacion exigirAdministrador(Usuario solicitante, Long idPersonaJuridica) {
        MiembrosOrganizacion membresia = exigirMembresia(solicitante, idPersonaJuridica);

        if (!ROL_ADMINISTRADOR.equals(membresia.getRolEnEmpresa())) {
            throw new ReglaNegocioException(
                    "Solo un administrador de la organizacion puede gestionar su equipo");
        }
        return membresia;
    }

    private MiembroResponse mapear(MiembrosOrganizacion membresia) {
        Usuario usuario = membresia.getUsuario();
        PersonaFisica pf = usuario.getPersonaFisica();

        return MiembroResponse.builder()
                .idUsuario(usuario.getIdUsuario())
                .nombre(pf == null ? null : pf.getNombre())
                .apellido(pf == null ? null : pf.getApellido())
                .email(usuario.getEmail())
                .nickname(usuario.getNickname())
                .rolEnEmpresa(membresia.getRolEnEmpresa())
                .build();
    }
}
