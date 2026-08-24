package com.enexia.rg.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.model.EstadoUsuarioNombre;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Puente entre la tabla {@code usuario} de Enexia y el modelo de usuario que
 * entiende Spring Security.
 *
 * POR QUE EXISTE ESTA CLASE SI EL LOGIN NO LA USA
 * El login de Enexia (ver AuthService) valida credenciales a mano, porque el
 * DFD exige pasos que el flujo estandar de Spring Security no contempla:
 * cooldown, contador de intentos y penalizacion escalonada.
 *
 * Aun asi este bean hace falta por dos motivos concretos:
 *
 *  1. Si no existe ningun UserDetailsService, Spring Boot autoconfigura un
 *     usuario "user" con contrasena aleatoria que imprime en consola al
 *     arrancar. Es una cuenta real y funcional que nadie quiere en produccion.
 *  2. Es el punto de entrada estandar para cualquier mecanismo de Spring
 *     Security que se agregue despues (HTTP Basic, remember-me, form login).
 *
 * TRADUCCION DE ESTADOS
 * Enexia maneja sus propios estados en la tabla {@code usuario_estado}.
 * Spring Security solo entiende cuatro booleanos. Este metodo hace la
 * conversion entre ambos vocabularios.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final String PREFIJO_ROL = "ROLE_";

    private final UsuarioRepository usuarioRepository;

    /**
     * @param email Enexia identifica por email, no por un "username" aparte.
     *              El parametro se llama username porque asi lo define la interfaz.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // La consulta ya filtra fecha_baja IS NULL: una cuenta con baja logica
        // se comporta igual que una inexistente (RF-1.6).
        Usuario usuario = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No existe un usuario activo con el email indicado"));

        List<SimpleGrantedAuthority> autoridades = usuario.getRoles().stream()
                .map(rol -> new SimpleGrantedAuthority(PREFIJO_ROL + rol))
                .toList();

        String nombreEstado = usuario.getEstadoUsuario() != null
                ? usuario.getEstadoUsuario().getEstadoUsuario()
                : null;

        boolean estaActivo = EstadoUsuarioNombre.ACTIVO.name().equalsIgnoreCase(nombreEstado);
        boolean noBloqueado = !EstadoUsuarioNombre.BLOQUEADO.name().equalsIgnoreCase(nombreEstado);

        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getPassword())   // hash BCrypt, nunca texto plano
                .authorities(autoridades)
                .disabled(!estaActivo)             // cubre SUSPENDIDO y DE_BAJA
                .accountLocked(!noBloqueado)       // cubre BLOQUEADO
                .accountExpired(false)             // Enexia no maneja expiracion de cuenta
                .credentialsExpired(false)         // ni de credenciales
                .build();
    }
}
