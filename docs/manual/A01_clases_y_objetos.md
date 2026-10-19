# A01 — Clases y objetos: el primer ladrillo

> **Parte A · Fundamentos de Java.** Este capítulo no supone que sepas programar. Cada concepto aparece primero como idea, después como sintaxis mínima y por último **dentro del código real de Enexia**, con la ruta del archivo para que lo abras y lo veas completo.

**Qué vas a aprender**
- Qué es un programa Java y cómo pasa de texto a algo que se ejecuta.
- Cómo se declara una clase, sus atributos y sus métodos.
- Tipos de datos: primitivos, objetos, fechas, dinero.
- Constructores, `this`, `static`, `final`, modificadores de acceso.
- Control de flujo: `if`, `switch`, `for`, `while`, operador ternario.
- Paquetes, `import` y la estructura de carpetas del proyecto.
- Cómo se compila y se ejecuta todo esto con Gradle.

---

## 1. Qué es un programa Java

Un programa Java es un conjunto de archivos de texto con extensión `.java`. Una persona los escribe; la máquina no los entiende directamente. El camino es:

```
Persona escribe                javac                      JVM
Usuario.java  ───────────►  Usuario.class  ───────────►  se ejecuta
(código fuente)            (bytecode)                 (Java Virtual Machine)
```

- **Compilador (`javac`)**: traduce el código fuente a **bytecode**, un formato intermedio que no depende del sistema operativo.
- **JVM (Java Virtual Machine)**: un programa que lee el bytecode y lo ejecuta. Por eso el mismo `.class` corre en Windows, Linux o macOS: lo que cambia es la JVM instalada.
- **JDK**: el kit de desarrollo (compilador + JVM + librerías estándar). Enexia usa **Java 17** (se declara en `enexia/build.gradle`, bloque `java { toolchain { ... } }`).

Todo programa necesita un punto de entrada, el método `main`. En Enexia es éste (archivo `enexia/src/main/java/com/enexia/rg/EnexiaApplication.java`):

```java
package com.enexia.rg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EnexiaApplication {

	public static void main(String[] args) {
		SpringApplication.run(EnexiaApplication.class, args);
	}

}
```

Lectura línea por línea:

| Línea | Qué significa |
|---|---|
| `package com.enexia.rg;` | Dice en qué "carpeta lógica" vive la clase. Debe coincidir con la ruta del archivo. |
| `import ...;` | Trae al archivo clases que viven en otros paquetes para poder nombrarlas. |
| `@SpringBootApplication` | Una **anotación**: una etiqueta que le da instrucciones a una librería (acá, a Spring). Se explica en B01. |
| `public class EnexiaApplication` | Declara una clase pública. El nombre **debe** coincidir con el del archivo. |
| `public static void main(String[] args)` | El método que la JVM busca al arrancar. `static` = no necesita un objeto; `void` = no devuelve nada; `String[] args` = los argumentos de la línea de comandos. |
| `SpringApplication.run(...)` | Le pasa el control a Spring Boot, que arranca el servidor web y arma toda la aplicación. |

> **Convención del lenguaje:** una clase pública por archivo, y el archivo se llama como la clase (`EnexiaApplication.java`).

---

## 2. Qué es una clase y qué es un objeto

Una **clase** es un **molde**: describe qué datos tiene algo y qué sabe hacer. Un **objeto** (o *instancia*) es una pieza concreta fabricada con ese molde.

Analogía: la clase `Usuario` es el formulario de alta en blanco; cada objeto `Usuario` es un formulario completado por una persona real.

### 2.1 Declaración mínima

```java
public class Persona {

    // atributos (el estado del objeto)
    private Long idPersona;
    private LocalDateTime fechaRegistro;

    // métodos (lo que el objeto sabe hacer)
    public Long getIdPersona() {
        return idPersona;
    }
}
```

