# Conceptos Básicos de Spring & Java Web

---

## 1. ¿Qué es un Bean?

Un **Bean** es un objeto que Spring **crea y administra** por ti.

```java
// SIN Spring (tú creas el objeto)
JwtProvider provider = new JwtProvider();
provider.setSecretKey("mi-clave");
provider.initialize();

// CON Spring (Spring lo crea automáticamente)
@Bean
public JwtProvider jwtProvider() {
    return new JwtProvider();  // Spring crea esto y lo guarda
}

// Ahora puedes inyectarlo donde quieras
@Autowired
private JwtProvider jwtProvider;  // ¡Spring lo proporciona!
```

**Ventajas:**
- ✅ Spring crea el objeto una sola vez (singleton)
- ✅ Lo inyecta donde lo necesites
- ✅ Maneja dependencias automáticamente
- ✅ Controla el ciclo de vida (creación, inicialización, destrucción)

---

## 2. ¿Qué es @Component?

Es una **anotación** que le dice a Spring: "Esto es un Bean, adminístraIo".

```java
// Opción 1: @Component (genérico, para cualquier cosa)
@Component
public class JwtAuthenticationFilter {
    // Spring automáticamente:
    // 1. Crea una instancia
    // 2. La guarda en el contenedor
    // 3. La inyecta donde se necesite
}

// Opción 2: @Service (especializado para lógica de negocio)
@Service
public class AuthService {
    // Lo mismo que @Component, pero más específico
}

// Opción 3: @Repository (especializado para acceso a datos)
@Repository
public class UsuarioRepository {
    // Lo mismo que @Component, pero para BD
}

// Opción 4: @Controller (especializado para endpoints)
@RestController
public class AuthController {
    // Lo mismo que @Component, pero para HTTP
}
```

**Todas son Beans**, solo que con nombres específicos según su función.

---

## 3. ¿Qué es Servlet?

Un **Servlet** es una clase Java que **procesa requests HTTP**.

```java
// Flujo HTTP básico:

Cliente                  Servidor (Servlet)
  |                            |
  |---GET /eventos-----→      |
  |                     doGet()
  |                      (procesa)
  |←---HTML response----  
  |                            |
```

**OncePerRequestFilter** es un tipo especial de Servlet que se ejecuta **una sola vez por cada request**.

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,    // ← El request que llega
        HttpServletResponse response,  // ← La respuesta que envías
        FilterChain filterChain        // ← Otros filtros después
    ) throws ServletException, IOException {
        
        // Tu lógica aquí
        System.out.println("Request llegó: " + request.getRequestURI());
        
        // Continúa con el siguiente filtro
        filterChain.doFilter(request, response);
    }
}
```

---

## 4. ¿Qué es HttpServletRequest?

Es un **objeto que contiene toda la información del HTTP request** que recibe el servidor.

```java
HttpServletRequest request
```

**Qué puedes hacer con él:**

```java
// Obtener información del request
String method = request.getMethod();           // GET, POST, PUT, DELETE
String uri = request.getRequestURI();          // /api/eventos/123
String ip = request.getRemoteAddr();           // IP del cliente

// Obtener headers (información adicional)
String authHeader = request.getHeader("Authorization");  // "Bearer eyJhbGc..."
String contentType = request.getHeader("Content-Type");  // "application/json"

// Obtener parámetros de query string
String email = request.getParameter("email");  // Si URL es /login?email=user@mail.com

// Obtener body (para POST/PUT)
BufferedReader reader = request.getReader();
String body = reader.readLine();  // {"email": "user@mail.com"}

// En JWT: EXTRAER EL TOKEN
String header = request.getHeader("Authorization");
if (header != null && header.startsWith("Bearer ")) {
    String token = header.substring(7);  // Quitar "Bearer "
    // Ahora validar el token
}
```

**Ejemplo real en JwtAuthenticationFilter:**

```java
private String extractToken(HttpServletRequest request) {
    // 1. Obtener header Authorization
    String header = request.getHeader("Authorization");
    
    // 2. Validar que no sea nulo
    if (header != null && header.startsWith("Bearer ")) {
        // 3. Extraer el token sin "Bearer "
        return header.substring(7);
        // Si header es "Bearer eyJhbGc..." 
        // devuelve "eyJhbGc..."
    }
    
    return null;
}
```

---

## 5. ¿Qué es HttpServletResponse?

Es un **objeto para construir la respuesta HTTP** que envías al cliente.

```java
HttpServletResponse response
```

**Qué puedes hacer con él:**

```java
// Establecer código de estado
response.setStatus(200);                    // OK
response.setStatus(401);                    // Unauthorized
response.setStatus(403);                    // Forbidden

// Establecer headers
response.setHeader("Content-Type", "application/json");
response.setHeader("Authorization", "Bearer token123");

// Escribir body
PrintWriter out = response.getWriter();
out.println("{\"error\": \"Token inválido\"}");

// Redirigir
response.sendRedirect("/login");
```

**Ejemplo: Rechazar request si token es inválido**

```java
if (token == null || !jwtProvider.isTokenValid(token)) {
    // Establecer error
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // 401
    response.setHeader("Content-Type", "application/json");
    
    // Escribir mensaje de error
    PrintWriter out = response.getWriter();
    out.println("{\"error\": \"Token inválido o expirado\"}");
    
    return;  // No continuar
}
```

---

## 6. FilterChain (Cadena de Filtros)

Son como **guardias en una puerta**, cada uno verifica algo:

```
Request entrante
    ↓
[Filtro 1: CORS] - ¿Es de un dominio permitido?
    ↓
[Filtro 2: JWT] ← JwtAuthenticationFilter - ¿Tiene token válido?
    ↓
