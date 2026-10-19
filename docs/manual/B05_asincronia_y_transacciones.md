# B05 — Asincronía, eventos y tareas programadas

> **Parte B · Spring Boot.** Hasta acá cada petición hacía su trabajo y respondía. Pero hay trabajo **lento** (subir imágenes, mandar correos) o **periódico** (finalizar eventos, mandar recordatorios) que no debe hacer esperar al usuario. Este capítulo explica **hilos**, **ejecutores**, **eventos transaccionales** y **tareas programadas**, y los errores sutiles que aparecen cuando varias cosas ocurren "a la vez".

**Qué vas a aprender**
- Hilos y *pools*: qué son, cómo se dimensionan, qué pasa cuando se llenan.
- `@Async` y por qué un método asíncrono **no puede propagar excepciones**.
- **Eventos de aplicación** y `@TransactionalEventListener(AFTER_COMMIT)`: cómo se evita una condición de carrera real que ocurrió en el proyecto.
- Enviar correo **después del commit**.
- Tareas `@Scheduled`: ventanas contra igualdades exactas, idempotencia, zonas horarias.
- El **patrón de contención** de fallos y el aislamiento de recursos.
- Cómo probar lo asíncrono.

---

## 1. Por qué hace falta la asincronía

Cuando un organizador crea un evento, el sistema debe moderar el texto y **subir y analizar cada imagen en un servicio externo** (Cloudinary). Eso puede tardar segundos. Hay dos diseños:

| | **Síncrono** | **Asíncrono** |
|---|---|---|
| Qué hace la petición | Espera la moderación completa y recién responde. | Guarda un registro mínimo, **responde enseguida** y la moderación sigue **en segundo plano**. |
| Experiencia | El usuario mira una ruedita; si el servidor es lento o cae, la petición muere. | Ve "Validando..." y consulta el resultado después. |
| En Enexia | Solo para **editar** un evento ya publicado (el evento viejo debe seguir visible y el resultado es inmediato). | **Crear** un evento y mandar correos. |

Por eso la creación responde **`202 Accepted`** y no 201: el recurso fue aceptado pero **todavía no está completo** (B02 §3.4).

---

## 2. Hilos y ejecutores

### 2.1 Hilo (*thread*)

Un **hilo** es una línea de ejecución. Un servidor web atiende **cada petición en un hilo distinto** (Tomcat tiene un *pool* de hilos para eso). Un programa puede tener muchos hilos corriendo **a la vez**.

```java
Thread hilo = new Thread(() -> System.out.println("hola desde otro hilo"));
hilo.start();
```

Crear hilos a mano es mala idea: cada uno consume memoria, y sin tope un pico de trabajo agotaría el servidor.

### 2.2 Ejecutor y *pool* de hilos

Un **ejecutor** (`Executor`) es un objeto que **recibe tareas** (`Runnable`) y las corre en hilos que **él administra**. Un **pool** reutiliza un grupo fijo de hilos y pone las tareas excedentes **en una cola**.

`config/AsyncConfig.java` define dos:

```java
@Bean(name = "ejecutorEnexia")
public Executor ejecutorEnexia() {
    ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
    ejecutor.setCorePoolSize(2);          // hilos permanentes
    ejecutor.setMaxPoolSize(5);           // tope si la cola se llena
    ejecutor.setQueueCapacity(100);       // tareas que esperan
    ejecutor.setThreadNamePrefix("enexia-async-");
    ejecutor.setWaitForTasksToCompleteOnShutdown(true);
    ejecutor.setAwaitTerminationSeconds(30);
    ejecutor.initialize();
    return ejecutor;
}
```

**Cómo se comporta (la regla que sorprende):** primero ocupa los **hilos *core***; cuando están ocupados, **encola**; solo cuando la **cola está llena** crea hilos hasta el máximo; si **todo** está lleno, **rechaza** la tarea con `RejectedExecutionException`.

```
tareas 1-2     → corren en los 2 hilos core
tareas 3-102   → esperan en la cola (100 lugares)
tareas 103-105 → crean hilos 3, 4 y 5 (hasta el máximo)
tarea 106      → RECHAZADA
```

**Por qué tan chico:** el trabajo asíncrono de Enexia es **espera de red**, no cálculo. Más hilos no lo acelerarían; solo abrirían más conexiones simultáneas contra terceros que tienen sus propios límites. Y **el prefijo del nombre** (`enexia-async-`) permite reconocer en un log si una línea viene de una tarea de fondo o de una petición HTTP.

