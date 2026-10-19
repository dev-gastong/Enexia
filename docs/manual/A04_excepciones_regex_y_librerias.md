# A04 — Excepciones, expresiones regulares y librerías

> **Parte A · Fundamentos de Java.** Tres herramientas que todo programa real necesita: **manejar errores** sin que el programa muera (excepciones), **reconocer patrones en el texto** (expresiones regulares) y **no reinventar la rueda** (librerías). Cierra la Parte A: a partir del próximo capítulo entramos en Spring Boot.

**Qué vas a aprender**
- Qué es una excepción, cómo se lanza, se captura y se traduce.
- `try` / `catch` / `finally`, `try-with-resources`, `throw` contra `throws`.
- Excepciones *checked* y *unchecked*; excepciones propias.
- Expresiones regulares: sintaxis, la API de Java y **cada regex real del proyecto** explicada.
- Registro de eventos (*logging*) con SLF4J.
- Mapa de las librerías que usa Enexia y qué hace cada una.

---

## 1. Excepciones

### 1.1 El problema

Cosas que **pueden salir mal** en cualquier programa: el archivo no existe, la base no responde, el usuario manda un dato inválido, un valor es `null`. Si cada método tuviera que devolver "un código de error" y todos los llamadores tuvieran que revisarlo, el código se volvería ilegible y los errores se perderían.

Una **excepción** es un objeto que representa **"algo anormal pasó"**. Cuando se **lanza**, el flujo normal se interrumpe y Java **sube por la pila de llamadas** buscando quién la **captura**. Si nadie lo hace, el programa (o el hilo) termina con un error.

```
controller.crear()
   └─ service.crear()
        └─ repository.save()      ← lanza DataIntegrityViolationException
              ↑ la excepción sube...
        ↑ ...nadie la captura...
   ↑ ...hasta GlobalExceptionHandler, que la convierte en una respuesta HTTP 409
```

### 1.2 Lanzar: `throw`

```java
if (!asistio) {
    throw new OperacionNoPermitidaException(
            "Solo pueden valorar el cronograma quienes tuvieron una inscripcion confirmada");
}
```

`throw` lanza **un objeto** (creado con `new`). Todo lo que sigue en el método **no se ejecuta**. Usar excepciones para las reglas de negocio tiene una gran ventaja: el código "feliz" queda sin `else` anidados, y cada precondición se lee como una **guarda** (se chequea y se corta temprano).

### 1.3 Capturar: `try` / `catch` / `finally`

```java
try {
    // código que puede fallar
    return EstadoUsuarioNombre.valueOf(estado.trim().toUpperCase()).name();
} catch (IllegalArgumentException ex) {
    // qué hacer si falla con ESE tipo de excepción
    throw new ReglaNegocioException("El estado '" + estado + "' no es valido");
} finally {
    // (opcional) se ejecuta SIEMPRE, haya o no excepción: ideal para liberar recursos
}
```

Reglas:
- Se puede encadenar **varios `catch`**; Java elige el **primero** que encaje. Poné los más específicos arriba y los generales abajo.
- Un solo `catch` puede atrapar varios tipos: `catch (IOException | SQLException ex)`.
- Atrapar `Exception` (la madre de casi todas) es muy amplio: solo se justifica en **fronteras** donde *nada* debe escapar. En `CloudinaryService.subirYModerar` está bien hecho, y el comentario del código dice por qué:

```java
} catch (Exception ex) {
    // Se atrapa Exception y no solo IOException: el SDK de Cloudinary
    // envuelve fallos de red y de parseo en tipos propios que no
    // comparten una raiz util.
    log.error("Fallo la subida a Cloudinary de '{}'", nombreArchivo, ex);
    return ResultadoImagen.rechazada("No se pudo procesar la imagen (error tecnico)");
}
```

Convierte **cualquier** fallo en un resultado ("rechazada: error técnico") y lo **registra** en el log. No se traga el error en silencio.

### 1.4 La regla de oro: nunca tragarse una excepción

```java
catch (Exception ex) { }          // ❌ el error desaparece sin dejar rastro
catch (Exception ex) {            // ✅ al menos se registra
    log.error("No se pudo ...", ex);
}
```

Un `catch` vacío convierte un fallo visible en un **bug misterioso**. Mirá cómo `BrevoClient.enviar` decide: **nunca lanza**, pero **siempre registra** y devuelve `false` — porque que falle un correo no debe tumbar el login ni la creación de un evento, pero tiene que quedar constancia.

