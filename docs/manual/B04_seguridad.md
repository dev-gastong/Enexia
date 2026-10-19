# B04 — Seguridad: contraseñas, JWT y control de acceso

> **Parte B · Spring Boot.** La seguridad **no es una función más**: atraviesa todo el sistema. Este capítulo explica los mecanismos que Enexia usa para saber **quién es** cada persona (*autenticación*), **qué puede hacer** (*autorización*) y para **no regalarle información a un atacante**. Cada decisión viene con la amenaza que cierra.

**Qué vas a aprender**
- Autenticación contra autorización; **hash** contra cifrado; por qué existe la **sal**.
- **BCrypt** y el costo adaptable.
- **JWT**: estructura, firma, qué contiene y qué no.
- **Spring Security**: la cadena de filtros, `SecurityFilterChain`, filtros propios, `@PreAuthorize`, roles.
- Diseño defensivo del login: respuestas indistinguibles, tiempo constante, bloqueo silencioso.
- Tokens de recuperación de un solo uso y SHA-256.
- CORS, CSRF, XSS, inyección SQL: qué son y dónde se resuelve cada una.

---

## 1. Conceptos base

| Término | Significa | Pregunta que responde |
|---|---|---|
| **Autenticación** (*authn*) | Probar **quién sos**. | "¿Esta persona es realmente `gaston_test`?" |
| **Autorización** (*authz*) | Decidir **qué podés hacer**. | "¿`gaston_test` puede suspender cuentas?" |
| **Confidencialidad** | Que solo quien debe vea el dato. | "¿Puede un tercero leer mi contraseña?" |
| **Integridad** | Que el dato no sea alterado sin que se note. | "¿Alguien modificó mi token?" |

Y dos herramientas que se confunden:

- **Cifrar** es **reversible** con una clave: lo que se cifra se puede descifrar. Sirve para datos que hay que volver a leer.
- **Hash** es **irreversible**: de un texto se obtiene una huella; de la huella no se vuelve al texto. Sirve para **verificar** sin guardar el original. **Las contraseñas se hashean, nunca se cifran ni se guardan en claro.**

---

## 2. Contraseñas: BCrypt

### 2.1 Por qué no alcanza un hash simple

Un hash rápido como SHA-256 o MD5 tiene dos problemas para contraseñas:

1. **Es demasiado rápido.** Una placa gráfica prueba miles de millones de combinaciones por segundo: se pueden **adivinar contraseñas por fuerza bruta** a escala.
2. **Es determinista.** La misma contraseña da siempre el mismo hash: dos usuarios con la misma clave se **delatan** en la base, y existen tablas precalculadas (*rainbow tables*) de los hashes de las claves comunes.

### 2.2 Qué hace BCrypt

`config/SecurityConfig.java`:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

BCrypt resuelve ambos problemas:

- **Sal automática.** Antes de hashear, agrega **un valor aleatorio** distinto en cada operación y lo guarda dentro del resultado. Hashear dos veces `Clave123` da dos resultados distintos, y las *rainbow tables* quedan inservibles.
- **Costo configurable y deliberadamente lento.** El **factor 12** significa `2^12` iteraciones, unos **~250 ms** por hash. Imperceptible para un login legítimo, pero **inviable** para un atacante que quiere probar millones.

Cómo se usa (toda la API que necesitás):

```java
String hash = passwordEncoder.encode("Contraseña1");        // al registrar: se guarda ESTO
boolean ok = passwordEncoder.matches("Contraseña1", hash);  // al hacer login: ¿coincide?
```

Un hash de BCrypt se ve así: `$2a$12$N9qo8uLOickgx2ZMRZoMye...` — `2a` es la versión, `12` el costo y el resto contiene la **sal y el hash**. Por eso `matches` no necesita la sal aparte.

### 2.3 Dónde se aplica