**Apagado ordenado:** `setWaitForTasksToCompleteOnShutdown(true)` espera hasta 30 s a que terminen las tareas en curso; un evento a mitad de moderar quedaría `EN_PROCESO` para siempre.

### 2.3 Aislar recursos: el segundo pool

El correo tiene **su propio** pool (`ejecutorCorreo`) a propósito. La recuperación de contraseña es un endpoint **público**; si compartiera pool con la moderación, **una ráfaga de pedidos de recuperación llenaría la cola** y los eventos nuevos **nunca se moderarían**. Aislados, lo peor que esa ráfaga provoca es perder correos de recuperación. Es el principio de **compartimentos estancos**: una falla no hunde todo el barco.

Y `RecuperacionCuentaService` maneja el rechazo **sin delatar nada**:

```java
try {
    ejecutorCorreo.execute(() -> procesarSolicitud(email));
} catch (RejectedExecutionException ex) {
    log.warn("Cola de correo llena: se descarto una solicitud de restablecimiento");
}
```

*"Si la cola está llena se descarta el pedido con un log; no se responde distinto, porque eso también revelaría algo."*

---

## 3. `@Async`: ejecutar un método en otro hilo

```java
@Async("ejecutorEnexia")        // "corré este método en ESE pool"
public void alCrearseElEvento(EventoCreadoEvent aviso) { ... }
```

Spring envuelve el bean en un **proxy** (B01 §9): al llamar al método, el proxy **no lo ejecuta**: lo manda al pool y **vuelve de inmediato**. Requisitos y consecuencias:

1. **Hay que activarla** con `@EnableAsync` (en `AsyncConfig`). Sin eso, se ignora **sin error**.
2. **Mismo límite de proxy que `@Transactional`:** solo funciona si el llamador es **otro bean** (autoinvocación = sin efecto).
3. **Un método `void` asíncrono no puede propagar excepciones.** Nadie recibe la excepción: el hilo del pool la registra y sigue. Un evento podría quedar `EN_PROCESO` para siempre sin que nadie se entere. **Por eso todo el pipeline está envuelto en `try/catch`** y traduce cualquier fallo a un estado visible (`RECHAZADO_SISTEMA / ERROR_PIPELINE`) que el organizador **y el administrador** pueden ver (A04 §1.9).
4. **El hilo nuevo NO hereda el contexto del pedido.** El `SecurityContext` (quién es el usuario) vive en el hilo de la petición. En el hilo de fondo no hay `Principal`. Por eso los datos que el trabajo asíncrono necesita (el **email** del organizador) **se pasan como parámetros** dentro del objeto del evento, no se "leen del contexto". Y por eso **los bytes de las imágenes se copian antes de que termine la petición**: el contenedor borra los archivos temporales del multipart al responder (B02 §8).

---

## 4. Eventos de aplicación y el *commit*

### 4.1 La condición de carrera real

Este es el error más instructivo del proyecto (está contado en el javadoc de `EventoCreadoEvent`). El diseño inicial hacía algo equivalente a esto, dentro de `EventoService.crear` (que es `@Transactional`) — el código es un **esquema ilustrativo** (los nombres exactos cambiaron), pero el síntoma fue real:

```java
Evento esqueleto = eventoRepository.save(nuevo);
moderacionService.procesarAsync(esqueleto.getIdEvento(), ...);   // ❌ arranca YA, en otro hilo
return respuesta;                                                 // la transacción confirma recién ACÁ
```

Línea de tiempo:

```
hilo de la petición                          hilo del pipeline (pool)
───────────────────                          ────────────────────────
save(evento)  → fila SIN CONFIRMAR
procesarAsync()  ─────────────────────────►  arranca
                                             busca el evento por id...
                                             ¡NO LO ENCUENTRA! (la fila es invisible
                                              fuera de la transacción que no confirmó)
                                             → RecursoNoEncontradoException
commit  ← recién ahora la fila es visible    → el evento termina RECHAZADO_SISTEMA / ERROR_PIPELINE
```

Y "no siempre": **depende de qué hilo gane la carrera**, que es *"la peor clase de error posible: funciona en las pruebas y falla en producción, o al revés"*.

### 4.2 La solución: publicar un evento y esperar al commit

**Paso 1 — publicar** (`EventoService.crear`):