Eso es, esencialmente, la clase `Persona` de `enexia/src/main/java/com/enexia/rg/model/Persona.java`, con una diferencia: en el proyecto no se escriben a mano los métodos `getIdPersona()`, `setIdPersona(...)` y compañía. Los genera una librería llamada **Lombok** a partir de las anotaciones `@Getter` y `@Setter` (capítulo A04). La clase real es:

```java
@Entity
@Table(name = "persona")
@Getter
@Setter
@NoArgsConstructor
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_persona")
    private Long idPersona;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;
}
```

### 2.2 Crear un objeto

```java
Persona p = new Persona();          // new fabrica un objeto con el molde Persona
p.setFechaRegistro(LocalDateTime.now());
```

- `Persona p` declara una **variable de tipo `Persona`** (una "referencia", ver §4).
- `new Persona()` reserva memoria y llama al **constructor** (§5).
- `p.setFechaRegistro(...)` **invoca un método** del objeto con el operador punto `.`.

Esto es exactamente lo que hace `ValoracionService.crear` para guardar una valoración (`enexia/src/main/java/com/enexia/rg/service/ValoracionService.java`):

```java
Valoracion valoracion = new Valoracion();
valoracion.setCronograma(cronograma);
valoracion.setUsuario(participante);
valoracion.setValor(datos.getValor());
valoracion.setComentario(datos.getComentario());
valoracion.setFecha(LocalDate.now());
valoracion = valoracionRepository.save(valoracion);
```

Se fabrica el objeto, se le cargan los atributos uno por uno y se lo entrega al *repository* para que lo guarde en la base de datos.

---

## 3. Atributos, variables y tipos

Un **atributo** (o *campo*) es una variable que vive dentro de un objeto. Una **variable local** vive dentro de un método y desaparece cuando éste termina. Ambas se declaran con la forma:

```
tipo nombre = valor;
```

Java es un lenguaje **de tipado estático**: cada variable tiene un tipo fijo, declarado de antemano, y el compilador rechaza lo que no encaja. Es una red de seguridad: un error de tipo se detecta al compilar y no en producción.

### 3.1 Tipos primitivos

Guardan **el valor directamente**. Se escriben en minúscula.

| Tipo | Qué guarda | Ejemplo |
|---|---|---|
| `int` | entero de 32 bits | `int intentos = 3;` |
| `long` | entero de 64 bits | `long total = 9_000_000_000L;` (la `L` marca el literal como `long`) |
| `double` | decimal de 64 bits | `double ocupacion = 87.5;` |
| `boolean` | `true` o `false` | `boolean asistio = true;` |
| `char` | un carácter | `char c = 'a';` |

Hay otros (`byte`, `short`, `float`) que casi no se usan acá.

### 3.2 Tipos de referencia (objetos)

Guardan **la dirección** de un objeto, no el objeto. Se escriben con mayúscula inicial: `String`, `Usuario`, `Long`, `List<...>`. Una variable de referencia puede valer `null` ("no apunta a nada").

**`String`** es texto. Es **inmutable**: una vez creado no cambia; operaciones como `toUpperCase()` devuelven **otro** `String`.

```java
String nickname = "gaston_test";
int largo = nickname.length();          // 11
boolean vacio = nickname.isBlank();     // false
String mayus = nickname.toUpperCase();  // "GASTON_TEST"; nickname sigue igual
```

### 3.3 Por qué `Long` y no `long` en las entidades

Mirá `Usuario`:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
@Column(name = "id_usuario")
private Long idUsuario;

