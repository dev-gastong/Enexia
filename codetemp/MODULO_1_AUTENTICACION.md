# Módulo 1 — Registro y Autenticación: guía teórica completa

**Fecha:** 2026-08-24
**Sprint:** 1
**Estado:** implementado y probado end-to-end contra MariaDB

Este documento explica **cada clase, anotación, método y decisión** del módulo.
Está pensado para leerse de arriba a abajo sin conocimiento previo.

Documentos hermanos en esta carpeta:
- [SPRING_SECURITY_TEORIA.md](SPRING_SECURITY_TEORIA.md) — teoría general de Spring Security
- [CONCEPTOS_BASICOS_SPRING.md](CONCEPTOS_BASICOS_SPRING.md) — Bean, Servlet, Request/Response

---

## Índice

1. [Mapa de archivos](#1-mapa-de-archivos)
2. [Decisiones de alcance](#2-decisiones-de-alcance)
3. [Anotaciones: diccionario completo](#3-anotaciones-diccionario-completo)
4. [Capa DTO](#4-capa-dto)
5. [Capa Repository](#5-capa-repository)
6. [Capa Security](#6-capa-security)
7. [Capa Service](#7-capa-service)
8. [Capa Controller](#8-capa-controller)
9. [Manejo de excepciones](#9-manejo-de-excepciones)
10. [Transacciones: el tema más difícil](#10-transacciones-el-tema-más-difícil)
11. [Decisiones de seguridad y su porqué](#11-decisiones-de-seguridad-y-su-porqué)
12. [Bugs reales encontrados al probar](#12-bugs-reales-encontrados-al-probar)
13. [Pruebas ejecutadas](#13-pruebas-ejecutadas)
14. [Pendientes](#14-pendientes)

---

## 1. Mapa de archivos

Archivos **creados** en este módulo:

```
enexia/src/main/java/com/enexia/rg/
├── config/
│   ├── SecurityConfig.java              Configuración central de Spring Security
│   └── DatosInicialesConfig.java        Precarga de roles y estados
├── controller/
│   └── AuthController.java              POST /registro y POST /login
├── security/
│   ├── JwtService.java                  Emite y verifica tokens
│   ├── JwtAuthenticationFilter.java     Autentica cada petición por su JWT
│   ├── JwtAuthenticationEntryPoint.java Responde 401 al no autenticado
│   ├── JwtAccessDeniedHandler.java      Responde 403 al no autorizado
│   └── UserDetailsServiceImpl.java      Puente BD ↔ Spring Security
├── service/
│   ├── AuthService.java                 Orquesta login y registro
│   ├── IntentosLoginService.java        Penalización escalonada
│   ├── ModeracionTextoService.java      Filtro de lenguaje ofensivo
│   ├── AuditoriaService.java            Bitácora
│   └── RateLimitService.java            Control por IP
├── exception/
│   ├── GlobalExceptionHandler.java      Traduce excepciones a HTTP
│   └── (7 excepciones de dominio)
├── model/
│   ├── RolNombre.java                   enum
│   └── EstadoUsuarioNombre.java         enum
└── resources/
    └── moderacion/terminos-bloqueados.txt
```

Archivos **modificados**: `UsuarioRepository`, `RolRepository`, `UsuarioEstadoRepository`,
`HistorialInteraccionesRepository`, `UsuarioRegistroRequest`, `UsuarioLoginRequest`,
`ErrorResponse`, `application.properties`, `build.gradle`.

---

## 2. Decisiones de alcance

Los DFD describen más de lo que entra en Sprint 1. Lo decidido el 2026-08-24:

| Paso del DFD | Decisión | Motivo |
|---|---|---|
| 1.2.4A CAPTCHA | **No se valida.** El flag `requiere_captcha` sí se marca | `CLAUDE.md` lo pone en Sprint 2+; requiere cuenta de reCAPTCHA |
| 1.2.8–1.2.9 2FA | **No se implementa** | Requiere servidor SMTP; Sprint 2+ |
| 1.2.7A email de alerta | **No se implementa** | Mismo motivo |
| 1.1.3 CUIT | **No aplica** | Es de Persona Jurídica, Sprint 2 |
| 1.1.7 ente corporativo | **No aplica** | Sprint 2 |
| `usuario_estado_sistema` | **No se consulta** | Se incorporará con el módulo de moderación |

**Escala de bloqueo** — se implementó el DFD, no RF-1.4:

| Fallos | Consecuencia |
|---|---|
| 3 | `requiere_captcha = true` + cooldown 5 min |
| 6 | cooldown 30 min |
| 9 | estado `BLOQUEADO` (lo destraba un admin) |

> ⚠️ **RF-1.4 dice "bloquear al 3er intento" y quedó desactualizado.** Hay que
> corregir el texto en `docs/requisitos/requisitos_funcionales/modulo_1.md`.

---

## 3. Anotaciones: diccionario completo

### 3.1 Estereotipos (marcan una clase como Bean)

| Anotación | Qué hace | Dónde se usó |
|---|---|---|
| `@Component` | Bean genérico | `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint` |
| `@Service` | Bean de lógica de negocio | `AuthService`, `JwtService`, los demás servicios |
| `@Repository` | Bean de acceso a datos | Todas las interfaces `*Repository` |
| `@RestController` | Bean que atiende HTTP y devuelve JSON | `AuthController` |
| `@Configuration` | Bean que **define otros beans** | `SecurityConfig`, `DatosInicialesConfig` |
| `@RestControllerAdvice` | Intercepta excepciones de todos los controllers | `GlobalExceptionHandler` |

Las cuatro primeras son técnicamente lo mismo que `@Component`; los nombres
distintos existen para que se lea la intención y para que herramientas de
análisis puedan distinguir capas.

### 3.2 Inyección de dependencias

```java
@Service
@RequiredArgsConstructor          // Lombok genera el constructor
public class AuthService {
    private final UsuarioRepository usuarioRepository;   // se inyecta solo
}
```

`@RequiredArgsConstructor` genera un constructor con todos los campos `final`.
Spring ve un único constructor y lo usa para inyectar.

**Por qué inyección por constructor y no `@Autowired` en el campo:**

| Constructor | `@Autowired` en campo |
|---|---|
| El campo puede ser `final` (inmutable) | No puede ser `final` |
| Imposible crear el objeto sin sus dependencias | Se puede crear a medio construir |
| Se puede instanciar en un test sin Spring | Hace falta reflexión o un contexto |
| Una lista de parámetros enorme *avisa* que la clase hace demasiado | El problema queda oculto |

`@Value` inyecta un valor de `application.properties`:

```java
@Value("${enexia.security.jwt.expiration}") long vigenciaMs
```

### 3.3 Validación (Bean Validation / Jakarta Validation)

| Anotación | Valida |
|---|---|
| `@NotBlank` | No nulo, no vacío, no solo espacios |
| `@NotNull` | No nulo (sirve para fechas y números) |
| `@Email` | Formato de correo |
| `@Size(min, max)` | Longitud |
| `@Pattern(regexp)` | Expresión regular |
| `@Past` | Fecha anterior a hoy |
| `@Valid` | **Dispara** todas las anteriores sobre un parámetro |

Sin `@Valid` en el controller, las demás anotaciones **no hacen nada**. Es el
error más común: se decoran los DTO y se olvida el disparador.

```java
public ResponseEntity<...> registrar(@Valid @RequestBody UsuarioRegistroRequest peticion)
```

**Diferencia clave con `@NotBlank` vs `@NotNull`:** para un `String` casi siempre
se quiere `@NotBlank`, porque `@NotNull` deja pasar `""` y `"   "`.

### 3.4 JPA

| Anotación | Qué hace |
|---|---|
| `@Entity` | La clase se mapea a una tabla |
| `@Table(name)` | Nombre de la tabla |
| `@Id` | Clave primaria |
| `@GeneratedValue(strategy = IDENTITY)` | La BD genera el id (AUTO_INCREMENT) |
| `@Column(name)` | Nombre de columna |
| `@ManyToOne` / `@OneToMany` / `@OneToOne` | Relaciones |
| `@JoinColumn(name)` | Columna de clave foránea |
| `@MapsId` | La PK se hereda de la entidad relacionada |
| `@EmbeddedId` | Clave primaria compuesta |
| `@Query` | Consulta JPQL propia |
| `@Param` | Enlaza un parámetro con `:nombre` en el JPQL |
| `@Lock(LockModeType)` | Bloqueo de fila |
| `@Transactional` | Delimita una transacción |

**`@MapsId` en detalle** — aparece en `PersonaFisica`:

```java
@Id @Column(name = "id_persona")
private Long idPersona;

@OneToOne @MapsId @JoinColumn(name = "id_persona")
private Persona persona;
```

Significa: "la clave primaria de `persona_fisica` **es** la de `persona`".
Por eso en el código se asigna la relación y **nunca el id a mano**:

```java
personaFisica.setPersona(persona);   // ✅ JPA deriva el id
personaFisica.setIdPersona(5L);      // ❌ no hacer esto
```

### 3.5 Spring Security

| Anotación | Qué hace |
|---|---|
| `@EnableWebSecurity` | Activa Spring Security |
| `@EnableMethodSecurity` | Habilita `@PreAuthorize` en métodos |
| `@PreAuthorize("hasRole('X')")` | Exige un rol antes de ejecutar el método |
| `@Bean` | Registra el valor de retorno como Bean |

### 3.6 Lombok

| Anotación | Genera |
|---|---|
| `@Getter` / `@Setter` | Los métodos de acceso |
| `@NoArgsConstructor` | Constructor vacío (**JPA lo exige**) |
| `@AllArgsConstructor` | Constructor con todos los campos |
| `@RequiredArgsConstructor` | Constructor con los campos `final` |
| `@Slf4j` | Un campo `log` listo para usar |

`@Slf4j` genera exactamente esto:

```java
private static final Logger log = LoggerFactory.getLogger(MiClase.class);
```

### 3.7 Ciclo de vida

`@PostConstruct` corre **una vez**, después de que Spring construyó el bean e
inyectó sus dependencias. Se usó en dos lugares:

- `ModeracionTextoService` — cargar la lista de términos desde disco
- `AuthService` — precalcular el hash señuelo

En ambos casos la alternativa sería repetir ese trabajo en cada petición.

---

## 4. Capa DTO

### 4.1 Qué es un DTO y por qué no exponer entidades

Un **DTO** (Data Transfer Object) es un objeto que sólo transporta datos entre
capas. La regla del proyecto: **una entidad JPA nunca sale ni entra por la API.**

Cuatro razones concretas:

1. **Seguridad.** `Usuario` tiene el campo `password`. Devolverlo en un JSON
   filtra el hash BCrypt. Y aceptarlo permitiría que un cliente mande
   `{"estadoUsuario": "ADMINISTRADOR"}` (*mass assignment*).
2. **Carga perezosa.** Serializar una entidad con relaciones `LAZY` fuera de la
   transacción lanza `LazyInitializationException`.
3. **Ciclos infinitos.** `Usuario → UsuarioRol → Usuario → ...` cuelga al
   serializador.
4. **Acoplamiento.** Renombrar una columna rompería el contrato del frontend.

### 4.2 Los DTO del módulo

| DTO | Dirección | Contenido |
|---|---|---|
| `UsuarioRegistroRequest` | entra | credenciales + identidad civil + perfil |
| `UsuarioRegistroResponse` | sale | id, email, nickname, estado, roles, mensaje |
| `UsuarioLoginRequest` | entra | email + password |
| `UsuarioLoginResponse` | sale | id, email, **token**, tipoToken, roles |
| `ErrorResponse` | sale | error, mensaje, status, timestamp, errores |

### 4.3 Por qué las validaciones del login son más laxas

```java
// Registro: exige mayúscula, minúscula y número
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")

// Login: sólo @NotBlank y un máximo
```

Dos motivos:

1. Exigir el patrón fuerte en el login le **confirmaría al atacante** que su
   candidato no cumple la política, reduciéndole el espacio de búsqueda.
2. Si mañana se endurece la política, los usuarios viejos no podrían entrar.

### 4.4 El regex de contraseña, explicado

```
^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$
```

| Parte | Significado |
|---|---|
| `^` | inicio |
| `(?=.*[a-z])` | *lookahead*: en algún lugar hay una minúscula |
| `(?=.*[A-Z])` | en algún lugar hay una mayúscula |
| `(?=.*\d)` | en algún lugar hay un dígito |
| `.+` | y hay al menos un carácter |
| `$` | fin |

Un **lookahead** `(?=...)` verifica sin consumir caracteres. Por eso se pueden
encadenar tres condiciones sobre el mismo texto.

### 4.5 `\p{L}` en nombre y apellido

```java
@Pattern(regexp = "^[\\p{L} '-]+$")
```

`\p{L}` es "cualquier letra Unicode". Con `[a-zA-Z]` se rechazarían apellidos
como *Núñez*, *Müller* o *Ángeles*. Esto fue **un error que cometí y corregí**:
la primera versión listaba las vocales acentuadas a mano y no funcionaba.

---

## 5. Capa Repository

### 5.1 Spring Data: consultas derivadas del nombre

```java
boolean existsByEmailIgnoreCase(String email);
```

Spring **lee el nombre del método** y genera la consulta. Palabras clave:
`findBy`, `existsBy`, `countBy`, `deleteBy`, `And`, `Or`, `IgnoreCase`,
`IsNull`, `After`, `Between`, `OrderBy`.

### 5.2 Prevención de inyección SQL

**Todas** las consultas del módulo son parametrizadas:

```java
@Query("SELECT u FROM Usuario u WHERE LOWER(u.email) = LOWER(:email)")
Optional<Usuario> buscarActivoPorEmailConRoles(@Param("email") String email);
```

El valor viaja como **parámetro enlazado** de un `PreparedStatement`, nunca
concatenado. Si alguien manda `' OR '1'='1`, la base lo trata como un email
literal y no encuentra nada.

Lo que **nunca** hay que hacer:

```java
// ❌ VULNERABLE
@Query("SELECT u FROM Usuario u WHERE u.email = '" + email + "'")
```

### 5.3 El problema N+1 y `JOIN FETCH`

```java
@Query("""
    SELECT u FROM Usuario u
    LEFT JOIN FETCH u.usuarioRoles ur
    LEFT JOIN FETCH ur.rol
    LEFT JOIN FETCH u.estadoUsuario
    WHERE LOWER(u.email) = LOWER(:email) AND u.fechaBaja IS NULL
    """)
```

**Sin `JOIN FETCH`:**

```
1 consulta   → traer el usuario
1 consulta   → traer sus usuario_rol
N consultas  → traer el rol de cada usuario_rol   ← el problema
1 consulta   → traer el estado
```

**Con `JOIN FETCH`:** una sola consulta.

Además, con `spring.jpa.open-in-view=false` (ya configurado en el proyecto), la
sesión de Hibernate se cierra al terminar la transacción. Acceder a una relación
`LAZY` después de eso lanza `LazyInitializationException`. El `JOIN FETCH` trae
todo por adelantado y evita ambos problemas de una vez.

> **Por qué `Set` y no `List`:** si se hace `JOIN FETCH` de **dos colecciones**
> declaradas como `List`, Hibernate lanza `MultipleBagFetchException`. Con `Set`
> no ocurre. `Usuario.usuarioRoles` ya es `Set`.

### 5.4 Bloqueo pesimista

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM Usuario u WHERE u.idUsuario = :id")
Optional<Usuario> bloquearParaActualizarSeguridad(@Param("id") Long id);
```

Genera `SELECT ... FOR UPDATE`. Nadie más puede modificar esa fila hasta que la
transacción termine.

**Por qué hace falta.** `intentos_fallidos` es un contador
*leer → modificar → escribir*. Sin bloqueo:

```
Petición A: lee intentos = 2
Petición B: lee intentos = 2      ← lee el mismo valor
Petición A: escribe 3
Petición B: escribe 3             ← se perdió un incremento
```

Con dos peticiones simultáneas el contador avanza uno solo. Un atacante que
mande intentos en paralelo nunca llegaría al umbral. Con `FOR UPDATE`, B espera
a que A termine y lee 3.

> ⚠️ **Requiere transacción activa.** Fuera de `@Transactional` el bloqueo no se
> aplica.

---

## 6. Capa Security

### 6.1 Anatomía de un JWT

```
eyJhbGciOiJIUzM4NCJ9 . eyJzdWIiOiJnYXN0b24u... . 4x1a-dMQuO6-O6aB...
      HEADER                   PAYLOAD                  FIRMA
```

Payload real emitido por este módulo:

```json
{
  "sub":   "gaston.test@enexia.com",
  "roles": ["ORGANIZADOR"],
  "iss":   "enexia",
  "iat":   1787563571,
  "exp":   1787649971
}
```

| Claim | Nombre | Significado |
|---|---|---|
| `sub` | subject | a quién identifica |
| `iss` | issuer | quién lo emitió |
| `iat` | issued at | cuándo se emitió |
| `exp` | expiration | hasta cuándo vale |
| `roles` | *propio* | roles para autorizar |

### 6.2 Las dos verdades del JWT

**1. NO está cifrado, sólo firmado.** Header y payload son Base64URL, que
cualquiera decodifica:

```bash
echo "eyJzdWIiOiJnYXN0b24u..." | base64 -d
```

→ **Nunca poner datos sensibles en el payload** (DNI, teléfono, contraseñas).

**2. Es infalsificable sin la clave.** La firma es un HMAC sobre
`header.payload` con la clave secreta. Cambiar un byte del payload invalida la
firma.

Eso es lo que hace posible la autenticación *stateless*: el servidor no guarda
sesiones, le alcanza con verificar la firma.

### 6.3 Selección automática del algoritmo

`signWith(clave)` **no recibe el algoritmo**: jjwt elige el HMAC más fuerte que
soporte el largo de la clave.

| Bytes de la clave | Algoritmo |
|---|---|
| 32 a 47 | HS256 |
| 48 a 63 | **HS384** ← la clave por defecto (56 bytes) cae acá |
| 64 o más | HS512 |

Por eso el header dice `{"alg":"HS384"}`.

### 6.4 `JwtAuthenticationFilter`

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    protected void doFilterInternal(request, response, filterChain) { ... }
}
```

**`OncePerRequestFilter`:** una petición puede atravesar la cadena de filtros más
de una vez (*forwards* internos, manejo de errores). Esta clase base garantiza
una sola ejecución por petición.

**El filtro nunca rechaza.** Si no hay token o es inválido, simplemente no
autentica y deja seguir. Quien decide si el acceso anónimo es aceptable es
`SecurityConfig`, más adelante en la cadena. Separar *identificar* de *autorizar*
deja cada pieza con una sola responsabilidad.

**El constructor de `UsernamePasswordAuthenticationToken` importa:**

```java
new UsernamePasswordAuthenticationToken(email, null, autoridades);  // 3 args
```

| Argumentos | Significado |
|---|---|
| 2 | autenticación **pendiente** de verificar |
| 3 | autenticación **ya confirmada** |

Las credenciales van en `null` a propósito: la contraseña ya se validó en el
login, no hay razón para tenerla en memoria en cada petición.

**El prefijo `ROLE_`:**

```java
new SimpleGrantedAuthority("ROLE_" + rol)
```

Spring Security lo exige para que `hasRole("ORGANIZADOR")` funcione. Sin él
habría que escribir `hasAuthority("ORGANIZADOR")` en todas partes.

### 6.5 `SecurityConfig`

```java
.csrf(csrf -> csrf.disable())
```

CSRF protege formularios que autentican por **cookie de sesión**: el navegador
adjunta la cookie sola, así que un sitio malicioso puede disparar peticiones en
nombre del usuario. Enexia autentica por cabecera `Authorization`, que el
navegador **no** adjunta sola. No hay vector CSRF que proteger.

```java
.sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
```

El servidor no crea ni consulta `HttpSession`. Es lo que permite escalar a varias
instancias sin sesiones compartidas.

```java
.anyRequest().authenticated()
```

**Deny by default.** Si mañana alguien agrega un endpoint y olvida declararlo,
queda protegido en lugar de abierto. El orden de las reglas importa: la primera
que coincide gana, así que las específicas van antes que `anyRequest()`.

```java
new BCryptPasswordEncoder(12)
```

Dos propiedades que un hash común (MD5, SHA-256) no tiene:

- **Sal automática.** Hashear dos veces la misma contraseña da resultados
  distintos → inutiliza las *rainbow tables* y oculta que dos usuarios comparten
  contraseña.
- **Costo configurable.** Factor 12 = 2¹² iteraciones ≈ 250 ms. Imperceptible en
  un login, inviable para fuerza bruta masiva.

### 6.6 401 vs 403

| Código | Significado | Manejador |
|---|---|---|
| **401** Unauthorized | "no sé quién sos, autenticate" | `JwtAuthenticationEntryPoint` |
| **403** Forbidden | "sé quién sos, pero no alcanza" | `JwtAccessDeniedHandler` |

Sin estas dos clases Spring responde **403 y en HTML** en ambos casos. La
diferencia le importa al frontend: ante un 401 hay que mandar al login; ante un
403, mostrar "no tenés permisos" porque reintentar no sirve.

Estos errores ocurren **en la cadena de filtros, antes de los controllers**, así
que `GlobalExceptionHandler` no los ve. Por eso hay que darles forma aparte.

---

## 7. Capa Service

### 7.1 `ModeracionTextoService` — normalización

> ⚠️ **`CLAUDE.md` menciona `better-profanity` como librería Java. Es falso:
> `better-profanity` es un paquete de Python.** No existe equivalente publicado
> para Java, así que el filtro se implementó en el proyecto. Cumple los mismos
> criterios: gratuito, liviano y offline.

Comparar el texto tal cual llega es inútil. La normalización:

```
"P.U.T.0"  → minúsculas  → "p.u.t.0"
           → sin acentos → "p.u.t.0"
           → sin l33t    → "p.u.t.o"
           → solo letras → "puto"      → coincide
```

**Quitar acentos con `Normalizer`:**

```java
Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD)
          .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
```

`Form.NFD` **descompone** cada letra acentuada en letra base + marca diacrítica
(`á` → `a` + `´`). Después se descartan las marcas y queda `a`.

**Sustituciones l33t:** `0→o`, `1→i`, `3→e`, `4→a`, `5→s`, `7→t`, `@→a`, `$→s`.

**Por qué `HashSet` y no `List`:** búsqueda O(1) en lugar de O(n). Este método
corre en cada registro.

**Limitación conocida (problema Scunthorpe).** La búsqueda por subcadena da
falsos positivos: un apellido legítimo puede contener un término de la lista. Se
mitiga comparando primero **palabras completas** y recurriendo a la subcadena
sólo en textos cortos (≤40 caracteres), donde `xXpu7oXx` sería indetectable de
otro modo.

### 7.2 `RateLimitService` — por qué es distinto del bloqueo de cuenta

| | Bloqueo de cuenta | Rate limit por IP |
|---|---|---|
| Protege | **una** cuenta | **el sistema** |
| Cuenta | fallos sobre un email | intentos desde una IP |
| Frena | fuerza bruta contra un usuario | *password spraying* |

El **password spraying** prueba una contraseña común (`Password123`) contra diez
mil emails distintos. Ninguna cuenta llega a 3 fallos y el ataque pasa entero por
debajo del radar del bloqueo por cuenta. Por eso hacen falta los dos controles.

**El orden importa.** El rate limit es lo **primero** del login, antes de buscar
al usuario y antes de comparar el hash. Si se hiciera al final, una IP abusiva
consumiría ~250 ms de CPU por intento y el propio mecanismo de defensa se
convertiría en el vector de denegación de servicio.

**Ventana deslizante.** Se cuentan los intentos de los últimos N minutos hacia
atrás desde ahora. Una ventana fija permitiría agotar el cupo al final de un
bloque y volver a agotarlo al principio del siguiente, duplicando el límite real.

### 7.3 `AuditoriaService` — la IP detrás de un proxy

```java
String forwarded = request.getHeader("X-Forwarded-For");
if (forwarded != null && !forwarded.isBlank()) {
    return forwarded.split(",")[0].trim();
}
return request.getRemoteAddr();
```

`getRemoteAddr()` devuelve la IP de quien abrió la conexión TCP. Detrás de Nginx
(como plantea el plan de despliegue de Enexia) esa IP es **siempre la del
proxy**, y el rate limiting contaría todo el tráfico como un único cliente.

> ⚠️ **`X-Forwarded-For` la puede falsificar cualquiera.** Sólo es confiable si
> el proxy la reescribe:
> ```nginx
> proxy_set_header X-Forwarded-For $remote_addr;
> ```
> Sin esa línea, un atacante evade el rate limiting mandando una cabecera
> distinta en cada petición.

### 7.4 `IntentosLoginService` — por qué es una clase aparte

**Esta es la sutileza más importante del módulo.**

`@Transactional` funciona por **proxy**: Spring envuelve el bean en un objeto que
abre la transacción antes de delegar al método real.

```
Cliente → [PROXY: abre TX] → método real → [PROXY: confirma TX]
```

Si el método se llama **desde adentro de la misma clase**:

```java
public void login() {
    this.registrarFallo();   // ❌ NO pasa por el proxy
}

@Transactional(propagation = REQUIRES_NEW)
private void registrarFallo() { ... }   // la anotación NO se aplica
```

La llamada va directo al objeto real y **la anotación queda sin efecto, en
silencio, sin ningún error visible**. Es un bug clásico y muy difícil de
detectar.

Por eso `registrarFallo` y `limpiarTrasLoginExitoso` viven en **otro bean**: así
la llamada entra desde afuera y pasa por el proxy.

### 7.5 `AuthService.login()` — el ataque de temporización

```java
if (usuario == null) {
    passwordEncoder.matches(peticion.getPassword(), hashSenuelo);  // ← señuelo
    throw new CredencialesInvalidasException();
}
```

Sin esa línea:

| Caso | Trabajo | Tiempo |
|---|---|---|
| Email **no existe** | consulta + `return` | ~5 ms |
| Email **existe**, clave mal | consulta + BCrypt | ~255 ms |

Midiendo el tiempo de respuesta muchas veces, un atacante distingue los dos casos
y **enumera qué emails están registrados** — aunque el mensaje sea idéntico.

El hash señuelo se precalcula una vez en `@PostConstruct` con el mismo factor de
costo, así comparar contra él tarda lo mismo.

### 7.6 `AuthService.registrar()` — orden de las operaciones

```
1. ¿password == passwordConfirmacion?     ← no lo cubre @Valid
2. ¿email único?  ¿nickname único?         ← DFD 1.1.1
3. Moderación de texto                     ← DFD 1.1.1A
4. Hashear con BCrypt                      ← DFD 1.1.4
5. persona → persona_fisica → usuario → usuario_rol
6. Auditoría
```

**Por qué la moderación va en el paso 3 y no después:** si se guardara primero,
un nickname ofensivo quedaría escrito en la base aunque después se rechace el
registro.

**Por qué `password == passwordConfirmacion` no lo cubre `@Valid`:** las
anotaciones de Bean Validation miran **un campo por vez**. Comparar dos campos
entre sí requiere lógica, y va en el service.

---

## 8. Capa Controller

```java
@PostMapping("/registro")
public ResponseEntity<UsuarioRegistroResponse> registrar(
        @Valid @RequestBody UsuarioRegistroRequest peticion,
        HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(...);
}
```

El controller es **deliberadamente delgado**: recibe, delega, traduce a HTTP.
Cero lógica de negocio, cero `try/catch`. Esa separación permite testear las
reglas de autenticación sin levantar un servidor web.

**`@RequestBody`** convierte el JSON del cuerpo en el DTO (lo hace Jackson).

**`HttpServletRequest`** se recibe sólo para auditar IP y user-agent. Spring lo
inyecta con sólo agregarlo a la firma.

**Códigos usados:**

| Endpoint | Éxito | Por qué |
|---|---|---|
| `POST /registro` | **201** Created | La petición **crea** un recurso |
| `POST /login` | **200** OK | No crea nada persistente |

**Por qué el login es POST y no GET** aunque "sólo consulte": en un GET las
credenciales irían en la query string, que queda registrada en los logs del
servidor, en el historial del navegador y en la cabecera `Referer` de la
siguiente petición.

---

## 9. Manejo de excepciones

### 9.1 Mapa completo

| Excepción | HTTP | Código | ¿Revela si la cuenta existe? |
|---|---|---|---|
| `CredencialesInvalidasException` | 401 | `CREDENCIALES_INVALIDAS` | **No** (a propósito) |
| `CuentaBloqueadaException` | 403 | `CUENTA_BLOQUEADA` | Sí |
| `CuentaEnCooldownException` | 403 | `CUENTA_EN_COOLDOWN` | Sí |
| `RateLimitExcedidoException` | 429 | `RATE_LIMIT_EXCEDIDO` | No |
| `RecursoDuplicadoException` | 409 | `RECURSO_DUPLICADO` | Sí (inevitable) |
| `ContenidoInapropiadoException` | 422 | `CONTENIDO_INAPROPIADO` | No |
| `ReglaNegocioException` | 400 | `REGLA_NEGOCIO` | No |
| `MethodArgumentNotValidException` | 400 | `VALIDACION_FALLIDA` | No |
| `Exception` (red de contención) | 500 | `ERROR_INTERNO` | No |

### 9.2 El mensaje genérico

`CredencialesInvalidasException` cubre **tres** situaciones con **un solo
mensaje**:

- el email no existe
- el usuario no está `ACTIVO`
- la contraseña no coincide

Distinguirlas permitiría **enumerar cuentas**: probar emails y saber cuáles están
registrados por la diferencia de respuesta. El motivo real sólo queda en el log
del servidor.

### 9.3 Por qué 422 y no 400 para moderación

| Código | Significado |
|---|---|
| **400** Bad Request | La petición está **mal formada** |
| **422** Unprocessable Content | Está bien formada, pero el **contenido** es inaceptable |

Un nickname ofensivo es sintácticamente perfecto. Es el caso exacto de 422.

> **Nota:** la constante se llamaba `UNPROCESSABLE_ENTITY`; la RFC 9110 renombró
> el código a *Unprocessable Content* y Spring dejó el nombre viejo deprecado.
> En Spring Boot 4 hay que usar `HttpStatus.UNPROCESSABLE_CONTENT`.

### 9.4 El stack trace nunca sale

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> manejarErrorInesperado(Exception ex) {
    log.error("Error no controlado en la API", ex);   // ← al log
    return construir("ERROR_INTERNO", "Ocurrio un error inesperado...", 500);
}
```

Un stack trace revela nombres de clases, versiones de librerías y estructura
interna: material útil para preparar un ataque. Va al log del servidor, nunca al
cliente.

---

## 10. Transacciones: el tema más difícil

### 10.1 Qué es una transacción

Un bloque de operaciones que se confirma **entero o nada**. El registro escribe
en cuatro tablas encadenadas:

```
persona → persona_fisica → usuario → usuario_rol
```

Sin transacción, un fallo en el tercer paso dejaría una `persona` sin `usuario`,
ocupando un DNI que después nadie podría volver a registrar.

### 10.2 Reversión automática ante excepción

**Spring revierte la transacción cuando una `RuntimeException` escapa de un
método `@Transactional`.** Es el comportamiento por defecto y la causa de la
sutileza que sigue.

### 10.3 Propagaciones usadas

| Propagación | Comportamiento | Dónde |
|---|---|---|
| `REQUIRED` (defecto) | Se suma a la del que llama, o crea una | `registrar`, `AuthService.registrar` |
| `REQUIRES_NEW` | **Suspende** la actual y abre una propia | `registrarAparte`, `IntentosLoginService` |

### 10.4 Por qué `REQUIRES_NEW` en los intentos fallidos

Al fallar un login hay que hacer dos cosas:

1. incrementar `intentos_fallidos`
2. lanzar la excepción que devuelve el 401

Si compartieran transacción, la excepción del paso 2 **revertiría el paso 1**.
Resultado: un contador que nunca avanza y una cuenta que jamás se bloquea —
exactamente la falla que el código intenta prevenir.

`REQUIRES_NEW` abre una transacción independiente que se confirma sola, así el
incremento sobrevive.

### 10.5 Por qué `REQUIRED` en la auditoría del registro

Acá `REQUIRES_NEW` produce un **interbloqueo real** (lo encontré probando, ver
§12.1):

```
TX-A (registro)     inserta usuario, sin confirmar → toma lock exclusivo
TX-A                llama a auditoría REQUIRES_NEW → se suspende
  TX-B (auditoría)  inserta en historial con FK a ese usuario
  TX-B              necesita lock compartido sobre la fila padre
  TX-B              ⏳ espera a que TX-A libere
TX-A                ⏳ espera a que TX-B termine
                    → se esperan mutuamente → "Lock wait timeout exceeded"
```

Además `REQUIRED` es lo **semánticamente correcto**: si el alta se revierte, el
registro de `REGISTRO_EXITOSO` tiene que revertirse con ella. Un alta que nunca
ocurrió no puede quedar auditada como exitosa.

### 10.6 Regla práctica

> Si la auditoría **referencia una fila que la transacción en curso todavía no
> confirmó** → `REQUIRED`.
> Si tiene que **sobrevivir a una excepción** → `REQUIRES_NEW`.

### 10.7 `readOnly = true`

```java
@Transactional(readOnly = true)
```

Le avisa a Hibernate que no hace falta rastrear cambios (*dirty checking*), lo
que ahorra memoria y CPU. Se usó en `RateLimitService` y `UserDetailsServiceImpl`.

---

## 11. Decisiones de seguridad y su porqué

| Decisión | Motivo |
|---|---|
| Mensaje genérico en login | Impide enumerar cuentas |
| Hash señuelo | Impide enumerar por temporización |
| BCrypt factor 12 | Sal automática + costo que frena la fuerza bruta |
| Rate limit **primero** | Que el defensor no se vuelva el vector de DoS |
| Bloqueo pesimista | Evita perder incrementos del contador |
| `deny by default` | Un endpoint nuevo queda protegido, no abierto |
| Consultas parametrizadas | Elimina la inyección SQL |
| No loguear el texto ofensivo | No replicarlo dentro del sistema |
| No decir qué término se detectó | Impide evadir la lista por prueba y error |
| No decir qué rol falta (403) | No describe el mapa de permisos |
| Stack trace sólo al log | No revela estructura interna |
| `fecha_baja IS NULL` en el login | Una cuenta dada de baja se comporta como inexistente |
| `ADMINISTRADOR` bloqueado en el registro | Nadie puede auto-asignarse el rol |
| Clave JWT por variable de entorno | No queda en el repositorio |

---

## 12. Bugs reales encontrados al probar

Los tres se detectaron **ejecutando la aplicación**, no compilando. Ninguno daba
error de compilación.

### 12.1 Interbloqueo por `REQUIRES_NEW` + clave foránea

**Síntoma:** `POST /registro` → 500. En el log:
`Lock wait timeout exceeded; try restarting transaction`.

**Causa:** la auditoría del registro abría una transacción nueva que necesitaba
un lock sobre la fila `usuario` que la transacción suspendida aún no había
confirmado. Ver §10.5.

**Corrección:** dos métodos con propagación distinta —
`registrar` (`REQUIRED`) y `registrarAparte` (`REQUIRES_NEW`).

**Lección:** `REQUIRES_NEW` no es "la opción segura". Suspender una transacción y
tocar datos que ésta bloqueó es un interbloqueo garantizado.

### 12.2 Dialecto equivocado: MariaDB reportado como MySQL

**Síntoma:** `POST /login` → 500 sólo cuando el usuario **existía**. En el log:
`You have an error in your SQL syntax ... near 'of u1_0'`.

**Causa:** XAMPP trae **MariaDB 10.4.32**, no MySQL. El conector de MySQL reporta
la versión con un prefijo `5.5.5` por compatibilidad, y eso hacía que Hibernate
autodetectara `MySQLDialect` y generara `SELECT ... FOR UPDATE OF alias` —
sintaxis válida en MySQL 8 pero **no en MariaDB**.

Sólo se disparaba con usuario existente porque `FOR UPDATE` viene del bloqueo
pesimista, que sólo se ejecuta al contabilizar un intento fallido.

**Corrección** en `application.properties`:

```properties
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect
```

**Lección:** la autodetección de dialecto puede fallar cuando el driver y el
servidor no son de la misma familia.

### 12.3 Spring Boot 4 usa Jackson 3 (paquete nuevo)

**Síntoma:** `package com.fasterxml.jackson.databind does not exist`.

**Causa:** Spring Boot 4.1 migró a **Jackson 3**, donde el paquete pasó de
`com.fasterxml.jackson.databind` a **`tools.jackson.databind`**. El Jackson 2 que
aparece en el classpath de runtime lo arrastra `jjwt-jackson:0.12.3`, que todavía
usa la versión vieja.

Las **anotaciones** siguen en `com.fasterxml.jackson.annotation` (por eso
`@JsonInclude` compiló sin problema).

**Corrección:** `import tools.jackson.databind.ObjectMapper;`

### 12.4 Además, dos ajustes de entorno

- **Codificación.** La JVM arrancaba con `file.encoding=windows-1252`, lo que
  corrompería los acentos en las respuestas JSON. Se forzó UTF-8 en
  `build.gradle`.
- **Anotación deprecada.** `org.springframework.lang.NonNull` está deprecada en
  Spring 7; se migró a `org.jspecify.annotations.NonNull` (JSpecify 1.0.0, ya
  presente en el classpath).

---

## 13. Pruebas ejecutadas

Todas contra la aplicación corriendo con MariaDB real.

| # | Caso | Esperado | Resultado |
|---|---|---|---|
| 1 | Registro válido | 201 | ✅ 201, roles `["ORGANIZADOR"]` |
| 2 | Email duplicado | 409 | ✅ `RECURSO_DUPLICADO` |
| 3 | Nickname `xXp3l0tud0Xx` (l33t) | 422 | ✅ detectado como `pelotudo` |
| 4 | Nickname `admin` | 422 | ✅ suplantación bloqueada |
| 5 | 8 campos inválidos a la vez | 400 | ✅ los 8 reportados por campo |
| 5b | `perfil: ADMINISTRADOR` | 400 | ✅ rechazado |
| 6 | Login correcto | 200 + JWT | ✅ payload correcto, `HS384` |
| 7 | Contraseña incorrecta | 401 | ✅ mensaje genérico |
| 8 | Email inexistente | 401 | ✅ **mismo** mensaje que el 7 |
| 9 | 3 intentos fallidos | captcha + 5 min | ✅ `requiere_captcha=1`, cooldown correcto |
| 10 | Login correcto en cooldown | 403 | ✅ + cabecera `X-Reintentar-Despues` |
| 11 | 9 intentos fallidos | `BLOQUEADO` | ✅ + fila en `historial_estado_usuario` |
| 12 | Login correcto en cuenta bloqueada | 403 | ✅ `CUENTA_BLOQUEADA` |
| 13 | Endpoint protegido sin token | 401 | ✅ `NO_AUTENTICADO` |
| 14 | Token inválido | 401 | ✅ `NO_AUTENTICADO` |
| 15 | `PARTICIPANTE` → `/api/admin` | 403 | ✅ `ACCESO_DENEGADO` |
| 16 | Auditoría | 4 tipos de acción | ✅ todos registrados con IP |

**Cuentas de prueba que quedaron en la BD** (ambas `ACTIVO`, contraseña
`Enexia2026`):

| Email | Nickname | Rol |
|---|---|---|
| `gaston.test@enexia.com` | `gaston_dev` | ORGANIZADOR |
| `maria@enexia.com` | `maria_p` | PARTICIPANTE |

---

## 14. Pendientes

### Antes de producción

- [ ] **Definir `JWT_SECRET_KEY` como variable de entorno.** El valor por defecto
      de `application.properties` está en el repositorio y sólo sirve para
      desarrollo local.
- [ ] **Restringir CORS** al dominio real (hoy permite `localhost`).
- [ ] **Configurar `X-Forwarded-For` en Nginx**, o el rate limiting por IP es
      evadible (§7.3).
- [ ] Cambiar `ddl-auto` de `update` a `validate`.

### Sprint 2

- [ ] CAPTCHA (DFD 1.2.4A) — el flag ya se marca, falta validar el token
- [ ] 2FA por email (DFD 1.2.8–1.2.9)
- [ ] Email de alerta al bloquear (DFD 1.2.7A)
- [ ] Recuperación de contraseña (RF-1.5) — la entidad `PasswordResetToken` ya existe
- [ ] Registro de Persona Jurídica + validación de CUIT
- [ ] Endpoint de baja voluntaria (RF-1.6, `fecha_baja`)
- [ ] Incorporar `usuario_estado_sistema` con el módulo de moderación

### Documentación a corregir

- [ ] **RF-1.4** dice "bloquear al 3er intento"; se implementó la escala del DFD
      (3/6/9). Actualizar `docs/requisitos/requisitos_funcionales/modulo_1.md`.
- [ ] **`CLAUDE.md`** menciona `better-profanity` como librería Java. Es de
      Python. Actualizar para reflejar el filtro propio.
- [ ] `CLAUDE.md` describe el stack como Spring Boot 3.x; el proyecto usa **4.1.0**
      con Jackson 3 y Spring Security 7.

### Tests automatizados

- [ ] `AuthServiceTest` — login correcto, contraseña incorrecta, cuenta bloqueada
- [ ] `IntentosLoginServiceTest` — los tres umbrales
- [ ] `ModeracionTextoServiceTest` — l33t, acentos, falsos positivos
- [ ] `JwtServiceTest` — token válido, expirado, firma alterada
- [ ] `AuthControllerTest` con `@WebMvcTest` — códigos HTTP

---

## Apéndice: comandos útiles

Levantar la aplicación:

```bash
cd enexia && ./gradlew bootRun
```

Registrar un usuario:

```bash
curl -X POST http://localhost:8080/api/auth/registro -H "Content-Type: application/json" -d '{"email":"test@enexia.com","nickname":"test_user","password":"Enexia2026","passwordConfirmacion":"Enexia2026","nombre":"Test","apellido":"Usuario","dni":"40123456","fechaNacimiento":"1998-05-14","perfil":"PARTICIPANTE"}'
```

Iniciar sesión:

```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"test@enexia.com","password":"Enexia2026"}'
```

Ver el payload de un JWT:

```bash
echo "PEGAR_TOKEN_ACA" | cut -d. -f2 | base64 -d
```

Desbloquear una cuenta a mano:

```bash
mysql -u root enexia -e "UPDATE usuario SET intentos_fallidos=0, requiere_captcha=0, fecha_desbloqueo_cooldown=NULL, id_estado_usuario=(SELECT id_estado_usuario FROM usuario_estado WHERE estado_usuario='ACTIVO') WHERE email='test@enexia.com';"
```