```java
publicadorDeEventos.publishEvent(new EventoCreadoEvent(
        esqueleto.getIdEvento(), datos, imagenes, emailOrganizador));
```

`EventoCreadoEvent` es un *record* (A02 §8) con todo lo que el pipeline necesita. `ApplicationEventPublisher` es el bus interno de Spring.

**Paso 2 — escuchar, pero solo después del commit** (`ModeracionEventoService`):

```java
@Async("ejecutorEnexia")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void alCrearseElEvento(EventoCreadoEvent aviso) {
    procesar(aviso.idEvento(), aviso.datos(), aviso.imagenes(), aviso.emailOrganizador());
}
```

**Dos anotaciones, dos preguntas distintas (están las dos y son imprescindibles):**

| Anotación | Decide | Sin ella… |
|---|---|---|
| `@TransactionalEventListener(AFTER_COMMIT)` | **CUÁNDO**: tras confirmar la transacción. | Un `@EventListener` común correría en el instante de publicar, con la fila aún sin confirmar (la carrera de §4.1). |
| `@Async("ejecutorEnexia")` | **EN QUÉ HILO**: el pool. | El listener correría en el hilo de la petición: el organizador esperaría toda la moderación y se perdería el sentido de ser asíncrono. |

`fallbackExecution = true` hace que corra **aun sin transacción** (en los tests unitarios no hay): sin eso, el evento se publicaría y **nunca se procesaría, en silencio**.

El método `procesar(...)` es **público y sin anotaciones a propósito**: así las pruebas lo invocan directo, sin montar Spring.

### 4.3 La lección general

> **Cuando un trabajo en otro hilo depende de datos que escribe tu transacción, no lo dispares dentro de la transacción: dispará un evento y procesalo *después del commit*.**

Esto vale tanto para el pipeline de moderación como para **los correos**.

---

## 5. Correo después del commit

Mandar un mail dentro de una transacción tiene dos problemas: si la transacción **se revierte**, el mail ya salió (aviso falso); y mientras el mail **tarda**, la transacción retiene bloqueos. `EmailService` lo resuelve con **sincronización transaccional**:

```java
private void programar(Runnable tarea) {
    Runnable contenida = () -> {
        try {
            tarea.run();
        } catch (RuntimeException ex) {
            log.error("Fallo inesperado en una tarea de correo", ex);   // nunca propaga
        }
    };

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                encolar(contenida);          // solo si la transacción CONFIRMÓ
            }
        });
    } else {
        encolar(contenida);                  // no hay transacción: se encola ya
    }
}
```

- `TransactionSynchronization` es un *gancho* con métodos como `afterCommit()`. Se **registra** durante la transacción y se **ejecuta** al confirmar. Es lo que hay debajo de `@TransactionalEventListener`.
- `new TransactionSynchronization() { ... }` es una **clase anónima**: crea y usa en el acto una implementación de una interfaz sin darle nombre.
- La tarea va a `ejecutorCorreo` (otro hilo): *"el intento de login que dispara el bloqueo no debe tardar más ni delatar por tiempo que la cuenta existe"*.
- **Nunca propaga:** que un correo falle no puede tumbar el login ni la creación de un evento.

Resultado para el servicio de negocio: llama `emailService.enviarCuentaSuspendida(...)` **dentro** de su transacción, con total tranquilidad; el envío real ocurre **después y aparte**.

---

## 6. Idempotencia: mandar un aviso exactamente una vez

### 6.1 El problema

El recordatorio "tu evento empieza en 10 minutos" lo dispara **un reloj cada minuto**. Una inscripción estaría dentro de la ventana unos **diez ticks seguidos**: sin cuidado, el participante recibiría **diez correos**. Además, la invitación a valorar la pueden disparar **dos caminos** distintos de finalización.

