# B06 — Testing: probar que el código hace lo que dice

> **Parte B · Spring Boot (cierre).** Un programa sin pruebas es una **promesa sin garantía**. En Enexia hay unas **7.600 líneas de tests** (más que muchos módulos de producción), en **tres niveles** que se complementan. Este capítulo explica qué es cada uno, las herramientas (JUnit, Mockito, AssertJ, Playwright, Awaitility) y cómo **leer** un test como si fuera una especificación.

**Qué vas a aprender**
- Por qué se prueba y la **pirámide de tests**.
- **JUnit 5**: `@Test`, `@DisplayName`, `@Nested`, `@BeforeEach`, tests parametrizados.
- **Mockito**: dobles de prueba (*mocks*), `when`/`thenReturn`, `verify`, `ArgumentCaptor`, *spies*.
- **AssertJ**: aserciones legibles.
- Tests de servicio sin base ni Spring; tests de API de punta a punta; tests de interfaz con un navegador real.
- Patrón *Page Object*; esperar lo asíncrono con **Awaitility**.
- Mantenimiento: qué hacer cuando un test se rompe.

---

## 1. Por qué probar y qué probar

Probar sirve para:
1. **Detectar errores** antes que el usuario.
2. **Cambiar con confianza** (refactorizar sin miedo): si algo se rompe, un test lo grita.
3. **Documentar el comportamiento**: un buen test dice **qué debe hacer** el código, con ejemplos ejecutables.
4. **Fijar decisiones difíciles**: reglas que costó aprender (el bloqueo silencioso, el cupo, la moderación antes de persistir) quedan **protegidas** contra un cambio bienintencionado.

### 1.1 La pirámide de tests

```
            /\          pocos, lentos, caros
           /UI\         Playwright con navegador (LoginTest, CorreosTest...)
          /----\
         / API  \       Playwright APIRequestContext: HTTP real → Spring → MariaDB
        /--------\
       / SERVICIO \     JUnit + Mockito: sin Spring, sin base. Milisegundos.
      /------------\
     /   UNIDAD     \   ValidadorCuit, CorreoHtml: puro cálculo
    /________________\  muchos, rápidos, baratos
```

| Nivel | Dónde (`enexia/src/test/java/com/enexia/rg/`) | Qué prueba | Necesita |
|---|---|---|---|
| **Unidad pura** | `util/` | Una función sin dependencias (`ValidadorCuit`, `CorreoHtml`). | Nada. |
| **Servicio** | `service/` (17 clases) | La lógica de un servicio, **con sus dependencias simuladas**. | Solo la JVM. |
| **API** | `api/` | El contrato HTTP completo, con la app **realmente levantada**. | App + MariaDB + puerto 8080 libre. |
| **Interfaz** | raíz y `pages/` | Una persona usando el navegador. | Todo lo anterior + Chromium. |

**Regla:** la mayoría de las pruebas debe estar **abajo** (rápidas, precisas); pocas **arriba** (lentas, pero prueban que todo encaja).

---

## 2. JUnit 5: el motor

JUnit es el *framework* que **descubre y ejecuta** los tests. Un test es **un método** anotado con `@Test`.

```java
@DisplayName("ValidadorCuit - digito verificador modulo 11 (RF-7.3)")
class ValidadorCuitTest {

    @Test
    @DisplayName("Acepta el formato con guiones, igual que el formulario")
    void aceptaGuiones() {
        assertThat(ValidadorCuit.esValido("30-71659554-0")).isTrue();
        assertThat(ValidadorCuit.esValido("30 71659554 0")).isTrue();
    }
}
```

| Anotación | Para qué |
|---|---|
| `@Test` | Marca un método como test. |
| `@DisplayName("...")` | Nombre legible en el reporte (en español, **como una frase de especificación**). |
| `@Nested` | Agrupa tests relacionados en una clase interna (`Validos`, `Invalidos`). El reporte los muestra en árbol. |
| `@BeforeEach` / `@AfterEach` | Corre antes / después de **cada** test (arma el escenario, limpia). |
| `@BeforeAll` / `@AfterAll` | Una vez por clase (deben ser `static`). |
| `@ParameterizedTest` + `@ValueSource` | **El mismo test con varios datos.** |
| `@Disabled` | Saltear (con motivo). |

