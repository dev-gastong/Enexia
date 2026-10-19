# B02 — MVC, controladores, DTO y validación

> **Parte B · Spring Boot.** Ya podés armar objetos y Spring sabe conectarlos (B01). Ahora aprendés **cómo entra una petición HTTP y cómo sale una respuesta**: la **arquitectura en capas** (MVC) con sus tres piezas del backend —controlador, servicio, repositorio—, los **DTO** que viajan por la red, y la **validación** que protege la puerta de entrada.

**Qué vas a aprender**
- El patrón **MVC** y cómo se adapta a una API REST con frontend separado.
- El recorrido completo de una petición, de punta a punta.
- Controladores: `@RestController`, `@RequestMapping`, `@GetMapping`/`@PostMapping`..., `@PathVariable`, `@RequestParam`, `@RequestBody`, `@RequestPart`, `ResponseEntity`.
- **DTO** (`*Request` / `*Response`) y por qué **nunca** se expone una entidad.
- Validación con Bean Validation: `@Valid`, `@NotBlank`, `@Size`, `@Pattern`, `@Email`, validaciones anidadas y **personalizadas**.
- Mapeo entidad ↔ DTO, paginación y subida de archivos.
- Cómo se eligen los códigos de estado HTTP.

---

## 1. El patrón MVC y las capas

**MVC** (*Model – View – Controller*) separa un programa en tres responsabilidades:

| Pieza | Qué es | En un sitio clásico | En Enexia |
|---|---|---|---|
| **Model** (modelo) | Los datos y las reglas del negocio. | Entidades + lógica. | `model/` (entidades), `service/` (reglas), `repository/` (datos). |
| **View** (vista) | Lo que ve el usuario. | HTML generado por el servidor. | **Archivos HTML/CSS/JS estáticos** en `src/main/resources/static/`, que viven en el navegador. |
| **Controller** | Recibe lo que hace el usuario y coordina. | Clases que atienden URLs. | `controller/`. |

La diferencia con un sitio tradicional: en Enexia **el servidor no genera HTML**. Devuelve **JSON** y el navegador (con JavaScript) arma la pantalla. Por eso se habla de una **API REST** (C01) con un frontend desacoplado; la "Vista" es **todo el frontend**.

### 1.1 Las capas del backend

```
          Navegador (View)
               │  HTTP + JSON
               ▼
┌────────────────────────────────────────────────────┐
│ CONTROLLER   recibe HTTP, valida entrada, responde │  ← traduce HTTP ↔ Java
├────────────────────────────────────────────────────┤
│ SERVICE      reglas de negocio, transacciones      │  ← lo que importa
├────────────────────────────────────────────────────┤
│ REPOSITORY   consultas a la base                   │  ← solo datos
├────────────────────────────────────────────────────┤
│ MODEL        entidades JPA (tablas)                │
└────────────────────────────────────────────────────┘
               │
               ▼
            MariaDB
```

Regla de oro (está en el `CLAUDE.md`): **cada capa solo habla con la de abajo y no sabe nada de la de arriba**.

| Capa | Puede | **No** puede |
|---|---|---|
| Controller | Leer la petición, llamar a **un** servicio, elegir el status HTTP. | Tener reglas de negocio ni tocar un repository directo (con una excepción del catálogo público, que es solo lectura). |
| Service | Aplicar reglas, abrir transacciones, orquestar repositorios y otros servicios. | Saber qué es un `ResponseEntity` ni un status HTTP. Lanza excepciones; otra capa las traduce. |
| Repository | Leer/escribir la base. | Contener decisiones de negocio. |
| Entity | Mapear columnas. | Tener lógica de negocio (salvo reglas **de su propio dato**, como `Usuario.getRoles()`). |

**Por qué tanta disciplina:** cada capa se puede **probar sola** (B06), cambiar sin romper las otras (cambiar la base no toca los controllers) y **leer** sin cargar todo en la cabeza.