@Column(name = "intentos_fallidos")
private Integer intentosFallidos;
```

`Long` e `Integer` son las **clases envoltorio** (*wrappers*) de los primitivos `long` e `int`. Se usan porque **pueden valer `null`**:

- Un `Usuario` recién creado, antes de guardarse, **todavía no tiene id**: `idUsuario` es `null`. Un `long` no podría expresarlo (valdría 0, que parece un id válido).
- Una columna de la base puede ser `NULL`; el wrapper puede representarla.

La contracara: con un wrapper hay que cuidarse de `null` (ver §8, *NullPointerException*).

### 3.4 Dinero y decimales: `BigDecimal`

`double` y `float` representan decimales en **binario**, y por eso no pueden guardar exactamente números como `0.1`:

```java
System.out.println(0.1 + 0.2);   // 0.30000000000000004
```

Para dinero eso es inaceptable. Enexia usa `BigDecimal`, que guarda decimales exactos. En `CronogramaTicket`:

```java
@Column(name = "precio")
private BigDecimal precio;
```

Y para compararlo se usa `compareTo`, no `==` ni `equals`:

```java
precio.compareTo(BigDecimal.ZERO) == 0     // es gratuito
```

(`equals` en `BigDecimal` considera distintos `2.0` y `2.00`; `compareTo` compara el valor.)

### 3.5 Fechas y horas (`java.time`)

El paquete `java.time` tiene un tipo para cada necesidad. Son **inmutables** y se crean con métodos estáticos:

| Tipo | Representa | Ejemplo |
|---|---|---|
| `LocalDate` | fecha sin hora | `LocalDate.now()`, `LocalDate.of(2026, 10, 19)` |
| `LocalTime` | hora sin fecha | `LocalTime.of(20, 30)` |
| `LocalDateTime` | fecha y hora | `LocalDateTime.of(fecha, hora)` |
| `Duration` | un lapso | `Duration.ofHours(24)` |

Un caso real de `ValoracionService.crear`: un cronograma termina en `fecha + horaFin`, y se compara con el momento actual:

```java
LocalDateTime fin = LocalDateTime.of(cronograma.getFecha(), cronograma.getHoraFin());
if (fin.isAfter(LocalDateTime.now())) {
    throw new OperacionNoPermitidaException("El cronograma aun no finalizo");
}
```

Los métodos `isAfter`, `isBefore`, `plusDays`, `minusMinutes`, `withDayOfMonth` devuelven **un objeto nuevo** (no modifican el original).

### 3.6 Constantes de configuración: `static final`

```java
private static final int BYTES_TOKEN = 32;
private static final Duration VENTANA_ENLACES = Duration.ofHours(24);
```

(De `RecuperacionCuentaService`.) `final` = no se puede reasignar; `static` = pertenece a la clase y no a cada objeto (hay una sola copia). Por convención las constantes se escriben en `MAYUSCULAS_CON_GUION_BAJO`. Poner el número en una constante con nombre (`BYTES_TOKEN`) en vez de un `32` suelto hace que el código se explique solo y que haya un único lugar para cambiarlo.

---

## 4. Variables de referencia y `null`

Cuando escribís `Persona a = new Persona(); Persona b = a;`, **no hay dos personas**: hay **un** objeto y **dos variables que apuntan a él**. Si cambiás algo usando `b`, `a` lo ve.

```java
Persona a = new Persona();
Persona b = a;
b.setIdPersona(7L);
System.out.println(a.getIdPersona());   // 7
```

`null` significa "esta variable no apunta a ningún objeto". Llamar un método sobre `null` lanza la excepción más famosa de Java:

```java
String texto = null;
texto.length();   // NullPointerException
```

El proyecto se defiende de `null` de varias maneras que vas a ver a lo largo del manual:

1. **Preguntar antes** — el patrón más frecuente (de `AdminUsuarioService`):
   ```java
   String estadoActual = objetivo.getEstadoUsuario() == null
           ? null : objetivo.getEstadoUsuario().getEstadoUsuario();
   ```
2. **Poner la constante primero** al comparar `String`s: `"SUSPENDER".equals(datos.getAccion())` nunca falla aunque `getAccion()` sea `null`, mientras que `datos.getAccion().equals("SUSPENDER")` sí.
3. **`Optional`** (capítulo A03) para devolver "puede que no haya resultado" sin usar `null`.

---

## 5. Constructores y `this`

Un **constructor** es un método especial que se ejecuta al hacer `new`. Tiene el nombre de la clase y no declara tipo de retorno.

```java
public class Envio {
    private String destinatario;
    private boolean aceptado;

