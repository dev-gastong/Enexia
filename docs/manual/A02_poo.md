# A02 — Programación orientada a objetos (POO)

> **Parte A · Fundamentos de Java.** En A01 aprendiste a fabricar objetos. Acá aprendés **a organizarlos**: cómo se protege su estado, cómo se reutiliza código entre clases, cómo se escribe código que funcione con "cualquier cosa que sepa hacer X". Cada idea se muestra con una clase real de Enexia.

**Qué vas a aprender**
- Los cuatro pilares: encapsulamiento, abstracción, herencia y polimorfismo.
- Interfaces y clases abstractas: la diferencia y cuándo usar cada una.
- Enums y *records*: dos formas modernas de modelar datos.
- Clases anidadas.
- Genéricos (`List<String>`, `Page<T>`, `JpaRepository<Usuario, Long>`).
- `equals`, `hashCode` y por qué importan.
- Composición contra herencia; principios SOLID aplicados.

---

## 1. Encapsulamiento: el estado se protege

**Idea:** un objeto esconde sus datos internos (`private`) y ofrece **métodos públicos** por los que se los lee o modifica. Así la clase controla las reglas: nadie puede dejarla en un estado inválido por accidente.

```java
public class Contador {
    private int valor;                 // nadie de afuera lo toca directo

    public void incrementar() { valor++; }
    public int getValor() { return valor; }
}
```

### 1.1 Getters y setters

Un **getter** devuelve un atributo (`getEmail()`); un **setter** lo cambia (`setEmail(...)`). En Enexia los genera Lombok (`@Getter`, `@Setter`), de modo que una entidad queda así de corta:

```java
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {
    @Column(name = "email")
    private String email;
    // ...
}
```

### 1.2 Encapsulamiento con lógica: `Usuario.getRoles()`

El poder real del encapsulamiento aparece cuando el *getter* **no devuelve el dato crudo** sino que aplica una regla. En `enexia/src/main/java/com/enexia/rg/model/Usuario.java`:

```java
public List<String> getRoles() {
    if (usuarioRoles == null || usuarioRoles.isEmpty()) {
        return List.of();
    }
    List<String> roles = usuarioRoles.stream()
        .map(ur -> ur.getRol().getNombreRol())
        .collect(Collectors.toList());
    return roles.contains(ROL_ADMINISTRADOR) ? List.of(ROL_ADMINISTRADOR) : roles;
}
```

La regla de negocio *"un administrador es solo administrador"* vive **en un único lugar**. Aunque la base tenga filas de `PARTICIPANTE` para esa cuenta, **todo el que pregunte los roles** (el JWT, Spring Security, los servicios) recibe `["ADMINISTRADOR"]`. Si la regla estuviera repartida en veinte endpoints, bastaría olvidarse de uno para romperla. El `CLAUDE.md` lo dice explícito: *"Don't re-add per-endpoint checks"*.

> **Regla práctica:** si una regla se refiere a **los datos de un objeto**, que viva **en ese objeto** (o en el servicio dueño de esos datos), no desparramada en quien lo usa.

---

## 2. Abstracción: mostrar el "qué", ocultar el "cómo"

**Idea:** quien usa una clase solo necesita saber **qué ofrece**, no cómo lo hace por dentro.

Ejemplo: `AdminUsuarioService.cambiarEstado(...)` se usa con una acción (`SUSPENDER`/`REACTIVAR`); el controller no sabe que por dentro se bloquea una fila, se valida una transición, se escribe un historial y se manda un correo. Esa complejidad queda **detrás de la firma** del método. Es la razón de que el proyecto tenga capas (B02): cada una expone una interfaz simple y esconde la de abajo.

---

## 3. Herencia: reutilizar y especializar

**Idea:** una clase (la **hija** o *subclase*) puede **heredar** de otra (la **padre** o *superclase*): recibe sus atributos y métodos y puede agregar o cambiar comportamiento. Se declara con `extends`. La relación se lee **"es un"**.

```java
public class Gerente extends Empleado { ... }    // un Gerente "es un" Empleado
```

### 3.1 La jerarquía de excepciones de login

El mejor ejemplo real es la familia de excepciones de autenticación (`enexia/src/main/java/com/enexia/rg/exception/`):

```
RuntimeException                         (de Java)
   └── AutenticacionFallidaException     (abstracta, raíz de todos los rechazos de login)
          ├── CredencialesInvalidasException   nickname inexistente / contraseña incorrecta
          ├── CuentaBloqueadaException          tercer fallo consecutivo
          └── CuentaNoHabilitadaException       cuenta suspendida o dada de baja
```

