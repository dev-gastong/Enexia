# D3 — Módulo 3: Participación (inscripción, pago, cancelación y valoración)

> **Parte D · Los módulos.** Un **participante** se anota a una función de un evento, paga (de forma simulada), puede cancelar, ve su historial y, **después de asistir, valora**. Es el módulo con los problemas de **concurrencia y consistencia** más finos: **cupos que no se pueden sobrevender**, un **pago rechazado que debe persistir** sin revertir nada, y un **ciclo de estados** que alimenta el historial y las valoraciones.

**Requisitos que cubre** (`requisitos_funcionales/modulo_3.md`)

| RF | Resumen |
|---|---|
| RF-3.1 | Inscripción a un **ticket de un cronograma**, con control de cupo. |
| RF-3.2 | Pago **simulado** para tickets no gratuitos. |
| RF-3.3 | Cancelación voluntaria, con reembolso simulado. |
| RF-3.4 | **Valoración de un cronograma** ya finalizado (1 a 5 + comentario). |
| RF-3.5 | Moderación **síncrona** del comentario. |
| RF-3.6 | Historial centralizado de inscripciones y asistencia. |

DFD: [`modulo_3_participacion/`](../diagrams/modulo_3_participacion/nivel1_participacion.md) (3.1, 3.3, 3.4/3.5, 3.6).

---

## 1. Modelo de datos

```
Usuario ──< Inscripcion >── Cronograma_Ticket >── Evento_Cronograma >── Evento
   │            │  (fecha_inscripcion,             (precio, cupo_maximo,   (fecha, hora_inicio, hora_fin)
   │            │   precio_abonado)                 cupo_actual)
   │            ├── Inscripcion_Estado   PENDIENTE · PENDIENTE_PAGO · CONFIRMADA · CANCELADA
   │            ├── Historial_Estado_Inscripcion
   │            └── Pago (1:1, opcional) ── Pago_Estado  COMPLETADO · FALLIDO · REEMBOLSADO
   │
   └──< Valoracion >── Evento_Cronograma        UNIQUE (id_usuario, id_cronograma)
```

Puntos de diseño:

- **La inscripción apunta a un ticket** (no a un evento): el participante elige **qué entrada de qué función**. De ahí se llega al cronograma y al evento.
- `Inscripcion.precioAbonado` **copia el precio** al momento de inscribirse: si el organizador cambia el precio después, el histórico no se altera (dato histórico, igual que una factura).
- `Cronograma_Ticket.cupo_actual` es un **contador desnormalizado** (cuántos hay confirmados). Se mantiene **bajo bloqueo de fila** (§3). Una consulta de control puede compararlo con el `COUNT` de inscripciones `CONFIRMADA`.
- **`Valoracion` apunta a un `Evento_Cronograma`, nunca a un evento** (ver §6, validación del 2026-10-19).

---

## 2. Máquina de estados de una inscripción

```
                  ┌──────────── precio = 0 ───────────────┐
(alta) → PENDIENTE ─┤                                      ├──► CONFIRMADA ──► CANCELADA
                  └──── precio > 0 ──► pasarela ─ aprobado ┘      (suma 1 al cupo)   (libera 1 cupo
                                          │                                            + reembolso)
                                          └─ rechazado ──► PENDIENTE_PAGO ──► CANCELADA
                                                            (queda guardada y visible;
                                                             NO suma cupo)
```

| Estado | Suma al cupo | Se puede valorar | Notas |
|---|---|---|---|
| `PENDIENTE` | no | no | Transitorio dentro de la misma transacción. |
| `PENDIENTE_PAGO` | no | no | La pasarela rechazó: el intento **queda guardado** para que el participante lo vea y reintente. |
| `CONFIRMADA` | **sí** | **sí** (cuando el cronograma terminó) | Gratuito directo, o pago aprobado. |
| `CANCELADA` | no | no | Borrado lógico; la fila y su historial permanecen. |

---