---

## 2. El recorrido de una petición

Seguí un `POST /api/auth/login` de punta a punta:

```
1. El navegador envía:   POST /api/auth/login
                         Content-Type: application/json
                         {"nickname":"gaston_test","password":"..."}

2. Tomcat recibe el pedido y lo pasa por la CADENA DE FILTROS (seguridad, CORS)  → B04

3. DispatcherServlet (el "portero" de Spring MVC) busca qué método atiende POST /api/auth/login
   → AuthController.login(...)

4. Jackson convierte el JSON del cuerpo en un objeto UsuarioLoginRequest.

5. @Valid ejecuta las validaciones del DTO. Si alguna falla → MethodArgumentNotValidException
   → GlobalExceptionHandler → 400 con el detalle por campo (y NUNCA se entra al método).

6. AuthController.login llama a  authService.login(peticion, request).

7. AuthService aplica las reglas (¿existe? ¿contraseña? ¿cuenta activa?) usando
   UsuarioRepository (que lee la base).

8. AuthService devuelve un UsuarioLoginResponse  (o lanza una AutenticacionFallidaException).

9. El controller envuelve el resultado: ResponseEntity.ok(...)  → status 200.

10. Jackson convierte el objeto en JSON y Tomcat lo envía al navegador.
    (Si hubo excepción: GlobalExceptionHandler fabrica un ErrorResponse con su status.)
```

Si memorizás este recorrido, **cualquier error** se ubica en uno de esos diez pasos.

---

## 3. Controladores

### 3.1 Anatomía

`controller/AuthController.java`:

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RecuperacionCuentaService recuperacionCuentaService;

    @PostMapping("/login")
    public ResponseEntity<UsuarioLoginResponse> login(
            @Valid @RequestBody UsuarioLoginRequest peticion,
            HttpServletRequest request) {

        return ResponseEntity.ok(authService.login(peticion, request));
    }
}
```

| Anotación | Qué hace |
|---|---|
| `@RestController` | La clase atiende HTTP **y** cada método devuelve **datos** (que se convierten a JSON), no el nombre de una vista. (= `@Controller` + `@ResponseBody`.) |
| `@RequestMapping("/api/auth")` | Prefijo común de todas las rutas de la clase. |
| `@PostMapping("/login")` | Este método atiende `POST /api/auth/login`. Hay una por verbo: `@GetMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`. |
| `@RequestBody` | "El cuerpo de la petición (JSON) es un objeto de este tipo". |
| `@Valid` | "Ejecutá las validaciones de ese objeto antes de entrar". |
| `HttpServletRequest request` | El objeto crudo de la petición. Spring lo inyecta solo con ponerlo en la firma; el servicio lo usa para **auditar IP y user-agent**. |

El controller es **delgado**: casi todo su cuerpo es una línea que delega.

### 3.2 Leer datos de la petición

| Quiero leer… | Anotación | Ejemplo real |
|---|---|---|
| El **cuerpo** JSON | `@RequestBody` | `@Valid @RequestBody UsuarioLoginRequest peticion` |
| Un **segmento de la URL** | `@PathVariable` | `@GetMapping("/{idEvento}") ... @PathVariable Long idEvento` |
| Un **parámetro de consulta** (`?texto=rock`) | `@RequestParam` | `@RequestParam(required = false) String texto` |
| Una **parte de un multipart** (JSON + archivos) | `@RequestPart` | `@RequestPart("datos") EventoCrearRequest datos` |
| El **usuario autenticado** | `Principal` | `Principal principal` → `principal.getName()` (el email del JWT) |

Los parámetros de consulta admiten **valor por defecto** y **opcionalidad**:

```java
@GetMapping
@PreAuthorize("hasRole('ORGANIZADOR')")
public ResponseEntity<Page<EventoResponse>> listar(
        @RequestParam(required = false) String texto,
        @RequestParam(required = false) Long idCategoria,
        @RequestParam(required = false) String estado,
        @RequestParam(defaultValue = "0") int pagina,
        @RequestParam(defaultValue = "" + TAMANO_PAGINA_DEFECTO) int tamano,
        Principal principal) { /* ... */ }
