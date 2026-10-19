# D6 — Módulo 6: Panel de administración

> **Parte D · Los módulos.** El **panel de control** de la plataforma: el administrador modera eventos (RF-6.1), gestiona cuentas (RF-6.2), el catálogo de categorías (RF-6.3) y audita suscripciones (RF-6.4). Es el módulo donde mejor se ve **un patrón repetido cuatro veces**: *acción pedida por el cliente → bloqueo de fila → validación de transición → escritura + historial + auditoría → mensaje decidido por el backend*. Completo desde el 2026-10-15; el desbloqueo de cuentas bloqueadas es **exclusivo de este panel** desde el 2026-10-19.

**Requisitos que cubre** (`requisitos_funcionales/modulo_6.md`)

| RF | Pantalla | Servicio |
|---|---|---|
| RF-6.1 Moderación manual de eventos | `moderacion-eventos.html` | `AdminEventoService` |
| RF-6.2 Gestión de cuentas | `usuarios.html` | `AdminUsuarioService` |
| RF-6.3 ABM de categorías | `categorias.html` | `AdminCategoriaService` |
| RF-6.4 Intervención de suscripciones | `suscripciones.html` | `AdminSuscripcionService` |

DFD (todos alineados con el código): [`modulo_6_admin/`](../diagrams/modulo_6_admin/nivel1_admin.md). Pendiente: [6.5 Otorgar el rol ADMINISTRADOR](../tempDFD/modulo_6_admin/6.5_otorgar_rol_administrador.md).

---

## 1. El patrón común de las cuatro intervenciones

```
PATCH /api/admin/<recurso>/{id}        { "accion": "..."  ,  motivo/observación opcionales }
   @PreAuthorize("hasRole('ADMINISTRADOR')")    +   SecurityConfig: /api/admin/** → ADMINISTRADOR
   AdminXxxService.metodo()       @Transactional(isolation = READ_COMMITTED)
     1. resolver al administrador (404 si no)
     2. bloquear la fila objetivo      (SELECT ... FOR UPDATE)            ← B03 §7.2
     3. ¿es gestionable? ¿la transición es válida desde el estado ACTUAL? → 409 si no
     4. aplicar el cambio (nunca DELETE)
     5. escribir HISTORIAL  (quién, cuándo, motivo)  +  AUDITORÍA  (acción, detalle, IP)
     6. (si corresponde) encolar un CORREO — se envía después del commit             ← B05 §5
     7. devolver { mensaje, fila actualizada }    ← el TEXTO lo decide el backend
```

### 1.1 Decisiones que se repiten (ADR-19, 23, 24)

| Decisión | Por qué |
|---|---|
| **El cliente pide una ACCIÓN, no un estado destino** (`SUSPENDER`, `REACTIVAR`, `DESACTIVAR`, `REVOCAR`, `CAMBIAR_PLAN`...). | Si el cliente mandara "estado = ACTIVO", habría que confiar en que conoce el estado actual. El servicio **resuelve origen y destino con lo que hay en la base** y valida la transición (**409 si ya está en ese estado**). La acción se valida con `@Pattern(regexp = "^(SUSPENDER\|REACTIVAR)$")` en el DTO (A04 §2.4). |
| **Bloqueo pesimista + `READ_COMMITTED`.** | Otro administrador, el login (contador de intentos) o el pipeline pueden tocar la misma fila a la vez. El bloqueo hace que **la segunda espere su turno y lea el estado ya actualizado**; con la instantánea por defecto de MariaDB 11.6+ abortaría con *"Record has changed since last read"*. |
| **El mensaje lo escribe el backend.** | *"El modal de confirmación solo existe con ese mensaje"* (ADR-24): la pantalla **nunca confirma algo que el servidor no aplicó**. |
| **Historial + auditoría en la misma transacción.** | O se cambia **y** queda constancia, o ninguna de las dos. |
| **El campo `gestionable` de cada fila.** | **Fuente única de verdad** de "se puede operar sobre esto": el servicio lo calcula y la pantalla **solo lo refleja** (no replica la regla). |

### 1.2 Seguridad del panel