## 3. Inscribirse (RF-3.1 / RF-3.2): el flujo con sus trampas

### 3.1 Dos servicios para una operación

```
InscripcionController.inscribir()   POST /api/participante/inscripciones   { idCronogramaTicket, metodoPago }
   InscripcionService.inscribir()          ← SIN @Transactional
        │  1. buscar al participante
        │  2. registroInscripcionService.registrar(...)   ← OTRO BEAN: cruza el proxy
        │         @Transactional(READ_COMMITTED)  — UNA transacción que SIEMPRE confirma
        │  3. con esa transacción ya cerrada: ¿pago rechazado? → lanzar PagoRechazadoException (402)
        │  4. si aprobó: correo INSCRIPCION_CONFIRMADA (después del commit)
   RegistroInscripcionService.registrar()
        3.1.1  bloquearParaActualizar(ticket)   ← SELECT ... FOR UPDATE
               · ¿la función ya empezó?            → 409 "las inscripciones ya cerraron"
               · ¿ya tiene inscripción activa en ESTE cronograma?  → 409 duplicado
               · ¿cupoActual >= cupoMaximo?        → 409 "no quedan cupos"
        3.1.2  INSERT inscripción PENDIENTE + historial + auditoría
        precio = 0  → confirmar()  (CONFIRMADA, cupoActual + 1)
        precio > 0  → procesarPago():  pasarela.procesar(monto, metodoPago)
                          aprobado  → Pago COMPLETADO + confirmar()
                          rechazado → Pago FALLIDO + inscripción PENDIENTE_PAGO   (¡y commit!)
```

### 3.2 El problema del pago rechazado (el diseño más sutil del módulo)

El DFD pide **dos cosas a la vez**: que el intento fallido **quede guardado** (para que el participante lo vea) **y** que la petición HTTP responda **402**. Pero en Spring **lanzar una excepción desde un método `@Transactional` revierte TODO** lo que esa transacción escribió, inscripción incluida. Las dos cosas chocan.

Las opciones y por qué se descartó cada una (del javadoc de `RegistroInscripcionService`):

1. **`REQUIRES_NEW` al estilo de la auditoría.** ❌ La transacción nueva necesita leer la fila de `Inscripcion` recién creada para insertar el `Pago` que la referencia por clave foránea, **pero esa fila está bloqueada (sin confirmar) por la transacción original**: **interbloqueo**.
2. **Lanzar y no persistir el rechazo.** ❌ Se pierde el intento.
3. **✅ La solución adoptada:** resolver toda la reserva en **una sola transacción que siempre confirma** y **no lanza por un pago rechazado** (*"es una salida de negocio válida, no un error"*). El veredicto vuelve en un **valor de retorno** (`ResultadoRegistro`, un *record*: A02 §8). Recién **después**, con la fila ya confirmada, `InscripcionService.inscribir` decide si lanza `PagoRechazadoException` (→ 402). Para entonces **no hay nada que revertir**.

Esto es posible porque `InscripcionService` **no es `@Transactional`** y llama a `RegistroInscripcionService` (**otro bean**, así que la llamada cruza el proxy: B03 §6.6).

> **Patrón general:** *"una salida de negocio válida no debe modelarse como una excepción dentro de la misma transacción que quiere conservar lo escrito"*. Se devuelve un resultado y se decide **afuera**.

### 3.3 Sobrevender: el cupo y la concurrencia

Dos personas apretando "Inscribirme" a la vez sobre el **último** cupo:

```
Sin bloqueo:    A lee cupo 99/100     B lee cupo 99/100     → los dos confirman → 101/100 ❌
Con bloqueo:    A: SELECT ... FOR UPDATE (toma la fila)
                B: SELECT ... FOR UPDATE → ESPERA
                A confirma (100/100) y suelta
                B lee 100/100 → "No quedan cupos" ✅
```