### 1.5 `try-with-resources`: cerrar recursos solos

Los archivos, conexiones y *streams* hay que **cerrarlos**. En vez de un `finally` manual, Java ofrece:

```java
// ModeracionTextoService.cargarTerminos
try (InputStream entrada = new ClassPathResource(ARCHIVO_TERMINOS).getInputStream();
     BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {

    String linea;
    while ((linea = lector.readLine()) != null) {
        // ... procesar cada línea
    }

} catch (IOException ex) {
    log.error("No se pudo leer {}. La moderacion de texto queda DESACTIVADA.", ARCHIVO_TERMINOS, ex);
}
```

Lo declarado entre paréntesis (`InputStream`, `BufferedReader`) se cierra **automáticamente** al salir del bloque, aun si hay una excepción. Funciona con cualquier clase que implemente `AutoCloseable`.

Notá también la decisión de diseño del comentario: si el archivo de términos no se puede leer, **la aplicación arranca igual** pero con un `log.error` bien visible, porque *"fallar el arranque seria peor que arrancar sin filtro, pero dejarlo pasar en silencio significaria creer que se esta moderando cuando no"*.

### 1.5.1 `@PostConstruct`

Ese método lleva la anotación `@PostConstruct`: Spring lo ejecuta **una sola vez**, justo después de construir el objeto. Se usa para inicializaciones (acá, cargar la lista de términos a un `Set` en memoria en vez de leer el disco en cada validación).

### 1.6 Excepciones *checked* y *unchecked*

Java distingue dos familias:

```
Throwable
 ├── Error                 problemas graves de la JVM (OutOfMemoryError): no se capturan
 └── Exception
      ├── (checked)  IOException, SQLException, ...   EL COMPILADOR OBLIGA a capturarlas o declararlas (throws)
      └── RuntimeException (unchecked)                 no obliga: NullPointerException, IllegalArgumentException, ...
```

- Una **checked** debe tratarse sí o sí: `catch` o `throws` en la firma (`void leer() throws IOException`). Por eso `cargarTerminos` captura `IOException`.
- Una **unchecked** (hija de `RuntimeException`) no obliga a nada: se usa para **errores de programación o reglas violadas** que normalmente no se "arreglan" en el sitio.

**Decisión del proyecto:** *todas* las excepciones propias extienden `RuntimeException`. Así los servicios no están plagados de `throws` ni de `try/catch` de relleno; el error **sube sin ensuciar el código** hasta el único lugar que lo traduce a HTTP, `GlobalExceptionHandler`. (`CLAUDE.md`: *"Create custom exceptions extending RuntimeException"*.)

### 1.7 Excepciones propias del proyecto

Todas viven en `enexia/src/main/java/com/enexia/rg/exception/`. Cada una nombra **un tipo de situación de negocio**, y el manejador global la mapea a un código HTTP:

| Excepción | Cuándo se lanza | HTTP | Código en el JSON |
|---|---|---|---|
| `AutenticacionFallidaException` (y sus 3 hijas) | Cualquier rechazo de login. | 401 | `CREDENCIALES_INVALIDAS` (siempre el mismo) |
| `ReglaNegocioException` | Una regla que `@Valid` no puede expresar (contraseñas distintas, catálogo incompleto). | 400 | `REGLA_NEGOCIO` |
| `RecursoNoEncontradoException` | El id no existe (o es **ajeno**: se responde igual para no revelar qué ids existen). | 404 | `RECURSO_NO_ENCONTRADO` |
| `RecursoDuplicadoException` | Email, nickname, DNI o CUIT ya tomado; valoración repetida. | 409 | `RECURSO_DUPLICADO` |
| `OperacionNoPermitidaException` | El dato es válido pero el **estado actual** no admite la acción (ya aprobado, cupo agotado). | 409 | `OPERACION_NO_PERMITIDA` |
| `ContenidoInapropiadoException` | Texto rechazado por moderación. | 422 | `CONTENIDO_INAPROPIADO` |
| `PagoRechazadoException` | La pasarela simulada rechazó el cobro. | 402 | `PAGO_RECHAZADO` |

Una excepción propia es muy corta (`exception/ReglaNegocioException.java`):

```java
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);       // el padre guarda el mensaje; getMessage() lo devuelve
    }
}
```

