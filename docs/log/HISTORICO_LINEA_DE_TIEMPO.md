# 📅 Histórico — Línea de Tiempo del Proyecto Enexia

> Archivo de **histórico acumulativo**: cada cierre de sesión agrega una sección al final (modo *append*); lo anterior no se reescribe.
> Fuente: `git log` (265 commits al 2026-10-11) + los registros de `docs/log/`.
> Para el detalle técnico de cada sprint ver [SPRINT_LOG.md](./SPRINT_LOG.md) y la carpeta por sprint.
>
> Leyenda: 🚀 Inicio/Setup · 🗄️ Base de Datos · ⚙️ Backend · 🎨 Frontend · 🐛 Fix/Debug · 📄 Doc · 🧪 Tests

---

## 📅 Línea de Tiempo del Proyecto

### Fase 0 — Especificación y diseño (2026-05-15 → 2026-06-27)

* **2026-05-15 18:41 - 🚀 Inicio / Setup**: repositorio creado (`3aeab92`), README inicial.
  - *Impacto*: punto de partida del proyecto de fin de año.
* **2026-05-19 → 2026-06-10 - 🗄️ Base de Datos**: primeros `DER.md` y `MER.md`, con ~25 iteraciones de ajuste del modelo.
  - *Archivos clave*: `docs/diseño_bd/MER.md`, `docs/diseño_bd/DER.md`
  - *Impacto*: modelo de datos que luego se mapea 1:1 a JPA.
* **2026-05-29 / 30 - 📄 Doc**: documentación general, objetivos, requisitos no funcionales, historias de usuario; reorganización de carpetas en `docs/`.
* **2026-06-09 - 📄 Doc**: primeros diagramas de flujo (registro, login, creación/modificación/baja de eventos, ver mis eventos, estadísticas).
  - *Archivos clave*: `docs/diagrams/login_registro/`, `docs/diagrams/gestion_de_eventos/`
* **2026-06-11 04:00-05:25 - 📄 Doc**: los **8 módulos de requisitos funcionales** (`modulo_1.md` … `modulo_8.md`) y reorganización completa de `docs/requisitos/` (~80 commits en una madrugada, casi todos renombres).
  - *Impacto*: base normativa de todo el desarrollo posterior.
* **2026-06-17 → 2026-06-20 - 📄 Doc**: pulido de los 8 módulos, **DFD Nivel 0 y Nivel 1**, `Evento_Ticket` pasa a `Cronograma_Ticket`.
  - *Archivos clave*: `docs/diagrams/DFD LVL 0.md`, `docs/diagrams/DFD LVL 1.md`
* **2026-06-27 - 🗄️ Base de Datos**: seguimiento de estados (tablas de estado + historiales) y relaciones de `Ubicacion` en el MER.

### Fase 1 — Sprint 1: arquitectura, modelo y autenticación (2026-07-20 → 2026-08-31)

* **2026-07-20 - 🚀 Inicio / Setup**: `CLAUDE.md` (arquitectura y guías) y proyecto **Spring Boot + Gradle** (`ca35612`).
* **2026-07-25 - 📄 Doc**: planificación del Sprint 1 y decisiones de autenticación MVP (`7f59590`).
* **2026-07-26 - 🗄️ Base de Datos**: **37 entidades JPA** + 2 claves compuestas, mapeadas 1:1 con el MER; base `enexia` creada (`f95024a`).
  - *Archivos clave*: `enexia/src/main/java/com/enexia/rg/model/`
* **2026-08-04 - ⚙️ Backend**: 37 `JpaRepository` + 10 DTOs; DFD Nivel 1 y 2 para módulos 3 a 8 (`ce540b9`, `50d343a`).
* **2026-08-23 / 24 - ⚙️ Backend**: JWT multi-rol, moderación asíncrona, **Módulo 1 (registro + login, JWT + RBAC)** (`6776de8`, `e13a872`).
* **2026-08-27 / 28 - 🐛 Fix**: `id_puntuacion` → `id_valoracion`; asignación dual de roles para organizador (`6a27917`).
* **2026-08-31 - 🎨 Frontend**: banco de pruebas visual de registro y login (`1dea04b`).

### Fase 2 — Sprint 2: frontend, organizaciones, eventos y catálogo (2026-09-01 → 2026-09-30)

* **2026-09-01 - 🎨 Frontend**: sistema de diseño con tokens, paleta "Atardecer Fueguino" (`4667bef`).
  - *Archivos clave*: `enexia/src/main/resources/static/pages/css/tokens.css`
* **2026-09-03 → 09-07 - 🎨 Frontend**: login y registro (incluye persona jurídica) con los Figma, JS conectado, testing (`76a90d9` … `4e50008`).
* **2026-09-09 03:45 - ⚙️ Backend**: **Sprint 2 backend completo** + MariaDB 11.8 + ADR-13 (bloqueo silencioso al 3.er fallo) y ADR-14 (alta de PJ inmediata) (`9802fd2`).
  - *Impacto*: organizaciones, eventos con moderación asíncrona, catálogo público, panel de organizador (backend).
