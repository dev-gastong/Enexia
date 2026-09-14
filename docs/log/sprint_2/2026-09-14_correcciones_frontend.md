# Correcciones de frontend: landing, ficha de evento y login

**Fecha:** 2026-09-11 a 2026-09-14 (continuación de sprint_2, sobre lo construido en [2026-09-10_landing_publica.md](./2026-09-10_landing_publica.md) y [2026-09-10_evento-detalle_figma_real.md](./2026-09-10_evento-detalle_figma_real.md))
**Motivo:** el usuario probó manualmente las pantallas ya construidas (`index.html`, `evento-detalle.html`, login, panel de organizador) y reportó una serie de bugs de UI/UX en varias rondas de testing.
**Resultado:** ✅ 10 bugs corregidos, todos con causa raíz identificada en código · ✅ 1 bug de tests (Page Object Model) corregido de paso · ✅ 0 cambios de backend · ✅ pusheado a `origin/main` (commit `cf34f9f`)

---

## 1. Bugs corregidos

### 1.1 — "Cargar más eventos" siempre visible
`index.html` mostraba el botón incluso con 0 o 1 evento. Causa: `.mas-eventos { display: flex; ... }` es una regla de autor, y una regla de autor **siempre** le gana a la regla de user-agent `[hidden]{display:none}`, sin importar que la especificidad "se vea" igual — el atributo `hidden` que el JS ponía correctamente quedaba sin ningún efecto visual. Fix: `.mas-eventos[hidden] { display: none; }`. Verificado con 0, 1 y 118 eventos (`hidden` + `getComputedStyle().display` por JS, no solo a ojo).

### 1.2 — Logo "NEXIA" se veía como un cuadrado sólido
Al mismo SVG le faltaban 3 rectángulos superpuestos (fill claro) que recortan la forma de la "E" por sustracción visual. Esos 3 paths **sí** existían en las páginas de login/registro, pero se perdieron al copiar el SVG a otras 6 páginas: `index.html`, `evento-detalle.html`, `dashboard.html`, `mis-eventos.html`, `perfil.html`, `evento-form.html`. Se agregaron los 3 paths faltantes en las 6, idénticos a los ya usados en login/registro.

### 1.3 — Login no redirigía a `index.html`
El submit de `login-desktop-claro.html` seguía apuntando a `pages/dashboard.html`, la vieja página de debug de JWT de Sprint 1, con un `setTimeout` de 100ms y `console.log`s de debug ("Debug: verificar que se guardó") — cargo cult, ya que `sessionStorage.setItem` es síncrono y no hace falta esperar nada. Se corrigió el redirect a `../../index.html` y se limpió el código de debug. Verificado end-to-end: registro de una cuenta de prueba real vía API + login por el formulario real → termina en `index.html` con el header autenticado.

### 1.4 — Header no centrado
El menú central (Explorar / Contacto / Legales) de `index.html` y `evento-detalle.html` no quedaba centrado respecto al ancho total de la barra. Causa: `display:flex; justify-content:space-between` con 3 hijos de ancho muy distinto (el logo es angosto, el bloque de cuenta con avatar+email+botones es ancho) — `space-between` no centra el elemento del medio respecto al contenedor, lo ubica según el espacio sobrante de cada lado, que no es simétrico si los extremos pesan distinto. Fix: `display:grid; grid-template-columns: 1fr auto 1fr` con `justify-self: start/center/end` en cada bloque. Verificado por JS: el centro del nav coincide con el centro de la barra con menos de 0.01px de diferencia.

### 1.5 — Bloque de cuenta "en el medio" en mobile (regresión del fix anterior)
El usuario lo encontró probando en un ancho intermedio (~800px): con el nav oculto (`display:none` bajo 900px), el bloque de cuenta dejaba de auto-ubicarse en la 3ª columna del grid y caía en la 2ª (la que le sobra a `auto` cuando el hijo del medio desaparece), dejando la 3ª columna entera vacía a la derecha — el bloque de cuenta se veía centrado en vez de pegado al borde, con un hueco después. **Lección:** el auto-placement de CSS Grid reubica los ítems restantes cuando uno se oculta con `display:none`; no asumir que "cada hijo cae en su columna" si alguno puede desaparecer. Fix: `grid-column: 1 / 2 / 3` explícito en marca/nav/acciones. De paso se truncó el email largo con `ellipsis` (160px desktop, 90px mobile) para que no empuje el layout. Verificado en 375px y 800px.

