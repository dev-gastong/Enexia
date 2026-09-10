# Landing pública: catálogo y ficha de evento (RF-4.1 a RF-4.5)

**Fecha:** 2026-09-10 (continuación del mismo día de [2026-09-10_panel_organizador_frontend.md](./2026-09-10_panel_organizador_frontend.md))
**Motivo:** Enexia no tenía ninguna página pública fuera de `pages/auth/**`. El Módulo 4 (catálogo público) estaba completo en el backend desde Sprint 2 pero **ningún frontend lo consumía todavía**.
**Resultado:** ✅ `index.html` (catálogo, primera vez que existe una página en la raíz de `static/`) · ✅ `evento-detalle.html` (ficha técnica + estado 404) · ✅ 168 tests en verde (0 regresiones) · 0 endpoints nuevos — todo contra el Módulo 4 ya existente

---

## 1. Otra vuelta del plugin "Figma to Code"

Mismo mecanismo que [2026-09-10_panel_organizador_frontend.md](./2026-09-10_panel_organizador_frontend.md) ya documentó para "Mi Perfil": el usuario exportó el HTML directo desde el panel de Figma en vez de pedir los nodos por el MCP (que sigue con el límite mensual agotado). Esta vez trajo tres pantallas:

1. **Catálogo general sin token** — landing para visitante anónimo.
2. **Catálogo general logueado** — mismo layout, header con avatar/usuario/botón "Crear Evento". Probado en el Figma con una cuenta de organizador, pero la lógica de header aplica igual a cualquier rol autenticado.
3. **Mensaje de evento not found** — estado 404 de una ficha de evento.

## 2. Decisión de alcance: una sola página para las 2 variantes de header

`index.html` es la primera página real en la raíz de `static/` — hasta hoy no existía ningún `index.html` ni nada público fuera de `pages/auth/**`. Igual que `evento-form.html` (crear/editar) y `perfil.html` (con/sin organización), **no hay dos archivos**: el header se resuelve en runtime con `Auth.autenticado()` / `Auth.hasRole('ORGANIZADOR')`, y muestra "Iniciar Sesión / Registrarse" o el bloque de cuenta según corresponda.

**Decisión no pedida explícitamente pero necesaria:** el Figma solo traía el catálogo y el estado "no encontrado" de una ficha de evento — no el estado "encontrado". Dejar `evento-detalle.html` sin construir habría hecho que cada "Ver detalle" del catálogo fuera un link muerto. Se construyó la página completa:
- El estado de error **replica el Figma** (re-skinneado a la paleta clara "Atardecer Fueguino" — el Figma usa acento celeste/azul propio del mockup, acá se mantiene el morado/naranja de marca).
- El estado "encontrado" **no tiene Figma de referencia todavía**. Se armó con los componentes ya establecidos en el resto del sitio (tarjeta, chip de categoría, campos de solo lectura) para no inventar un lenguaje visual nuevo. **Queda anotado como deuda de diseño pendiente**: si en algún momento llega un Figma para la ficha "encontrada", esta página se re-skinnea contra ese diseño real.

## 3. Backend: un solo cambio, cero endpoints nuevos

El Módulo 4 (`CatalogoPublicoController`, `CatalogoPublicoService`) ya estaba completo y probado desde Sprint 2 — simplemente nadie lo había conectado a una pantalla todavía. Se usó tal cual:

```
GET /api/publico/eventos                    búsqueda + filtros + paginación (RF-4.1/4.2/4.3)
GET /api/publico/eventos/{id}                ficha técnica + registra visita (RF-4.4/4.5)
GET /api/publico/categorias
GET /api/publico/provincias
GET /api/publico/provincias/{id}/ciudades
```

El único cambio de backend fue en `SecurityConfig`: agregar `.requestMatchers("/evento-detalle.html").permitAll()` (`/index.html` ya estaba permitido desde el arranque del proyecto, sin usarse). 168 tests en verde.

## 4. Honestidad de datos: lo que el Figma mostraba y el DTO real no tiene

- **Filtro "Ubicación"** → se implementó como selector de **ciudad únicamente**, no provincia. Mismo patrón que `evento-form.html`: se resuelve la provincia Tierra del Fuego por nombre y se listan solo sus ciudades — Enexia opera solo en TDF (CLAUDE.md), un selector de provincia sería ruido.
- **Filtro "Fecha"** → el Figma mostraba un pill compacto sin su panel expandido visible en el export. Se implementó como un `<input type="date">` nativo mapeado al parámetro real `desde` del backend, en vez de inventar un selector de rango sin tener el diseño de ese estado.
- **Hora y dirección en las tarjetas** → el Figma mostraba "19:00 hs" junto a la fecha y la calle completa ("Av. Belgrano 450"). `EventoResponse` (el DTO que arma la grilla) **no trae ninguno de los dos** — viven en `EventoCronograma`/`Ubicacion`, y el mapeo de tarjeta no los resuelve a propósito para no introducir un N+1 (documentado en el propio `EventoMapper`). Se omitieron de la tarjeta en vez de inventarlos; la ficha de detalle sí los muestra completos porque `EventoDetalleResponse` los trae.
- **Botón de compra de entradas** → la ficha muestra cada ticket de solo lectura (tipo, precio o "Gratis", cupo disponible o "Agotado") con un aviso "Las inscripciones online están próximamente disponibles" en vez de un botón funcional. El Módulo 3 (Inscripción) no existe en el backend todavía — un botón "Inscribirme" que no hace nada sería peor que no tener botón.
- **"Mi cuenta" para un Participante puro** → si el usuario autenticado tiene rol `ORGANIZADOR`, el bloque de avatar linkea a `pages/organizador/perfil.html` (real). Si es un `PARTICIPANTE` sin `ORGANIZADOR`, el bloque queda sin link (`<span>`, no `<a>`) con un `title` explicando que el perfil de participante todavía no existe. No hay ninguna pantalla de perfil para participantes construida — mejor ser honesto que armar un link que redirige mal.

## 5. Verificación end-to-end

Probado contra el backend real, con los ~117 eventos de prueba acumulados en sesiones anteriores:

| Escenario | Resultado |
|---|---|
| Carga inicial del catálogo | 117 eventos reales, categorías (8) y ciudades (3) reales pobladas en los selects |
| "Cargar más eventos" | Pagina correctamente: 9 → 18 tarjetas |
| Filtro por categoría "Gaming" (0 eventos reales en esa categoría) | `idCategoria=5` real en la petición, estado vacío pintado correctamente |
| Header invitado → autenticado | Avatar, "+ Crear Evento" (solo `ORGANIZADOR`), botón "Salir" cierra sesión y recarga |
| Ficha de evento real (id=204) | Título, categoría, organizador, dirección, descripción, galería y cronograma con tickets reales (`Inscripcion General — Gratis — 200 disponibles`, `Pase VIP — $15.000 — 30 disponibles`) |
| Ficha con id inexistente (999999) | 404 `RECURSO_NO_ENCONTRADO` del backend → estado "no encontrado" re-skinneado |
| Responsive mobile (375px) | Sin overflow horizontal en ninguna de las 2 páginas |

## 6. Estado final

| | |
|---|---|
| Páginas nuevas | `index.html`, `evento-detalle.html` |
| Endpoints nuevos | Ninguno — se conectó el Módulo 4 ya existente |
| Cambio de backend | 1 línea en `SecurityConfig` (`permitAll` de `/evento-detalle.html`) |
| Tests | 168 pasan, 0 fallos |
| Deuda de diseño anotada | La ficha "evento encontrado" no tiene Figma de referencia todavía; el perfil de Participante puro no existe |