* **2026-09-10 - ⚙️ Backend / 🎨 Frontend**: módulo de eventos y registro PJ; editar evento síncrono (RF-2.7); landing pública, ficha de evento, panel del organizador (`d08de5e`).
* **2026-09-14 - 🐛 Fix**: correcciones de landing/detalle/login, paquete de tests Playwright, **normalización de radios con los tokens en 6 páginas** (`cf34f9f`, `4dd977c`).
* **2026-09-19 - ⚙️ Backend / 📄 Doc**: bloqueo de cuenta alineado con ADR-13, módulo "Mi Equipo", regla de no levantar el frontend en un servidor aparte (`2c65b7b`, `333071b`, `49fbd15`).
* **2026-09-20 → 09-28 - 🧪 Tests**: suites de validación, unitarias y de API; inscripción de participante (RF-3.1 a 3.3) (`92c4f05`, `5baee27`, `060c075`).
* **2026-09-30 - ⚙️ Backend / 🐛 Fix**: valoraciones (RF-3.4, RF-4.4) y corrección de bugs reportados (`4457299`).

### Fase 3 — Módulo 6: panel de administración (2026-10-01 → hoy)

* **2026-10-01 / 02 - ⚙️ Backend + 🎨 Frontend**: **moderación administrativa de eventos (RF-6.1)**: `GET`/`PATCH /api/admin/eventos`, suspensión disciplinaria reversible, primera pantalla del panel admin (`52eb711`).
  - *Archivos clave*: `AdminEventoService.java`, `EventoAdminController.java`, `pages/admin/moderacion-eventos.html`
  - *Impacto*: primera pantalla del rol `ADMINISTRADOR`.

#### 2026-10-11 — Gestión completa de estados de evento desde el panel admin

* **Auditoría - 📄 Doc**: `check-rules` sobre el módulo admin; detecta DFD y RF-6.1 desalineados con el código (`tipo_agente`, `ADMIN_REVISO`, `CAMBIO_*` inexistentes).
* **⚙️ Backend**: suspender exige **motivo + observación** (10-500); ratificar un rechazo automático se elimina (409); `EN_PROCESO`/`EN_REVISION` no admiten decisión; nuevo `GET /api/admin/eventos/{id}` de detalle.
  - *Archivos clave*: `AdminEventoService.java`, `AdminDecisionEventoRequest.java`, `EventoAdminDetalleResponse.java`
* **🗄️ Base de Datos**: columna `historial_estado_evento.observacion` + migración idempotente.
  - *Archivos clave*: `docs/diseño_bd/migraciones/2026-10-11_historial_evento_observacion.sql`
* **🎨 Frontend**: filtro de estado de dos ejes, modales de suspender / aprobar de todas formas / revertir / detalle y avisos de confirmación, sobre los diseños de Figma pasados a `tokens.css`.
* **🐛 Fix (seguridad)**: `moderacion-eventos.html` interpolaba datos de terceros en `innerHTML` (XSS almacenado); se escapa con `esc()`. **Quedó abierto** en `index.html`, `evento-detalle.html`, `mis-eventos.html` y `dashboard.html`.
* **🐛 Fix**: el organizador veía "No se registró un motivo" en un evento suspendido; ahora ve la categoría y la observación del administrador por un endpoint propio (`GET /api/organizador/eventos/{id}/moderacion`).
* **⚙️ Backend**: `FINALIZADO` pasa a asignarlo el sistema (`FinalizacionEventoScheduler`, cada 5 min); la ficha pública acepta `PUBLICADO` y `FINALIZADO`; dar de baja un finalizado/cancelado responde 409.
* **🎨 Frontend / 🐛 Fix**: filas descentradas y columna de acciones en tres líneas; se quita el override `--radio-tarjeta: 16px` de 5 pantallas y los controles pasan a `--radio-campo`.
* **🧪 Tests**: `AdminEventoServiceTest` (19), `FinalizacionEventoServiceTest` (5), 4 casos nuevos en `EventoServiceTest`.
* **📄 Doc**: RF-6.1, DFD 6.1 (+ diagrama de consulta de detalle), MER, `CLAUDE.md`/`CLAUDE_es-ES.md`, ADR-20, log de sprint.
  - *Detalle completo*: [sprint_2/2026-10-11_estados_evento_panel_admin.md](./sprint_2/2026-10-11_estados_evento_panel_admin.md)

---

## 📊 Resumen de Actividad (al 2026-10-11)

| Indicador | Valor |
|---|---|
| Commits en `main` | 266 (con el de esta sesión) |
| Período | 2026-05-15 → 2026-10-11 (~5 meses) |
| Módulos funcionales con backend | M1 Auth, M2 Eventos, M3 Participación (inscripción + valoración), M4 Catálogo público, M5 Moderación, M6 Admin (parcial: RF-6.1), M7 Organizaciones |
| Módulos sin construir | M8 Membresías/pagos; RF-6.2 a RF-6.4; notificaciones por correo de eventos |
| Entidades JPA | 37 (+ 1 columna: `historial_estado_evento.observacion`) |
| ADRs registrados | hasta ADR-20 (la numeración tiene saltos: ADR-10 bis, 11, 14, etc.) |
| Pantallas de frontend | landing, ficha de evento, login, registro (paso 2 y 2 de completado), 5 de organizador, 2 de participante, 1 de admin |

### Siguientes pasos lógicos

1. **Corregir el XSS almacenado** en el nombre del evento (pantallas públicas incluidas) y validar `<`/`>` en el backend.
2. **Otorgar `ADMINISTRADOR` de forma real** (hoy solo existe la cuenta promovida por SQL) y enlazar el panel admin.
3. Pantallas y backend de **RF-6.2 (usuarios)** y **RF-6.3 (categorías)**; RF-6.4 junto con M8.
4. **Recuperación de contraseña / desbloqueo (RF-1.5)**: backend listo, sin pantallas.
5. **Módulo 8 (membresías y pagos)** y notificaciones por correo.

---

<!-- Las próximas sesiones se agregan debajo de esta línea -->