### 2.1 Tests parametrizados

```java
@ParameterizedTest(name = "{0} es valido")
@ValueSource(strings = {
        "30716595540",   // termina en 0: el caso critico (resto 0 -> dv 0)
        "20123456786",
        "27230938607"
})
void reconoceCuitValidos(String cuit) {
    assertThat(ValidadorCuit.esValido(cuit)).isTrue();
}
```

Un solo método, un caso por cada valor. Observá el **criterio**: se usan CUIT reales de organismos públicos *"y no inventados a propósito: un valor inventado que 'da bien' con MI implementación probaría únicamente que la implementación es consistente consigo misma"*. Y la clase de equivalencia **más fácil de romper** (CUIT terminados en 0) tiene su caso dedicado.

### 2.2 Estructura de un buen test: Arrange–Act–Assert

```java
@Test
void sinAvisoSiFalla() {
    // ARRANGE (preparar): el escenario
    when(tokenRepository.buscarPorHashConUsuario(anyString())).thenReturn(Optional.empty());

    // ACT (actuar): ejecutar lo que se prueba
    assertThatThrownBy(() ->
            servicio.confirmarRestablecimiento("inventado", PASSWORD_NUEVA, PASSWORD_NUEVA))
            .isInstanceOf(ReglaNegocioException.class);

    // ASSERT (verificar): el resultado y los efectos
    verify(emailService, never()).enviarPasswordCambiada(anyString());
}
```

**Probá un comportamiento por test.** Si un test falla, el nombre ya dice qué se rompió.

### 2.3 Probar tanto el éxito como el error

El `CLAUDE.md` lo exige: *"Test both success cases and error cases (exceptions)"*. Los errores **también son comportamiento**: ¿se lanza la excepción correcta? ¿se evita mandar un correo cuando falla?

---

## 3. AssertJ: aserciones que se leen

AssertJ reemplaza `assertEquals(a, b)` por una API **fluida**:

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

assertThat(resultado.aprobado()).isTrue();
assertThat(ticket.getCupoActual()).isEqualTo(4);
assertThat(guardado.getNombre()).isNull();
assertThat(lista).hasSize(2).contains("ADMINISTRADOR");
assertThat(json.get("roles").toString()).contains("ORGANIZADOR").contains("PARTICIPANTE");

assertThatThrownBy(() -> servicio.registrar(participante, ID_TICKET, null, request))
        .isInstanceOf(OperacionNoPermitidaException.class)
        .hasMessageContaining("cupo");
```

Si falla, el mensaje dice **qué esperaba y qué recibió**. Se usa con `import static` (A01 §10).

---

## 4. Mockito: dobles de prueba

### 4.1 El problema

`ValoracionService` necesita 6 colaboradores (repositorios, auditoría, moderación). Para probar **solo** su lógica no querés una base real, ni que se mande un correo de verdad. Querés **reemplazar** cada colaborador por un **doble**: un objeto falso que **controlás**.

| Doble | Qué hace |
|---|---|
| **Mock** | Objeto falso que **responde lo que le programás** y **registra cómo lo usaron**. |
| **Spy** | Objeto **real** al que se le puede observar (y reprogramar) alguna llamada. |
| **Stub** | Respuesta fija programada (lo que hace `when(...).thenReturn(...)`). |

### 4.2 El esqueleto de un test de servicio

`service/EventoServiceTest.java` (recortado):

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EventoService - creacion y baja de eventos (RF-2.1 a RF-2.9)")
class EventoServiceTest {

    @Mock private EventoRepository eventoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ApplicationEventPublisher publicadorDeEventos;
    @Mock private AuditoriaService auditoriaService;
    @Mock private HttpServletRequest request;

    private final EventoMapper eventoMapper = new EventoMapper();     // el REAL: es lógica pura

    @InjectMocks private EventoService servicio;

    @BeforeEach
    void prepararEscenario() {
        ReflectionTestUtils.setField(servicio, "eventoMapper", eventoMapper);
        ReflectionTestUtils.setField(servicio, "maxImagenes", 3);

        when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL))
                .thenReturn(Optional.of(organizador()));
        when(categoriaRepository.existsByIdCategoriaAndActivaTrue(anyLong())).thenReturn(true);
        // ...
    }
}
```

