package com.enexia.rg.security;

import java.io.IOException;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Filtro que autentica cada peticion a partir del JWT (RF-1.3).
 *
 * POR QUE UN FILTRO Y NO UN INTERCEPTOR
 * Los filtros corren ANTES que los controllers, dentro de la cadena de Spring
 * Security. Cuando la peticion llega al controller, la autorizacion ya se
 * resolvio. Asi ningun controller necesita preguntar "quien sos".
 *
 * POR QUE OncePerRequestFilter
 * Un mismo request puede atravesar la cadena de filtros mas de una vez
 * (forwards internos, manejo de errores). Extender OncePerRequestFilter
 * garantiza una unica ejecucion por peticion y evita trabajo duplicado.
 *
 * IMPORTANTE: este filtro nunca rechaza la peticion. Si no hay token o es
 * invalido, simplemente no autentica y deja seguir. Quien decide si ese acceso
 * anonimo es aceptable es la configuracion de SecurityConfig, mas adelante en
 * la cadena. Separar "identificar" de "autorizar" mantiene cada pieza con una
 * sola responsabilidad.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String CABECERA_AUTH = "Authorization";
    private static final String PREFIJO_BEARER = "Bearer ";

    /**
     * Prefijo que Spring Security espera en las autoridades para que
     * {@code hasRole("ADMINISTRADOR")} funcione. Sin el, habria que usar
     * siempre {@code hasAuthority("ADMINISTRADOR")}.
     */
    private static final String PREFIJO_ROL = "ROLE_";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extraerToken(request);

        // La segunda condicion evita repetir trabajo si otro filtro ya autentico.
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            autenticarSiElTokenEsValido(token, request);
        }

        // Siempre continuar la cadena. Omitir esta linea colgaria la peticion.
        filterChain.doFilter(request, response);
    }

    private void autenticarSiElTokenEsValido(String token, HttpServletRequest request) {
        if (!jwtService.esValido(token)) {
            return;
        }

        String email = jwtService.extraerEmail(token);

        // El JWT es stateless: sigue firmado y vigente aunque un administrador
        // haya suspendido la cuenta (RF-6.2) o el sistema la haya bloqueado
        // (RF-1.4) DESPUES de emitirlo. Sin este chequeo, esa sancion solo
        // impediria el proximo login, y el usuario seguiria navegando e
        // inscribiendose hasta que el token venza. Se consulta el estado vigente
        // en la base en cada peticion autenticada; una cuenta que no esta ACTIVO
        // se trata como anonima y los endpoints protegidos responden 401.
        if (!cuentaHabilitada(email)) {
            log.debug("Token valido descartado: la cuenta {} ya no esta habilitada", email);
            return;
        }

        // Una cuenta ADMINISTRADOR es solo de administracion. Los tokens
        // emitidos antes de esa regla pueden traer ademas PARTICIPANTE u
        // ORGANIZADOR; se descartan aca para no esperar a que venzan.
        List<String> rolesToken = jwtService.extraerRoles(token);
        if (rolesToken.contains("ADMINISTRADOR")) {
            rolesToken = List.of("ADMINISTRADOR");
        }
        List<SimpleGrantedAuthority> autoridades = rolesToken.stream()
                .map(rol -> new SimpleGrantedAuthority(PREFIJO_ROL + rol))
                .toList();

        // Este constructor de 3 argumentos marca la autenticacion como CONFIRMADA.
        // El de 2 argumentos crearia una autenticacion pendiente de verificar.
        // Las credenciales van en null a proposito: la contrasena ya fue validada
        // en el login y no hay razon para tenerla en memoria en cada peticion.
        UsernamePasswordAuthenticationToken autenticacion =
                new UsernamePasswordAuthenticationToken(email, null, autoridades);

        // Adjunta IP y session id del request, utiles para auditoria.
        autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // A partir de aca, cualquier punto del request puede recuperar al usuario
        // con SecurityContextHolder.getContext().getAuthentication().
        SecurityContextHolder.getContext().setAuthentication(autenticacion);

        log.debug("Peticion autenticada por JWT para {}", email);
    }

    /** Falla cerrado: ante un error de base de datos la peticion queda anonima, nunca autenticada. */
    private boolean cuentaHabilitada(String email) {
        try {
            return email != null && usuarioRepository.cuentaHabilitada(email);
        } catch (RuntimeException ex) {
            log.error("No se pudo verificar el estado de la cuenta; se rechaza el token", ex);
            return false;
        }
    }

    /**
     * Lee la cabecera {@code Authorization: Bearer <token>} y devuelve solo el token.
     *
     * @return el token, o null si la cabecera falta o no tiene el formato esperado
     */
    private String extraerToken(HttpServletRequest request) {
        String cabecera = request.getHeader(CABECERA_AUTH);

        if (cabecera == null || !cabecera.startsWith(PREFIJO_BEARER)) {
            return null;
        }

        String token = cabecera.substring(PREFIJO_BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