```

`GET /api/organizador/eventos?texto=rock&pagina=2` → `texto="rock"`, `pagina=2`, el resto toma el valor por defecto o `null`.

### 3.3 `ResponseEntity`: controlar la respuesta

`ResponseEntity<T>` es "la respuesta HTTP completa": **status + cabeceras + cuerpo**.

```java
return ResponseEntity.ok(objeto);                                   // 200
return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);   // 201
return ResponseEntity.accepted().body(new MensajeResponse("..."));  // 202
return ResponseEntity.noContent().build();                          // 204 (sin cuerpo)
```

### 3.4 Elegir el código de estado: las decisiones del proyecto

El status **comunica** qué pasó. Los controllers de Enexia no usan siempre `200`; eligen con intención (y lo comentan):

| Situación | Código | Por qué (del código real) |
|---|---|---|
| Alta de cuenta | **201 Created** | "El código correcto cuando la petición crea un recurso." |
| Crear un evento | **202 Accepted** | "El evento quedó ACEPTADO y está EN_PROCESO, pero todavía no está publicado. Un 201 le diría al cliente que el recurso ya está completo, y no lo está: falta la moderación asíncrona." |
| Editar un evento | **200 OK** | "La moderación de la edición es sincrónica: para cuando llega la respuesta el resultado ya es definitivo." |
| Pedir enlace de recuperación | **202 Accepted** | "El envío del correo es asíncrono: la acción está aceptada pero no completada." Y siempre el mismo mensaje, exista o no la cuenta. |
| Dar de baja un evento | `DELETE` → **200** | "DELETE por semántica HTTP, pero NO borra: muta el estado a DADO_DE_BAJA." |

Tabla de referencia de los códigos que usa la API:

| Código | Significado | Cuándo en Enexia |
|---|---|---|
| 200 | OK | Lecturas y cambios exitosos |
| 201 | Created | Registro |
| 202 | Accepted | Trabajo asíncrono |
| 400 | Bad Request | Validación o regla de negocio |
| 401 | Unauthorized | Sin sesión o login fallido |
| 402 | Payment Required | Pago rechazado |
| 403 | Forbidden | Autenticado pero sin el rol |
| 404 | Not Found | No existe (o es ajeno) |
| 409 | Conflict | Duplicado o estado que no admite la acción |
| 422 | Unprocessable Content | Texto rechazado por moderación |
| 500 | Internal Server Error | Falla imprevista (genérico, sin detalle) |

### 3.5 Autorización por rol: `@PreAuthorize`

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasRole('ORGANIZADOR')")
public ResponseEntity<EventoResponse> crear( ... )
```

Antes de ejecutar el método, Spring Security comprueba que el usuario tenga el rol. Si no, responde **403**. Se detalla en B04.

---

## 4. DTO: los objetos que viajan

### 4.1 Qué es y por qué

Un **DTO** (*Data Transfer Object*) es un objeto **hecho a medida para una petición o una respuesta**. En Enexia hay **un `*Request` y un `*Response` por endpoint** (paquete `dto/`, 46 clases).

**Por qué NO devolver la entidad directamente:**

1. **Seguridad:** `Usuario` tiene el campo `password` (el hash). Si la devolvieras tal cual, saldría por la red.
2. **Acoplamiento:** si mañana cambia una columna, cambiaría el contrato de la API y se rompería el frontend.
3. **Carga perezosa (*lazy*):** una entidad tiene relaciones que se cargan a demanda; convertirla a JSON fuera de la transacción provoca `LazyInitializationException` (B03).
4. **Forma:** la pantalla necesita datos de varias entidades juntos (`EventoResponse` mezcla evento + categoría + organizador + cupos), que no existen como una sola entidad.
5. **Entrada:** el cliente **no debe poder fijar** campos como el `id` o el estado: el `*Request` solo tiene los campos que se **permiten** enviar (evita *mass assignment*).

