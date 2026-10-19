# B01 — Spring Boot: inversión de control y configuración

> **Parte B · Spring Boot.** Ya sabés Java. Ahora el salto: pasar de "clases sueltas" a **una aplicación web completa**. Lo primero que hay que entender de Spring es una idea que cambia cómo se arman los objetos: **no los creás vos, los crea el framework**.

**Qué vas a aprender**
- Qué es Spring y qué agrega Spring Boot.
- **Inversión de control** e **inyección de dependencias**: la idea central.
- Beans, anotaciones de estereotipo (`@Component`, `@Service`, `@Repository`, `@Controller`) y cómo Spring los encuentra.
- Configuración: `@Configuration`, `@Bean`, `application.properties`, `@Value`, variables de entorno.
- Qué pasa, paso a paso, cuando ejecutás `./gradlew bootRun`.
- Tareas de arranque, tareas programadas y activación condicional.

---

## 1. Qué es Spring y qué es Spring Boot

**Spring Framework** es una caja de herramientas para construir aplicaciones Java. Su corazón es un **contenedor** que **crea y conecta los objetos** de tu programa. Alrededor tiene módulos: web (MVC), acceso a datos, seguridad, pruebas.

**Spring Boot** es la capa que lo hace **fácil de arrancar**:

| Característica | Qué significa |
|---|---|
| **Starters** | Una sola dependencia trae todo un conjunto compatible. `spring-boot-starter-webmvc` = servidor web + MVC + JSON. Mirá `enexia/build.gradle`: 4 starters reemplazan decenas de librerías. |
| **Autoconfiguración** | Spring Boot **mira qué librerías hay en el classpath** y configura solo lo razonable. Ve `mysql-connector-j` y `spring-boot-starter-data-jpa` y arma la conexión a la base con lo que dice `application.properties`. |
| **Servidor embebido** | Trae **Tomcat adentro**: la aplicación es un programa que *levanta su propio servidor* en el puerto 8080. No hay que instalar nada aparte. |
| **Configuración externa** | Todo parámetro (puertos, URLs, claves) se cambia en `application.properties` o con variables de entorno, sin recompilar. |

---

## 2. Inversión de control (IoC) e inyección de dependencias (DI)

### 2.1 El problema

Un servicio necesita un repositorio, un servicio de correo, un servicio de auditoría. La forma "ingenua" es que **cada clase cree lo que necesita**:

```java
public class ValoracionService {
    private ValoracionRepository valoracionRepository = new ValoracionRepository();  // ← ❌
    private AuditoriaService auditoriaService = new AuditoriaService(...);           // ← ❌
}
```

Problemas:
1. **Acoplamiento fuerte:** `ValoracionService` queda atado a *cómo* se construyen sus piezas.
2. **Imposible de probar:** no podés reemplazar el repositorio real por uno falso.
3. **Duplicación:** si diez clases necesitan el mismo `AuditoriaService`, ¿hay diez copias? ¿Quién las configura?

### 2.2 La idea

**Inversión de control:** en vez de que cada clase construya sus dependencias, **las declara** (dice "necesito un `ValoracionRepository`") y **un tercero se las entrega**. Ese tercero es el **contenedor de Spring** (*ApplicationContext*). El control de "quién crea qué" se **invierte**: pasa de tu código al framework.

**Inyección de dependencias:** la técnica por la cual el contenedor *inyecta* (pasa) las dependencias. Hay tres formas; la que usa Enexia —y la recomendada— es **por constructor**:

```java
@Service
@RequiredArgsConstructor
public class ValoracionService {

    private final ValoracionRepository valoracionRepository;
    private final InscripcionRepository inscripcionRepository;
    private final EventoCronogramaRepository cronogramaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final ModeracionTextoService moderacionTextoService;
    // ...
}
```

`@RequiredArgsConstructor` (Lombok) escribe **un constructor con todos los atributos `final`**. Spring ve ese constructor, comprueba que tiene un bean de cada tipo y se los pasa. El código equivalente "a mano" sería:

```java
public ValoracionService(ValoracionRepository valoracionRepository,
                         InscripcionRepository inscripcionRepository,
                         /* ... */) {
    this.valoracionRepository = valoracionRepository;
    // ...
}
```

