# 2026-10-16 — Correcciones de diseño y lógica (admin, formulario de evento, catálogo)

1. **Bordes del panel admin**: las 4 pantallas (`pages/admin/*.html`) usaban `--radio` (10px), píldoras `999px` y valores fijos de 12–24px; `moderacion-eventos` además redefinía `--radio-tarjeta: 16px`. Ahora todo usa los tokens cuadrados (`--radio-sm`/`--radio-tarjeta`/`--radio-campo`). `50%` se conserva solo en avatares, puntos y botones redondos. La regla quedó escrita en `CLAUDE.md` / `CLAUDE_es-ES.md` ("Design Rules").
2. **"Usuarios activos" (Control de Suscripciones)**: la tarjeta de plan contaba organizadores sin baja pero incluía bloqueados/suspendidos. Ahora `UsuarioRepository.contarOrganizadores()` exige `estado = ACTIVO` y el plan Pro usa `SuscripcionRepository.contarPorEstadoYPlanConCuentaActiva`. Bloquear o dar de baja a un usuario lo resta. El KPI de "suscripciones Pro activas" no cambia.
3. **Botones "Crear Evento"**: se quitó el ícono/texto "+" en sidebar de organizador (dashboard, equipo, evento-form, mis-eventos, perfil), `index.html` y `evento-detalle.html`.
4. **Formulario de evento**: eliminados los rótulos "Tabla: ..." (4 secciones) y su CSS.
5. **Detalle de evento para admin**: ya no aparece el botón de inscripción ni el aviso "Las cuentas de administración no se inscriben".
6. **Barra de filtros (index)**: orden nuevo — Fecha, Ubicación, Categoría, Orden asc/desc, Búsqueda por texto, Botón Buscar.
7. **"Cargar más eventos"**: el tamaño de página era fijo (9) y con 5 columnas la última fila quedaba incompleta. Ahora el tamaño es múltiplo de las columnas visibles (calculado en cada búsqueda nueva), así cada tanda completa filas.

Pendiente de verificar visualmente con `bootRun` (no se probó en navegador en esta sesión).