### 1.8 Del `throw` al JSON: `GlobalExceptionHandler`

`@RestControllerAdvice` convierte una clase en "el manejador de errores de todos los controllers". Cada método con `@ExceptionHandler(TipoDeExcepción.class)` captura ese tipo y **fabrica la respuesta HTTP**:

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> manejarReglaNegocio(ReglaNegocioException ex) {
        return construir("REGLA_NEGOCIO", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<ErrorResponse> construir(String codigo, String mensaje, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(codigo, mensaje, status.value()));
    }
}
```

Todas las respuestas de error tienen **la misma forma JSON** (`ErrorResponse`): `{ "error": "...", "mensaje": "...", "status": 400, "timestamp": "...", "errores": {...} }`. Eso permite que el frontend escriba **un solo** manejador de errores (C02).

Detalles que enseñan mucho:

1. **El orden de especificidad.** Spring elige el `@ExceptionHandler` **más específico**. Por eso hay uno solo para toda la familia de autenticación, con un cartel de advertencia: *"no agregar acá un @ExceptionHandler más específico para CuentaBloqueadaException"*, porque Spring lo elegiría y la respuesta volvería a delatar que la cuenta está bloqueada.
2. **Traducir excepciones de la base.** `DataIntegrityViolationException` (violación de un `UNIQUE`) se traduce a un 409 amable. Se inspecciona `ex.getMostSpecificCause().getMessage()`, que contiene el nombre del índice (`uk_usuario_email`), para dar un mensaje preciso ("Ya existe una cuenta registrada con ese email"). Es la red de seguridad cuando dos altas simultáneas pasan ambas el chequeo previo (B03).
3. **Red de contención final:** `@ExceptionHandler(Exception.class)` atrapa lo imprevisto, **registra el detalle completo en el log** y responde un 500 genérico. Jamás se envía al cliente el stack trace ni el SQL.

### 1.9 Excepciones en el *pipeline* asíncrono

`ModeracionEventoService` envuelve todo el pipeline en un `try { ... } catch (RuntimeException ex)` y, si algo falla, marca el evento como `RECHAZADO_SISTEMA / ERROR_PIPELINE` (una **red de contención**: nunca debe quedar un evento colgado en `EN_PROCESO`). Y dentro de esa red hay **otra** (`intentarRechazoPorError`), porque *"si hasta esto falla (por ejemplo, la base caída), solo queda el log. Se atrapa igual para que una segunda excepción no oculte la primera"*.

---

## 2. Expresiones regulares (*regex*)

### 2.1 Qué son

Una **expresión regular** es un **patrón** que describe un conjunto de textos. Sirve para tres cosas: **validar** ("¿esto parece un CUIT?"), **buscar** y **reemplazar o dividir**.

### 2.2 Sintaxis esencial

| Elemento | Significado | Ejemplo |
|---|---|---|
| `abc` | los caracteres literales | `gaston` coincide con `gaston` |
| `.` | cualquier carácter (menos salto de línea) | `a.c` ← `abc`, `a-c` |
| `[abc]` | **uno** de esos caracteres | `[aeiou]` ← una vocal |
| `[a-z]` | un carácter del rango | `[0-9]` ← un dígito |
| `[^0-9]` | **cualquiera menos** esos | `[^0-9]` ← un no-dígito |
| `\d` | dígito (= `[0-9]`) | |
| `\w` | letra, dígito o `_` | |
| `\s` | espacio en blanco | |
| `\S` | lo que **no** es espacio | |
| `\p{L}` | cualquier **letra** Unicode (incluye `ñ`, `é`) | |
| `\p{N}` | cualquier **número** Unicode | |
| `\R` | cualquier salto de línea | |
| `^` / `$` | inicio / fin del texto | `^\d+$` ← solo dígitos |
| `*` | cero o más | |
| `+` | **una o más** | `\d+` ← un número |
| `?` | cero o una (opcional) | `-?` ← guion opcional |
| `{n}` / `{n,m}` | exactamente *n* / entre *n* y *m* | `\d{8}` ← 8 dígitos |
| `(a\|b)` | **grupo** con alternativa | `(SUSPENDER\|REACTIVAR)` |
| `(?=...)` | **mirar adelante** (*lookahead*): exige que lo que sigue cumpla algo, sin consumirlo | `(?=.*\d)` ← "hay un dígito en algún lado" |

> **En Java las barras se duplican.** En un literal de `String`, `\` es el carácter de escape, así que `\d` se escribe `"\\d"`. Por eso en el código ves `"^\\d{2}-?\\d{8}-?\\d$"`.

### 2.3 La API de Java

```java
"30-71659554-0".matches("^\\d{2}-?\\d{8}-?\\d$");     // true: ¿el texto ENTERO coincide?
"a1b2".replaceAll("[^0-9]", "");                       // "12": reemplaza todas las coincidencias
"a, b;c".split("[,;]\\s*");                            // ["a", "b", "c"]: divide
```

Para uso repetido o para extraer partes se usa `Pattern` y `Matcher`:

```java
Pattern p = Pattern.compile("^(\\d{2})-?(\\d{8})-?(\\d)$");   // se compila UNA vez
Matcher m = p.matcher("30-71659554-0");
if (m.matches()) {
    String tipo = m.group(1);      // "30"
    String dni = m.group(2);       // "71659554"
    String verificador = m.group(3);   // "0"
}
```

Compilar una regex es costoso: si se usa en un camino caliente, guardala en un `private static final Pattern`.

### 2.4 Cada regex del proyecto, explicada

**1. Nickname** (`UsuarioRegistroRequest`):

```java
@Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "El nickname solo admite letras, numeros y guion bajo")
```
`^...$` ancla al texto completo; `[a-zA-Z0-9_]` = una letra, dígito o guion bajo; `+` = una o más. → `gaston_test` ✔, `gas ton` ✘ (espacio), `gaston!` ✘.

**2. Política de contraseña** (`UsuarioRegistroRequest`, `RecuperacionConfirmarRequest`):

```java
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
         message = "La contrasena debe incluir al menos una minuscula, una mayuscula y un numero")