| Pieza | Qué hace |
|---|---|
| `@ExtendWith(MockitoExtension.class)` | Activa Mockito en esta clase. |
| `@Mock` | Crea un **doble** de ese tipo (no toca la base: todos sus métodos devuelven "nada" por defecto). |
| `@InjectMocks` | Crea el objeto **a probar** (`EventoService`) e **inyecta los mocks** en su constructor/campos. |
| `ReflectionTestUtils.setField` | Fija un atributo privado por **reflexión**. Se usa para los `@Value`, que Mockito **no resuelve** (sin esto `maxImagenes` valdría 0 y el primer test rechazaría todo). |
| `Strictness.LENIENT` | No falla si un `when(...)` del `@BeforeEach` no se usó en ese test en particular. |

**Por qué el `EventoMapper` es real y no un mock:** es lógica pura sin dependencias; mockearlo *"obligaría a repetir en cada test qué devuelve, y no detectaría un error en el armado de la respuesta"*. **Se mockea lo que toca el mundo exterior** (base, red, tiempo), **no la lógica propia**.

### 4.3 Programar respuestas: `when(...).thenReturn(...)`

```java
when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(participante));
when(valoracionRepository.existsByUsuarioIdUsuarioAndCronogramaIdCronograma(anyLong(), anyLong()))
        .thenReturn(true);
when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));   // devuelve el mismo argumento
```

Los **comodines** (`anyLong()`, `anyString()`, `any()`) significan "cualquier valor de ese tipo". `thenThrow(...)` programa una excepción; `thenAnswer(...)` una respuesta calculada con una lambda.

### 4.4 Verificar interacciones: `verify`

```java
verify(emailService).enviarPasswordCambiada(usuario.getEmail());     // se llamó exactamente una vez
verify(emailService, never()).enviarPasswordCambiada(anyString());   // NUNCA se llamó
verify(eventoRepository, times(25)).save(any());                     // se llamó 25 veces
verify(eventoDetalleRepository, never()).save(any());                // no se guardó contenido
```

`verify(...)` comprueba **efectos** que no se ven en el valor devuelto: "¿se mandó el correo?", "¿se escribió en la base?". `never()` es tan importante como el caso positivo: prueba que algo **no** pasó.

### 4.5 Capturar argumentos: `ArgumentCaptor`

```java
ArgumentCaptor<PasswordResetToken> guardado = ArgumentCaptor.forClass(PasswordResetToken.class);
ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);

servicio.notificarBloqueoConEnlaceDeRecuperacion(1L);

verify(tokenRepository).save(guardado.capture());
verify(emailService).enviarAvisoBloqueo(anyString(), enviado.capture());

assertThat(guardado.getValue().getToken()).isNotEqualTo(enviado.getValue());   // en la base va el HASH, no el token
```

Permite **inspeccionar el objeto que el servicio entregó** a una dependencia. Este test verifica nada menos que una propiedad de seguridad: **lo guardado en la base no es el token que viajó por correo**.

### 4.6 Los tests protegen decisiones de arquitectura

Dos ejemplos de `EventoServiceTest` cuyo valor es **evitar regresiones silenciosas**:

```java
@Test
@DisplayName("NO se persiste contenido: ni titulo, ni categoria, ni portada (RF-2.2)")
void noPersisteContenido() {
    servicio.crear(EMAIL, datos, imagenes, request);

    verify(eventoRepository).save(captor.capture());
    assertThat(captor.getValue().getNombre()).isNull();
    verify(eventoDetalleRepository, never()).save(any());
    verify(cronogramaRepository, never()).save(any());
}
```

La suite lo explica: *"Si un cambio futuro empezara a guardar el título 'porque es cómodo para el dashboard', se estaría rompiendo la regla arquitectónica de moderar antes de persistir, y esta suite lo detecta."*