> **Regla del `CLAUDE.md`:** *"Never expose entities in API responses"*.

### 4.2 Un `Request` real

`dto/UsuarioLoginRequest.java`:

```java
@Getter
@Setter
@NoArgsConstructor
public class UsuarioLoginRequest {

    @NotBlank(message = "El nickname es obligatorio")
    @Size(max = 20, message = "El nickname no puede superar los 20 caracteres")
    private String nickname;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(max = 72, message = "La contrasena no puede superar los 72 caracteres")
    private String password;
}
```

- `@NoArgsConstructor` + `@Setter`: **Jackson** crea el objeto vacío y llama a los setters con los valores del JSON. Por eso el DTO de entrada necesita constructor vacío.
- Las anotaciones de validación están **sobre cada atributo**, con su `message`.
- Decisión de seguridad comentada en el propio archivo: las restricciones del login son **deliberadamente más laxas** que las del registro; exigir el mismo patrón le revelaría a un atacante que su candidato no cumple la política.

### 4.3 Un `Response` real

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioLoginResponse {
    private Long idUsuario;
    private String email;
    private String token;
    private String tipoToken = "Bearer";
    private List<String> roles;
}
```

Jackson recorre los *getters* y produce: `{"idUsuario":3,"email":"...","token":"eyJ...","tipoToken":"Bearer","roles":["ORGANIZADOR","PARTICIPANTE"]}`. No hay `password`: **el DTO simplemente no lo tiene**.

### 4.4 El DTO de error

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String error;      // etiqueta corta y estable ("REGLA_NEGOCIO")
    private String mensaje;    // texto en español listo para mostrar
    private int status;
    private LocalDateTime timestamp;
    private Map<String, String> errores;   // campo → motivo, solo en validaciones
}
```

`@JsonInclude(NON_NULL)` **omite del JSON** los campos nulos (`errores` solo aparece cuando hay errores de validación). Todo error de la API tiene esta forma: el frontend escribe **un solo** manejador.

---

## 5. Validación con Bean Validation

### 5.1 La idea

Nunca confíes en lo que llega de afuera. La primera defensa es **declarar reglas sobre los atributos del DTO** con anotaciones; el framework las verifica antes de que tu código corra. (Viene en `spring-boot-starter-validation`.)

### 5.2 Anotaciones más usadas

| Anotación | Exige | Se aplica a |
|---|---|---|
| `@NotNull` | no nulo | cualquier tipo |
| `@NotBlank` | no nulo, no vacío y no solo espacios | `String` |
| `@NotEmpty` | no nulo ni vacío | `String`, colecciones |
| `@Size(min, max)` | largo/tamaño en rango | `String`, colecciones |
| `@Min` / `@Max` | número mínimo / máximo | números (`@Min(1) @Max(5)` en la valoración) |
| `@Email` | forma de email | `String` |
| `@Pattern(regexp)` | coincide con la regex (A04) | `String` |
| `@Valid` | **entrar** a validar el objeto anidado | objetos y listas |
| `@Past` / `@Future` | fecha pasada / futura | fechas |

Cada una admite `message = "..."`: el texto que verá el usuario.

### 5.3 Un DTO con varias reglas, anidado

`dto/EventoCrearRequest.java` (recortado):

```java
public class EventoCrearRequest {

    @NotBlank(message = "El nombre del evento es obligatorio")
    @Size(min = 5, max = 150, message = "El nombre debe tener entre 5 y 150 caracteres")
    private String nombre;

    @NotNull(message = "La categoria es obligatoria")
    private Long idCategoria;

    @NotNull(message = "La ubicacion del evento es obligatoria")
    @Valid
    private UbicacionRequest ubicacion;

    @NotEmpty(message = "El evento debe tener al menos una fecha en la agenda")
    @Size(max = 20, message = "No se admiten mas de 20 fechas por evento")
    @Valid
    private List<CronogramaRequest> cronogramas;
}
```