    public Envio(String destinatario, boolean aceptado) {
        this.destinatario = destinatario;   // this = "este objeto"
        this.aceptado = aceptado;
    }
}
```

- `this.destinatario` es **el atributo**; `destinatario` a secas es **el parámetro**. `this` evita la ambigüedad cuando comparten nombre.
- Si no escribís ningún constructor, Java crea uno vacío ("constructor por defecto"). Si escribís uno con parámetros, el vacío **deja de existir**, salvo que lo declares.

En Enexia los constructores casi no se escriben a mano: Lombok los genera.

| Anotación de Lombok | Genera |
|---|---|
| `@NoArgsConstructor` | constructor vacío (lo **exige JPA** para poder fabricar entidades al leer la base) |
| `@AllArgsConstructor` | constructor con todos los atributos |
| `@RequiredArgsConstructor` | constructor con los atributos `final` (clave para la inyección de dependencias, B01) |
| `@Builder` | un "constructor fluido" (§6.3) |

Excepción donde sí se escribió a mano, por la necesidad de `@Value`: `RecuperacionCuentaService` (ver B01):

```java
public RecuperacionCuentaService(
        UsuarioRepository usuarioRepository,
        PasswordResetTokenRepository tokenRepository,
        PasswordEncoder passwordEncoder,
        EmailService emailService,
        NotificacionService notificaciones,
        @Qualifier("ejecutorCorreo") Executor ejecutorCorreo,
        TransactionTemplate transaccion,
        @Value("${enexia.security.recuperacion.vigencia-minutos}") int vigenciaMinutos) {
    this.usuarioRepository = usuarioRepository;
    this.tokenRepository = tokenRepository;
    // ...
}
```

### 5.1 Sobrecarga

Una clase puede tener **varios métodos o constructores con el mismo nombre pero distintos parámetros**. `CredencialesInvalidasException` tiene dos constructores:

```java
public CredencialesInvalidasException() {
    this(CODIGO_PASSWORD_INCORRECTA);        // this(...) llama al otro constructor
}

public CredencialesInvalidasException(String codigoInterno) {
    super(codigoInterno);                    // super(...) llama al constructor de la clase padre
}
```

`this(...)` reutiliza otro constructor de la misma clase; `super(...)` llama al de la clase padre (herencia, capítulo A02).

---

## 6. Métodos

Un **método** es un bloque de código con nombre. Su forma:

```
modificador tipoDeRetorno nombre(parámetros) { cuerpo }
```

### 6.1 Un método paso a paso: `ValidadorCuit.normalizar`

```java
public static String normalizar(String cuit) {
    if (cuit == null) {
        return null;
    }
    return cuit.replaceAll("[^0-9]", "");
}
```

- `public`: se puede llamar desde cualquier clase.
- `static`: se llama sobre la **clase**, sin crear un objeto: `ValidadorCuit.normalizar("30-71659554-0")`.
- `String` (antes del nombre): el tipo de lo que **devuelve**.
- `String cuit`: un **parámetro** de entrada.
- `return`: devuelve el resultado y termina el método.
- `replaceAll("[^0-9]", "")` usa una **expresión regular**: reemplaza todo lo que no sea dígito por nada. Quedan solo los números (regex en A04).

### 6.2 Métodos de utilidad: constructor privado

```java
public final class ValidadorCuit {

    private static final int[] MULTIPLICADORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private ValidadorCuit() {
        // Clase de utilidad: no se instancia.
    }
    // ...
}
```

Una clase que solo agrupa funciones `static` no tiene sentido como objeto. El **constructor `private`** impide que alguien haga `new ValidadorCuit()`, y `final` en la clase impide que otra clase herede de ella. Lo mismo hace `CorreoHtml`.

### 6.3 Métodos que devuelven el propio objeto: el patrón *Builder*

Cuando un objeto tiene muchos campos, un constructor con 12 parámetros es ilegible y propenso a errores de orden. El **Builder** arma el objeto nombrando cada campo. Lombok lo genera con `@Builder` y se usa así (de `ValoracionService.mapear`):

```java
return ValoracionResponse.builder()
        .idValoracion(valoracion.getIdvaloracion())
        .idCronograma(cronograma.getIdCronograma())
        .valor(valoracion.getValor())
        .comentario(valoracion.getComentario())
        .fecha(valoracion.getFecha())
        .autor(autor == null ? null : autor.getNickname())
        .build();
