# Spring Security & JWT - Teoría + Referencia

**Objetivo:** Entender cómo funciona autenticación con Spring Security y JWT en Enexia.

---

## 1. Flujo General de Login

```
1. Usuario envía: email + password
                    ↓
2. AuthenticationManager recibe las credenciales
                    ↓
3. AuthenticationManager pregunta: "¿Quién es este usuario?"
                    ↓
4. DaoAuthenticationProvider usa UserDetailsService
                    ↓
5. UserDetailsService busca en BD
                    ↓
6. Si existe: compara password (BCrypt)
                    ↓
7. Si coincide: devuelve token JWT
                    ↓
8. Usuario guarda token en localStorage/sessionStorage
                    ↓
9. En requests posteriores: envía "Authorization: Bearer <token>"
                    ↓
10. JwtAuthenticationFilter valida el token
```

---

## 2. Componentes de Spring Security

### **SecurityFilterChain**
- Es una cadena de **filtros** (como guardias en una puerta)
- Cada request pasa por los filtros
- Filtros deciden: permitir, denegar, o enviar a login

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())           // Desactiva CSRF (REST API no lo necesita)
        .sessionManagement(...)                 // Configura manejo de sesiones
        .authorizeHttpRequests(...)             // Quién puede acceder a qué
        .build();
}
```

### **HttpSecurity**
- Configura reglas de acceso a endpoints

```java
.authorizeHttpRequests(auth -> {
    auth.requestMatchers("/api/auth/login").permitAll();      // Público
    auth.requestMatchers("/api/auth/register").permitAll();   // Público
    auth.requestMatchers("/api/eventos/**").authenticated();  // Solo usuarios autenticados
    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");   // Solo admin
    auth.anyRequest().denyAll();                              // Lo demás: denegar
})
```

### **AuthenticationManager**
- Es el "jefe de seguridad"
- Recibe credenciales (email/password)
- Delega a AuthenticationProvider para validar

```java
@Bean
public AuthenticationManager authenticationManager(
    AuthenticationConfiguration authConfig) throws Exception {
    return authConfig.getAuthenticationManager();
}
```

### **AuthenticationProvider**
- Implementación específica de cómo validar (con BD, LDAP, etc)
- Usa PasswordEncoder para comparar passwords

```java
@Bean
public AuthenticationProvider authenticationProvider(
    UserDetailsService userDetailsService) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder());
    return provider;
}
```

### **UserDetailsService**
- Interfaz que **TÚ implementas**
- Busca usuario en BD por username/email
- Devuelve UserDetails (usuario + roles + permisos)

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) {
        // Buscar en BD
        Usuario user = repo.findByEmail(username);
        
        // Devolver UserDetails (que entienda Spring)
        return new User(
            user.getEmail(),
            user.getPassword(),  // Ya hasheado
            user.isEnabled(),
            true, true, true,
            getAuthorities(user)  // Roles/permisos
        );
    }
}
```

### **PasswordEncoder (BCrypt)**
- Hashea contraseñas
- Valida: "¿esta contraseña coincide con el hash?"
- **Nunca** guardar plaintext

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// Uso:
String hashedPassword = passwordEncoder.encode("miPassword123");
boolean matches = passwordEncoder.matches("miPassword123", hashedPassword);  // true
```

---

## 3. JWT (JSON Web Tokens)

### **¿Qué es?**
Un token que contiene información del usuario, firmado criptográficamente.

### **Estructura**
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9 . eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ . SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
      ↓ HEADER                              ↓ PAYLOAD                                    ↓ SIGNATURE
```

- **HEADER:** Algoritmo (HS256, RS256, etc)
- **PAYLOAD:** Datos del usuario (email, roles, id)
- **SIGNATURE:** Firma para validar que no fue modificado

### **Ventajas sobre Sessions**
| Sesiones | JWT |
|----------|-----|
| Se guardan en servidor | Stateless (no necesita BD) |
| Escalado difícil | Escalable (múltiples servidores) |
| Cookie automática | Manual en header Authorization |

### **Campos típicos en JWT**
```json
{
  "sub": "usuario@email.com",      // subject (identificador)
  "roles": ["PARTICIPANTE"],        // roles
  "iat": 1516239022,                // issued at (cuándo se creó)
  "exp": 1516242622                 // expiration (cuándo expira)
}
```

---

## 4. JwtAuthenticationFilter (Custom)