Y el pipeline se dispara **por evento** (`verify(publicadorDeEventos).publishEvent(...)`) y no por llamada directa: protege contra reintroducir la carrera de B05 §4.

### 4.7 Spies y `doAnswer`

`CorreosTest` necesita que `BrevoClient` **mande el correo de verdad** pero **anotar** cada envío:

```java
@MockitoSpyBean
private BrevoClient brevo;

Mockito.doAnswer(invocacion -> {
    Object resultado = invocacion.callRealMethod();         // llama al método REAL
    envios.add(new Envio(invocacion.getArgument(0), invocacion.getArgument(1),
            Boolean.TRUE.equals(resultado)));
    return resultado;
}).when(brevo).enviar(anyString(), anyString(), anyString(), anyString());
```

`@MockitoSpyBean` es la variante de Spring para espiar un **bean real** dentro del contexto levantado.

### 4.8 Límites de los mocks

Un test con mocks prueba que **tu código usa bien sus colaboradores**, no que los colaboradores **funcionen**. Por ejemplo, no detecta que una consulta JPQL esté mal escrita. Para eso existen los **tests de API** (§5), que usan la base real.

---

## 5. Tests de API: la app real, sin navegador

`api/BaseApiTest.java` usa el **cliente HTTP de Playwright** (`APIRequestContext`) contra la aplicación **levantada de verdad** (`@SpringBootTest(webEnvironment = DEFINED_PORT)`):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@DisplayName("API - Autenticacion y recuperacion de acceso")
class AuthApiTest extends BaseApiTest {

    @Test
    @DisplayName("201 con los roles asignados y la cuenta ACTIVA")
    void altaCorrecta() {
        String sufijo = sufijoUnico();
        APIResponse respuesta = api.post("/api/auth/registro", jsonCon(alta(sufijo, "ORGANIZADOR")));

        assertThat(respuesta.status()).isEqualTo(201);
        var json = cuerpo(respuesta);
        assertThat(json.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(json.get("roles").toString()).contains("ORGANIZADOR").contains("PARTICIPANTE");
    }
}
```

Recorre **todo**: HTTP → filtros → controller → servicio → repository → MariaDB. Detecta lo que los mocks no pueden (consultas, mapeos, constraints, seguridad).

Detalles de diseño del proyecto:
- **Identidades irrepetibles:** como el sistema usa borrado lógico, *un email o un DNI no se pueden reutilizar*; `sufijoUnico()` genera uno nuevo en cada corrida.
- Las pruebas de **moderación asíncrona** usan **Awaitility** (§7).
- **Requisitos:** MariaDB corriendo y el puerto 8080 **libre** (si hay un `bootRun` abierto, hay que cerrarlo: el test levanta su propia copia).

---

## 6. Tests de interfaz: un navegador real

### 6.1 Playwright

**Playwright** controla un navegador (Chromium) como lo haría una persona: abrir una página, escribir, hacer clic, leer lo que aparece. `BaseTest` prepara cada test:

```java
@BeforeEach
void setUp() {
    playwright = Playwright.create();
    boolean esCI = System.getenv("CI") != null;
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(esCI));
    context = browser.newContext();
    page = context.newPage();
    context.tracing().start(new Tracing.StartOptions()
            .setScreenshots(true).setSnapshots(true).setSources(true));
}
```

- *Headless* = sin ventana (en integración continua). En local se ve el navegador.
- El **trace** graba capturas y DOM de cada paso: `npx playwright show-trace build/traces/trace.zip` permite **ver paso a paso** qué pasó.
- Al fallar un test, `BaseTest` **toma una captura automática** en `evidencias/` gracias a una extensión de JUnit declarada como lambda:

```java
@RegisterExtension
AfterTestExecutionCallback screenshotOnFailure = extensionContext -> {
    if (extensionContext.getExecutionException().isPresent()) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("evidencias/" + extensionContext.getDisplayName() + ".png")));
    }
};
```

### 6.2 Page Object Model (POM)

Si cada test conoce los **selectores** de la pantalla (`#password`, "Iniciar sesión"), cada cambio de HTML rompe decenas de tests. El patrón **Page Object** encapsula **una pantalla** en una clase: los tests hablan en términos de **acciones** ("hacer login"), no de selectores.