La clase padre:

```java
@Getter
public abstract class AutenticacionFallidaException extends RuntimeException {

    public static final String MENSAJE_PUBLICO = "Usuario o contrasena incorrectos";
    public static final String CODIGO_PUBLICO = "CREDENCIALES_INVALIDAS";

    private final String codigoInterno;

    protected AutenticacionFallidaException(String codigoInterno) {
        super(MENSAJE_PUBLICO);
        this.codigoInterno = codigoInterno;
    }
}
```

Una hija:

```java
public class CuentaBloqueadaException extends AutenticacionFallidaException {

    public static final String CODIGO = "CUENTA_BLOQUEADA";

    public CuentaBloqueadaException() {
        super(CODIGO);
    }
}
```

Lectura:
- `extends` establece la herencia. Una `CuentaBloqueadaException` **es una** `AutenticacionFallidaException` y, más arriba, **es una** `RuntimeException`.
- `super(CODIGO)` llama al **constructor del padre**. Toda hija debe inicializar la parte heredada.
- `protected`: el constructor solo lo pueden usar las subclases.
- `abstract` (ver §5): la clase **no se puede instanciar** (`new AutenticacionFallidaException()` no compila); existe solo para ser extendida.
- `RuntimeException` ya sabe todo lo que necesita una excepción (mensaje, traza). Enexia solo agrega lo propio: un código interno.

**¿Para qué sirve esta jerarquía?** Para que `GlobalExceptionHandler` capture **una sola vez** a toda la familia con un único manejador (`@ExceptionHandler(AutenticacionFallidaException.class)`) y responda igual para las cuatro (seguridad: el atacante no debe distinguir el motivo, B04). Y para que el código interno de cada una siga siendo distinguible en los logs. Es herencia usada con un propósito concreto.

### 3.2 `@Override`: reemplazar un método heredado

Una subclase puede **redefinir** un método de su padre. La anotación `@Override` le pide al compilador que verifique que realmente estás sobrescribiendo algo (si te equivocás en el nombre, no compila). Ejemplo real: `UsuarioRolId.equals` (ver §9) y `ValidadorFechaFutura.isValid`.

### 3.3 `Object`: el padre de todos

**Toda clase de Java hereda, directa o indirectamente, de `java.lang.Object`**, que aporta `equals`, `hashCode` y `toString`. Por eso cualquier objeto sabe compararse y convertirse en texto.

---

## 4. Interfaces: contratos

**Idea:** una **interfaz** declara **qué métodos** debe tener una clase, sin decir cómo. Una clase que **`implements`** una interfaz **promete** cumplir ese contrato. La relación se lee **"sabe hacer"**.

```java
public interface Notificador {
    void avisar(String destinatario, String texto);   // sin cuerpo: solo el contrato
}

public class NotificadorPorMail implements Notificador {
    @Override
    public void avisar(String destinatario, String texto) { /* manda un mail */ }
}
```

Ventaja: el código que **usa** la interfaz no depende de ninguna implementación concreta; se puede cambiar una por otra sin tocarlo.

### 4.1 Interfaces que Enexia implementa

**`ConstraintValidator`** (Jakarta Validation) — la validación `@FechaFuturaValida` (`validation/ValidadorFechaFutura.java`):

```java
public class ValidadorFechaFutura implements ConstraintValidator<FechaFuturaValida, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;   // de null se encarga @NotNull
        }
        return value.isAfter(LocalDate.now().minusDays(1));
    }
}
```

La librería de validación define el contrato (`isValid`); Enexia lo cumple. Cuando un DTO tiene `@FechaFuturaValida`, la librería llama a **esta** clase sin saber que existe.

**`Serializable`** — `UsuarioRolId implements Serializable` es una interfaz **marcadora**: no tiene métodos, solo indica "este objeto se puede convertir a bytes". Hibernate lo exige para las claves compuestas.

### 4.2 Interfaces que Enexia *usa* sin implementarlas

Casi todos los *repositories* son interfaces sin una sola línea de código de implementación (`repository/UsuarioRepository.java`):

```java
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // solo declaraciones de consultas; Spring genera la implementación
}
```