- **Registro:** el servicio hashea **antes** de persistir. La contraseña en claro *"solo existe en memoria durante el request"* (javadoc de `UsuarioRegistroRequest`): no se guarda ni se loguea.
- **Login:** `passwordEncoder.matches(peticion.getPassword(), usuario.getPassword())`.
- **Recuperación:** la contraseña nueva se hashea igual.

### 2.4 Límite de 72 caracteres

BCrypt **solo considera los primeros 72 bytes**. Por eso el DTO limita la contraseña a 72 con `@Size(max = 72)`: aceptar más daría una falsa sensación de seguridad (los caracteres sobrantes se ignorarían).

---

## 3. JWT: la sesión sin sesión

### 3.1 El problema

Después del login, ¿cómo sabe el servidor, en el pedido número 50, que quien llama sigue siendo `gaston_test`? Opciones:

- **Sesiones en el servidor:** el servidor guarda "la sesión 123 es de gaston_test" y le da al navegador una *cookie*. Funciona, pero el servidor **tiene estado**; para escalar a varias máquinas hay que compartirlo.
- **Tokens (*stateless*):** el servidor entrega un **token firmado** que ya trae la identidad. En cada pedido el cliente lo presenta y el servidor **verifica la firma**. **No guarda nada.** Es lo que usa Enexia (`SessionCreationPolicy.STATELESS`).

### 3.2 Anatomía de un JWT

**JWT** = *JSON Web Token*. Son **tres partes** separadas por puntos:

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJhbmFAZW5leGlhLnRlc3QiLCJyb2xlcyI6WyJQQVJUSUNJUEFOVEUiXSwi... . 4kQ0a...
        HEADER                              PAYLOAD (claims)                                  FIRMA
```

- **Header:** algoritmo (`HS256`).
- **Payload:** los **claims**, datos en JSON. Los de Enexia (`JwtService.generarToken`):

```json
{
  "sub": "ana@enexia.test",          // subject: a quién identifica (el EMAIL)
  "roles": ["ORGANIZADOR", "PARTICIPANTE"],
  "iss": "enexia",                   // issuer: quién lo emitió
  "iat": 1760000000,                 // issued at: cuándo
  "exp": 1760086400                  // expiration: hasta cuándo vale (24 h)
}
```

- **Firma:** un **HMAC-SHA256** calculado sobre `header.payload` con una **clave secreta que solo conoce el servidor**.

Header y payload están codificados en **Base64URL**, que **cualquiera puede decodificar**. Dos consecuencias que el javadoc de `JwtService` subraya:

1. **El JWT no está cifrado, solo firmado.** Nunca pongas en el payload datos sensibles (DNI, teléfono, contraseñas).
2. **Es infalsificable sin la clave.** Si alguien cambia un byte del payload (`"roles":["ADMINISTRADOR"]`), la firma deja de coincidir y la verificación falla.

Podés probar esto: copiá un token a `jwt.io` y vas a ver el contenido sin necesidad de la clave; lo que no podés es **modificarlo y que siga siendo válido**.

### 3.3 Emisión y verificación con jjwt

```java
// Emitir
return Jwts.builder()
        .subject(email)
        .claim(CLAIM_ROLES, roles)
        .issuer(emisor)
        .issuedAt(ahora)
        .expiration(expiracion)
        .signWith(claveFirma)       // calcula el HMAC y lo adjunta
        .compact();                 // header.payload.firma

// Verificar: recalcula el HMAC y lo compara; si no coincide, lanza excepción
Claims claims = Jwts.parser()
        .verifyWith(claveFirma)
        .requireIssuer(emisor)
        .build()
        .parseSignedClaims(token)
        .getPayload();