```
Tres **lookaheads** consecutivos, cada uno parado en el inicio: `(?=.*[a-z])` "en algún punto hay una minúscula", `(?=.*[A-Z])` "una mayúscula", `(?=.*\d)` "un dígito". Como mirar adelante **no consume** caracteres, los tres chequeos se aplican al **mismo** texto y el orden no importa. El `.+$` final consume el texto entero (al menos un carácter). La longitud (8 a 72) la valida otra anotación (`@Size`), porque **una regex por regla es más legible** que una gigante.

**3. Acciones cerradas** (todos los `Admin*Request`):

```java
@Pattern(regexp = "^(SUSPENDER|REACTIVAR)$", message = "La accion debe ser SUSPENDER o REACTIVAR")
```
Un "enum" en forma de regex: solo uno de esos valores exactos.

**4. CUIT** (`OrganizacionRegistroRequest`):

```java
@Pattern(regexp = "^\\d{2}-?\\d{8}-?\\d$", message = "El CUIT debe tener 11 digitos, con o sin guiones")
```
2 dígitos + guion opcional + 8 dígitos + guion opcional + 1 dígito. Acepta `30-71659554-0` y `30716595540`. Es la validación de **formato**; el **dígito verificador** lo calcula `ValidadorCuit` (A01 §6).

**3b. Teléfono**: `^[0-9+()\\s-]{7,20}$` — entre 7 y 20 caracteres de ese conjunto. Notá que el `-` va **al final** del corchete para que cuente como guion literal y no como rango.

**5. Nombre de persona** (`PerfilActualizarRequest`):

```java
@Pattern(regexp = "^(?=.{2,50}$)[\\p{L} '-]+$", ...)
```
Un lookahead `(?=.{2,50}$)` fija el **largo total** (2 a 50) y el resto exige solo letras Unicode, espacios, apóstrofes o guiones. Así `María José`, `O'Brien` y `Pérez-Gómez` pasan, y `Ana123` no.

**6. Nombre de un tipo de ticket** (`TicketRequest`): `^(?=.{3,20}$)\\S+( \\S+)*$` — 3 a 20 caracteres, formado por "palabras" (`\S+`) separadas por **un único** espacio; no admite espacios al principio, al final ni dobles.

**7. Reemplazar lo que no es dígito** (`ValidadorCuit.normalizar`): `cuit.replaceAll("[^0-9]", "")`.

**8. Dividir en párrafos** (`CorreoHtml.aHtml`): `texto.split("\\R\\s*\\R")` — un salto de línea, espacios opcionales y **otro** salto de línea = una línea en blanco. Cada trozo es un párrafo.