`interface ... extends ...` (una interfaz puede heredar de otra) hereda los métodos `save`, `findById`, `findAll`, `delete`... de `JpaRepository`. **Spring Data crea la clase que los implementa en tiempo de ejecución.** Es el ejemplo más claro de abstracción: escribís el contrato y el framework pone el motor (B03).

Otras interfaces del proyecto que se inyectan: `Executor` (algo que ejecuta tareas en otro hilo), `PasswordEncoder` (algo que sabe cifrar contraseñas).

### 4.3 Interfaz contra clase abstracta

| | Interfaz | Clase abstracta |
|---|---|---|
| Qué es | Un contrato puro ("sabe hacer"). | Una clase incompleta ("es un", con parte del trabajo hecho). |
| Atributos | Solo constantes. | Atributos de instancia. |
| Herencia | Una clase puede `implements` **varias**. | Una clase puede `extends` **una sola**. |
| Cuándo | Para desacoplar y permitir varias implementaciones. | Para compartir código base entre clases emparentadas. |

---

## 5. Clases abstractas

Una clase `abstract` no se puede instanciar y puede tener métodos **sin cuerpo** (que las hijas deben implementar) junto a métodos completos.

En el proyecto aparece en tres lugares:

1. **`AutenticacionFallidaException`** (arriba).
2. **`BaseTest`** (`src/test/.../BaseTest.java`): la clase base de todas las pruebas con navegador. Abre y cierra Playwright antes/después de cada test para que cada subclase (`LoginTest`, `CorreosTest`) herede `page` ya listo. Es **reutilización de código por herencia**.
3. **`BaseApiTest`** (`src/test/.../api/BaseApiTest.java`): lo mismo para las pruebas de API, y es `abstract`.

```java
public abstract class BaseApiTest {
    protected static Playwright playwright;
    protected static APIRequestContext api;

    @BeforeAll
    static void abrirContextoApi() { /* crea el cliente HTTP */ }
    // ...
}
```

`protected` en `playwright` y `api` es justamente para que **las subclases** los usen y nadie más.

---

## 6. Polimorfismo: muchas formas, una interfaz

**Idea:** una variable declarada con el tipo del **padre o la interfaz** puede guardar un objeto de **cualquier hija o implementación**; al llamar un método se ejecuta **el de la clase real**.

```java
AutenticacionFallidaException error = new CuentaBloqueadaException();
error.getCodigoInterno();    // "CUENTA_BLOQUEADA": ejecuta la versión del objeto real
```

El caso del proyecto que lo muestra mejor está en un **test** (`RecuperacionCuentaServiceTest.prepararEscenario`):

```java
Executor sincrono = Runnable::run;
servicio = new RecuperacionCuentaService(/* ... */, sincrono, /* ... */);
```

El servicio recibe un `Executor` (una interfaz). En **producción** le llega un ejecutor con un grupo de hilos (el correo sale en otro hilo); en el **test** se le pasa uno que corre la tarea *en el mismo hilo*. El servicio **no sabe cuál tiene** ni le importa: eso es polimorfismo, y es lo que vuelve el código **probable**.

El otro gran uso de polimorfismo es la **inyección de dependencias** (B01): el servicio declara que necesita "un `EmailService`" y Spring le entrega uno; en los tests se le entrega un *mock*.

---

## 7. Enums: un conjunto cerrado de valores

**Idea:** un `enum` define un **tipo con un número fijo de valores posibles**. Es mucho más seguro que usar `String`s sueltos ("ACTIVO", "activo", "ACTIVO " son tres cosas distintas para un `String`; para un enum solo existe `ACTIVO`).

```java
public enum EstadoUsuarioNombre {
    /** Unico estado que habilita el login. */
    ACTIVO,
    /** Bloqueo automatico por acumulacion de intentos fallidos. */
    BLOQUEADO,
    /** Suspension aplicada por un administrador. */
    SUSPENDIDO,
    /** Baja logica solicitada por el usuario; se acompana de fecha_baja. */
    DE_BAJA
}
```

Usos habituales:

```java
EstadoUsuarioNombre.ACTIVO                 // referirse a un valor
EstadoUsuarioNombre.ACTIVO.name()          // "ACTIVO" (el texto)
EstadoUsuarioNombre.valueOf("SUSPENDIDO")  // del texto al valor; IllegalArgumentException si no existe
EstadoUsuarioNombre.values()               // arreglo con todos los valores
```

`AdminUsuarioService.normalizarEstado` usa justamente `valueOf` dentro de un `try/catch` para validar el filtro que llega del cliente:

```java
try {
    return EstadoUsuarioNombre.valueOf(estado.trim().toUpperCase()).name();
} catch (IllegalArgumentException ex) {
    throw new ReglaNegocioException("El estado '" + estado + "' no es valido");
}
```

### 7.1 Enum **y** tabla: una decisión de diseño del proyecto

En la base de datos los estados viven en **tablas de catálogo** (`usuario_estado`, `evento_estado_sistema`, `suscripcion_estado`...), porque así lo define el modelo (MER, ADR-02). Los enums de Java (`EstadoUsuarioNombre`, `SuscripcionEstadoNombre`...) **no se persisten**: son el *vocabulario* del código, para no escribir `"BLOQUEADO"` a mano en veinte lugares. Cada uno se traduce a su fila con `nombreEstado` + una consulta `findByEstadoUsuarioIgnoreCase(...)`.

> Esto explica un patrón que vas a ver mucho: `usuarioEstadoRepository.findByEstadoUsuarioIgnoreCase(EstadoUsuarioNombre.ACTIVO.name())`. Del enum se pasa al texto con `.name()`, y del texto a la fila con la consulta.

Un enum también puede tener atributos y métodos, pero los de Enexia son "simples" a propósito.

---

## 8. Records: datos inmutables en una línea

**Idea:** un `record` (Java 16+) es una clase **inmutable** pensada solo para **transportar datos**. El compilador genera por vos el constructor, los *getters* (sin el prefijo `get`: `idEvento()`), `equals`, `hashCode` y `toString`.

```java
public record EventoCreadoEvent(Long idEvento,
                                EventoCrearRequest datos,
                                List<ImagenPendiente> imagenes,
                                String emailOrganizador) { }
```

Eso equivale a ~40 líneas de clase tradicional. Se usa así:

```java
EventoCreadoEvent evento = new EventoCreadoEvent(10L, datos, imagenes, "ana@enexia.test");
evento.idEvento();            // 10
```

Otros records del proyecto:

- `EventoMapper.EstadoParticipante(Set<Long> idsInscriptoActivo, Set<Long> idsConfirmado, Set<Long> idsYaValorado)`: tres datos que **siempre viajan juntos**.
- `PasarelaPagoSimuladaService.ResultadoPago(boolean aprobado, String tokenOperacion, String motivoRechazo)`, con **métodos estáticos de fábrica**:
  ```java
  public record ResultadoPago(boolean aprobado, String tokenOperacion, String motivoRechazo) {
      public static ResultadoPago aprobado(String token) {
          return new ResultadoPago(true, token, null);
      }
      public static ResultadoPago rechazado(String token, String motivo) {
          return new ResultadoPago(false, token, motivo);
      }
  }
  ```
  `ResultadoPago.aprobado(token)` se lee mejor que `new ResultadoPago(true, token, null)`: el nombre explica la intención.
- En `CorreosTest`: `private record Envio(String destinatario, String asunto, boolean aceptado) { }`.

**¿Record o clase con Lombok?** Los *records* son inmutables (no tienen setters); las entidades JPA **no pueden** ser records porque Hibernate necesita poder modificarlas. Los DTO de respuesta con `@Builder` sí podrían ser records; el proyecto eligió clases con Lombok por consistencia con el resto.

---

## 9. `equals` y `hashCode`: cuándo dos objetos son "el mismo"

Por defecto, `equals` de `Object` compara **identidad** (¿son el mismo objeto en memoria?). Si querés que dos objetos con los mismos datos sean "iguales", hay que **sobrescribir `equals` y `hashCode` juntos** (si dos objetos son `equals`, deben dar el mismo `hashCode`). Las colecciones basadas en *hash* (`HashSet`, `HashMap`) dependen de eso.

El proyecto lo hace donde es imprescindible: las **claves compuestas**. `UsuarioRol` tiene como clave **el par** (`id_usuario`, `id_rol`), modelado por `UsuarioRolId`:

```java
@Embeddable
public class UsuarioRolId implements Serializable {

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "id_rol")
    private Long idRol;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsuarioRolId that = (UsuarioRolId) o;
        return Objects.equals(idUsuario, that.idUsuario)
                && Objects.equals(idRol, that.idRol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idUsuario, idRol);
    }
}
```