**Por qué por constructor y con `final`:**
- El objeto **nace completo**: no puede existir un `ValoracionService` al que le falte una dependencia.
- Los atributos `final` son **inmutables**: nadie los reasigna a mitad de camino.
- En un test **se instancia con `new` pasando falsos** (B06). Con inyección por campo (`@Autowired` sobre el atributo) eso no se puede hacer limpiamente.
- Un constructor con **demasiados parámetros** es una alarma de diseño: la clase hace demasiado.

---

## 3. Beans y el contenedor

Un **bean** es un objeto **creado y administrado por Spring**. Por defecto es un **singleton**: **una sola instancia** para toda la aplicación, compartida por todas las peticiones.

> ### ⚠️ Consecuencia importante: los beans no guardan estado de una petición
> Como un servicio es **uno solo** y atiende a **muchos usuarios a la vez** (en hilos distintos), un atributo mutable de un servicio sería compartido por todos. Por eso los servicios de Enexia tienen **solo atributos `final` (dependencias)** y los datos de cada pedido viajan **por parámetros y variables locales**. La excepción a propósito es `ModeracionTextoService.terminosBloqueados`: se asigna **una vez** al arrancar (`@PostConstruct`) y desde entonces es solo de lectura.

### 3.1 Cómo sabe Spring qué clases son beans: los estereotipos

Se marca la clase con una anotación. Todas son variantes de `@Component`:

| Anotación | Capa | Qué comunica | Ejemplo |
|---|---|---|---|
| `@Component` | genérica | "esta clase es un bean" | `BrevoClient`, `FinalizacionEventosJob` |
| `@Service` | lógica de negocio | "acá vive una regla del negocio" | `ValoracionService`, `AuthService` |
| `@Repository` | acceso a datos | "esto habla con la base" (además traduce las excepciones de la base) | `UsuarioRepository` |
| `@Controller` / `@RestController` | web | "esto atiende HTTP" | `AuthController` |
| `@Configuration` | configuración | "esta clase define beans con `@Bean`" | `AsyncConfig`, `SecurityConfig` |

Funcionalmente `@Service` y `@Component` son iguales; la diferencia es **semántica** (le dice al lector la intención) y permite herramientas y reglas por capa.

### 3.2 El escaneo de componentes

Al arrancar, Spring **recorre los paquetes** a partir de la clase marcada con `@SpringBootApplication` (`com.enexia.rg`) y todos sus subpaquetes, y registra cada clase con un estereotipo. Por eso **todo el código de Enexia vive bajo `com.enexia.rg`**: lo que quede fuera de ese árbol no se encuentra.

