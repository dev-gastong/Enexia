# Editar Evento: el "hueco real del CRUD" que quedaba pendiente (RF-2.7)

**Fecha:** 2026-09-10
**Motivo:** Backlog de [SPRINT_LOG.md](../SPRINT_LOG.md), ítem 6 ("RF-2.7 / el UPDATE de eventos — hueco real del CRUD... Es el próximo trabajo de código")
**Resultado:** ✅ `PUT /api/organizador/eventos/{id}` implementado y probado · ✅ `GET /api/organizador/eventos/{id}` (precarga) · ✅ 168 tests en verde (0 regresiones) · ✅ primer HTML del panel de organizador: `evento-form.html`

---

## 1. Por qué no alcanzaba con copiar el flujo de `crear()`

El alta de un evento (RF-2.1 a RF-2.6) no persiste contenido: crea un "skeleton" en `EN_PROCESO` y recién `PublicacionEventoService.aprobarYPersistir()` escribe `EventoDetalle`/`Ubicacion`/`Cronograma`/`Tickets`/`Multimedia`, y solo si la moderación asíncrona aprueba. Si se copiara ese mecanismo tal cual para editar, un evento **ya publicado** desaparecería del catálogo mientras se re-modera — el modelo ya tenía previsto ese problema: `EstadoEventoSistemaNombre.EN_REVISION` existe desde Sprint 2 con el comentario *"la version anterior sigue en el catalogo mientras se remodera el borrador"*, pero nada lo usaba nunca.

Para sostener esa promesa de verdad hacía falta una tabla de borradores que **no existe en el MER**, y crearla era un alcance mucho mayor al pedido. Se optó por un camino distinto y más simple:

## 2. 💡 ADR-15 — Editar es síncrono; crear sigue siendo asíncrono

**Decisión:** `EventoService.editar()` corre la moderación (texto + Cloudinary) **dentro de la misma transacción**, antes de escribir una sola fila. Si el texto o todas las imágenes se rechazan, se lanza una excepción y la transacción entera revierte: la versión publicada queda **exactamente como estaba**, sin ningún estado intermedio visible.

**Por qué no rompe la RNF de 4s para escrituras (CLAUDE.md):** crear es asíncrono porque no hay nada que mostrar todavía y el organizador puede esperar mirando "Validando...". Editar es lo opuesto: el evento **ya está publicado**, y bloquear la respuesta es justamente lo que permite no tocar la versión vigente hasta tener el veredicto. El costo es 1 a 3 imágenes contra Cloudinary — no las decenas que sí justificarían un pipeline aparte — y ya hay precedente sincrónico en el proyecto: el registro modera el nickname así (`AuthService` → `ModeracionTextoService`).

**Consecuencia:** `EN_REVISION` sigue sin usarse. Documentado explícitamente en el javadoc de `ModeracionEventoService.moderarSincrono()` para que quien lo retome no asuma que ya está resuelto — si algún día se necesita un draft real (por ejemplo, para que la edición SÍ sea asíncrona), ahí es donde engancha.

## 3. 💡 ADR-16 — El reemplazo de contenido es delete + reinsert, no upsert fila por fila

`PublicacionEventoService.reemplazarContenido()`:
- **Reutiliza** la fila `EventoDetalle` (es 1:1 con `Evento` vía `@MapsId`, no se puede duplicar sin violar la PK).
- **Borra y vuelve a crear** `Cronograma`, `Tickets` y `Multimedia` en vez de actualizar en el lugar.
- **Crea una `Ubicacion` nueva** y borra la vieja — no la actualiza in-place. Convención que ya regía en el proyecto (`UbicacionRepository`: *"editar la direccion de un evento no debe cambiar silenciosamente la de otro"*).

**Límite asumido y escrito en el código:** el borrado de cronogramas/tickets es seguro **solo porque el Módulo 3 (Inscripcion) no existe todavía** — no hay ninguna fila que referencie esos ids. El día que exista, este método tiene que dejar de borrar los tickets con inscripciones activas (la regla de abajo ya bloquea la EDICIÓN en ese caso, pero el borrado en sí va a necesitar revisarse igual).

## 4. RF-2.7 propiamente dicho: el piso de cupo y el candado de precio

`EventoService.validarCupoYPrecio()`, corrida antes de moderar (fail-fast: no tiene sentido subir imágenes a Cloudinary para una edición que ya se sabe inválida):

- Un ticket existente **no puede terminar con menos cupo del que ya tiene**.
- Un ticket con `cupoActual > 0` **no puede cambiar de precio**.

La correspondencia entre "este ticket del formulario" y "este ticket ya guardado" se resolvió por **id**, no por heurística: se agregó `idCronograma` / `idCronogramaTicket` (nullable) a `CronogramaRequest`/`TicketRequest` — el frontend los recibe del `GET` de precarga y los reenvía tal cual; si no vienen (alta, o un sector nuevo agregado durante una edición), se trata como fila nueva sin piso que respetar.

```json
// PUT con cupoMaximo por debajo del ya cargado (50) -> 400, nada cambia
{"error":"REGLA_NEGOCIO","mensaje":"El cupo de 'General' no puede bajar de 50 (el ya cargado)"}
```

## 5. Validaciones nuevas de campo (a pedido explícito del usuario)