- Filtro personalizado que validamos en cada request
- Extrae token del header "Authorization: Bearer <token>"
- Valida que sea válido y no esté expirado
- Si es válido: establece el usuario en SecurityContext

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        
        // 1. Extraer token del header
        String token = extractToken(request);
        
        // 2. Validar token
        if (token != null && jwtProvider.isTokenValid(token)) {
            String email = jwtProvider.getEmailFromToken(token);
            List<String> roles = jwtProvider.getRolesFromToken(token);
            
            // 3. Crear Authentication
            Authentication auth = new UsernamePasswordAuthenticationToken(
                email, null, convertRolesToAuthorities(roles)
            );
            
            // 4. Guardar en SecurityContext
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);  // Quitar "Bearer "
        }
        return null;
    }
}
```

---

## 5. JwtProvider (Utility para crear/validar JWT)

```java
@Component
public class JwtProvider {
    
    @Value("${application.security.jwt.secret-key}")
    private String secretKey;
    
    @Value("${application.security.jwt.expiration}")
    private long expirationTime;
    
    public String generateToken(String email, List<String> roles) {
        return Jwts.builder()
            .subject(email)
            .claim("roles", roles)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationTime))
            .signWith(SignatureAlgorithm.HS256, secretKey)
            .compact();
    }
    
    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
    
    public String getEmailFromToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(secretKey)
            .build()
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
    }
}
```

---

## 6. AuthController (Endpoints de autenticación)

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // AuthService se encarga de la lógica
        AuthResponse response = authService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

---

## 7. AuthService (Lógica de negocio)

```java
@Service
public class AuthService {
    
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    
    public AuthResponse login(String email, String password) {
        // 1. Validar credenciales
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password)
        );
        
        // 2. Obtener usuario
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        
        // 3. Generar JWT
        List<String> roles = usuario.getRoles().stream()
            .map(r -> r.getRoleEnum().name())
            .toList();
        String token = jwtProvider.generateToken(email, roles);
        
        // 4. Devolver response
        return AuthResponse.builder()
            .token(token)
            .email(email)
            .message("Login exitoso")
            .build();
    }
}
```

---

## 8. Configuración en application.properties

```properties
server.port=8080

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/enexia
spring.datasource.username=root
spring.datasource.password=password

# JWT
application.security.jwt.secret-key=miClaveSecretaMegaSuperSecreto123
application.security.jwt.expiration=86400000

# JPA
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=false
```

---

## 9. Flujo Completo (Visual)

```
CLIENTE                          SERVIDOR
  |                                |
  |--1. POST /login-----------→  AuthController
  |    (email, password)           |
  |                                |--2. authService.login()
  |                                |    |
  |                                |----3. authenticationManager
  |                                |       .authenticate()
  |                                |       |
  |                                |----4. UserDetailsService
  |                                |       .loadUserByUsername()
  |                                |       (busca en BD)
  |                                |       |
  |                                |----5. PasswordEncoder
  |                                |       .matches() ← valida password
  |                                |
  |                                |----6. JwtProvider.generateToken()
  |                                |
  |←--7. AuthResponse (token)----  |
  |    { token: "eyJhbGc..." }     |
  |                                |
  |--8. GET /eventos--------→  JwtAuthenticationFilter
  |    Header: Authorization      |
  |    Bearer eyJhbGc...           |
  |                                |--9. JwtProvider.isTokenValid()
  |                                |
  |                                |--10. Extrae email/roles
  |                                |     SecurityContext.setAuthentication()
  |                                |
  |                                |--11. EventoController
  |                                |     (usuario ya autenticado)
  |                                |
  |←--12. Eventos JSON------------ |
```

---

## 10. Cheatsheet Rápido

### **Crear usuario (register)**
```java
Usuario user = Usuario.builder()
    .email(email)
    .password(passwordEncoder.encode(password))  // ← Hashear SIEMPRE
    .estado(EstadoUsuario.ACTIVO)
    .intentosFallidos(0)
    .build();
usuarioRepository.save(user);
```

### **Validar en controlador**
```java
@PreAuthorize("hasRole('ADMIN')")  // Solo admin
public ResponseEntity<?> deleteUser(@PathVariable Long id) { ... }

@PreAuthorize("hasPermission('CREAR_EVENTOS')")  // Con permisos específicos
public ResponseEntity<?> createEvent(@RequestBody EventoRequest req) { ... }
```

### **Obtener usuario autenticado**
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String email = auth.getName();
List<String> roles = auth.getAuthorities().stream()
    .map(GrantedAuthority::getAuthority)
    .toList();
```

---

## 11. Dependencias en build.gradle

```gradle
dependencies {
    // Spring Security
    implementation 'org.springframework.boot:spring-boot-starter-security'
    
    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'
    
    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    
    // JPA + MySQL
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'com.mysql:mysql-connector-j'
}
```

---

**Próximo paso:** Adaptamos UserEntity y creamos JwtProvider.