Línea por línea de `equals`:
1. `this == o`: si es literalmente el mismo objeto, son iguales (atajo).
2. `o == null || getClass() != o.getClass()`: un `null` o un objeto de otra clase nunca es igual.
3. `(UsuarioRolId) o`: una **conversión de tipo** (*cast*): como ya comprobamos que `o` es de esta clase, podemos tratarlo como tal.
4. `Objects.equals(a, b)`: compara dos objetos **tolerando `null`** (devuelve `true` si ambos son `null`).

`Objects.hash(...)` calcula un hash combinando los campos. Hibernate necesita estos métodos para saber que "el rol 3 del usuario 7" es **una** fila y no dos.

---

## 10. Clases anidadas

Una clase puede declararse **dentro de otra**.

- **`static class`** anidada: no depende de una instancia de la clase de afuera; es solo una forma de **agrupar nombres**. Ejemplo: `AdminSuscripcionesResumenResponse.Plan`, que describe *un plan* dentro de la respuesta del resumen:

```java
public class AdminSuscripcionesResumenResponse {
    // ...
    @Getter
    @AllArgsConstructor
    public static class Plan {
        private String plan;
        private BigDecimal precioMensual;
        private long usuariosActivos;
        private Integer eventosPermitidos;
    }
}
```

Se la usa como `new AdminSuscripcionesResumenResponse.Plan(...)`.

- **`@Nested`** (JUnit): en los tests, cada clase interna agrupa casos relacionados (`class Skeleton { ... }`, `class Validaciones { ... }`). El reporte de tests los muestra en árbol (B06).

---

## 11. Genéricos: tipos que se parametrizan

**Idea:** un **genérico** deja una clase o método trabajar con **un tipo que se decide al usarla**, manteniendo el chequeo del compilador. Se escribe entre `< >`.

```java
List<String> nombres = new ArrayList<>();
nombres.add("Ana");
nombres.add(7);              // ERROR DE COMPILACIÓN: la lista es de String
String primero = nombres.get(0);   // no hace falta convertir: el compilador sabe que es String
```

Sin genéricos, una lista guardaría `Object` y tendrías que convertir cada elemento (y equivocarte en ejecución). Genéricos que ya viste en el proyecto:

| Tipo | Se lee |
|---|---|
| `List<String> getRoles()` | una lista de textos |
| `Optional<Usuario>` | "puede haber un `Usuario` o no" |
| `Page<ValoracionResponse>` | una página de resultados, cada uno una `ValoracionResponse` |
| `JpaRepository<Usuario, Long>` | un repositorio **de** `Usuario` cuya clave es `Long` |
| `ConstraintValidator<FechaFuturaValida, LocalDate>` | un validador **de la anotación** `FechaFuturaValida` aplicado a un `LocalDate` |
| `Map<Long, List<CronogramaTicket>>` | un mapa de id de cronograma → lista de tickets |

El operador diamante `<>` (`new ArrayList<>()`) hace que el compilador **deduzca** el tipo del lado izquierdo.

---

## 12. Composición contra herencia

La herencia modela **"es un"**. Pero muchas relaciones son **"tiene un"**, y para eso se usa **composición**: una clase **contiene** un atributo de otra.

`Usuario` **tiene una** `PersonaFisica`, **tiene un** estado y **tiene** roles; no *es* ninguna de esas cosas:

```java
public class Usuario {
    private PersonaFisica personaFisica;
    private UsuarioEstado estadoUsuario;
    private Set<UsuarioRol> usuarioRoles;
    // ...
}
```

Del mismo modo, `ValoracionService` **tiene** un `ValoracionRepository`, un `InscripcionRepository`, un `AuditoriaService`... No hereda de ninguno: **colabora** con ellos. Esa es la forma natural de armar capas.

> **Regla práctica:** preferí composición. Heredá solo cuando de verdad exista un "es un" (como las excepciones de login) y cuando la clase padre esté pensada para ser extendida.

Un caso del modelo de datos que muestra bien por qué *no* se usó herencia: `Persona`, `PersonaFisica` y `PersonaJuridica`. Se pensó una jerarquía `Persona → (Física | Jurídica)` y se descartó (el javadoc de `Persona` lo cuenta): una persona jurídica **no es** una persona humana, es un **contenedor administrativo** que se vincula a personas por una relación (`Miembros_Organizacion`). Modelar mal un "es un" habría permitido crear una persona jurídica con login.

---

## 13. Principios SOLID, con ejemplos del proyecto

Cinco ideas para escribir clases que sean fáciles de cambiar.