`pages/LoginPage.java`:

```java
public class LoginPage {
    private final Page page;
    private final Locator nickname;
    private final Locator password;
    private final Locator loginButton;

    public LoginPage(Page page) {
        this.page = page;
        this.nickname = page.getByLabel("Nombre de usuario");
        this.password = page.locator("#password");
        this.loginButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Iniciar sesión"));
    }

    public void navegar() { page.navigate("http://localhost:8080/pages/auth/login-desktop-claro.html"); }

    public void login(String user, String pass) {
        nickname.fill(user);
        password.fill(pass);
        loginButton.click();
    }
}
```

Usado por el test:

```java
LoginPage login = new LoginPage(page);
login.navegar();
login.login(NICKNAME, PASSWORD);
assertThat(page).hasURL(Pattern.compile(".*index\\.html"));
```

**Los selectores accesibles** (`getByLabel`, `getByRole`) son mejores que los CSS (`.btn-primary`): se parecen a **cómo una persona encuentra el elemento**, y de paso verifican que la página sea **accesible**. **Las aserciones van en el test, no en el Page Object.**

### 6.3 Auto-espera

Las aserciones de Playwright (`assertThat(locator).isVisible()`) **reintentan** hasta un plazo: no hace falta `sleep` mientras la página reacciona. Para sincronizar con un pedido de red:

```java
page.waitForResponse(r -> r.url().endsWith("/api/auth/login"),
        () -> login.login(NICKNAME, "ClaveEquivocada" + n));
```

Se **registra la espera antes de hacer clic** (el segundo argumento es la acción que provoca la respuesta).

---

## 7. Esperar lo asíncrono: Awaitility

Cuando algo ocurre **en otro hilo** (la moderación, un correo), el test no puede preguntar al instante. `Thread.sleep(5000)` es lento (casi siempre espera de más) y frágil (a veces de menos). **Awaitility** consulta **hasta que se cumple o vence el plazo**:

```java
await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> assertThat(envios)
                .as("correos procesados hasta ahora: %s", envios)
                .anyMatch(e -> e.asunto().equals(asunto)));
```

`.as("...")` agrega un mensaje al fallo: *"correos procesados hasta ahora: [...]"* dice **por qué** no llegó.

---

## 8. Un test de punta a punta real: `CorreosTest`

La prueba que manda correos reales a la bandeja del equipo (escrita el 2026-10-17) combina casi todo:

1. **`@SpringBootTest(DEFINED_PORT)`** levanta la app; **`BaseTest`** abre el navegador.
2. `@BeforeEach` **prepara datos**: crea la cuenta `gaston_test` por la API de registro si no existe, la deja `ACTIVA` y borra el límite de enlaces de recuperación (con `JdbcTemplate`, SQL directo, **solo sobre esa cuenta de prueba**).
3. **`@MockitoSpyBean` sobre `BrevoClient`** para anotar cada envío real.
4. **Tres tests**: login, recuperación (pantalla → "¡Correo enviado!" → correo con el enlace) y **tres fallos que bloquean la cuenta en silencio y mandan el aviso**.
5. **Awaitility** para esperar el correo.
6. **Falla a propósito si no hay `BREVO_API_KEY`**: *"no tiene sentido que 'pase' sin haber mandado nada"*.
7. `@AfterEach` **deja la cuenta ACTIVA** para poder repetir.

Fijate en la regla de higiene: **un test no debe dejar el sistema en un estado que rompa al siguiente.**

---

## 9. Ejecutar y leer resultados

Desde `enexia/`:

```bash
./gradlew test                                          # todos (los de API/UI necesitan la app y la base)
./gradlew test --tests "*RecuperacionCuentaServiceTest"  # una clase
./gradlew test --tests "*EventoServiceTest" --tests "*InscripcionServiceTest"
```

- Reporte HTML: `enexia/build/reports/tests/test/index.html`.
- Resultados XML: `enexia/build/test-results/test/`.
- Los tests de **servicio** corren en segundos; los de **API/UI** en minutos.

