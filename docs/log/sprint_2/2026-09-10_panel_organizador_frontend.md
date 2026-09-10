# Panel del Organizador: Dashboard, Mis Eventos y Mi Perfil

**Fecha:** 2026-09-10 (continuación del mismo día de [2026-09-10_editar_evento.md](./2026-09-10_editar_evento.md))
**Motivo:** Backlog de [SPRINT_LOG.md](../SPRINT_LOG.md), ítem 10 ("Frontend del panel de organizador — falta el resto. Solo existe `evento-form.html`")
**Resultado:** ✅ `dashboard.html` (Panel de Control) · ✅ `mis-eventos.html` · ✅ `perfil.html` + backend nuevo de perfil · ✅ 168 tests en verde (0 regresiones) · 4 de 6 pantallas del panel ya construidas

---

## 1. Bloqueo de Figma y cómo se resolvió

El plan Starter de Figma MCP tiene un límite de **6 tool calls por mes**, no por sesión. Se agotó a mitad de la construcción de Perfil, después de haber servido bien para `evento-form.html`, `mis-eventos.html` y `panel-equipo` (design fetch, sin construir todavía).

**Salida encontrada por el usuario:** el plugin nativo "Figma to Code" (HTML, Tailwind, Flutter, SwiftUI) que Figma ya integra en su propio panel de diseño. No pasa por el MCP en absoluto — es una exportación local del archivo, sin límite de cuota. El usuario lo usó con las opciones **HTML** + **Color Variables** activado (para tener los valores de color reales) y **Embed Vectors/Images** apagados (no hacían falta: los íconos de este proyecto se dibujan a mano como SVG simples, no se dependía de los que exporta Figma), copió el código y lo pegó directo en el chat.

**Vale la pena dejarlo anotado como alternativa reutilizable** para Mi Equipo y Plan Pro, las dos pantallas que todavía faltan: si el MCP vuelve a estar sin cuota, este camino no depende de él.

## 2. `dashboard.html` (Panel de Control) y `mis-eventos.html`

Construidas sin fetch de Figma nuevo (no había node-id guardado de esas dos pantallas de una sesión anterior, y reconstruir desde cero con MCP habría gastado cuota). Se armaron siguiendo el mismo sistema de diseño ya fijado por `evento-form.html` (sidebar, franja de plan, `tokens.css`, paleta clara), con datos **reales** del backend, no maquetados:

- **`dashboard.html`**: KPIs (total, publicados, en revisión, rechazados) calculados en el cliente sobre `GET /api/organizador/eventos` (no hay endpoint de agregados), lista de últimos eventos con chip de estado y modal de estadísticas (`GET /api/organizador/eventos/{id}/estadisticas`), accesos rápidos a Mis Eventos / Mi Equipo / Mi Perfil.
- **`mis-eventos.html`**: tabla completa con filtro por estado (server-side para los valores reales de `estado_organizador`; client-side para los pseudo-estados "En revisión"/"Rechazado", que son atributos de `estado_sistema` y el endpoint no los expone como parámetro), búsqueda por texto, paginación, chip de categoría coloreado con los tokens `--cat-*`, y acciones según el estado real del evento: Editar (solo si es editable), "Ver motivo" en rechazados (mapea `motivoRechazo` — `MODERACION_TEXTO`/`MODERACION_IMAGEN`/`SIN_IMAGENES_VALIDAS`/`ERROR_PIPELINE` — a un mensaje legible), y "Dar de baja" con modal de confirmación que avisa si el evento tiene cupo ocupado.

**Decisión deliberada de no inventar datos:** el Figma de Mis Eventos mostraba una columna "Fecha e Inscritos" (ej. "88/100 inscritos"). El Módulo 3 (Inscripción) no existe en el backend todavía — `EventoResponse` no trae ese dato, y `EventoEstadisticasResponse.cupoOcupado` es un contador que hoy nunca se escribe. Se omitió esa cifra de la tabla en vez de mostrar un número falso; el cupo total sí se puede consultar bajo demanda con "Ver estadísticas".

**Bug de layout encontrado y corregido:** la grilla de la tabla usaba `display: contents` en las filas para que cada celda fuera ítem directo del grid padre. Una media query que intentaba ocultar columnas en pantallas angostas rompía esa estructura (las celdas restantes se repartían en columnas equivocadas). Se corrigió envolviendo la tabla en un contenedor con `overflow-x: auto` y un `min-width` en la grilla, en vez de tocar las columnas por breakpoint — mismo patrón que ya usa el resto del proyecto para contenido ancho.

## 3. `Mi Perfil`: decisión de alcance

A diferencia de dashboard/mis-eventos (que ya tenían backend completo), el research de "¿qué endpoints de perfil existen?" mostró un vacío real:

| Necesidad | Estado antes de hoy |
|---|---|
| GET del perfil propio (nombre, apellido, DNI, fecha nacimiento) | ❌ No existía. `/api/auth/me` solo devuelve email + roles |
| PUT del perfil propio | ❌ No existía |
| GET-by-id de una organización | ❌ No existía (solo el listado) |
| PUT de una organización | ❌ No existía |
| Gestión de miembros de organización | ❌ No existía ningún endpoint |

Con esa información, el usuario eligió explícitamente **"Backend primero"** — el mismo criterio que ya se había usado para "editar evento": no maquetar con datos falsos, construir el endpoint que falta. Alcance acotado a propósito:

- **Sí se construyó:** GET/PUT de nombre, apellido y fecha de nacimiento.
- **No se construyó (y se documentó como tal en la UI):** edición de organización, gestión de miembros. Editar una organización es un alcance mucho mayor al pedido de hoy; la tarjeta de organización quedó de **solo lectura** sobre el `GET /api/organizador/organizaciones` que ya existía.
- **Deliberadamente fuera de alcance para siempre (no es un "todavía"):** editar email, nickname o DNI desde esta pantalla. Email/nickname identifican la cuenta (login, unicidad) y cambiarlos necesitaría su propio flujo de verificación; el DNI identifica a la persona y no hay ningún caso de negocio para que el usuario lo autoedite.

### Backend nuevo

- `GET /api/usuario/perfil` y `PUT /api/usuario/perfil` en un `UsuarioController` nuevo, respaldados por `PerfilService` (separado de `AuthService` a propósito: ese cubre el flujo de autenticación/alta; `PerfilService` cubre una cuenta que ya existe y ya inició sesión).
- Cuelga de `/api/usuario/**`, que **no tiene matcher propio** en `SecurityConfig` — cae en el cierre por defecto `anyRequest().authenticated()`, que es lo correcto acá: cualquier rol autenticado (no solo `ORGANIZADOR`) tiene un perfil propio. No hizo falta tocar `SecurityConfig`.
- `PerfilResponse` (idUsuario, email, nickname, roles, nombre, apellido, dni, fechaNacimiento, fechaRegistro) y `PerfilActualizarRequest` (solo nombre/apellido/fechaNacimiento).
- `UsuarioRepository.buscarActivoPorEmailConRoles` se extendió con `LEFT JOIN FETCH` de `personaFisica` y `persona`, en vez de escribir un método casi idéntico aparte — es el mismo punto único donde ya se carga un `Usuario` completo para el resto de los servicios autenticados.
- Nueva constante `AuditoriaService.ACCION_PERFIL_ACTUALIZADO`.

### 🐛 El mismo bug de validación, otra vez

`PerfilActualizarRequest.nombre`/`apellido` se escribieron primero copiando literal el patrón de `UsuarioRegistroRequest` (`@NotBlank` + `@Size` + `@Pattern` por separado). Probado en vivo con curl, un valor vacío reprodujo **el mismo bug de orden no determinístico de Bean Validation** documentado hoy mismo en [2026-09-10_editar_evento.md](./2026-09-10_editar_evento.md): el mensaje mostrado fue "debe tener entre 2 y 50 caracteres" en lugar de "es obligatorio".

Se corrigió con el mismo patrón ya establecido: una única `@Pattern` con lookahead (`^(?=.{2,50}$)[\p{L} '-]+$`). **Queda pendiente y sin tocar** (fuera de alcance de hoy): `UsuarioRegistroRequest.nombre`/`apellido`, el DTO original de donde se copió el patrón, todavía tiene el mismo problema latente y nunca se corrigió.

### Frontend: una sola página para las 2 variantes de Figma

`perfil.html` no tiene un `if/else` de "modo Persona Física" vs "modo con Persona Jurídica". La tarjeta de organización se repite una vez por cada elemento que devuelva `GET /api/organizador/organizaciones` — cero, una, o varias (el Figma solo mostraba una, pero nada impide que un organizador administre más de una empresa).

- **Tarjeta personal:** avatar con iniciales, `@nickname`, chip de rol, campos editables (nombre, apellido, fecha de nacimiento) y campos de solo lectura (email, DNI) con nota aclaratoria. "Guardar Cambios" pinta errores campo por campo con `UI.pintarErrores` (mismo patrón que `evento-form.html`) y muestra un toast de éxito/error.
- **Tarjeta(s) de organización:** solo lectura — razón social, nombre de fantasía, CUIT formateado, email corporativo, teléfono — con chip de estado (Aprobada/En revisión/Rechazada, mapeado desde `estadoSistema`) y una nota explícita avisando que la edición de organización y la gestión de miembros no están disponibles todavía.
- **Estado vacío:** tarjeta con borde punteado explicando cómo dar de alta una organización, para el organizador que todavía no administra ninguna.

### Verificación end-to-end

Probado contra el backend real con dos cuentas: una **sin** organización y otra **con** una organización de prueba (CUIT válido por módulo 11 generado a propósito para no chocar con el CUIT de ejemplo ya usado en otra prueba de la sesión).

| Escenario | Resultado |
|---|---|
| Cuenta sin organización | Tarjeta personal + estado vacío de organización |
| Cuenta con 1 organización | Las 2 tarjetas lado a lado, datos reales |
| Editar nombre y guardar | `200`, toast de éxito, avatar recalculado con las iniciales nuevas |
| Guardar con nombre vacío | `400`, mensaje "es obligatorio..." pintado en el campo exacto |
| Responsive mobile (375px) | Sin overflow horizontal, tarjetas apiladas |

## 4. Estado final

| | |
|---|---|
| Endpoints nuevos | `GET /api/usuario/perfil`, `PUT /api/usuario/perfil` |
| Tests | 168 pasan, 0 fallos (corrido antes y después del fix de validación) |
| Frontend | 4 de ~6 pantallas del panel de organizador: `evento-form.html`, `dashboard.html`, `mis-eventos.html`, `perfil.html` |
| Pendiente | Mi Equipo (maqueta visual, sin backend de momento) y Plan Pro (diferido explícitamente por el usuario) |
| Bug de validación de Bean Validation | Corregido en `PerfilActualizarRequest`; **sigue presente** en `UsuarioRegistroRequest`, no tocado hoy |
