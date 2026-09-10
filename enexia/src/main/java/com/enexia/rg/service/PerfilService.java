package com.enexia.rg.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.PerfilActualizarRequest;
import com.enexia.rg.dto.PerfilResponse;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.model.PersonaFisica;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.PersonaFisicaRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Mi Perfil": consulta y edicion de los datos propios del usuario
 * autenticado (Modulo 1).
 *
 * Separado de {@link AuthService} a proposito: ese servicio cubre el flujo
 * de autenticacion (login, alta de cuenta); este cubre la gestion de una
 * cuenta que ya existe y ya inicio sesion. Mezclarlos habria hecho crecer
 * AuthService con una responsabilidad que no es la suya.
 *
 * Alcance deliberadamente acotado: solo nombre, apellido y fecha de
 * nacimiento son editables (ver el javadoc de {@link PerfilActualizarRequest}
 * para el porque de dejar email/nickname/DNI fuera). La edicion de la
 * organizacion (Persona_Juridica) y la gestion de miembros no tienen
 * endpoint todavia -- quedan fuera de este servicio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final PersonaFisicaRepository personaFisicaRepository;
    private final ModeracionTextoService moderacionService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public PerfilResponse obtenerPerfil(String email) {
        Usuario usuario = buscarUsuario(email);
        return mapear(usuario);
    }

    /**
     * Actualiza nombre, apellido y fecha de nacimiento.
     *
     * Sin bloqueo pessimista: a diferencia del contador de intentos de login,
     * esto no es un leer-modificar-escribir concurrente que deba serializarse
     * (nadie mas escribe estos tres campos), asi que un {@code @Transactional}
     * simple alcanza.
     */
    @Transactional
    public PerfilResponse actualizarPerfil(String email, PerfilActualizarRequest peticion,
                                            HttpServletRequest request) {

        Usuario usuario = buscarUsuario(email);
        PersonaFisica personaFisica = usuario.getPersonaFisica();

        if (personaFisica == null) {
            // No deberia poder pasar: toda Usuario nace con una PersonaFisica
            // (ver AuthService.crearCuentaPersonaFisica). Se deja como regla de
            // negocio y no como 500 para que, si algun dia aparece, el cliente
            // reciba un mensaje entendible en vez de un error generico.
            throw new ReglaNegocioException("Esta cuenta no tiene datos personales asociados");
        }

        moderacionService.validar(peticion.getNombre(), "nombre");
        moderacionService.validar(peticion.getApellido(), "apellido");

        personaFisica.setNombre(peticion.getNombre().trim());
        personaFisica.setApellido(peticion.getApellido().trim());
        personaFisica.setFechaNacimiento(peticion.getFechaNacimiento());
        personaFisicaRepository.save(personaFisica);

        auditoriaService.registrar(usuario, AuditoriaService.ACCION_PERFIL_ACTUALIZADO,
                "Datos personales actualizados", request);

        log.info("Perfil actualizado para el usuario {}", usuario.getIdUsuario());

        return mapear(usuario);
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));
    }

    private PerfilResponse mapear(Usuario usuario) {
        PersonaFisica pf = usuario.getPersonaFisica();

        return PerfilResponse.builder()
                .idUsuario(usuario.getIdUsuario())
                .email(usuario.getEmail())
                .nickname(usuario.getNickname())
                .roles(usuario.getRoles())
                .nombre(pf == null ? null : pf.getNombre())
                .apellido(pf == null ? null : pf.getApellido())
                .dni(pf == null ? null : pf.getDni())
                .fechaNacimiento(pf == null ? null : pf.getFechaNacimiento())
                .fechaRegistro(pf == null || pf.getPersona() == null ? null : pf.getPersona().getFechaRegistro())
                .build();
    }
}