**9. Saneo de asunto** (`CorreoHtml.sanearAsunto`): `asunto.replaceAll("[\\r\\n\\t]+", " ")` — saltos y tabulaciones pasan a un espacio. Evita la **inyección de cabeceras** (un asunto con salto de línea podría agregar cabeceras falsas al correo).

**10. Moderación** (`ModeracionTextoService`):

```java
String[] palabras = texto.toLowerCase().split("[^\\p{L}\\p{N}]+");      // separar por todo lo que no sea letra/dígito
...
Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD)
        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");            // quitar los acentos
```
`NFD` separa `á` en `a` + marca de acento; el reemplazo borra las marcas (`\p{InCombiningDiacriticalMarks}` es el bloque Unicode de acentos combinables). Es la técnica estándar para "comparar sin acentos".

**11. Del lado del navegador** (`password-reset.html`): `/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(valor)` — "algo sin espacios ni `@`", un `@`, "algo", un punto, "algo". Es deliberadamente **flojo**: atrapa el tipeo obvio, y la validación real la repite el servidor. En JavaScript las regex van entre barras `/.../` y no necesitan duplicar la `\`.

### 2.5 Cuidados

- **Validar en el servidor siempre.** La validación del navegador es comodidad; cualquiera puede saltearla.
- **Regex "de email" perfecta no existe.** Por eso el proyecto usa `@Email` (anotación) y la prueba definitiva es mandar un correo.
- **ReDoS:** una regex mal escrita con repeticiones anidadas (`(a+)+$`) puede tardar **exponencialmente** ante cierta entrada y colgar el servidor. Las del proyecto son lineales y acotadas con `@Size`.

---

## 3. Registro de eventos (*logging*)

`System.out.println` no sirve en un servidor: no tiene nivel, fecha ni destino configurable. Enexia usa **SLF4J** (la fachada) con **Logback** (la implementación que trae Spring Boot).

```java
@Slf4j                         // Lombok crea:  private static final Logger log = LoggerFactory.getLogger(Clase.class);
public class ValoracionService {

