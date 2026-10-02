# Módulo 6 arranca: Moderación Administrativa de Eventos (RF-6.1)

**Fecha:** 2026-10-01 (verificación en navegador: 2026-10-02)
**Motivo:** el Módulo 6 estaba descartado desde el 2026-09-09 ("se priorizan CRUD de eventos e inscripciones"). El usuario pidió retomarlo a partir de una pregunta concreta: *¿el DFD de moderación contempla banear un evento ya publicado?* La respuesta honesta era que no — y de ahí salió todo este trabajo.
**Resultado:** ✅ RF-6.1 reescrito · ✅ DFD reescrito · ✅ backend nuevo (`AdminEventoService` + `EventoAdminController`) · ✅ primera pantalla del panel de administrador · ⚠️ 1 bug real encontrado y corregido en la prueba manual · ⚠️ sigue sin existir forma de otorgar el rol `ADMINISTRADOR` desde la app

---

## 1. El hueco que destapó la pregunta

El DFD existente (`6.1_moderacion_manual_eventos_v2.md`) y el RF-6.1 original ("Anulación Manual de Moderación") solo contemplaban **un** escenario: un administrador actuando como segunda instancia sobre un evento que el pipeline automático ya había rechazado (`RECHAZADO_SISTEMA`). La precondición del DFD lo dejaba explícito: si el evento no estaba en `RECHAZADO_SISTEMA` (o `CAMBIO_RECHAZADO`, un estado que tampoco llegó a implementarse), la API debía responder `409`.

Eso deja sin resolver el caso real que motivó la pregunta: un evento **ya aprobado y visible en el catálogo** recibe una denuncia de fraude, o alguna infracción se detecta después de publicado. No hay forma de bajarlo sin forzarlo artificialmente a un estado de rechazo que nunca tuvo. RF-6.2 (gestión disciplinaria de cuentas) tampoco lo cubre: ese RF banea **usuarios**, no eventos puntuales, y no hay ninguna regla de cascada que dé de baja los eventos de una cuenta baneada.

## 2. RF-6.1 y el DFD, reescritos

`docs/requisitos/requisitos_funcionales/modulo_6.md`: el título pasó de "Anulación Manual de Moderación" a **"Moderación Administrativa General de Eventos"**, y ahora declara explícitamente dos escenarios bajo el mismo mecanismo (PATCH + decisión):

- **(a) Revisión de 2.ª instancia** — evento `RECHAZADO_SISTEMA`: el administrador aprueba de todas formas (pasa a `APROBADO_MANUAL`) o ratifica el rechazo (`RECHAZADO_MANUAL`, sin motivo).
- **(b) Suspensión disciplinaria** — evento `APROBADO_SISTEMA` o `APROBADO_MANUAL`: el administrador lo suspende (`RECHAZADO_MANUAL`, con un motivo obligatorio) y puede revertir esa suspensión más adelante.

`docs/diagrams/modulo_6_admin/6.1_moderacion_manual_eventos_v2.md`: se sacó la precondición rígida `C_EsRechazado` y se reemplazó por cuatro ramas que miran el estado **vigente** del evento en el momento de decidir, no un campo aparte que el cliente pudiera mandar mal:

| Decisión pedida | Estado vigente | Resultado |
|---|---|---|
| Aprobar | ya `APROBADO_*` | `409` — "el evento ya está aprobado" |
| Aprobar | `RECHAZADO_SISTEMA` o `RECHAZADO_MANUAL` | → `APROBADO_MANUAL` |
| Rechazar | ya `RECHAZADO_MANUAL` | `409` — "el evento ya está rechazado/suspendido" |
| Rechazar | `APROBADO_SISTEMA` / `APROBADO_MANUAL` | → `RECHAZADO_MANUAL` + motivo de suspensión **obligatorio** |
| Rechazar | `RECHAZADO_SISTEMA` | → `RECHAZADO_MANUAL`, sin motivo (ratificación) |

## 3. Implementación de backend (antes esto era solo documentación)

El Módulo 6 no tenía ni una línea de código de evento hasta hoy. Se construyó reutilizando, a propósito, los mismos patrones que ya existían para el pipeline automático — no se inventó nada nuevo que ya estuviera resuelto:

- **`MotivoSuspensionAdmin`** (enum nuevo): `DENUNCIA_FUNDADA`, `FRAUDE_DETECTADO`, `INFRACCION_POST_PUBLICACION`. Deliberadamente **separado** de `MotivoModeracionEvento` (ese es vocabulario exclusivo del pipeline automático): mezclarlos en un único enum volvería ambiguo, al leer el historial de un evento, si un `RECHAZADO_MANUAL` fue una ratificación de segunda instancia o una sanción sobre algo que ya estaba al aire.
- **`DatosInicialesConfig.cargarEstadosDeEvento()`**: se agregó el sembrado de filas nuevas en el catálogo `evento_estado_sistema`, cruzando `RECHAZADO_MANUAL` con cada valor de `MotivoSuspensionAdmin` (antes `RECHAZADO_MANUAL` solo tenía la fila con `motivo_codigo = null`). **No hizo falta ninguna migración SQL**: ese catálogo se siembra por `CommandLineRunner` (son datos, un `INSERT`), no por una columna nueva (DDL) — la regla de "toda columna nueva va a `docs/diseño_bd/migraciones/`" no aplica acá.
- **`AdminEventoService` + `EventoAdminController`** (nuevos), bajo `/api/admin/eventos`:
  - `GET` — listado paginado **sin** filtro de estado del lado del servidor: el admin tiene que ver a la vez los rechazados y los aprobados (son los dos escenarios de RF-6.1), así que se trae un lote grande (techo 100) y el filtro por estado concreto lo resuelve el cliente sobre ese mismo lote — exactamente el mismo patrón que ya usa `mis-eventos.html` del organizador para sus pseudo-filtros.
  - `PATCH /{id}` — body `{ decision: APROBAR|RECHAZAR, motivoCodigo? }`. Reutiliza el patrón de concurrencia de `PublicacionEventoService` tal cual: bloqueo pesimista (`EventoRepository.bloquearParaActualizar`, `SELECT ... FOR UPDATE`) **antes** de leer el estado que se va a decidir, y `@Transactional(isolation = READ_COMMITTED)` — obligatorio desde el upgrade a MariaDB 11.6+ (ver [2026-09-09_upgrade_mariadb.md](./2026-09-09_upgrade_mariadb.md)).
  - La diferencia real con el pipeline automático: `HistorialEstadoEvento.usuario` queda seteado al `Usuario` administrador en vez de `null`. Esto no es una decisión nueva — era un comentario ya dejado en el código de `PublicacionEventoService.cambiarEstado()` anticipando exactamente este trabajo ("cuando el cambio venga del panel de admin, RF-6.1, ahí se registra quién lo hizo").
- Soporte menor: `EventoRepository.buscarParaModeracion` (query nueva, mismo `@EntityGraph` que las demás, sin filtro de organizador), `EventoResponse.organizadorEsOrganizacion` (booleano nuevo para que la tabla del admin distinga "Persona física" de "Organización"), `AuditoriaService.ACCION_EVENTO_MODERADO_ADMIN`, y el javadoc de `EstadoEventoSistemaNombre.RECHAZADO_MANUAL` actualizado para reflejar que ahora cubre los dos escenarios y es reversible.

## 4. 🐛 Bug real encontrado en la prueba manual: `/pages/admin/**` no era pública

Al abrir la página nueva con una sesión `ADMINISTRADOR` válida, `SecurityConfig` la redirigía igual a login (`302`). La causa es la misma que ya se documentó el 2026-09-10 para `/pages/organizador/**`: cada carpeta nueva bajo `/pages/**` necesita su propia línea `permitAll()` explícita, no hereda nada por convención de nombre — y esta vez el patrón volvió a repetirse porque `/pages/admin/**` nunca se agregó. Se corrigió agregando `.requestMatchers("/pages/admin/**").permitAll()` junto a las otras dos. El razonamiento sigue siendo el mismo: el HTML se sirve público a propósito (el JWT vive en `sessionStorage`, nunca llega en una navegación de browser), la protección real es `Auth.exigirSesion()` + `Auth.hasRole('ADMINISTRADOR')` del lado del cliente, y `/api/admin/**` sí exige el rol del lado del servidor.

## 5. Frontend nuevo: primera pantalla del panel de administrador