- `/api/admin/**` exige `ADMINISTRADOR` (B04 §4.2) **y** cada método lleva `@PreAuthorize`: dos capas.
- **Una cuenta administradora es solo administradora** (`Usuario.getRoles()`, A02 §1.2): no puede inscribirse, valorar ni crear eventos. Por eso `paneles.js` **no ofrece** "cambiar de panel" a un administrador (`if (Auth.hasRole('ADMINISTRADOR')) return [];`) y la ficha de evento le oculta los botones de participante.
- **Las pantallas se sirven sin token** (`/pages/admin/**` → `permitAll`: una navegación del navegador no manda `Authorization`); **los datos** siempre pasan por `/api/admin/**`, protegido.

---

## 2. RF-6.1 — Moderación manual de eventos (`AdminEventoService`)

Cubre **dos escenarios con el mismo mecanismo** (`PATCH` + decisión), distinguidos **por el estado VIGENTE del evento**, nunca por un campo que el cliente pudiera mandar mal:

| Escenario | Estado actual | Acción | Resultado |
|---|---|---|---|
| **(a) Segunda instancia** | `RECHAZADO_SISTEMA` | `APROBAR` | `APROBADO_MANUAL` (corrige al filtro automático) |
| **(b) Suspensión disciplinaria** | `APROBADO_SISTEMA` / `APROBADO_MANUAL` | `RECHAZAR` + **motivo** + **observación** | `RECHAZADO_MANUAL` |
| **(b') Revertir una suspensión** | `RECHAZADO_MANUAL` | `APROBAR` | `APROBADO_MANUAL` |

**Casos prohibidos** (→ 409): decidir sobre `EN_PROCESO` / `EN_REVISION` (*"el pipeline todavía no dictaminó"*), aprobar uno ya aprobado, rechazar uno ya rechazado o suspendido. **Ratificar un rechazo automático no es una decisión** (el evento ya está fuera del catálogo; marcarlo `RECHAZADO_MANUAL` no cambiaría nada para nadie: decidido el 2026-10-11).

### 2.1 Los datos de una suspensión

- **`MotivoSuspensionAdmin`** (enum): `DENUNCIA_FUNDADA`, `FRAUDE_DETECTADO`, `INFRACCION_POST_PUBLICACION`. Es un **enum aparte** de `MotivoModeracionEvento` a propósito: ese es el vocabulario del pipeline **automático**; mezclarlos volvería ambiguo, al leer el historial, si un rechazo fue *una decisión del software* o *una sanción manual sobre algo que ya estaba al aire*.
- **Observación obligatoria**: 10 a 500 caracteres (`OBSERVACION_MINIMA = 10`): *"evita el 'ok' o el '.' que no deja constancia de nada"*. **Solo una suspensión lleva observación**; al aprobar se **descarta** aunque el cliente la mande, para que el historial no cargue texto que nadie pidió. Se guarda en `historial_estado_evento.observacion` (migración `2026-10-11_historial_evento_observacion.sql`).
- La observación **nunca viaja en respuestas que ve el público**: el organizador la ve en `GET /{id}/moderacion` (D2).

### 2.2 El aviso al organizador (`avisarAlOrganizador`)

| Decisión | Correo |
|---|---|
| Suspender | `EVENTO_SUSPENDIDO` (con motivo y observación) |
| Aprobar sobre una **suspensión** | `EVENTO_REACTIVADO` |
| Aprobar sobre un **rechazo del sistema** | `MODERACION_RESULTADO` ("ya está publicado") — **no** "reactivado": *el evento nunca estuvo al aire* (ADR-29). |

### 2.3 Pantalla `moderacion-eventos.html`

Cola paginada con filtros (estado, texto con **debounce de 350 ms**), un **detalle** (`GET /api/admin/eventos/{id}`, **sin registrar visita**) y modales de decisión. `pideCarga` descarta respuestas viejas si el administrador sigue tecleando (C02 §11.3).

---

## 3. RF-6.2 — Gestión de cuentas (`AdminUsuarioService`)

Acciones **`SUSPENDER`** y **`REACTIVAR`**. Reglas (todas en el servidor):

| Regla | Detalle |
|---|---|
| **SUSPENDER** parte de `ACTIVO` o `BLOQUEADO`. | Una cuenta bloqueada también se puede sancionar. |
| **REACTIVAR** parte de `SUSPENDIDO` o `BLOQUEADO` y deja `intentos_fallidos = 0`. | Si no, volvería a bloquearse al primer error de tipeo. |
| **REACTIVAR es la ÚNICA vía de desbloqueo** (decisión 2026-10-19). | El enlace de recuperación por correo solo cambia la contraseña (D1 §4). Una puerta, auditada. |
| **`DE_BAJA` no se toca.** | La baja es decisión del titular (RF-1.6). |
| **Nunca sobre otro administrador ni sobre uno mismo.** | Una suspensión por error del único administrador dejaría el panel inaccesible. |
| **Solo al suspender se avisa por correo** (`CUENTA_SUSPENDIDA`). | Reactivar no envía aviso (sugerencia C1 en `SUGERENCIAS_PENDIENTES.md`). |

```java
// El corazón de la regla de "gestionable": UNA función, usada por el listado Y por el cambio de estado
private boolean esGestionable(Usuario objetivo, Usuario administrador) {
    if (objetivo.getIdUsuario().equals(administrador.getIdUsuario())) return false;
    if (objetivo.getRoles().contains(RolNombre.ADMINISTRADOR.name())) return false;
    String estado = objetivo.getEstadoUsuario() == null ? null : objetivo.getEstadoUsuario().getEstadoUsuario();
    return !EstadoUsuarioNombre.DE_BAJA.name().equalsIgnoreCase(estado);
}
```

### 3.1 El listado con paginación en **dos pasos** (patrón importante)

`listar` **no** trae las entidades paginadas directamente. Hace:

1. `buscarIdsParaAdmin(...)` → `Page<Long>`: **solo los ids** de la página (paginar sobre una consulta con `JOIN FETCH` de colecciones es peligroso: Hibernate pagina **en memoria**).
2. `cargarParaAdmin(ids)` → las entidades **con sus relaciones** de esos ids en **una** consulta.
3. `collect(toMap(Usuario::getIdUsuario, u -> u))` y **reordenar según el orden de los ids** (*"el IN no garantiza orden"*).
4. Un `Map<Long, Long> eventosPorUsuario` (conteo en lote) para evitar N+1.

(A03 §3.3, B03 §2.5: una de las mejores lecciones de rendimiento del proyecto.)

### 3.2 Pantalla `usuarios.html`

Tarjetas de resumen (`GET /resumen`), tabla paginada con búsqueda por texto (con *debounce*) y filtro por estado (`ACTIVO`, `SUSPENDIDO`, `BLOQUEADO`, `DE_BAJA`), y **la acción de cada fila según su estado**: ACTIVO → "Suspender"; SUSPENDIDO **o BLOQUEADO** → "Reactivar" (el comentario de cabecera: *"BLOQUEADO → Reactivar (bloqueo por intentos fallidos; el admin…)"*). Modal de confirmación y toasts. Los textos de usuario se escapan con una función local `esc` (C02 §12).

---

## 4. RF-6.3 — ABM de categorías (`AdminCategoriaService`)

**La baja es lógica y reversible** (`categoria.activa`, ADR-22). El requisito pedía "evitar huérfanos"; el DFD original decía *"409 si tiene eventos"*, lo que habría vuelto **imposible retirar casi cualquier categoría**. Desactivarla resuelve ambas cosas: **deja de ofrecerse** para eventos nuevos y en los filtros del catálogo (`findByActivaTrueOrderByNombreCategoriaAsc`), y **los eventos existentes conservan su categoría**.

| Operación | Endpoint | Reglas |
|---|---|---|
| Alta | `POST /api/admin/categorias` | Nombre **único sin distinguir mayúsculas ni espacios de más** (`normalizar`: `trim().replaceAll("\\s+", " ")`) → 409 si ya existe. |
| Renombrar | `PUT /api/admin/categorias/{id}` | Único contra **las demás** (`existsByNombreCategoriaIgnoreCaseAndIdCategoriaNot`). |
| Desactivar / Activar | `PATCH /api/admin/categorias/{id}/estado` | Ya en ese estado → 409. **No se puede desactivar la última categoría activa** (*"los organizadores no tendrían dónde clasificar"*). |
| Listar | `GET /api/admin/categorias` | Cada fila con **cuántos eventos publicados y totales** (dos `Map<Long, Long>` en lote). |

Conceptos: `Optional.isPresent`, métodos derivados con `IgnoreCase` y `...AndIdCategoriaNot`, `mapToLong(...).sum()` y `filter(Categoria::isActiva).count()` (A03).

**Dato de migración:** `docs/diseño_bd/migraciones/2026-10-15_categoria_activa.sql` agrega `activa BIT(1) NOT NULL DEFAULT 1`: el **`DEFAULT 1` es obligatorio**, sin él las categorías existentes quedarían inactivas y el catálogo se vaciaría (B03 §4).

**Lección del arranque:** `DatosInicialesConfig` sembraba categorías "las que falten por nombre" en cada arranque; con un ABM, renombrar una la **resucitaba** al reiniciar. Ahora solo siembra con la tabla **vacía** (B01 §7).

---

## 5. RF-6.4 — Intervención de suscripciones (`AdminSuscripcionService`)

### 5.1 Qué muestra

- **Resumen**: suscripciones Pro activas, vencidas, **ingresos del mes** (`sumarIngresosPorSuscripciones`) y **dos tarjetas de plan** (Gratuito / Pro) con usuarios activos y precio.
- **Listado** de organizadores con su **plan vigente**: **el de la última suscripción; sin suscripciones, Gratuito** (ADR-26). **Facturación (mensual/anual) y monto se deducen** (duración ≥ 300 días → anual; precio Pro de configuración), porque el modelo no guarda el ciclo de cobro.
- Los contadores cuentan **solo cuentas `ACTIVAS`** (un organizador bloqueado, suspendido o dado de baja no es "usuario activo del plan").

### 5.2 Las tres acciones

| Acción | Se puede sobre | Resultado |
|---|---|---|
| `REVOCAR` | `ACTIVA` o `VENCIDA` | `CANCELADA`; el organizador queda con el plan Gratuito. |
| `CAMBIAR_PLAN` | solo `ACTIVA` | conmuta `tipo_plan` FREE ↔ PRO (exige indicar el plan; si ya lo tiene → 409). |
| `RENOVAR` | solo `VENCIDA` **PRO** | `ACTIVA` con cobertura nueva **desde hoy**, de la misma duración (anual si duraba ≥ 300 días). **Omite la pasarela a propósito y no genera `Pago`**: es una excepción administrativa, no es ingreso. |

Un `switch` con flechas (A01 §8.3) despacha las tres acciones; la rama `default` lanza `ReglaNegocioException`. Toda intervención guarda **historial con motivo** (por defecto uno estándar si no se manda; recortado a 255) y **auditoría**.

### 5.3 Decisión del 2026-10-19 sobre el producto

**Ningún plan limita la cantidad de eventos.** Se eliminó el tope del código (`EventoService.verificarLimiteDePlan`), de la configuración y del texto del panel ("Plan Gratuito: **Eventos ilimitados**, estadísticas básicas por cronograma"; "Plan Pro: **Eventos ilimitados**, estadísticas avanzadas por cronograma"). Lo que el **Módulo 8** agregará son las **estadísticas avanzadas por cronograma**: ver D8 y `tempDFD`.

---

## 6. El frontend del panel: cuatro pantallas, una estructura

Cada `pages/admin/*.html` comparte **la misma anatomía**:

```
┌──────────┬───────────────────────────────────────────────────┐
│ sidebar  │  encabezado + tarjetas de resumen                  │
│ (4 ítems)│  filtros (texto con debounce, estado)              │
│          │  tabla con filas  (acción según estado)            │
│          │  paginación                                         │
└──────────┴───────────────────────────────────────────────────┘
   modales (.modal-velo): confirmar acción · confirmación · formularios
   .toast-pila  (aria-live="polite")
```

| Pieza | Técnica |
|---|---|
| **Sidebar** | `<nav class="sidebar__nav">` con 4 enlaces; el ítem activo con una clase modificador. `paneles.js` agrega "Cambiar de panel" (**no** a los administradores). |
| **Tarjetas de resumen** | Una llamada `GET .../resumen`; números pintados con `textContent`. |
| **Tabla** | Cada fila es un `<div class="tabla-fila">` (CSS Grid), creada con `createElement` + `innerHTML` **escapando** (`esc`). El botón de acción se arma según `u.estado` y `u.gestionable`. |
| **Modales** | Un `.modal-velo` oculto (`display: none`) que se **muestra con la clase `--visible`** (`display: flex`). Atributos **accesibles**: `role="dialog"`, `aria-modal="true"`, `aria-labelledby`. Cada modal **guarda el objetivo** en una variable y el botón "Confirmar" llama a la API. |
| **Confirmación** | *"Solo se abre con el mensaje que devolvió el backend"* (ADR-24). |
| **Toasts** | Pila `aria-live="polite"`; se autodescartan con `setTimeout`. |
| **Errores** | `UI.mensajeDeError` + toast; el `403` y `409` muestran el mensaje del backend. |

**Reglas de diseño** (C03 §6.5): paleta clara con tokens, **esquinas cuadradas** (`--radio-tarjeta`, `--radio-campo`; un incidente del 2026-10-16 corrigió pantallas que usaban `10px`, `999px` y hasta `--radio-tarjeta: 16px` local).

**Cuenta de desarrollo:** la cuenta `admin_enexia` se creó **por SQL** respetando las relaciones (documentado en `docs/log/sprint_2/2026-10-14_cuenta_admin_dev.md`): **no hay forma de otorgar el rol desde la aplicación** (DFD 6.5 propuesto).

---

## 7. Pruebas

| Clase | Qué protege |
|---|---|
| `AdminEventoServiceTest` (19 casos) | La matriz de transiciones; observación obligatoria; **aprobar descarta la observación**; `EN_PROCESO` no admite decisión; ratificar → 409; cada correo. |
| Servicios de usuarios, categorías y suscripciones | Transiciones válidas/inválidas, protegidas (admin, propia, `DE_BAJA`), última categoría, las tres acciones y sus requisitos. |
| API (verificación manual del cierre del módulo) | Documentada en `docs/log/sprint_2/2026-10-14_15_panel_admin_completo_y_correcciones.md`. |

---

## 8. Límites y pendientes

1. **No hay forma de crear administradores desde la aplicación** (DFD 6.5).
2. **Reactivar una cuenta no avisa al titular** (C1) y **no hay canal** para que un bloqueado pida el desbloqueo (C2).
3. **Un administrador bloqueado solo se recupera por la base de datos** (C3).
4. **No hay un reporte de auditoría** en pantalla (la tabla `historial_interacciones` existe; falta una vista).
5. Las pruebas automáticas del panel de **usuarios, categorías y suscripciones** son más acotadas que las de eventos.

---

## 9. Conceptos aplicados

| Concepto | Dónde |
|---|---|
| Acción en vez de estado; transición validada | §1.1, A02 |
| Bloqueo pesimista + `READ_COMMITTED` | B03 §7.2 |
| Paginación en dos pasos (ids → entidades) | §3.1, B03 §2.5 |
| `switch` con flechas, `Optional`, `Map`/`Set` | A01, A03 |
| Dos enums para dos vocabularios | §2.1, A02 §7 |
| Modales accesibles, `aria-*`, tokens de forma | C03 §3, §6 |
| Debounce y descarte de respuestas viejas | C02 §11.3 |

---

## 10. Para practicar

1. Dibujá la **matriz de decisiones** de `AdminEventoService` (estados en filas, acciones en columnas) marcando qué celdas dan 200 y cuáles 409.
2. Un administrador intenta **suspender su propia cuenta** desde `curl`. ¿Qué código y mensaje obtiene? ¿Qué capa lo frena? ¿Aunque el botón no exista?
3. ¿Por qué `listar` pagina **ids** y no entidades? Reproducí el problema (en papel): `JOIN FETCH` de una colección + `Pageable`.
4. Implementá (en papel) la acción `CAMBIAR_PLAN` de modo que, si el nuevo plan es igual al actual, devuelva 409 con un mensaje claro. ¿Cuál es el `if`?
5. Agregá un cuarto estado de suscripción (p. ej. `EN_GRACIA`): listá **todos los lugares** (enum, catálogo, migración, servicio, pantalla, tests) que habría que tocar.

**Siguiente capítulo:** [D7 — Módulo 7: Organizaciones y equipos](./D7_modulo_7_organizaciones.md)