```

Cada método intermedio devuelve el *builder* (por eso se encadenan con `.`); `build()` fabrica el objeto final. Se lee casi como una frase y no depende del orden.

---

## 7. Modificadores de acceso y `final`

| Modificador | Quién puede usarlo |
|---|---|
| `public` | cualquier clase |
| `protected` | la misma clase, su paquete y sus subclases |
| *(ninguno)* | solo las clases del mismo paquete ("package-private") |
| `private` | solo la propia clase |

**Regla de oro del proyecto: los atributos son `private` y se exponen con `getX()`/`setX()`** (encapsulamiento, capítulo A02). Los métodos de ayuda también: `ValoracionService.mapear` es `private` porque solo tiene sentido dentro del servicio.

`final` tiene tres usos:
- en una **variable**: no se puede reasignar (`private final UsuarioRepository usuarioRepository;`);
- en un **método**: no se puede sobrescribir en una subclase;
- en una **clase**: no se puede heredar (`ValidadorCuit`).

---

## 8. Control de flujo

Es lo que hace que un programa **decida** y **repita**.

### 8.1 `if` / `else if` / `else`

```java
if (!asistio) {
    throw new OperacionNoPermitidaException("Solo pueden valorar ...");
}
```

Los operadores de comparación son `==`, `!=`, `<`, `>`, `<=`, `>=`; los lógicos, `&&` (y), `||` (o), `!` (no). `&&` y `||` son de **cortocircuito**: si el primer término ya decide el resultado, el segundo no se evalúa. Eso permite escribir condiciones seguras:

```java
if (digitos == null || digitos.length() != LARGO_CUIT) {   // si es null, ni mira .length()
    return false;
}
```

### 8.2 Operador ternario

`condición ? valorSiVerdadero : valorSiFalso` es un `if` que **devuelve un valor**:

```java
String estadoActual = objetivo.getEstadoUsuario() == null
        ? null : objetivo.getEstadoUsuario().getEstadoUsuario();
```

### 8.3 `switch`

El `switch` elige entre varias opciones según un valor. La forma moderna (Java 14+) usa flechas, no necesita `break` y puede **devolver** un valor. Así escapa `CorreoHtml` los caracteres peligrosos:

```java
switch (c) {
    case '&' -> salida.append("&amp;");
    case '<' -> salida.append("&lt;");
    case '>' -> salida.append("&gt;");
    case '"' -> salida.append("&quot;");
    case '\'' -> salida.append("&#39;");
    default -> salida.append(c);
}
```

Y `AdminSuscripcionService.intervenir` lo usa para despachar las tres acciones del administrador:

```java
switch (datos.getAccion()) {
    case "REVOCAR" -> { /* ... */ }
    case "CAMBIAR_PLAN" -> { /* ... */ }
    case "RENOVAR" -> { /* ... */ }
    default -> throw new ReglaNegocioException("Accion no soportada: " + datos.getAccion());
}
```

La rama `default` captura todo lo demás, y acá **lanza una excepción**: una acción desconocida es un error del cliente, no algo que se ignore.

### 8.4 Bucles

```java
// for clásico: cuando necesitás el índice
for (int i = 0; i < MULTIPLICADORES.length; i++) {
    suma += (digitos.charAt(i) - '0') * MULTIPLICADORES[i];
}

// for-each: cuando recorrés una colección entera
for (String parrafo : texto.split("\\R\\s*\\R")) {
    // ...
}