`ticketRepository.bloquearParaActualizar` (`@Lock(PESSIMISTIC_WRITE)`) + **`READ_COMMITTED`** (B03 §7.2): el segundo **espera su turno y lee el estado ya actualizado**. Con `REPEATABLE READ` y el modo de aislamiento por instantánea de MariaDB 11.6+, la segunda transacción abortaría con *"Record has changed since last read"* (error real que costó diagnosticar al actualizar la base: B03 §4).

### 3.4 Las reglas, y de dónde sale cada mensaje

| Regla | Excepción → HTTP |
|---|---|
| El ticket no existe | `RecursoNoEncontradoException` → 404 |
| La función **ya empezó** (`!inicio.isAfter(ahora)`) | `OperacionNoPermitidaException` → 409 |
| Ya tiene una inscripción **activa en ese cronograma** | `RecursoDuplicadoException` → 409 |
| Sin cupo | `OperacionNoPermitidaException` → 409 |
| La pasarela rechazó | `PagoRechazadoException` → **402** |

**Por qué se compara contra `hora_inicio` y no `hora_fin`** (comentario del código): *"dejar sumarse a un evento en curso permitía inscribirse y, para cuando la función terminaba, calificar con una inscripción que nunca correspondió a haber asistido desde el principio"*. Es una regla que **protege la integridad de las valoraciones**.

### 3.5 La pasarela simulada

`PasarelaPagoSimuladaService.procesar(monto, metodoPago)` devuelve un `ResultadoPago` (record con fábricas estáticas `aprobado(token)` / `rechazado(token, motivo)`). **Aprueba por defecto**; si el método de pago contiene `"rechazar"` (sin importar mayúsculas) **rechaza**: es el gancho para probar la rama de error sin un tercero (mismo patrón que `CloudinaryService`). Genera un `UUID` como token de operación, que se guarda en `Pago.token_operacion` **aprobado o no**, para correlacionar el intento en los logs.

---

## 4. Cancelar (RF-3.3)

`DELETE /api/participante/inscripciones/{id}` → `InscripcionService.cancelar` (`@Transactional`, `READ_COMMITTED`).

1. **Ajena = 404** (no 403): el DFD documentaba 403, pero *"confirmar que una inscripción ajena existe permite mapear la plataforma iterando ids"*. Mismo criterio que `EventoService.buscarPropio`.
2. Ya cancelada → 409.
3. **No se cancela una función que ya empezó** (mismo umbral que la inscripción).
4. **Bloqueo de fila del ticket** (una inscripción nueva puede estar tocando el mismo cupo).
5. **Solo se libera cupo si se había sumado**: `estabaConfirmada`. Una reserva `PENDIENTE_PAGO` nunca sumó, así que cancelarla **no debe restar** (*"se liberaría un lugar que esa reserva nunca ocupó"*). `Math.max(0, cupoActual - 1)` evita un contador negativo.
6. **Reembolso simulado**: si el `Pago` estaba `COMPLETADO`, pasa a `REEMBOLSADO`. `pagoRepository.findByInscripcionIdInscripcion(...).ifPresent(pago -> {...})` es un buen ejemplo de `Optional` + lambda (A03 §4).
7. Historial + auditoría.

---

## 5. Historial (RF-3.6)

`GET /api/participante/inscripciones?pagina&tamano` → `Page<InscripcionResponse>`: **todas** las inscripciones del usuario con **su línea de tiempo de estados**. Ejemplo del aprovechamiento de **lotes** (B03 §2.5 y A03 §3.3): para la página de resultados se hacen **tres consultas** (las inscripciones; los renglones de historial de todas **agrupados por inscripción** con `Collectors.groupingBy`; los cronogramas ya valorados en un `Set<Long>`) en vez de tres por cada fila. Cada respuesta trae `yaValorado` y los datos para que la pantalla ofrezca **"Dejar puntuación"** solo cuando corresponde.

> Un test falló alguna vez por un `@Mock ValoracionRepository` ausente (B06 §10): el historial **consulta valoraciones** desde que existe la invitación a valorar.