```

La **clave** viene de configuración (`enexia.security.jwt.secret-key`, Base64, mínimo 32 bytes = 256 bits). El constructor **valida el largo al arrancar** y falla ahí con un mensaje claro, en vez de fallar en el primer login. *"Si la clave está mal configurada la aplicación falla al arrancar, que es mucho más fácil de detectar."* En producción debe venir de la variable `JWT_SECRET_KEY`.

### 3.4 Límite de los JWT: no se pueden "des-emitir"

Un token **sigue siendo válido hasta que vence** (24 h), aunque la cuenta se suspenda o se cambie la contraseña. Este límite generó un **bug crítico real** (ADR-20, 2026-10-14): un usuario suspendido podía seguir navegando e inscribiéndose. La solución del proyecto: `JwtAuthenticationFilter` **consulta el estado vigente de la cuenta en la base en cada petición** y, si ya no está `ACTIVO`, trata la petición como **anónima**. Es el costo de una consulta liviana (`COUNT` por email, con índice único) a cambio de poder revocar al instante.

*Limitación aún abierta* (documentada en el DFD N.1): los JWT emitidos siguen valiendo tras **cambiar la contraseña**. La solución natural es un campo `token_version` (ver `SUGERENCIAS_PENDIENTES.md`, C5).

---

## 4. Spring Security: la cadena de filtros

### 4.1 Qué es un filtro

Un **filtro** (*servlet filter*) es una pieza que se ejecuta **antes** de que la petición llegue al controller y **después** cuando vuelve la respuesta. Spring Security es, en esencia, **una cadena de filtros** que inspecciona cada petición.

```
Petición ──► [CORS] ──► [JwtAuthenticationFilter] ──► [autorización por ruta] ──► Controller
                              ↑ nuestro filtro