- **`@Valid` en un atributo** hace validación **en cascada**: valida también el contenido de `UbicacionRequest` y de **cada** `CronogramaRequest` de la lista. Sin `@Valid`, esas reglas internas se **ignoran en silencio**.
- Varias anotaciones juntas se evalúan todas.
- El tope de 20 fechas no es arbitrario (comentario del código): *"sin límite una sola petición podría generar cientos de filas"*.

### 5.4 Qué pasa cuando una validación falla

1. `@Valid` en el parámetro del controller dispara el chequeo **antes de entrar al método**.
2. Si algo falla, Spring lanza `MethodArgumentNotValidException`.
3. `GlobalExceptionHandler.manejarValidacion` junta **cada campo con su mensaje** (el primero si un campo viola varias reglas, con `putIfAbsent`) y responde **400**:

```json
{
  "error": "VALIDACION_FALLIDA",
  "mensaje": "Hay campos con errores. Revise el detalle.",
  "status": 400,
  "timestamp": "2026-10-19T10:15:00",
  "errores": {
    "nickname": "El nickname solo admite letras, numeros y guion bajo",
    "password": "La contrasena debe tener entre 8 y 72 caracteres"
  }
}
```

El frontend usa `errores` para **marcar cada campo** del formulario con su mensaje (C02).

### 5.5 Validación personalizada: tu propia anotación

Cuando ninguna anotación alcanza, se crea una. Son **dos piezas**:

**1) La anotación** (`validation/FechaFuturaValida.java`):

```java
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidadorFechaFutura.class)   // ← quién la valida
@Documented
public @interface FechaFuturaValida {

    String message() default "La fecha debe ser futura y válida";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

- `@interface` declara una **anotación**.
- `@Target` dice dónde se puede poner (en atributos y parámetros); `@Retention(RUNTIME)` la mantiene viva **en ejecución** (si no, desaparecería al compilar y el framework no la vería).
- `groups` y `payload` son obligatorios por la especificación; se dejan así.

**2) El validador** (`validation/ValidadorFechaFutura.java`):

```java
public class ValidadorFechaFutura implements ConstraintValidator<FechaFuturaValida, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;      // de null se encarga @NotNull
        }
        return value.isAfter(LocalDate.now().minusDays(1));   // hoy o posterior
    }
}
```

Uso en `CronogramaRequest`:

```java
@FechaFuturaValida(message = "La fecha ingresada debe ser posterior o igual al día de hoy")
private LocalDate fecha;
```

Detalle de diseño: el validador devuelve `true` para `null` para **no mezclar responsabilidades**: "¿es obligatoria?" lo decide `@NotNull`; "¿es futura?" lo decide ésta.

### 5.6 Qué NO puede validar la anotación: las reglas de negocio

Hay reglas que dependen de **otros datos o de la base**: que `password` y `passwordConfirmacion` coincidan, que la categoría exista, que el cupo alcance. Esas se validan **en el servicio** y lanzan `ReglaNegocioException`. `ValoracionService` lo comenta: *"El rango 1-5 lo valida `@Valid` en el controller: no se repite la comprobación, evita que las dos reglas puedan divergir"*.

**Dos capas de defensa, cada una con su rol:**
- **DTO:** forma y rango del dato (¿es un email? ¿entre 5 y 150 caracteres?).
- **Servicio:** coherencia con el negocio y el estado actual (¿existe? ¿ya está aprobado?).
- **Base de datos:** la última red (`UNIQUE`, claves foráneas) frente a condiciones de carrera (B03).

---

## 6. Mapeo: de entidad a DTO

Convertir una entidad en su respuesta es trabajo del **servicio** (o de una clase auxiliar), **no** del controller. En Enexia hay dos estilos:

**1) Dentro del servicio, con `@Builder`** (`ValoracionService.mapear`):

```java
private ValoracionResponse mapear(Valoracion valoracion, EventoCronograma cronograma, Usuario autor) {
    return ValoracionResponse.builder()
            .idValoracion(valoracion.getIdvaloracion())
            .idCronograma(cronograma.getIdCronograma())
            .eventoNombre(cronograma.getEvento() == null ? null : cronograma.getEvento().getNombre())
            .valor(valoracion.getValor())
            .comentario(valoracion.getComentario())
            .fecha(valoracion.getFecha())
            .autor(autor == null ? null : autor.getNickname())
            .cronogramaFecha(cronograma.getFecha())
            .build();
}
```

Notá `autor.getNickname()`: de la valoración se expone el **nickname**, **no** el nombre y apellido civiles (RF-3.4). El mapeo es también **una decisión de privacidad**.

**2) Una clase dedicada** cuando el mapeo es largo o se reutiliza: `service/EventoMapper` (un `@Component`) arma la tarjeta del catálogo, la ficha completa y la respuesta del organizador. Es lógica "pura" (sin dependencias de base), por eso en los tests se usa **la real** y no un mock.

**Páginas:** `Page<T>.map(funcion)` aplica el mapeo a cada elemento **conservando los datos de paginación** (total, número de página):

```java
return valoracionRepository.findByCronogramaEventoIdEventoOrderByFechaDesc(idEvento, pageable)
        .map(v -> mapear(v, v.getCronograma(), v.getUsuario()));