---

## 6. Valoración (RF-3.4 / RF-3.5): **por cronograma**

### 6.1 Validación del modelo (2026-10-19)

> **Pregunta:** ¿las valoraciones son **de un cronograma** o **de un evento**? **Respuesta verificada: de un cronograma.**

| Capa | Evidencia |
|---|---|
| **Base de datos** | La tabla `valoracion` tiene `id_cronograma` y **no tiene** `id_evento`. Índice **`UNIQUE (id_usuario, id_cronograma)`** (`uk_valoracion_usuario_cronograma`). |
| **Entidad** | `Valoracion.cronograma` es `@ManyToOne` a `EventoCronograma`; el `UNIQUE` está en `@Table`. |
| **Servicio** | `ValoracionService.crear`: exige **inscripción CONFIRMADA en ESE cronograma**, que **ese cronograma haya finalizado** y que no se haya valorado **ese cronograma**. El evento nunca interviene. |
| **API y pantalla** | `ValoracionRequest.idCronograma`; la ficha calcula `puedeValorar` **cronograma por cronograma**; cada reseña muestra **a qué función** se refiere (`cronogramaFecha`). |

Lo que se ve como "promedio del evento" en la ficha es un **agregado derivado** (`ValoracionRepository.promedioDeEvento`, que navega `v.cronograma.evento.idEvento`): el promedio de todas las valoraciones de todos sus cronogramas.

### 6.2 El orden de las comprobaciones (el DFD 3.4 al pie de la letra)

```java
@Transactional
public ValoracionResponse crear(String email, ValoracionRequest datos, HttpServletRequest request) {
    // 0. participante y cronograma existen (404)
    // 3.4.1  ¿tuvo una inscripción CONFIRMADA en ese cronograma?        → 409 "Solo pueden valorar..."
    // 3.4.2  ¿el cronograma ya finalizó? (fecha + hora_fin vs ahora)    → 409 "aún no finalizó"
    // RF-3.4 ¿ya lo valoró?  existsByUsuario...AndCronograma...         → 409 "Ya valoraste este cronograma"
    // RF-3.5 moderación SÍNCRONA del comentario                         → 422 (y se audita APARTE)
    // persistir + auditoría
}
```

- **El rango 1–5 lo valida `@Valid`** (`@Min(1) @Max(5)` en `ValoracionRequest`); *no se repite en el servicio*: evita que las dos reglas puedan divergir.
- **Asistir** = tener una inscripción `CONFIRMADA`: no hay registro de asistencia real, es la mejor aproximación disponible.
- **El comentario ofensivo** (`ModeracionTextoService`) rechaza con `ContenidoInapropiadoException` (422). Se audita con `auditoriaService.registrarAparte` (`REQUIRES_NEW`) para que **el intento quede registrado aunque el `rollback` deshaga el resto**; es seguro acá porque solo referencia al usuario (fila ya confirmada), **a diferencia del pago** (§3.2).
- **Unicidad en dos niveles** (otra vez): el `existsBy...` da el mensaje amable; el `UNIQUE` frena la carrera, y el `GlobalExceptionHandler` lo traduce a *"Ya valoraste este cronograma"* por el nombre de la constraint.

### 6.3 Listado público de reseñas

`GET /api/publico/eventos/{id}/valoraciones?pagina&tamano` → `Page<ValoracionResponse>` más reciente primero, **paginado a propósito** (un evento con cientos de reseñas no puede traerlas todas) y con `@EntityGraph` para evitar N+1. Muestra el **nickname** del autor, **no** el nombre civil (privacidad).

---

## 7. Pantallas

