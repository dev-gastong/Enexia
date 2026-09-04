package com.enexia.rg.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.enexia.rg.security.JwtAccessDeniedHandler;
import com.enexia.rg.security.JwtAuthenticationEntryPoint;
import com.enexia.rg.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Configuracion central de Spring Security (RF-1.3).
 *
 * Define tres cosas: como se hashean las contrasenas, que endpoints son
 * publicos, y donde se engancha el filtro de JWT.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // habilita @PreAuthorize / @PostAuthorize en services y controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    /**
     * Algoritmo de hash de contrasenas.
     *
     * BCrypt es adecuado aca por dos propiedades que un hash comun (SHA-256,
     * MD5) no tiene:
     *
     *  - Sal automatica: hashear dos veces la misma contrasena da resultados
     *    distintos. Eso inutiliza las rainbow tables y evita que se note en la
     *    base que dos usuarios comparten contrasena.
     *  - Costo configurable: es deliberadamente lento. El factor 12 significa
     *    2^12 iteraciones, unos ~250ms por hash. Imperceptible en un login real,
     *    pero convierte un ataque de fuerza bruta masivo en algo inviable.
     *
     * Subir el factor endurece el sistema pero encarece cada login; 12 es el
     * punto de equilibrio habitual hoy.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF protege formularios que autentican por cookie de sesion.
                // Enexia autentica por cabecera Authorization, que el navegador no
                // adjunta sola: no hay vector CSRF que proteger y el token de CSRF
                // solo estorbaria a los clientes de la API.
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // STATELESS: el servidor no crea ni consulta HttpSession. Cada
                // peticion se autentica sola con su JWT. Es lo que permite
                // escalar a varias instancias sin sesiones compartidas.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // --- Endpoints API publicos: son la puerta de entrada, no
                        // pueden exigir el token que todavia no existe.
                        .requestMatchers(HttpMethod.POST, "/api/auth/registro").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                        // --- Recursos estaticos (siempre publicos)
                        // Los HTML viven bajo /pages/**, y CSS/assets quedaron
                        // anidados dentro de esa carpeta (pages/css, pages/assets)
                        // en vez de en la raiz de static/.
                        .requestMatchers("/pages/css/**").permitAll()
                        .requestMatchers("/pages/assets/**").permitAll()
                        .requestMatchers("/js/**").permitAll()
                        .requestMatchers("/index.html").permitAll()
                        .requestMatchers("/").permitAll()

                        // --- Paginas de autenticacion (publicas)
                        .requestMatchers("/pages/auth/**").permitAll()

                        // --- Paginas privadas (requieren autenticacion)
                        .requestMatchers("/pages/dashboard.html").authenticated()
                        .requestMatchers("/pages/prueba-token.html").authenticated()

                        // --- Endpoints por rol (RF-1.3)
                        .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/organizador/**").hasRole("ORGANIZADOR")

                        // --- Cierre por defecto: todo lo no listado exige token
                        // valido. Es deny-by-default: si manana se agrega un
                        // endpoint y alguien olvida declararlo, queda protegido
                        // en vez de quedar abierto.
                        .anyRequest().authenticated())

                // Sin estos dos manejadores, Spring Security responde 403 y en
                // HTML tanto al que no mando token como al que no tiene permisos.
                // Aca se distingue 401 de 403 y se devuelve el mismo JSON que el
                // resto de la API.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))

                // El filtro de JWT va ANTES del de usuario/contrasena para que la
                // autenticacion ya este resuelta cuando la cadena llega ahi.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    /**
     * CORS para el frontend.
     *
     * El navegador bloquea peticiones entre origenes distintos salvo que el
     * servidor las autorice. El frontend de Enexia se sirve por separado
     * (puerto 8000 en desarrollo) y el backend corre en el 8080: son origenes
     * distintos, asi que sin esto el navegador rechaza cada llamada.
     *
     * PENDIENTE: reemplazar por el dominio real antes de desplegar. Dejar
     * localhost en produccion permitiria que una pagina local ataque la API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.of(
                "http://localhost:8000",
                "http://127.0.0.1:8000",
                "http://localhost:5500"));
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuracion.setExposedHeaders(List.of("X-Reintentar-Despues"));
        configuracion.setMaxAge(3600L);   // cachea el preflight OPTIONS por 1 hora

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }
}