`enexia/src/main/resources/static/pages/admin/moderacion-eventos.html`. El usuario trajo como referencia un Figma genérico en **dark mode** (plantilla de panel admin con sidebar oscuro, tarjetas `#0B0F19`, etc.). Se descartó esa paleta a propósito: `tokens.css` es la única fuente de verdad de color del proyecto ("ninguna otra hoja ni página debe escribir un color en hexadecimal") y la paleta real del producto es clara ("Atardecer Fueguino"). Se conservó del Figma la **estructura** (3 KPIs, tabla con portada/evento-categoría/organizador/estado/acciones, filtros) pero resuelta con los componentes y colores que ya usan `dashboard.html`/`mis-eventos.html` del organizador.

Mismo patrón de carga que `mis-eventos.html`: un solo `fetch` grande (`tamaño=100`) y filtrado/paginado del lado del cliente — no existe (ni va a existir todavía) un endpoint de agregados para los KPIs.

Las 4 acciones de la tabla salen directo del estado real, nunca de un campo aparte:

| `estado_sistema` del evento | Acción disponible |
|---|---|
| `EN_PROCESO` | ninguna (el pipeline todavía no dictaminó, no hay contenido que aprobar) |
| `RECHAZADO_SISTEMA` | "Aprobar de todas formas" |
| `APROBADO_SISTEMA` / `APROBADO_MANUAL` | "Suspender" (pide motivo) |
| `RECHAZADO_MANUAL` | "Revertir suspensión" |

## 6. Prueba manual (navegador, no es test automatizado)

Se verificó en vivo con Claude Browser contra el backend reiniciado:

1. Login con una cuenta admin de prueba (ver ítem 7).
2. Carga de la cola de moderación: 11 eventos, los 11 en `APROBADO_SISTEMA` en ese momento.
3. **Suspender** un evento real con motivo "Denuncia fundada" → `RECHAZADO_MANUAL`, el KPI "Eventos aprobados y visibles" bajó de 11 a 10, el chip pasó a "Suspendido (Denuncia)".
4. **Revertir** esa misma suspensión → volvió a `APROBADO_MANUAL` (distinto de `APROBADO_SISTEMA` en el chip, correctamente — quedó registrado que fue un admin quien lo reaprobó), el KPI volvió a 11.
5. Buscador de texto server-side, probado contra el mismo lote.

Las cinco peticiones (`GET`/`PATCH` × 2 + `GET` de búsqueda) devolvieron `200`, sin errores de consola relevantes (solo el ya conocido `ERR_NAME_NOT_RESOLVED` de Google Fonts, bloqueado por la sandbox de red del entorno de prueba, no por el código).

## 7. Gap que sigue abierto: no hay forma de otorgar `ADMINISTRADOR` desde la app

Confirmado de nuevo (ya se sospechaba): ni endpoint, ni seed, ni backdoor de desarrollo. El catálogo `rol` sí trae sembrada la fila `ADMINISTRADOR` (`DatosInicialesConfig.cargarRoles()`), pero ningún `Usuario` queda vinculado a ella por ningún camino de la aplicación — el alta pública rechaza explícitamente cualquier perfil que no sea `PARTICIPANTE`/`ORGANIZADOR`.

Para poder probar, se registró una cuenta por el alta pública normal y se le agregó el rol a mano:

```sql
-- cuenta de prueba: gastonrev01@example.com / Password123! (id_usuario = 7)
INSERT INTO usuario_rol (id_usuario, id_rol) VALUES (7, 3); -- 3 = ADMINISTRADOR
```

Contra la base de dev local (MariaDB 11.8.9 como servicio, puerto 3306). Esa cuenta queda disponible como login admin real para seguir probando el panel.

## 8. Estado final

| | |
|---|---|
| Endpoints nuevos | `GET /api/admin/eventos`, `PATCH /api/admin/eventos/{id}` |
| Rol requerido | `ADMINISTRADOR` (`SecurityConfig` + `@PreAuthorize`, doble capa como el resto del proyecto) |
| Migración SQL | Ninguna — el catálogo nuevo se siembra por `CommandLineRunner` |
| Frontend | 1 pantalla nueva (`pages/admin/moderacion-eventos.html`), primera del panel de administrador |
| Compilación | `gradle compileJava` y `compileTestJava`, ambos en verde |
| Pendiente | Endpoint o flujo real para otorgar `ADMINISTRADOR`; el resto del panel admin (Gestión de Usuarios, Categorías, Suscripciones — hoy son ítems inertes en el sidebar con "Disponible más adelante") |