| DTO | Campo | Regla nueva |
|---|---|---|
| `UbicacionRequest` | `calle` | 5–120 caracteres (antes solo tenía máximo) |
| `UbicacionRequest` | `numeroExterior` | numérico, positivo, hasta 5 dígitos (antes: texto libre hasta 10) |
| `UbicacionRequest` | `numeroInterior` | 1–12 caracteres, sin símbolos especiales |
| `UbicacionRequest` | `latitud` / `longitud` | rango real: -90/90 y -180/180 |
| `TicketRequest` | `tipoTicket` | 3–20 caracteres (antes 60), sin espacio al borde ni dobles |
| `TicketRequest` | `precio` | tope $50.000.000 (antes sin techo) |
| `TicketRequest` | `cupoMaximo` | tope 1.000.000 (antes sin techo) |
| `CronogramaRequest` (regla de negocio, no anotación) | `fecha` + `horaInicio` | debe ser posterior al **momento actual**, no solo a la fecha del día — aplica a crear y a editar por igual |

### 🐛 Bug de validación encontrado en la propia implementación

`tipoTicket` se anotó primero con `@NotBlank` + dos `@Pattern` separados. Bean Validation **no garantiza el orden** de evaluación entre anotaciones del mismo campo sin usar grupos (que este proyecto no adopta en ningún otro DTO), y en la práctica un valor vacío terminaba devolviendo *"no puede tener espacios al inicio, al final ni dobles"* en lugar de *"es obligatorio"* — técnicamente correcto (el string vacío tampoco cumple el patrón) pero confuso para quien completa el formulario. Se consolidó en una **única** `@Pattern` con lookahead (`^(?=.{3,20}$)\S+( \S+)*$`) que cubre longitud, espacios y el caso vacío con un solo mensaje, eliminando la ambigüedad de raíz.

## 6. 🐛 Bug de infraestructura: el panel de organizador no cargaba

Al armar el primer HTML del panel (`evento-form.html`) y probarlo con un usuario real logueado, la página rebotaba al login **antes de que corriera nada de JavaScript**. Causa: `SecurityConfig` nunca declaró `/pages/organizador/**` como público. El patrón ya existía documentado para `/pages/auth/**` y `/pages/dashboard.html` — el HTML de estas pantallas se sirve público a propósito, porque una navegación de browser nunca adjunta el header `Authorization`; la protección real vive en `Auth.exigirSesion()` del lado del cliente y en `/api/organizador/**` (`hasRole('ORGANIZADOR')`) del lado del servidor — pero nadie había extendido la regla al agregar la carpeta nueva.

**Corrección:** una sola línea, `.requestMatchers("/pages/organizador/**").permitAll()`, generalizando el comentario existente para cubrir toda la carpeta y no una página a la vez.

## 7. Frontend: primer HTML del panel de organizador

`enexia/src/main/resources/static/pages/organizador/evento-form.html` — una sola página para **crear y editar** (`?id=123` activa el modo edición), siguiendo el mismo patrón dual que `register-paso2.html` ya usa para Persona Física / Jurídica. Reconstruida a partir del Figma "Crear / Modificar Evento" (tema oscuro) pero re-skinneada con la paleta clara "Atardecer Fueguino" de `tokens.css`, a pedido explícito del usuario — los mockups de Figma de esta sesión están todos en dark theme y no reflejan la paleta real del producto.

**Decisión técnica reutilizable:** los ids de los inputs dinámicos de cronograma/tickets se generan con el **mismo formato** que usa Bean Validation para reportar errores en listas (`cronogramas[0].fecha`, `cronogramas[0].tickets[0].precio`). Como resultado, la función `UI.pintarErrores()` de `utils.js` —ya existente, pensada para formularios planos— pinta también los errores de campos generados dinámicamente **sin ningún mapeo especial**. Vale la pena repetir el patrón en cualquier formulario futuro con listas dinámicas.

### Verificación end-to-end (no solo visual)

Se levantó el backend, se creó un usuario ORGANIZADOR real, se inyectó el JWT en `sessionStorage` y se operó la página contra la API real (no mocks):

| Escenario | Resultado |
|---|---|
| Envío vacío (11 campos inválidos, incluidos anidados) | Los 11 mensajes se pintan en el campo exacto |
| Agregar / quitar instancia y sector de ticket | Reindexado correcto de ids y de los `min`/`disabled` de los botones |
| Alta válida con imagen real (multipart) | `202` → moderación aprueba → evento visible en el dashboard |
| Edición bajando el cupo por debajo del ya cargado | `400` con el mensaje específico, contenido vigente intacto |
| Edición válida (nombre, cupo, portada nueva) | `200`, contenido reemplazado, nuevos `idCronograma`/`idCronogramaTicket` confirmando el delete + reinsert |

**Limitación conocida, comunicada al usuario:** al editar hay que volver a adjuntar la portada (y las adicionales que se quieran conservar), porque la edición vuelve a moderar todo el contenido — no hay reuso parcial de imágenes ya aprobadas.

## 8. Estado final

| | |
|---|---|
| Endpoints nuevos | `PUT /api/organizador/eventos/{id}`, `GET /api/organizador/eventos/{id}` |
| Tests | 168 pasan, 0 fallos (0 regresiones sobre los 168 previos) |
| Frontend | 1 de ~6 pantallas del panel de organizador (`evento-form.html`) |
| CRUD de eventos | **C-R-U-D completo** (antes faltaba la U) |
| `EN_REVISION` | Sigue sin usarse — decisión documentada, no olvido (ADR-15) |