    log.info("Valoracion {} registrada por el usuario {} para el cronograma {}",
            valoracion.getIdvaloracion(), participante.getIdUsuario(), cronograma.getIdCronograma());
}
```

- Los `{}` son **marcadores**: SLF4J reemplaza cada uno por el argumento correspondiente **solo si el nivel está activo** (no concatena texto en vano).
- **Niveles**, de menor a mayor gravedad: `trace`, `debug`, `info` (lo normal), `warn` (algo raro pero sigue), `error` (falló algo).
- Para registrar una excepción, pasala como **último argumento**: `log.error("Fallo la subida de '{}'", nombre, ex)` imprime el mensaje **y la traza**.

**Qué NO registrar** (decisiones de seguridad del proyecto):
- Contraseñas, tokens de recuperación, claves de API (`BrevoClient` jamás loguea el cuerpo de un correo real: *"algunos correos llevan un token de recuperación, que en un log sería una credencial en claro"*).
- El **texto ofensivo** rechazado por moderación: se registra el *campo*, no el valor.
- El motivo real de un rechazo de login se registra en el servidor (`log.warn("Fallo de autenticacion. Motivo interno: {}", ...)`) y **nunca** va en la respuesta.

---

## 4. Librerías: mapa de lo que usa Enexia

Una **librería** es código de otros que se agrega al proyecto en `build.gradle`. Un **framework** (Spring) además **dirige el flujo**: él te llama a vos.

| Librería | Para qué se usa en Enexia | Dónde se ve |
|---|---|---|
| **Spring Boot** (`webmvc`, `data-jpa`, `security`, `validation`) | Servidor web, acceso a datos, seguridad y validación. | Parte B |
| **Hibernate** (dentro de `data-jpa`) | ORM: convierte clases Java en tablas. | B03 |
| **Lombok** | Genera getters, setters, constructores, builders y el `log`. | A01, abajo |
| **Jackson** (viene con Spring) | Convierte objetos ↔ JSON automáticamente. | B02, C01 |
| **jjwt** (`io.jsonwebtoken`) | Crear y verificar JWT. | B04 |
| **BCrypt** (Spring Security Crypto) | Cifrar contraseñas (hash con sal). | B04 |
| **MySQL Connector/J** | El *driver* para hablar con MariaDB/MySQL. | B03 |
| **Cloudinary SDK** | Subir y moderar imágenes. | D5 |
| **RestClient** (Spring) | Cliente HTTP, usado para llamar a la API de Brevo. | D9 |
| **JUnit 5** | Escribir y correr tests. | B06 |
| **Mockito** | Crear dobles de prueba (*mocks*). | B06 |
| **AssertJ** | Aserciones legibles (`assertThat(x).isEqualTo(y)`). | B06 |
| **Awaitility** | Esperar condiciones asíncronas con tiempo límite. | B06 |
| **Playwright** | Manejar un navegador real y hacer pedidos HTTP en los tests. | B06 |

### 4.1 Lombok en detalle

Lombok es un **procesador de anotaciones**: en la compilación **escribe por vos** el código repetitivo. Por eso aparece como `compileOnly` y `annotationProcessor` en `build.gradle`.

| Anotación | Genera |
|---|---|
| `@Getter` / `@Setter` | getters / setters de todos los atributos. |
| `@NoArgsConstructor` | constructor vacío. |
| `@AllArgsConstructor` | constructor con todos los atributos. |
| `@RequiredArgsConstructor` | constructor con los atributos `final` (clave en Spring, B01). |
| `@Builder` | el patrón *builder* (A01 §6.3). |
| `@Slf4j` | el atributo `log`. |

> **Cuidado:** si abrís el `.class` compilado, los métodos están. Si tu editor se queja de que `getEmail()` no existe, es que no tiene el plugin de Lombok instalado; el compilador de Gradle sí lo procesa.

### 4.2 Jackson: JSON ↔ objetos

Cuando el navegador manda `{"email":"a@b.com","password":"Clave123"}`, **Jackson** lo convierte en un objeto `UsuarioLoginRequest` (por los nombres de los atributos y sus setters). Al revés, un objeto `UsuarioLoginResponse` se convierte en JSON. Todo esto es automático; solo hace falta que el DTO tenga atributos con *getters/setters* y un constructor vacío (otra razón de `@NoArgsConstructor`). Se explica en B02.

---

## 5. Cómo se relaciona todo esto con lo que viene

- Las excepciones propias y el `GlobalExceptionHandler` son la **mitad de la API**: la que habla cuando algo sale mal (B02, C01).
- Las regex y `@Pattern`/`@Size`/`@NotBlank` forman la **primera línea de defensa** de los datos de entrada (B02).
- Lombok, SLF4J y las librerías de seguridad están en **todas** las capas.

---

## 6. Errores típicos

| Error | Consecuencia | Remedio |
|---|---|---|
| `catch (Exception e) {}` | Errores invisibles. | Registrar y/o relanzar con contexto. |
| Lanzar `RuntimeException` pelada. | No se puede distinguir un caso de otro. | Excepciones con nombre de negocio. |
| Poner lógica de negocio dentro del `catch`. | Flujo difícil de seguir. | Capturar para traducir o limpiar, no para decidir. |
| Olvidar duplicar `\` en una regex de Java. | `PatternSyntaxException` o regex que no hace lo esperado. | `"\\d"`, o probar la regex en un test. |
| Regex como única validación. | Datos inválidos que "parecen" válidos. | Combinar con `@Size`, lógica y la base de datos. |
| Loguear datos sensibles. | Credenciales en un archivo de log. | Registrar identificadores, nunca secretos. |

---

## 7. Para practicar

1. Escribí una regex para una patente argentina de formato nuevo (`AB123CD`). Probala con `matches` y con una lista de casos válidos e inválidos.
2. En `GlobalExceptionHandler`, agregá (en papel) un manejador para `OperacionNoPermitidaException`: ¿qué status, qué código? Compará con el real.
3. Escribí un método que lea un archivo de texto con `try-with-resources` y cuente las líneas no vacías, ignorando las que empiezan con `#` (como hace `cargarTerminos`).
4. Explicá por qué la regex de la contraseña usa tres lookaheads en vez de una sola expresión como `[a-z][A-Z]\d`.
5. ¿Qué pasaría si `BrevoClient.enviar` **lanzara** la excepción cuando el proveedor falla, en vez de devolver `false`? Pensá en el login con tres intentos fallidos.

**Siguiente capítulo (Parte B):** [B01 — Spring Boot: inversión de control y configuración](./B01_spring_ioc_configuracion.md)