```

### 4.2 `SecurityFilterChain`: las reglas

`SecurityConfig.securityFilterChain` declara **todas las reglas de acceso**:

```java
return http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/publico/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/api/organizador/**").hasRole("ORGANIZADOR")
                .requestMatchers("/api/participante/**").hasRole("PARTICIPANTE")
                .anyRequest().authenticated())
        .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
```

Lectura (las líneas **lambda** `x -> ...` son la API "fluida" de Spring Security; A03 §2):

| Regla | Qué hace |
|---|---|
| `permitAll()` | **Sin token.** Para registro, login, recuperación y el catálogo público. |
| `hasRole("ADMINISTRADOR")` | Solo tokens con ese rol. |
| **`.anyRequest().authenticated()`** | **Cierre por defecto (*deny-by-default*).** *"Si mañana se agrega un endpoint y alguien olvida declararlo, queda protegido en vez de quedar abierto."* Es un principio de seguridad: lo que no está explícitamente permitido, está prohibido. |
| `STATELESS` | Sin `HttpSession`. Cada petición se autentica sola. |
| `addFilterBefore(...)` | Inserta **nuestro** filtro antes del de usuario/contraseña. |

**El orden importa:** Spring evalúa las reglas **de arriba hacia abajo** y se queda con la **primera que coincide**.

### 4.3 El filtro propio: `JwtAuthenticationFilter`

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extraerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            autenticarSiElTokenEsValido(token, request);
        }
        filterChain.doFilter(request, response);     // SIEMPRE continuar la cadena
    }
}
```

Lo que hace, en orden:

1. **Extrae el token** de la cabecera `Authorization: Bearer <token>` (`extraerToken`).
2. Si es válido (firma, emisor, vigencia), **toma el email**.
3. **Consulta si la cuenta sigue `ACTIVO`** (ADR-20). *"Falla cerrado"*: si la base da error, **se rechaza el token** (jamás se autentica por defecto).
4. **Colapsa los roles** si trae `ADMINISTRADOR` (regla del 2026-10-15, también para tokens viejos).
5. Crea la autenticación con sus **autoridades** (`ROLE_ORGANIZADOR`...) y la guarda en el **`SecurityContextHolder`**: el "contexto de seguridad" del hilo actual. A partir de ahí, cualquier punto del pedido sabe quién es el usuario (`Principal`, `@PreAuthorize`).

Decisiones de diseño explícitas en su javadoc:
- **Este filtro nunca rechaza.** Si no hay token o es inválido, **no autentica y deja seguir**; quien decide si el acceso anónimo está permitido es `SecurityConfig`. *"Separar 'identificar' de 'autorizar' mantiene cada pieza con una sola responsabilidad."* (Es lo que permite que el catálogo público reconozca al usuario logueado para atribuirle la visita, sin exigirle login.)
- **`OncePerRequestFilter`** garantiza una sola ejecución por petición.
- El prefijo `ROLE_` es una convención de Spring: `hasRole("X")` busca la autoridad `ROLE_X`.

### 4.4 Autorización en el método: `@PreAuthorize`

```java
@GetMapping("/{idEvento}")
@PreAuthorize("hasRole('ORGANIZADOR')")
public ResponseEntity<EventoDetalleResponse> obtenerParaEditar(@PathVariable Long idEvento, Principal principal) { ... }
```

Habilitada con `@EnableMethodSecurity`. Es una **segunda capa** sobre las reglas por ruta, útil cuando una misma ruta admite roles distintos según el método.

> **Principio:** la autorización se **enforza en el backend**. El frontend oculta botones por comodidad, pero **un atacante puede llamar a la API directamente**: lo único que cuenta es lo que valida el servidor.

### 4.5 401 contra 403

| Código | Significa | Cuándo |
|---|---|---|
| **401** Unauthorized | "No sé quién sos" (falta token, es inválido o vencido). | `JwtAuthenticationEntryPoint` |
| **403** Forbidden | "Sé quién sos, pero no podés" (falta el rol). | `JwtAccessDeniedHandler` |

Sin esos dos manejadores, Spring respondería 403 con HTML en ambos casos. Los propios devuelven **el mismo JSON de error** que el resto de la API.

---

## 5. Diseño defensivo del login

El login de Enexia es el mejor ejemplo del proyecto de **pensar como atacante**. La política completa está en el `CLAUDE.md` y en `AuthService`.

### 5.1 Principio: no revelar nada

Un atacante que prueba nicknames y contraseñas busca **información**: ¿este nickname existe? ¿acerté la contraseña? ¿mi ataque disparó un bloqueo? **Toda diferencia observable es una pista.**

**Respuesta indistinguible.** Todo rechazo de login —nickname inexistente, contraseña incorrecta, cuenta bloqueada, suspendida o dada de baja— devuelve **exactamente lo mismo**: `401`, código `CREDENCIALES_INVALIDAS`, mismo mensaje, **sin cabeceras extra**. La jerarquía de excepciones (A02 §3.1) y el **único** `@ExceptionHandler` para toda la familia (A04 §1.8) existen para garantizarlo. El motivo real (`NICKNAME_INEXISTENTE`, `PASSWORD_INCORRECTA`, `CUENTA_BLOQUEADA`...) solo se escribe en el **log del servidor** y en la tabla de auditoría.

### 5.2 El canal lateral del *tiempo*

Igualar el cuerpo no alcanza: **el tiempo de respuesta también habla**. Si "nickname inexistente" respondiera en 1 ms (no hay hash que comparar) y "contraseña incorrecta" en 250 ms (hay una comparación BCrypt), midiendo varias veces **se distingue qué nicknames existen**. La defensa en `AuthService`:

```java
@PostConstruct
void prepararHashSenuelo() {
    this.hashSenuelo = passwordEncoder.encode("senuelo-para-nivelar-tiempos-de-respuesta");
}

// ...
if (usuario == null) {
    nivelarTiempoDeRespuesta(peticion.getPassword());   // gasta el MISMO tiempo que una comparación real
    // ...
    throw new CredencialesInvalidasException(CODIGO_NICKNAME_INEXISTENTE);
}

private void nivelarTiempoDeRespuesta(String passwordCandidata) {
    passwordEncoder.matches(passwordCandidata, hashSenuelo);
}
```

El **hash señuelo** se calcula una vez al arrancar, con el mismo costo (12) que los reales; compararlo cuesta lo mismo que comparar el de un usuario verdadero. **Toda ruta de rechazo paga una comparación BCrypt.**

### 5.3 Bloqueo silencioso por cuenta

- Conteo **por cuenta** (`usuario.intentos_fallidos`), **nunca por IP** (detrás de un CGNAT cientos de personas legítimas comparten una IP; bloquearla sería **una denegación de servicio que el atacante provoca a voluntad**: se eliminó el *rate limiting* por IP el 2026-09-08).
- Al **tercer** fallo consecutivo, la cuenta pasa a `BLOQUEADO`. Un login exitoso reinicia el contador.
- El bloqueo **no se informa en la respuesta**: se avisa **por correo** al titular (el único canal que el atacante no controla) y **solo un administrador desbloquea** (decisión del 2026-10-19).
- El incremento del contador se hace con **bloqueo de fila y `REQUIRES_NEW`** (B03 §6.5 y §7.2): sin eso, dos intentos simultáneos perderían un incremento y la cuenta no se bloquearía.

> *Riesgo aceptado:* el *password spraying* (una contraseña común contra miles de nicknames) no acumula fallos en ninguna cuenta. La mitigación prevista es **alertar por volumen anómalo** en la auditoría, no rechazar pedidos (pendiente, `SUGERENCIAS_PENDIENTES.md` C7).

### 5.4 Login por POST, no por GET

*"Es POST y no GET aunque 'solo consulte': las credenciales viajan en el cuerpo, no en la URL. En un GET irían en la query string, que queda registrada en los logs del servidor, en el historial del navegador y en la cabecera Referer."* Regla general: **nunca pongas secretos en la URL.**

---

## 6. Tokens de un solo uso: la recuperación de contraseña

`RecuperacionCuentaService` genera un enlace que equivale, mientras vive, **a la contraseña**. Cómo se protege:

```java
private static final SecureRandom ALEATORIO = new SecureRandom();

private String emitirToken(Usuario usuario) {
    tokenRepository.borrarPorUsuario(usuario.getIdUsuario());     // un solo token vigente por cuenta

    byte[] bytes = new byte[BYTES_TOKEN];                         // 32 bytes = 256 bits
    ALEATORIO.nextBytes(bytes);
    String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    PasswordResetToken registro = new PasswordResetToken();
    registro.setUsuario(usuario);
    registro.setToken(hashear(tokenPlano));                       // en la base va el HASH, no el token
    registro.setFechaExpiracion(LocalDateTime.now().plusMinutes(vigenciaMinutos));
    tokenRepository.save(registro);

    return tokenPlano;                                            // solo viaja por correo
}
```

| Medida | Amenaza que cierra |
|---|---|
| **`SecureRandom`** (no `Random`) con 256 bits | Tokens predecibles o adivinables por fuerza bruta. `Random` es predecible; `SecureRandom` es criptográficamente seguro. |
| **Guardar el hash SHA-256, no el token** | Un volcado de la tabla (backup filtrado, otra inyección SQL) entregaría acceso a todas las cuentas con recuperación pendiente. Con el hash, lo filtrado es inservible. |
| **SHA-256 y no BCrypt** | El token ya es aleatorio de 256 bits: no hay diccionario que probar. BCrypt solo sumaría ~250 ms sin ganancia de seguridad. (Distinto de las contraseñas, que las eligen personas y sí necesitan un hash lento.) |
| **Vigencia de 30 min y un solo uso** | Ventana de ataque mínima; un enlace reenviado o guardado en el historial ya no sirve. |
| **Un token vigente por cuenta** | Los enlaces viejos dejan de servir al pedir uno nuevo. |
| **Mismo error para token inexistente, vencido o usado** | No ayudar a distinguir un token inventado de uno caducado. |
| **Quitar el token de la barra de direcciones** (`history.replaceState`) | Que no quede en el historial ni se copie por accidente (C02). |
| **Límite por destinatario, no por IP** (3 cada 24 h, 2 min entre uno y otro) | *Email bombing* a una casilla ajena sin reintroducir el problema del CGNAT. |
| **Todo lo que depende de si la cuenta existe corre en otro hilo, tras responder** | Que ni el cuerpo ni el **tiempo** de la respuesta delaten qué correos están registrados (otra vez el canal lateral, esta vez en el endpoint de recuperación). |

El hash se calcula así (`MessageDigest` es la API estándar de Java para resúmenes criptográficos):

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] resumen = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
StringBuilder hex = new StringBuilder(resumen.length * 2);
for (byte b : resumen) {
    hex.append(String.format("%02x", b));       // cada byte como 2 dígitos hexadecimales
}
```

---

## 7. Las otras amenazas clásicas

### 7.1 Inyección SQL
Concatenar texto del usuario en una consulta permite que ese texto **sea SQL**. **Defensa del proyecto:** consultas con **parámetros enlazados** (`:email` + `@Param`) y métodos derivados; nunca concatenar (B03 §5.4).

### 7.2 XSS (*Cross-Site Scripting*)
Si el sitio muestra texto de un usuario (el nombre de un evento) **como HTML**, un atacante puede escribir `<script>...</script>` y ejecutar código en el navegador de **los demás visitantes**. Es el hallazgo que el propio proyecto registró como "XSS almacenado" en el nombre del evento. Defensas por capa:
- **Backend (correos):** `CorreoHtml.escapar` convierte `& < > " '` en entidades HTML, y **nunca** convierte URLs del texto en enlaces (A01 §8.3).
- **Frontend:** usar `textContent` en vez de `innerHTML` con datos del usuario, o **escapar** antes (función `esc()` en las pantallas; C02).
- **Validación de entrada:** rechazar `<` y `>` en nombres donde no corresponden.

### 7.3 CSRF
Un ataque donde otro sitio hace que **tu navegador envíe una petición con tu cookie de sesión**. Enexia **lo desactiva** (`csrf.disable()`) con razón: autentica con una **cabecera `Authorization`** que el navegador **no adjunta solo**; sin cookies de sesión no hay vector CSRF. (Si algún día se pasara a cookies, habría que reactivarlo.)

### 7.4 CORS
El navegador **bloquea** que una página de un origen (`http://sitio-a.com`) llame a la API de otro (`http://localhost:8080`) salvo que el servidor lo autorice con cabeceras CORS. `corsConfigurationSource` lista **orígenes, métodos y cabeceras permitidos**. Como en Enexia el frontend se sirve **desde el mismo origen** que la API (puerto 8080), el CORS casi no entra en juego; por eso el `CLAUDE.md` insiste en **no** levantar un servidor estático aparte: sería otro origen y cada `fetch()` fallaría "como si el backend estuviera caído". *(Pendiente antes de desplegar: reemplazar los orígenes `localhost` por el dominio real.)*

### 7.5 Enumeración de recursos ajenos
Un id ajeno **no devuelve 403, devuelve 404**: un 403 confirmaría que el id existe y permitiría recorrer la plataforma probando números (`EventoService.buscarPropio`).

### 7.6 Mass assignment
Aceptar en el DTO solo los campos que se permiten fijar (B02 §4.1). Un cliente no puede enviar `"estadoSistema": "APROBADO"` porque el DTO no tiene ese campo.

### 7.7 Datos sensibles en logs, respuestas y URLs
Nunca: contraseñas, tokens, claves de API (A04 §3). `BrevoClient` ni siquiera loguea el cuerpo de un correo real porque podría llevar un token de recuperación.

---

## 8. Seguridad en el frontend: lo que se hace y lo que NO protege

- El JWT se guarda en `sessionStorage` (lo explica C02). Cualquier XSS podría leerlo: **la defensa real contra XSS es escapar la salida**.
- `auth.js` redirige si no hay sesión y oculta botones por rol, **pero eso es comodidad, no seguridad**: la autorización verdadera es la del backend.
- Las páginas "privadas" (`/pages/organizador/**`) se sirven **sin token** a propósito: una navegación del navegador nunca adjunta `Authorization`. Lo que protege los **datos** es que cada `fetch()` a `/api/**` sí lo lleva y el backend lo exige (comentario largo en `SecurityConfig`).

---

## 9. Mapa de decisiones de seguridad (resumen)

| Amenaza | Defensa | Dónde |
|---|---|---|
| Contraseñas filtradas | BCrypt costo 12 con sal | `SecurityConfig.passwordEncoder` |
| Falsificar un token | Firma HMAC-SHA256 con clave secreta | `JwtService` |
| Cuenta suspendida que sigue operando | Chequeo de estado en cada pedido | `JwtAuthenticationFilter` |
| Enumerar usuarios por el mensaje | Respuesta idéntica | `GlobalExceptionHandler` + excepciones |
| Enumerar usuarios por el tiempo | Hash señuelo | `AuthService.nivelarTiempoDeRespuesta` |
| Fuerza bruta | Bloqueo a los 3 fallos por cuenta | `IntentosLoginService` |
| DoS por bloqueo de IP | No limitar por IP | decisión 2026-09-08 |
| Token de recuperación robado de la base | Solo SHA-256 en la tabla | `RecuperacionCuentaService` |
| Escalada de privilegios | Roles por ruta + `@PreAuthorize` + deny-by-default | `SecurityConfig` |
| Admin con otros roles | `Usuario.getRoles()` colapsa | `Usuario` |
| Inyección SQL | Parámetros enlazados | repositories |
| XSS | Escapar salida | `CorreoHtml`, `esc()` |
| Duplicados concurrentes | `UNIQUE` en la base | entidades |
| Abuso de moderación | Pipeline en pool aislado | `AsyncConfig` |

---

## 10. Errores típicos

| Error | Consecuencia | Remedio |
|---|---|---|
| Guardar contraseñas en claro o con MD5/SHA simple. | Todas las cuentas comprometidas ante una filtración. | BCrypt. |
| Poner datos sensibles en el payload del JWT. | Cualquiera los lee. | Solo identidad y roles. |
| Confiar en que el frontend oculta el botón. | Cualquiera llama a la API directo. | Autorizar en el backend. |
| Mensajes de error distintos por motivo en el login. | Enumeración de cuentas. | Una respuesta única. |
| Secretos en el repositorio. | Claves públicas. | Variables de entorno. |
| `Random` para tokens. | Tokens predecibles. | `SecureRandom`. |
| Un `catch` que autentica "por las dudas". | Acceso indebido ante un error. | Fallar **cerrado**. |

---

## 11. Para practicar

1. Entrá con la cuenta `gaston_test`, copiá el token de `sessionStorage` y pegalo en `jwt.io`. ¿Qué claims ves? ¿Qué pasa si cambiás un byte del payload y lo volvés a verificar?
2. Medí con las DevTools (pestaña *Network*) el tiempo de `POST /api/auth/login` con un nickname inexistente y con uno existente con contraseña errónea. ¿Se parecen?
3. Agregá en papel una regla en `SecurityConfig` para que `/api/participante/**` también acepte `ADMINISTRADOR`. ¿Qué rompería? (Pista: `Usuario.getRoles()`.)
4. ¿Por qué el token de recuperación se hashea con SHA-256 y la contraseña con BCrypt, si ambos son "secretos"?
5. Explicá con tus palabras por qué el filtro JWT **nunca** devuelve 401 por sí mismo y en cambio lo hace `JwtAuthenticationEntryPoint`.

**Siguiente capítulo:** [B05 — Asincronía, eventos y tareas programadas](./B05_asincronia_y_transacciones.md)