// while: mientras se cumpla una condición
while (!terminado) { /* ... */ }
```

- `break` sale del bucle; `continue` salta a la siguiente vuelta.
- En `ValidadorCuit.esValido`, el `for` multiplica cada dígito por su multiplicador fijo. La expresión `digitos.charAt(i) - '0'` convierte el carácter `'7'` en el número 7 (los caracteres son números por debajo, y los dígitos están consecutivos).

### 8.5 Arreglos (*arrays*)

Un arreglo es una lista de **tamaño fijo** de elementos del mismo tipo:

```java
private static final int[] MULTIPLICADORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
int primero = MULTIPLICADORES[0];            // los índices empiezan en 0
int cantidad = MULTIPLICADORES.length;       // length, sin paréntesis
```

Para listas que crecen y se achican se usa `List` (capítulo A03).

---

## 9. Comparar: `==` contra `equals`

- Con **primitivos**, `==` compara el valor: `3 == 3` es `true`.
- Con **objetos**, `==` compara **si son el mismo objeto en memoria**, no si tienen el mismo contenido. Para el contenido se usa `.equals(...)`.

```java
String a = new String("hola");
String b = new String("hola");
a == b;          // false: son dos objetos distintos
a.equals(b);     // true:  mismo texto
```

Por eso el proyecto compara siempre con `equals`:

```java
if (!SuscripcionEstadoNombre.ACTIVA.name().equals(estadoActual) ...
boolean esPropio = evento.getOrganizador().getIdUsuario().equals(organizador.getIdUsuario());
```

El segundo ejemplo es doblemente importante: `getIdUsuario()` devuelve un `Long` (objeto). Comparar dos `Long` con `==` **parece funcionar** para valores chicos y **falla** para valores grandes, por cómo Java cachea objetos. `equals` es siempre correcto.

---

## 10. Paquetes e `import`

Un **paquete** agrupa clases relacionadas, igual que las carpetas agrupan archivos. Evita choques de nombres (dos clases `Evento` en paquetes distintos) y comunica la **arquitectura**. Los paquetes de Enexia (`enexia/src/main/java/com/enexia/rg/`):

| Paquete | Contiene | Capítulo |
|---|---|---|
| `model` | Las entidades: clases que se guardan en la base (`Usuario`, `Evento`, `Valoracion`...) y los enums de estados. | B03 |
| `repository` | Las interfaces que leen y escriben la base. | B03 |
| `service` | La lógica de negocio. | B02 |
| `controller` | Los endpoints HTTP. | B02 |
| `dto` | Los objetos que viajan por la API (*Data Transfer Objects*). | B02 |
| `exception` | Excepciones propias y el manejador global de errores. | A04, B02 |
| `config` | Clases de configuración de Spring. | B01 |
| `security` | JWT y filtros de seguridad. | B04 |
| `util` | Utilidades sin dependencias (`ValidadorCuit`, `CorreoHtml`). | A01, A04 |
| `event` | Eventos de aplicación (`EventoCreadoEvent`). | B05 |
| `validation` | Validaciones personalizadas (`@FechaFuturaValida`). | B02, A04 |

Para usar una clase de otro paquete se la **importa**:

```java
import com.enexia.rg.exception.OperacionNoPermitidaException;
import java.time.LocalDateTime;
```

Las clases del paquete `java.lang` (`String`, `Math`, `Integer`, `Long`...) se importan solas. El `import static` trae **métodos** estáticos para usarlos sin el nombre de la clase (se ve mucho en los tests: `import static org.assertj.core.api.Assertions.assertThat;`).

---

## 11. Comentarios y Javadoc

```java
// comentario de una línea
/* comentario de varias
   líneas */
/** Javadoc: documenta la clase o el método que viene abajo. */
```

Los comentarios `/** ... */` son **Javadoc**; los IDEs los muestran al pasar el mouse sobre el nombre.

> **Estilo del proyecto:** los comentarios de Enexia casi nunca explican *qué* hace una línea (eso lo dice el código) sino **por qué** se decidió así: qué ataque evita, qué error ya ocurrió, qué alternativa se descartó. Por eso las clases tienen javadocs largos: son el registro de decisiones de diseño. Leelos antes de modificar una clase.
>
> **Ojo:** un comentario puede quedar desactualizado. Por ejemplo, el javadoc de `ValidadorCuit` todavía dice que el alta "queda en REVISION_PENDIENTE", pero esa etapa se eliminó el 2026-09-09 (ADR-14). El código manda; el comentario avisa del contexto.

---

## 12. Cómo se compila y ejecuta: Gradle

**Gradle** es la herramienta de construcción: descarga las librerías, compila, corre los tests y arranca la aplicación. Su configuración está en `enexia/build.gradle`. Las partes clave:

```groovy
plugins {
	id 'java'
	id 'org.springframework.boot' version '4.1.0'
	id 'io.spring.dependency-management' version '1.1.7'
}

java {
	toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.mockito:mockito-core'
	// ...
}
```

| Bloque | Significado |
|---|---|
| `plugins` | Capacidades que se suman a Gradle: compilar Java, empaquetar una app Spring Boot. |
| `java.toolchain` | Fija la versión de Java (17), sin depender de la que tenga instalada la máquina. |
| `repositories` | De dónde descargar las librerías (`mavenCentral()`). |
| `dependencies` | Las librerías. `implementation` = necesaria para compilar y ejecutar; `compileOnly` = solo al compilar (Lombok); `runtimeOnly` = solo al ejecutar (el driver de MySQL); `testImplementation` = solo para los tests. |

Los comandos más usados (se ejecutan desde la carpeta `enexia/`):

```bash
./gradlew bootRun                    # arranca la aplicación en http://localhost:8080
./gradlew test                       # corre todos los tests
./gradlew test --tests "*EventoServiceTest"    # un test puntual
./gradlew compileJava                # solo compila
```

`gradlew` (el *wrapper*) es un script que descarga la versión exacta de Gradle que necesita el proyecto: nadie tiene que instalarlo.

> **Detalle de configuración que ya costó un problema** (`build.gradle`): `options.encoding = 'UTF-8'` y `-Dfile.encoding=UTF-8`. Sin ellos, Windows usa `windows-1252` y los acentos de los mensajes salen corruptos en las respuestas y los logs.

---

## 13. Errores típicos de principiante (y cómo los evita el proyecto)

| Error | Síntoma | Cómo se evita en Enexia |
|---|---|---|
| Comparar `String`s con `==` | Condiciones que "a veces" no se cumplen. | Siempre `.equals(...)`, con la constante primero. |
| No controlar `null` | `NullPointerException` en producción. | Ternarios `x == null ? ... : ...`, `Optional`, validaciones de entrada. |
| Usar `double` para dinero | Centavos que no cierran. | `BigDecimal` + `compareTo`. |
| Números mágicos | Nadie sabe qué significa un `32`. | Constantes `static final` con nombre. |
| Atributos `public` | Cualquiera modifica el estado sin control. | Atributos `private` + getters/setters. |
| Clases enormes que lo hacen todo | Imposible de probar y de entender. | Capas: controller → service → repository (B02). |

---

## 14. Para practicar

1. Abrí `ValidadorCuit.java` y calculá **a mano** el dígito verificador del CUIT `30-71659554-0`. Compará con lo que devuelve `esValido`.
2. En `model/Valoracion.java`, listá los atributos con su tipo e indicá cuáles son primitivos, cuáles wrappers y cuáles objetos de otras entidades.
3. Escribí una clase `Ocupacion` con un método `static double porcentaje(int inscriptos, int cupoTotal)` que devuelva 0 si el cupo es 0 (como exige el DFD 2.10) y el porcentaje en caso contrario. ¿Por qué hay que cuidar la división por cero?
4. Reescribí el `switch` de `CorreoHtml.escapar` usando `if / else if`. ¿Cuál se lee mejor?
5. Encontrá en el proyecto tres usos de `static final` y explicá por qué cada valor es una constante.

**Siguiente capítulo:** [A02 — Programación orientada a objetos](./A02_poo.md)