| Letra | Principio | Cómo se ve en Enexia |
|---|---|---|
| **S** | *Single Responsibility*: una clase, una razón para cambiar. | `ValoracionService` solo valora. El envío de correo está en `EmailService`; la salida al proveedor, en `BrevoClient`; la moderación de texto, en `ModeracionTextoService`. `IntentosLoginService` y `VisitaService` son beans separados precisamente para tener su propia transacción (B05). |
| **O** | *Open/Closed*: abierto a extensión, cerrado a modificación. | Para agregar un tipo de aviso por correo se **suma** un valor a `NotificacionTipo` y un método a `EmailService`; no se reescribe lo existente. El enum tiene el comentario "se agregan valores nuevos y no se renombran los existentes". |
| **L** | *Liskov*: una hija debe poder usarse donde se espera el padre. | Cualquier `AutenticacionFallidaException` hija se maneja igual en `GlobalExceptionHandler`. |
| **I** | *Interface Segregation*: interfaces chicas y específicas. | Cada repository declara solo las consultas que su entidad necesita. |
| **D** | *Dependency Inversion*: depender de abstracciones, no de implementaciones concretas. | Los servicios reciben interfaces (`Executor`, `PasswordEncoder`, repositories) por **constructor**; en los tests se reemplazan (B01, B06). |

---

## 14. Un ejemplo completo: leer una clase real de punta a punta

Con todo lo visto, podés leer **`ValoracionService`** (`service/ValoracionService.java`) y reconocer cada concepto:

```java
@Service                       // anotación: Spring lo registra como bean (B01)
@RequiredArgsConstructor       // Lombok: constructor con los atributos final (A01 §5)
@Slf4j                         // Lombok: crea el atributo "log" (A04)
public class ValoracionService {

    private final ValoracionRepository valoracionRepository;      // composición
    private final InscripcionRepository inscripcionRepository;    // "tiene un"
    private final EventoCronogramaRepository cronogramaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final ModeracionTextoService moderacionTextoService;

    @Transactional             // el método corre dentro de una transacción (B03)
    public ValoracionResponse crear(String email, ValoracionRequest datos, HttpServletRequest request) {
        Usuario participante = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));
        // ...
    }
}
```

Checklist de lectura:
1. ¿Qué **atributos** tiene y de qué **tipo** son? → dependencias (composición, interfaces).
2. ¿Qué **métodos públicos** ofrece? → su contrato con el resto.
3. ¿Qué **excepciones** lanza y de qué familia? → qué errores pueden llegar al cliente.
4. ¿Qué **métodos privados** tiene? → los detalles que esconde (encapsulamiento).

---

## 15. Errores típicos

| Error | Consecuencia | Remedio |
|---|---|---|
| Heredar para reutilizar código aunque no haya "es un". | Jerarquías rígidas y difíciles de cambiar. | Composición. |
| Sobrescribir `equals` sin `hashCode`. | Objetos que no se encuentran en un `HashSet`/`HashMap`. | Siempre los dos, con `Objects.equals` / `Objects.hash`. |
| Exponer atributos `public`. | Estado inválido y reglas esquivadas. | `private` + métodos con lógica. |
| Usar `String` para valores cerrados. | `"activo"` ≠ `"ACTIVO"` y errores silenciosos. | `enum`. |
| Clases "Dios" que hacen todo. | Imposibles de probar. | Dividir por responsabilidad (SRP). |

---

## 16. Para practicar

1. Dibujá el diagrama de herencia de las excepciones del paquete `exception/`. ¿Cuáles extienden `RuntimeException` directamente y cuáles pasan por `AutenticacionFallidaException`?
2. Agregá (en papel) una nueva hija `CuentaEnRevisionException` con código `CUENTA_EN_REVISION`. ¿Qué otros archivos habría que tocar para que el login la maneje igual que las demás? (Respuesta: ninguno; ese es el valor del polimorfismo.)
3. Convertí `EstadoParticipante` en una clase tradicional con constructor, getters, `equals` y `hashCode`. Contá las líneas y compará con el `record`.
4. En `UsuarioRolId`, ¿qué pasaría si `hashCode` devolviera siempre `1`? ¿Seguiría siendo correcto? ¿Y eficiente?
5. Elegí un servicio del proyecto y marcá cada dependencia con la letra SOLID que ilustra.

**Siguiente capítulo:** [A03 — Java funcional y colecciones](./A03_java_funcional_y_colecciones.md)