### 1.6 — Barra de búsqueda no centraba al envolver
Cuando "Ubicación" y "Buscar" no entraban en la fila y bajaban a una línea nueva, quedaban pegados a la izquierda. Fix: `justify-content: center` en `.busqueda__caja`. Verificado en 800px (el ancho donde se reproducía).

### 1.7 — Checkbox de "Selección de Cronograma" no se podía marcar
Era un `<span>` puramente decorativo en vez de un `<input type="checkbox">` real, así que ni siquiera se podía tildar. Se convirtió en checkbox real con `accent-color: var(--acento)`. Sigue sin estar atado a ninguna lógica de inscripción — eso continúa pendiente del módulo de pagos — pero ahora al menos responde al click.

### 1.8 — Letra del avatar del organizador casi invisible
El avatar circular violeta junto a "Organizado por: X" en `evento-detalle.html` mostraba la inicial casi invisible. Causa: colisión de especificidad CSS. `.ficha__organizador span { color: var(--texto-secundario) }` (pensada solo para el texto "Organizado por...") tiene especificidad clase+tipo, mayor que `.ficha__organizador-avatar { color: #fff }` (una sola clase) — y como el avatar **también** es un `<span>`, la regla genérica le pisaba el blanco con un gris apagado, casi invisible sobre fondo violeta. Fix: se le dio clase propia al texto (`.ficha__organizador-texto`) en vez de depender de un selector genérico `span`, eliminando la colisión de raíz. Verificado por JS: `getComputedStyle().color` pasó a `rgb(255, 255, 255)`.

### 1.9 — Imágenes de la ficha no eran clickeables
Se agregó un lightbox en `evento-detalle.html`: click en la portada o en cualquier foto de la galería la abre centrada y grande (máx. 90vw / 85vh) sobre fondo negro semitransparente (`rgba(0,0,0,.75)`), con cierre por click afuera de la imagen, botón X arriba a la derecha, o tecla Escape. Implementado con **event delegation en `document`** — la ficha se re-renderiza dinámicamente con `innerHTML` en cada carga, así que un listener puesto directo en cada `<img>` se perdería en cada render. El mismo bug de la sección 1.1 (`[hidden]` pierde contra un `display` de autor) se previno desde el arranque con `.lightbox[hidden] { display: none; }`.

### 1.10 — Ícono del footer
A pedido del usuario, se reemplazó el ícono genérico de "casita" dentro de un cuadrado violeta por la misma marca "E" de colores del navbar (los mismos 15 paths SVG). Primera iteración: 12px dentro del cuadrado violeta de fondo. El usuario lo vio "feo con el fondo" y pidió solo la E, más grande — se sacó el cuadrado violeta por completo y se agrandó el ícono a 26px sin contenedor.

## 2. De paso: fix en los tests (no reportado por el usuario)

Al revisar el estado del repo antes de pushear apareció modificado `LoginPage.java`/`RegisterPage.java` (Page Object Model de los tests Playwright): el paquete estaba declarado como `pages` en vez de `com.enexia.rg.pages`, que es el paquete real según la carpeta (`src/test/java/com/enexia/rg/pages/`). Se incluyó en el mismo commit junto con un reformateo de indentación que ya venía en el working tree.

## 3. Verificación

Cada fix se probó en vivo contra el backend real (`gradle bootRun`), no se dio nada por bueno "a ojo": se verificó con JavaScript (`getBoundingClientRect`, `getComputedStyle`, lectura de atributos `hidden`/`checked`) además de capturas visuales, en varios anchos de viewport (375px, 800px, desktop). Cero cambios de backend en toda la sesión — los 9 archivos tocados son estáticos de frontend + 2 de tests Playwright.

## 4. Cierre

Se commiteó todo (9 archivos; se excluyó `.vscode/settings.json` por ser configuración personal del editor, sin un `.gitignore` que lo cubra) y se pusheó a `origin/main` — commit `cf34f9f`.

**No se sumaron pantallas nuevas** al panel de organizador: Mi Equipo y Plan Pro siguen sin construir. El conteo "4/~6 pantallas" del estado del sprint no cambia con esta sesión — fue pura corrección de lo ya construido.