**Idempotencia:** hacer la operación una vez o muchas veces produce el mismo resultado. Se consigue con un **libro de correos** (`notificacion_enviada`) y una restricción `UNIQUE (tipo, id_usuario, id_referencia)`:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public Optional<Long> reservar(NotificacionTipo tipo, Long idUsuario, Long idReferencia) {
    if (idReferencia != null
            && repository.existsByTipoAndIdUsuarioAndIdReferencia(tipo.name(), idUsuario, idReferencia)) {
        return Optional.empty();                  // ya se mandó: no hacer nada
    }
    return Optional.of(guardar(tipo, idUsuario, idReferencia));   // reservar (INSERT)
}
```

- Primero **reserva** (INSERT en el libro), después **manda**, después **registra el resultado** (`ENVIADO`/`FALLIDO`).
- **El `UNIQUE` es la defensa real**, el `exists` solo ahorra un INSERT: bajo concurrencia dos hilos pueden pasar ambos el `exists` y solo el índice frena al segundo (la misma lección de B03 §7.2).
- Si el segundo choca con el `UNIQUE`, la `DataIntegrityViolationException` **sale hacia afuera a propósito**: capturarla dentro dejaría la transacción *marcada como rollback-only* y el commit fallaría con un error más confuso. Quien llama la atrapa **afuera**.
- **Por qué `REQUIRES_NEW`:** quien llama corre en un hilo de fondo, y se quiere que una reserva confirmada **no dependa de nada ajeno**.

### 6.2 Dos tipos de avisos

`EmailService` distingue (A02 §13, DFD N.5):
- **`despachar`**: aviso por **una acción puntual de una persona** (moderación, equipo, bloqueo): sin libro, porque no hay un reloj que repita el disparo.
- **`despacharUnico`**: avisos que **pueden repetirse** (recordatorio, invitación, inscripción confirmada): con libro.

Y el **límite por destinatario** (`reservarConLimite`: 3 en 24 h, separados por 2 minutos) usa el mismo libro **contando filas recientes**, no por IP.

### 6.3 Honestidad sobre los límites

El javadoc de `reservarConLimite` reconoce: *"bajo concurrencia dos pedidos simultáneos pueden pasar ambos el conteo, de modo que el tope puede excederse en uno. Es un freno al abuso, no una garantía contable, y el costo de cerrar esa ventana no lo justifica."* Saber **qué garantía necesitás de verdad** evita sobre-ingeniería: acá importa frenar el abuso, no contabilizar al centavo.

---

## 7. Tareas programadas con `@Scheduled`

(La anotación se vio en B01 §8; acá, el diseño.)

### 7.1 Ventana, no igualdad exacta

`RecordatorioEventoService` busca funciones cuyo inicio esté **entre ahora y ahora + 10 minutos**, **no** "exactamente dentro de 10 minutos". Razón (DFD N.3): *"si el Scheduler pierde un tick (reinicio, carga), el recordatorio igual sale en el siguiente. Una comparación de igualdad exacta lo perdería para siempre."* Una ventana + idempotencia (§6) es el par que hace **robusto** a un proceso periódico.

Y una advertencia de configuración: *"el intervalo tiene que ser menor que el adelanto, o una función puede empezar entre dos pasadas y no avisarse"*.

### 7.2 Cada tick atrapa sus errores

```java
@Scheduled(...)
public void avisar() {
    try {
        servicio.avisarProximos(LocalDateTime.now(zona));
    } catch (RuntimeException ex) {
        log.error("Fallo el recordatorio de inicio de eventos", ex);   // un fallo no cancela los ticks siguientes
    }
}
```

### 7.3 La zona horaria

Las funciones se guardan como **fecha y hora locales sin zona**. Un servidor en UTC con `LocalDateTime.now()` estaría **3 horas adelantado** respecto de Tierra del Fuego. Por eso el recordatorio pide la hora explícitamente: `LocalDateTime.now(zona)` con `zona = ZoneId.of("America/Argentina/Ushuaia")` (propiedad `enexia.zona-horaria`).

> **Deuda conocida que sigue abierta** (`SUGERENCIAS_PENDIENTES.md`, E1): `FinalizacionEventoScheduler` todavía usa `LocalDateTime.now()` (la zona de la JVM) y en un servidor UTC finalizaría los eventos 3 horas antes. **Hay que corregirlo antes del primer despliegue.**

### 7.3.1 Un bean aparte por tarea (de nuevo, el proxy)

`FinalizacionEventoScheduler` solo **dispara**; la lógica con transacción está en `FinalizacionEventoService`, cuya `finalizar(id)` se ejecuta **una vez por evento a través del proxy**, cada una en **su propia transacción**: un evento que falla no deshace a los que ya se finalizaron.

### 7.4 Apagar una tarea

`@ConditionalOnProperty(... havingValue = "true", matchIfMissing = true)` (B01 §5.4): útil en una prueba que no quiere que el reloj le cambie los estados de debajo.

---

## 8. El patrón de contención de fallos

Todo trabajo en segundo plano de Enexia sigue la misma disciplina:

1. **Nunca propaga** una excepción al llamador (no hay nadie que la reciba).
2. **Siempre registra** (`log.error(..., ex)`).
3. **Traduce el fallo a un estado visible** cuando es de negocio (`ERROR_PIPELINE`), para que alguien pueda actuar.
4. Si hasta el manejo del fallo puede fallar (la base caída), hay **una segunda red** (`intentarRechazoPorError`) que solo registra: *"se atrapa igual para que una segunda excepción no oculte la primera"*.
5. **Falla cerrado** cuando hay seguridad de por medio (`JwtAuthenticationFilter.cuentaHabilitada`).

Y la contracara: **degradación controlada** (B01 §5.3): sin credenciales, modo simulado, para que el sistema siga funcionando.

---

## 9. Timeouts: nunca esperes para siempre

`BrevoClient` fija explícitamente:

```java
private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(5);
private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(10);
```

*"Sin ellos, un proveedor lento deja los hilos del pool colgados indefinidamente."* Con un pool de 2–3 hilos, dos correos colgados **paralizan todo el envío**. Todo llamado a un servicio externo necesita un *timeout*.

---

## 10. Probar lo asíncrono

- **Inyectar un ejecutor síncrono:** en el test de `RecuperacionCuentaService`, `Executor sincrono = Runnable::run;` hace que la "otra hebra" corra **en el mismo hilo**, así el test es determinista (A02 §6).
- **Esperar con límite, no con `sleep`:** los tests de API usan **Awaitility**:

```java
await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> assertThat(envios).anyMatch(e -> e.asunto().equals(asunto)));
```

Consulta cada 500 ms hasta que la condición se cumple o vence el plazo. El `build.gradle` explica por qué es mejor que `Thread.sleep`: *"son a la vez lentos (esperan de más casi siempre) y frágiles (a veces no esperan lo suficiente)"*.

- **Espiar sin reemplazar:** `CorreosTest` usa `@MockitoSpyBean` sobre `BrevoClient` para anotar cada envío real (destinatario, asunto, resultado) sin sustituir el cliente (B06).

---

## 11. Errores típicos

| Error | Síntoma | Remedio |
|---|---|---|
| Disparar trabajo asíncrono dentro de la transacción. | A veces no encuentra la fila (carrera). | Evento + `AFTER_COMMIT`. |
| Olvidar `@EnableAsync`. | El método corre en el mismo hilo, sin error. | Activarlo en una `@Configuration`. |
| Método `@Async` que lanza. | La excepción se pierde. | `try/catch` interno + estado visible. |
| Leer el usuario actual en el hilo de fondo. | `SecurityContext` vacío. | Pasar los datos por parámetro. |
| Mandar un correo dentro de la transacción. | Aviso de algo que se revirtió. | `afterCommit`. |
| Pool compartido entre trabajos de distinto riesgo. | Una ráfaga bloquea todo. | Un pool por compartimento. |
| Comparar con igualdad exacta en un reloj periódico. | Avisos perdidos. | Ventana + idempotencia. |
| `LocalDateTime.now()` en un servidor UTC. | Horarios corridos. | `now(zona)` explícita. |
| Llamada externa sin timeout. | Hilos colgados. | Timeouts de conexión y lectura. |

---

## 12. Para practicar

1. Dibujá la línea de tiempo de la creación de un evento **con** el diseño final (evento + `AFTER_COMMIT`): ¿en qué momento exacto arranca el pipeline respecto del commit?
2. Con `corePoolSize=2`, `maxPoolSize=3` y `queueCapacity=200` (el pool de correo), ¿cuántas tareas acepta antes de rechazar? ¿Y cuántos hilos tiene trabajando cuando entran las primeras 100?
3. Si el recordatorio usara una igualdad exacta (`inicio == ahora + 10 min`) y el servidor se reiniciara durante ese minuto, ¿qué pasaría? ¿Y con la ventana + libro?
4. ¿Qué ocurriría si `NotificacionService.reservar` capturara la `DataIntegrityViolationException` dentro del método `@Transactional`? Pensá en el estado de la transacción.
5. Cambiá (en un test) `enexia.notificaciones.recordatorio.segundos-antes` a 30 e `intervalo-ms` a 5000 e inscribite a una función que empiece en 25 segundos. Observá el log.

**Siguiente capítulo:** [B06 — Testing](./B06_testing.md)