```

*(Se podría usar `ModelMapper`, que el `CLAUDE.md` menciona como opción; el proyecto eligió el mapeo manual: es explícito, se lee, y un campo que cambie rompe en compilación en vez de en silencio.)*

---

## 7. Paginación

Devolver 5.000 eventos de una vez es lento y rompe la pantalla. **Paginar** es devolver "la página 2 de 12 elementos".

Spring Data ofrece tres piezas:

| Tipo | Rol |
|---|---|
| `Pageable` / `PageRequest` | **Lo que pedís:** número de página (desde 0), tamaño y orden. |
| `Sort` | El criterio de orden. |
| `Page<T>` | **Lo que recibís:** el contenido de la página **más** el total de elementos y de páginas. |

En el controller:

```java
PageRequest paginado = PageRequest.of(
        Math.max(0, pagina),
        Math.min(Math.max(1, tamano), TAMANO_PAGINA_MAXIMO),
        Sort.by(Sort.Direction.DESC, "fechaCreacion"));
```

Dos protecciones en una línea cada una: **`Math.max(0, pagina)`** evita páginas negativas y **`Math.min(..., TAMANO_PAGINA_MAXIMO)`** evita que un cliente pida `tamano=1000000` y tumbe el servidor. Se **sanea la entrada** aunque venga de tu propio frontend.

El JSON de una `Page` incluye `content` (la lista), `totalElements`, `totalPages`, `number` (página actual) y `size`; el frontend lo usa para el botón **"Cargar más"** (C02).

---

## 8. Subir archivos: `multipart/form-data`

Para crear un evento hay que mandar **datos + imágenes en una sola petición**. `multipart/form-data` permite varias **partes**, cada una con su tipo:

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasRole('ORGANIZADOR')")
public ResponseEntity<EventoResponse> crear(
        @Valid @RequestPart("datos") EventoCrearRequest datos,                          // parte JSON
        @RequestPart(value = "imagenes", required = false) List<MultipartFile> imagenes, // partes binarias
        Principal principal,
        HttpServletRequest request) {
    EventoResponse respuesta = eventoService.crear(
            principal.getName(), datos, leerImagenes(imagenes), request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(respuesta);
}
```