[Filtro 3: CSRF] - ¿Es un ataque CSRF?
    ↓
[Controlador] - Procesar request

// Si un filtro dice NO → respuesta 401/403 y STOP
// Si todos dicen SÍ → pasar al siguiente filtro
```

**En código:**

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain  // ← La cadena
    ) throws ServletException, IOException {
        
        String token = extractToken(request);
        
        if (token != null && jwtProvider.isTokenValid(token)) {
            // ✅ Token válido
            // Establecer autenticación
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        
        // Continuar con el siguiente filtro
        filterChain.doFilter(request, response);
        
        // Si hubieras querido DETENER:
        // response.setStatus(401);
        // return;  // No llames a filterChain.doFilter()
    }
}
```

---

## 7. Ciclo Completo: Request → Filter → Controller → Response

```
1. CLIENTE ENVÍA REQUEST
   GET /api/eventos
   Header: Authorization: Bearer eyJhbGc...

2. JWTAUTHENTICATIONFILTER
   ├─ Extrae token del header
   ├─ Valida token con JwtProvider
   ├─ Si válido: crea Authentication + SecurityContext
   └─ Continúa al siguiente filtro

3. OTROS FILTROS
   ├─ CORS filter
   ├─ CSRF filter
   └─ ...

4. SPRING SECURITY (Autorización)
   ├─ Verifica si el usuario tiene permisos
   ├─ @PreAuthorize("hasRole('ADMIN')")
   └─ Si no cumple: devuelve 403 Forbidden

5. CONTROLADOR
   @GetMapping("/eventos")
   public List<Evento> getEventos() {
       // El usuario ya está autenticado aquí
       // SecurityContextHolder.getContext().getAuthentication()
       // te da la info del usuario
   }

6. RESPUESTA HTTP
   200 OK
   [{...eventos...}]
```

---

## 8. Inyección de Dependencias (@Autowired)

Spring inyecta Beans donde los necesites:

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtProvider jwtProvider;  // Spring inyecta esto automáticamente
    
    public void doFilterInternal(...) {
        // Ahora puedo usar jwtProvider
        boolean isValid = jwtProvider.isTokenValid(token);
    }
}

// Es equivalente a:
// JwtProvider jwtProvider = new JwtProvider();
// filter.setJwtProvider(jwtProvider);
// 
// Pero Spring lo hace automáticamente
```

---

## 9. Ciclo de Vida de un Bean

```
1. Spring ve @Component o @Bean
2. Crea una instancia
3. Inyecta dependencias (@Autowired)
4. Llama @PostConstruct (si existe)
5. Bean listo para usar
6. App se detiene → Llama @PreDestroy (si existe)

@Component
public class MiServicio {
    
    @Autowired
    private OtraClase dependency;  // ← Inyectada automáticamente
    
    @PostConstruct
    public void init() {
        System.out.println("Bean creado e inicializado");
        // Aquí puedo hacer setup
    }
    
    @PreDestroy
    public void destroy() {
        System.out.println("Bean destruido");
        // Aquí limpio recursos
    }
}
```

---

## 10. Cheatsheet Rápido

| Concepto | Qué es | Ejemplo |
|----------|--------|---------|
| **Bean** | Objeto que Spring crea y administra | `@Bean public JwtProvider jwtProvider()` |
| **@Component** | Marca una clase como Bean | `@Component public class MiClase {}` |
| **@Service** | Bean para lógica de negocio | `@Service public class AuthService {}` |
| **@Repository** | Bean para acceso a BD | `@Repository public class UsuarioRepo {}` |
| **HttpServletRequest** | Objeto con datos del request HTTP | `request.getHeader("Authorization")` |
| **HttpServletResponse** | Objeto para construir respuesta HTTP | `response.setStatus(401)` |
| **Servlet** | Clase que procesa requests HTTP | Extiende `OncePerRequestFilter` |
| **FilterChain** | Cadena de filtros que procesan request | `filterChain.doFilter(request, response)` |
| **@Autowired** | Inyecta un Bean | `@Autowired private JwtProvider jwtProvider` |
| **OncePerRequestFilter** | Filtro que se ejecuta 1 vez por request | Extiende para validar JWT |

---

## 11. Ejemplo Práctico: JwtAuthenticationFilter

```java
@Component  // ← Spring lo crea como Bean
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired  // ← Spring inyecta esto
    private JwtProvider jwtProvider;
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,   // ← Datos del cliente
        HttpServletResponse response, // ← Respuesta que envío
        FilterChain filterChain       // ← Siguiente filtro
    ) throws ServletException, IOException {
        
        try {
            // 1. Extraer token del request
            String token = extractToken(request);
            
            // 2. Validar token
            if (token != null && jwtProvider.isTokenValid(token)) {
                // 3. Token válido → obtener email y roles
                String email = jwtProvider.getEmailFromToken(token);
                
                // 4. Crear objeto de autenticación
                Authentication auth = new UsernamePasswordAuthenticationToken(
                    email, null, Collections.emptyList()
                );
                
                // 5. Guardar en SecurityContext (para uso posterior)
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            
        } catch (JwtException e) {
            // Token inválido
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Token inválido\"}");
            return;
        }
        
        // 6. Continuar con el siguiente filtro
        filterChain.doFilter(request, response);
    }
    
    private String extractToken(HttpServletRequest request) {
        // Obtener header Authorization
        String header = request.getHeader("Authorization");
        
        // Validar formato "Bearer <token>"
        if (header != null && header.startsWith("Bearer ")) {
            // Extraer token sin "Bearer "
            return header.substring(7);
        }
        
        return null;
    }
}
```

---

**Próximo paso:** Crear la clase JwtProvider con métodos para generar y validar tokens.