> **Estado al cierre del proyecto:** los tests de servicio pasan. Los de API y de interfaz (`LoginTest`, `RegistroTest`, `EventoApiTest`, `CrearEventoTest`) **necesitan el backend, la base y parámetros** (`-Dnickname=...`); sin ellos fallan por *entorno*, no por código. Propuesta pendiente: moverlos a un *source set* aparte (`integrationTest`) para que `gradle test` corra solo los de servicio (`SUGERENCIAS_PENDIENTES.md`, E2).

---

## 10. Mantenimiento: cuando un test se rompe

No todo test rojo es un bug de producción. Preguntate **qué cambió**:

| Causa | Ejemplo real del proyecto | Qué hacer |
|---|---|---|
| **El código cambió a propósito** y el test describe lo viejo. | `EventoServiceTest` mockeaba `categoriaRepository.existsById`; el servicio pasó a `existsByIdCategoriaAndActivaTrue` con el ABM de categorías. | **Actualizar el test** (la nueva regla es la correcta). |
| **Falta una dependencia nueva** en el escenario. | `InscripcionServiceTest` no tenía `@Mock ValoracionRepository`; `@InjectMocks` lo dejó en `null` → `NullPointerException`. | Agregar el mock. |
| **El fixture no refleja un dato que ahora se usa.** | `RegistroInscripcionServiceTest`: el servicio compara contra `hora_inicio` y el *fixture* solo fijaba `hora_fin` → `NullPointerException: time`. | Completar el dato. |
| **La regla de negocio cambió.** | `RecuperacionCuentaServiceTest`: ya no se desbloquea con el correo (decisión 2026-10-19). | Reescribir el test para la **nueva** regla (y verificar que lo **viejo ya no ocurre**). |
| **Un bug real.** | — | Arreglar el código; el test hizo su trabajo. |

> **Lección:** un `@Mock` ausente **no falla al compilar**: falla en ejecución con un `NullPointerException` lejano. Cuando un servicio gana una dependencia, **hay que acompañarlo en sus tests**.

Y cuando una **decisión de negocio** cambia, el test que la protegía **debe cambiar con ella**, y es buena idea convertirlo en un test de la **nueva garantía**: así el test `noDesbloqueaCuentaBloqueada` verifica ahora que cambiar la contraseña **no** reactiva la cuenta.

---

## 11. Principios

1. **Un test, una razón para fallar.**
2. **Nombres que se leen como especificaciones** (`"Cuenta SUSPENDIDA: cambia la contrasena pero NO levanta la sancion"`).
3. **Comentá el porqué** en los tests no obvios (los de Enexia lo hacen: *"Es seguro porque solo referencia al usuario..."*).
4. **Mockeá los bordes** (base, red, reloj), **no la lógica propia**.
5. **Probá el caso feliz, los errores y los efectos que NO deben ocurrir** (`never()`).
6. **Los tests no dependen del orden** ni de datos que otro dejó.
7. **Determinismo:** nada de `sleep` ni de datos aleatorios sin control (`sufijoUnico()` es aleatorio pero **para evitar** colisiones, no para variar el resultado).
8. **Rápidos abajo, pocos arriba** (pirámide).

---

## 12. Para practicar

1. Escribí un test parametrizado para `CorreoHtml.escapar` con cinco entradas (`<`, `>`, `&`, `"`, `'`) y su salida esperada.
2. Dado `ValoracionService.crear`, listá **cinco casos de error** que deberían tener test y qué excepción lanza cada uno. Compará con `ValoracionServiceTest`.
3. Escribí (en papel) un test con `ArgumentCaptor` que compruebe que, al crear una valoración, `fecha` es hoy y `valor` es el del request.
4. Explicá por qué `EventoServiceTest.sinLimiteDePlan` crea **25** eventos en vez de uno. ¿Qué regresión detecta que un solo evento no detectaría?
5. Corré `./gradlew test --tests "*ValidadorCuitTest"`, abrí el reporte HTML y encontrá el árbol de `@Nested`.

**Siguiente capítulo (Parte C):** [C01 — API REST y HTTP](./C01_api_rest_http.md)