Por qué **multipart** y no Base64 dentro del JSON: *"cada imagen crecería un 33% y habría que tenerla dos veces en memoria"*.

Un detalle sutil, comentado en `leerImagenes`: los bytes se copian **antes de que termine la petición** porque la moderación corre en otro hilo y, para cuando arranque, **el contenedor ya borró los archivos temporales** del multipart. Es una consecuencia de mezclar archivos con trabajo asíncrono (B05).

Los **límites** se configuran en `application.properties`: `spring.servlet.multipart.max-file-size=3MB` debe ser **mayor** que el límite de negocio (2 MB por imagen) para que el rechazo lo haga la **regla con su mensaje** y no el contenedor con un 500 seco.

---

## 9. Un controlador completo para leer, comentado

Con todo lo visto, leé `ValoracionController` (`controller/ValoracionController.java`) buscando:

1. La ruta base (`@RequestMapping`) y los verbos.
2. Qué parámetros llegan por cuerpo, ruta y consulta.
3. Cómo obtiene el usuario (`Principal`).
4. A qué servicio delega y qué código HTTP devuelve.
5. Qué excepciones puede lanzar el servicio y qué status produce cada una (A04 §1.7).

Ese ejercicio es **el método** para entender cualquier endpoint de Enexia.

---

## 10. Comentarios desactualizados: un recordatorio

Al leer `AuthController.registrarConOrganizacion` vas a encontrar un comentario que dice que la organización "nace en REVISION_PENDIENTE". **Ya no es así**: esa etapa se eliminó el 2026-09-09 (ADR-14) y la organización queda aprobada al validar el CUIT. El código y las pruebas mandan; los comentarios largos envejecen. Cuando una explicación y el comportamiento no coincidan, **confiá en el comportamiento** (probalo) y corregí el comentario.

---

## 11. Errores típicos

| Error | Síntoma | Remedio |
|---|---|---|
| Olvidar `@Valid`. | Las validaciones del DTO no se ejecutan. | `@Valid` en el parámetro del controller. |
| Olvidar `@Valid` en un atributo anidado. | Se ignoran las reglas del objeto interno. | `@Valid` sobre el atributo. |
| Devolver una entidad. | Se filtra el hash de la contraseña; `LazyInitializationException`. | DTO de respuesta. |
| Lógica de negocio en el controller. | Imposible de probar; se duplica. | Moverla al servicio. |
| DTO sin constructor vacío. | Jackson no puede crearlo (400 raro). | `@NoArgsConstructor`. |
| `@RequestParam` sin saneo. | Un cliente pide `tamano=1000000`. | Acotar con `Math.min/max`. |
| Confiar solo en la validación del navegador. | Cualquiera puede mandar datos inválidos con `curl`. | Validar siempre en el servidor. |

---

## 12. Para practicar

1. Dibujá el recorrido de `POST /api/organizador/eventos` marcando en qué paso podría devolver 400, 403, 404 y 202.
2. Agregá (en papel) un campo `telefono` a `PerfilActualizarRequest` con una regex y un `@Size`, y escribí qué JSON de error recibiría el cliente si manda `"abc"`.
3. Creá una anotación `@HoraValida` que exija que `horaFin` sea posterior a `horaInicio`. ¿Sobre qué se aplicaría: sobre un atributo o sobre la clase? (Pista: necesita **dos** campos.)
4. ¿Por qué `listar` en `EventoOrganizadorController` llama a `finalizacionEventosService.finalizarVencidosDe(...)` **antes** de `eventoService.listarDeOrganizador(...)`? Leé el comentario del código y explicalo con tus palabras.
5. Pedí la página 0 con `tamano=3` y la página 1 con `tamano=3` del catálogo público (`/api/publico/eventos`) con el navegador. Compará `totalElements` en ambas.

**Siguiente capítulo:** [B03 — Persistencia: bases de datos, JPA e Hibernate](./B03_persistencia_jpa.md)