`@SpringBootApplication` es en realidad tres anotaciones juntas: `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.

### 3.3 Cómo resuelve las dependencias

Para cada parámetro del constructor, Spring busca **un bean de ese tipo**:

- Para `UsuarioRepository` encuentra **uno**: perfecto.
- Para una **interfaz** (`Executor`, `PasswordEncoder`) busca el bean que la implemente.
- Si encuentra **más de uno** del mismo tipo, **no sabe cuál elegir** y falla al arrancar. Hay que desambiguar con `@Qualifier("nombre")`. Es exactamente lo que ocurre con los pools de hilos del proyecto, que son dos beans del mismo tipo `Executor`: `ejecutorEnexia` y `ejecutorCorreo`. Por eso `RecuperacionCuentaService` pide el segundo **por nombre**:

```java
public RecuperacionCuentaService(
        UsuarioRepository usuarioRepository,
        PasswordResetTokenRepository tokenRepository,
        PasswordEncoder passwordEncoder,
        EmailService emailService,
        NotificacionService notificaciones,
        @Qualifier("ejecutorCorreo") Executor ejecutorCorreo,    // ← "el de correo", no "el otro"
        TransactionTemplate transaccion,
        @Value("${enexia.security.recuperacion.vigencia-minutos}") int vigenciaMinutos) {
```

Fijate también en el último parámetro: **`@Value`**, que inyecta un valor de configuración (ver §5). Este es el motivo por el que esta clase tiene un constructor escrito a mano: `@RequiredArgsConstructor` no puede poner `@Qualifier` ni `@Value` en los parámetros.

### 3.4 Dependencias circulares

Si A necesita a B y B necesita a A, Spring no puede construir ninguno primero y **falla al arrancar**. La solución casi nunca es un truco técnico: es **rediseñar**. En Enexia, `IntentosLoginService` y `VisitaService` son beans *separados* (no métodos de `AuthService`) por una razón relacionada: poder abrir su **propia transacción** (B05). La separación de responsabilidades evita los ciclos.

---

## 4. Definir beans a mano: `@Configuration` y `@Bean`

A veces el objeto que querés inyectar **no es una clase tuya** (no podés ponerle `@Service`) o necesita configuración. Entonces lo **fabricás vos** en una clase `@Configuration` con un método `@Bean`; Spring guarda lo que devuelve como bean.

`config/AsyncConfig.java`:

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "ejecutorEnexia")
    public Executor ejecutorEnexia() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(5);
        ejecutor.setQueueCapacity(100);
        ejecutor.setThreadNamePrefix("enexia-async-");
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationSeconds(30);
        ejecutor.initialize();
        return ejecutor;
    }

    @Bean(name = "ejecutorCorreo")
    public Executor ejecutorCorreo() { /* otro pool, aislado */ }
}
```

Lectura:
- `@Bean(name = "...")` registra el objeto devuelto con ese **nombre** (el que usa `@Qualifier`).
- Un `ThreadPoolTaskExecutor` es un **grupo de hilos**: `corePoolSize` hilos fijos, hasta `maxPoolSize` si la cola se llena, y `queueCapacity` tareas esperando. (B05)
- Por qué **dos** pools: el correo está separado del pipeline de moderación *a propósito*. Si una ráfaga de pedidos públicos de recuperación llenara el pool compartido, los eventos nuevos quedarían sin moderar. **Aislar recursos limita el daño**.
- `@EnableAsync` **activa** el mecanismo `@Async` (sin esto, la anotación se ignora). Igual `@EnableScheduling` en `SchedulingConfig` activa `@Scheduled`.

---

## 5. Configuración externa: `application.properties` y `@Value`

### 5.1 El archivo

`enexia/src/main/resources/application.properties` son pares `clave=valor`. Los comentarios empiezan con `#`. Spring Boot lo carga al arrancar. Secciones reales del proyecto:

```properties
# Conexión a la base
spring.datasource.url=jdbc:mysql://localhost:3306/enexia?useSSL=false&serverTimezone=America/Argentina/Ushuaia&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false

# JWT
enexia.security.jwt.secret-key=${JWT_SECRET_KEY:ZW5leGlhLWRl...}
enexia.security.jwt.expiration=86400000

# Reglas propias
enexia.security.login.intentos-bloqueo=3
enexia.security.recuperacion.vigencia-minutos=30
enexia.multimedia.max-imagenes=3
```

Las claves con prefijo `spring.` las **define Spring Boot** (están documentadas). Las que empiezan con `enexia.` son **propias del proyecto**: las inventamos nosotros.

### 5.2 Leer una propiedad: `@Value`

```java
@Value("${enexia.multimedia.max-imagenes:3}")
private int maxImagenes;
```

- `${clave}` = "el valor de esta propiedad".
- `${clave:3}` = "…o `3` si no está definida" (valor por defecto).
- Spring **convierte el tipo** solo (`String` → `int`, `Duration`, `BigDecimal`...).
- Se usa en atributos, o en parámetros del constructor (§3.3).

### 5.3 Variables de entorno y secretos

Mirá esta línea:

```properties
enexia.email.brevo.api-key=${BREVO_API_KEY:}
```

`${BREVO_API_KEY:}` significa: "tomá la **variable de entorno** `BREVO_API_KEY`; si no existe, usá vacío". Así **los secretos nunca se escriben en el archivo** (que se sube a Git): se definen en la máquina que ejecuta la aplicación.

Consecuencias de diseño que verás en el proyecto, todas del mismo patrón *"sin credencial, modo simulado"*:

| Sin esta variable | El servicio… |
|---|---|
| `BREVO_API_KEY` | escribe el correo en el log y no sale a la red (`BrevoClient`). |
| `CLOUDINARY_CLOUD_NAME` | no sube imágenes y las aprueba (`CloudinaryService`). |
| `JWT_SECRET_KEY` | usa una clave **solo de desarrollo** (en producción hay que definirla). |

Esa **degradación controlada** permite desarrollar y correr los tests sin cuentas de terceros.

> ⚠️ Una variable de entorno la ve **el proceso que se lanzó después de definirla**. Si la definís en Windows y tu terminal ya estaba abierta, esa terminal no la ve. Es lo que pasó al escribir `CorreosTest`: el test falla a propósito si no ve `BREVO_API_KEY`.

### 5.4 Activación condicional

```java
@Component
@ConditionalOnProperty(name = "enexia.eventos.finalizacion.habilitada",
        havingValue = "true", matchIfMissing = true)
public class FinalizacionEventoScheduler { ... }
```

El bean **solo existe si** la propiedad vale `true` (o si **falta**, por `matchIfMissing`). Útil para apagar una tarea desde la configuración, por ejemplo en un test que no quiere que el reloj cambie estados a su alrededor.

---

## 6. Qué pasa cuando ejecutás `./gradlew bootRun`

Seguí el arranque paso a paso:

1. **Gradle compila** el proyecto (Lombok genera getters, constructores, builders) y lanza la JVM con la clase `EnexiaApplication`.
2. `SpringApplication.run(...)` **crea el contenedor** y lee `application.properties` y las variables de entorno.
3. **Escanea** `com.enexia.rg` y registra todos los beans (`@Service`, `@Controller`, `@Repository`, `@Configuration`...).
4. La **autoconfiguración** crea lo que falta según el classpath: el `DataSource` (conexión a MariaDB, con un *pool* de conexiones Hikari), el `EntityManagerFactory` de Hibernate, el servidor Tomcat, el conversor JSON (Jackson), la cadena de filtros de seguridad.
5. **Hibernate**, con `ddl-auto=update`, compara las clases `@Entity` con la base y **crea o modifica tablas** (B03).
6. Se **instancian los beans en orden de dependencia**: primero los que no dependen de nada, después los que los usan. Se ejecutan los `@PostConstruct`.
7. Corren los **`CommandLineRunner`** (§7).
8. **Tomcat abre el puerto 8080** y empieza a atender. Los archivos de `src/main/resources/static/` se sirven tal cual **desde el mismo origen** (por eso el frontend se abre en `http://localhost:8080/index.html` y **no** necesita otro servidor: ver la advertencia del `CLAUDE.md`).
9. Arrancan las tareas `@Scheduled`.

Si algo falla (falta un bean, hay dos candidatos, una propiedad inválida) **la aplicación no arranca** y el error aparece en la consola. Eso es una **ventaja**: los errores de cableado se detectan al iniciar, no en producción a las tres de la mañana.

---

## 7. Tareas de arranque: `CommandLineRunner`

`DatosInicialesConfig` precarga los catálogos que el sistema necesita para funcionar:

```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatosInicialesConfig {

    private static final List<String> CATEGORIAS = List.of(
            "Cultural", "Educativo", "Social", "Deportivo", "Gaming",
            "Musica", "Gastronomia", "Tecnologia");
    // ...
}
```

Con `ddl-auto=update`, Hibernate crea las tablas **vacías**. En una base nueva faltaría la fila `ACTIVO`, el rol `PARTICIPANTE`, el estado `EN_PROCESO`... y **el primer registro fallaría**. Un `CommandLineRunner` es un bean cuyo método `run` **se ejecuta una vez**, después de que el contexto levantó (y por lo tanto después de que Hibernate armó el esquema). Está escrito para ser **idempotente**: antes de insertar cada fila verifica si ya existe, así puede correr en **cada arranque** sin duplicar ni pisar datos editados a mano.

> *Idempotente* = ejecutarlo una vez o diez veces deja el mismo resultado. Es una propiedad clave en tareas de arranque y en reintentos.

Lección real del proyecto (log de 2026-10-15): el seeder sembraba "las categorías que falten por nombre" en cada arranque. Con un ABM de categorías, un administrador que **renombrara** "Musica" la veía **resucitar** al reiniciar. Se corrigió para sembrar **solo si la tabla está vacía**. Moraleja: un arranque que "ayuda" puede deshacer decisiones del usuario.

---

## 8. Tareas programadas: `@Scheduled`

`FinalizacionEventoScheduler` corre periódicamente para pasar eventos terminados a `FINALIZADO`:

```java
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "enexia.eventos.finalizacion.habilitada",
        havingValue = "true", matchIfMissing = true)
public class FinalizacionEventoScheduler {

    private final FinalizacionEventoService finalizacionService;

    @Scheduled(
            initialDelayString = "${enexia.eventos.finalizacion.retraso-inicial-ms:10000}",
            fixedDelayString = "${enexia.eventos.finalizacion.intervalo-ms:300000}")
    public void finalizarVencidos() { /* ... */ }
}
```

| Atributo de `@Scheduled` | Significado |
|---|---|
| `initialDelay` | espera antes de la primera ejecución (acá 10 s). Una pasada **al arrancar** evita que un evento que terminó mientras la app estaba apagada espere un intervalo completo. |
| `fixedDelay` | espera **entre el fin de una ejecución y el inicio de la siguiente** (5 min). Evita que dos pasadas se superpongan. |
| `fixedRate` | intervalo fijo desde el **inicio** de cada ejecución (puede solaparse). |
| `cron` | expresión tipo cron (`"0 0 3 * * *"` = todos los días a las 3). |
| `...String` | versión que lee el valor de una **propiedad** (`${...}`), para poder cambiarlo sin recompilar. |

Dos decisiones de diseño que explican por qué existen **dos clases** para finalizar eventos (`...Scheduler` y `...Job`) y por qué cada disparador es un **bean aparte** de su servicio:

> *"Vive en su propio bean (y no como método `@Scheduled` del service) para que cada `finalizar(id)` cruce el **proxy** y corra en su propia transacción: un evento que falla no deshace a los que ya se finalizaron."*

Esto se explica a fondo en B05 (la trampa de la **autoinvocación**): `@Transactional` solo funciona cuando el método se llama **desde otro bean**, porque Spring envuelve cada bean en un **proxy**.

Además, cada tarea programada **atrapa sus propias excepciones** (`catch (RuntimeException ex)` + `log`): si una pasada lanzara, el *scheduler* podría dejar de ejecutarla.

---

## 9. Proxies y anotaciones de comportamiento: la magia detrás de `@Transactional`

Muchas anotaciones de Spring **no hacen nada por sí mismas**: Spring **envuelve** el bean en un objeto *proxy* que, antes y después de cada llamada, agrega comportamiento.

```
controller ──► [ PROXY de ValoracionService ] ──► ValoracionService (tu código)
                   abre transacción   ──────────►  crear(...)
                   commit / rollback  ◄──────────  (si lanza excepción: rollback)
```

Anotaciones que funcionan así: `@Transactional`, `@Async`, `@Scheduled` (en parte), `@PreAuthorize`, `@Cacheable`. Tienen una limitación común que vas a ver en B05: **solo actúan si el llamador es otro bean**. Una llamada de un método a otro **de la misma clase** no pasa por el proxy y la anotación se ignora en silencio.

---

## 10. Errores típicos

| Error | Síntoma | Remedio |
|---|---|---|
| Clase sin estereotipo. | `NoSuchBeanDefinitionException` al arrancar. | Agregar `@Service`/`@Component`, o revisar que esté bajo `com.enexia.rg`. |
| Dos beans del mismo tipo. | `NoUniqueBeanDefinitionException`. | `@Qualifier("nombre")` o `@Primary`. |
| Guardar estado de un pedido en un atributo de un servicio. | Datos mezclados entre usuarios. | Variables locales y parámetros. |
| `new MiServicio()` en vez de inyectarlo. | El objeto no tiene sus dependencias ni su proxy. | Siempre inyectar. |
| Secretos escritos en `application.properties`. | Claves filtradas en Git. | Variables de entorno `${NOMBRE:}`. |
| Olvidar `@EnableAsync` / `@EnableScheduling`. | `@Async`/`@Scheduled` no hacen nada, sin error. | Declararlos en una `@Configuration`. |

---

## 11. Para practicar

1. Abrí `AsyncConfig` y explicá qué ocurre con la tarea **número 106** si hay 2 hilos activos, 100 tareas en cola y el máximo es 5 (pista: ¿se crean hilos nuevos antes o después de llenarse la cola?).
2. Agregá (en papel) una propiedad `enexia.eventos.tamano-pagina` con valor por defecto 9 y mostrá cómo la leería un servicio con `@Value`.
3. ¿Por qué `ModeracionTextoService.cargarTerminos` usa `@PostConstruct` y no el constructor? (Pista: ¿ya están disponibles las propiedades y los recursos?)
4. Listá, mirando `RecuperacionCuentaService`, cuántas dependencias tiene. ¿Es mucho? ¿Qué las agrupa en tres responsabilidades?
5. Ejecutá `./gradlew bootRun` y, en el log de arranque, encontrá: la línea de Tomcat con el puerto, la línea de Hibernate y el mensaje `Started EnexiaApplication`.

**Siguiente capítulo:** [B02 — MVC, controladores, DTO y validación](./B02_mvc_dto_validacion.md)