| Pantalla | Qué hace |
|---|---|
| **`evento-detalle.html`** | Muestra la **agenda** (cronogramas) y las **"Opciones de acceso"** (tickets) con cupo disponible; el botón **"Inscribirme"** usa **delegación de eventos** (C02 §7.2), deshabilita el botón durante el pedido, y trata el **402** distinto (*"Pago rechazado. Podés intentarlo de nuevo"*). Los botones se **deshabilitan de antemano** cuando se sabe que fallarían (función finalizada, iniciada, ya inscripto, agotado), pero *"el mensaje de error del backend sigue intacto por si alguien fuerza el POST"*. También el bloque de **opiniones** con paginación y el formulario de valoración. |
| **`pages/participante/mis-inscripciones.html`** | Historial en dos listas (próximas / pasadas), con estadísticas, **"Cancelar inscripción"** y **"Dejar puntuación y reseña"** (modal) según `puedeValorar` / `yaValorado`. |

Aplicados: `fetch` con manejo de `status` (C02 §8), `UI.esc` en los textos de usuario (C02 §12), estados *cargando / vacío / error* (C02 §11.1), toasts.

---

## 8. Pruebas

| Clase | Qué protege |
|---|---|
| `RegistroInscripcionServiceTest` | Gratuito confirma y suma cupo; cupo agotado, función ya iniciada, duplicado; **pago rechazado persiste y no lanza**; ticket inexistente. *(Su fixture necesita `horaInicio`: B06 §10.)* |
| `InscripcionServiceTest` | Orquestación (**el 402 se decide DESPUÉS**), cancelación (libera solo si estaba confirmada, reembolso, ajena = 404), historial. |
| `PasarelaPagoSimuladaServiceTest` | Aprueba por defecto; rechaza con la marca. |
| `ValoracionServiceTest` | Las comprobaciones en orden, moderación con auditoría aparte, unicidad. |
| API | `EventoApiTest` recorre la creación de eventos de punta a punta con la base real (los tests de API se explican en B06 §5). |

---

## 9. Límites y pendientes

1. **El plan del organizador no influye** en la participación (módulo 8 sin construir).
2. **La asistencia es una aproximación** (`CONFIRMADA`), no un check-in.
3. **No hay reintento de pago** explícito para `PENDIENTE_PAGO`: el mensaje dice *"cancelala antes de volver a inscribirte"*. (`Pago.id_inscripcion` es 1:1.)
4. Las **notificaciones** de inscripción, cancelación de evento, recordatorio e invitación a valorar están en D9.

---

## 10. Conceptos aplicados

| Concepto | Dónde |
|---|---|
| Transacción que siempre confirma + resultado en vez de excepción | §3.2, B03 §6 |
| Bloqueo pesimista y `READ_COMMITTED` para el cupo | B03 §7.2 |
| `record` para un resultado, fábricas estáticas | A02 §8 |
| `Optional.ifPresent`, `groupingBy`, `Set` en lote | A03 §3–§4 |
| `UNIQUE` como red de seguridad | B03 §7.2 |
| 404 en vez de 403 para recursos ajenos | B04 §7.5 |
| Auditoría en `REQUIRES_NEW` y su límite (interbloqueo) | B03 §6.5 |

---

## 11. Para practicar

1. Escribí (en papel) la línea de tiempo de dos inscripciones simultáneas al último cupo, con y sin `FOR UPDATE`.
2. ¿Qué pasaría si `InscripcionService.inscribir` fuera `@Transactional` y lanzara `PagoRechazadoException`? ¿Qué quedaría guardado? ¿Y en la base?
3. Un participante tiene una inscripción `PENDIENTE_PAGO` y la cancela. ¿Se libera cupo? ¿Hay reembolso? Justificá con el código.
4. Un evento tiene dos funciones y un participante asistió a ambas. ¿Cuántas valoraciones puede dejar? ¿Y si valora la primera dos veces?
5. Diseñá el test de `ValoracionService` para "comentario ofensivo": ¿qué `verify` agregás sobre `auditoriaService` y sobre `valoracionRepository`?

**Siguiente capítulo:** [D4 — Módulo 4: Interfaz pública (catálogo, ficha y visitas)](./D4_modulo_4_catalogo_publico.md)
