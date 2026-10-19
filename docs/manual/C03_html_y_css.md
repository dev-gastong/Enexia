# C03 — HTML y CSS: la estructura y el diseño de las pantallas

> **Parte C · Del servidor al navegador (cierre).** JavaScript manipula lo que **ya existe**; alguien tuvo que definir **qué elementos hay** (HTML) y **cómo se ven** (CSS). Este capítulo explica ambos desde cero, con las pantallas reales de Enexia, y el **sistema de diseño** del proyecto (`tokens.css`).

**Qué vas a aprender**
- La estructura de un documento HTML y las etiquetas que se usan.
- Formularios: `label`, tipos de `input`, atributos, accesibilidad.
- CSS: selectores, cascada y especificidad, modelo de caja, unidades, `flex` y `grid`, posición.
- **Variables CSS** y el **sistema de tokens** (color, forma, tipografía).
- Estados (`:hover`, `:focus-within`), transiciones y **diseño adaptable** (`@media`).
- Nombres de clases (**BEM**) y organización de las hojas.
- Accesibilidad y **contraste** (WCAG).
- Las reglas de diseño del proyecto (paleta clara, esquinas cuadradas).

---

## 1. HTML: el esqueleto

**HTML** (*HyperText Markup Language*) no es un lenguaje de programación: es un lenguaje de **marcado**. Describe **qué es** cada parte del contenido (un título, un párrafo, un enlace, un campo de formulario) usando **etiquetas**.

### 1.1 Anatomía de un elemento

```html
<a href="login.html" class="enlace">Iniciar sesión</a>
 ↑      ↑                ↑            ↑            ↑
etiqueta atributo        atributo     contenido    etiqueta de cierre
de apertura
```

- Un **elemento** = etiqueta de apertura + contenido + etiqueta de cierre.
- Los **atributos** (`href`, `class`, `id`) agregan información: `nombre="valor"`.
- Algunos elementos **no tienen contenido** (*vacíos*): `<input>`, `<img>`, `<br>`, `<meta>`.
- Los elementos se **anidan** formando un árbol (el DOM de C02): cada uno debe cerrarse **dentro** del que lo contiene.

### 1.2 Estructura de un documento

`pages/auth/password-reset.html` (recortado):

```html
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Recuperar contrase&ntilde;a</title>

    <link rel="stylesheet" href="../css/tokens.css">
    <link rel="stylesheet" href="../css/recuperacion.css">
</head>

<body>
    <header class="barra"> ... </header>
    <main class="centro"> ... </main>
    <footer class="pie"> ... </footer>

    <script src="../../js/api.js"></script>
    <script> /* script propio */ </script>
</body>
</html>
```

| Pieza | Para qué |
|---|---|
| `<!DOCTYPE html>` | Le dice al navegador que use HTML5 (sin esto entra en "modo de compatibilidad" con reglas viejas). |
| `<html lang="es">` | Idioma del documento: lo usan lectores de pantalla y traductores. |
| `<head>` | Metadatos: **no se muestra**. |
| `<meta charset="UTF-8">` | Codificación de caracteres (acentos, ñ). Debe ser lo primero. |
| **`<meta name="viewport" ...>`** | **Imprescindible en celulares:** sin esta línea el teléfono simula una pantalla de escritorio y achica todo. |
| `<title>` | El título de la pestaña y de los resultados de búsqueda. |
| `<link rel="stylesheet">` | Incluye una hoja de estilos. **`tokens.css` va primero** (define las variables que usan las demás). |
| `<body>` | Lo que se ve. |
| `<script>` al final | El JavaScript va **después** del contenido (C02 §1.1). |

### 1.3 Entidades

Para escribir caracteres especiales o reservados se usan **entidades**: `&ntilde;` = ñ, `&iexcl;` = ¡, `&aacute;` = á, `&lt;` = `<`, `&gt;` = `>`, `&amp;` = `&`, `&quot;` = `"`, `&middot;` = ·. Con `charset="UTF-8"` se pueden escribir directo (`ñ`), pero varias pantallas del proyecto usan las entidades. **Son las mismas que usa `UI.esc` para escapar** (C02 §12): escapar un texto es convertir sus caracteres reservados en entidades.

### 1.4 HTML semántico: elegir la etiqueta por lo que **es**

No uses `<div>` para todo. Cada etiqueta **comunica significado** a navegadores, buscadores y lectores de pantalla.

| Etiqueta | Qué es | Dónde se usa en Enexia |
|---|---|---|
| `<header>`, `<main>`, `<footer>` | Cabecera, contenido principal (uno por página), pie. | Pantallas de recuperación. |
| `<nav>` | Navegación. | Barras de menú. |
| `<section>`, `<article>` | Sección temática / contenido autónomo. | Cada tarjeta de evento es un `<article class="tarjeta-evento">`. |
| `<h1>`–`<h6>` | Títulos jerárquicos (un solo `<h1>` por página). | `<h1 class="encabezado__titulo">Recuperar contraseña</h1>` |
| `<p>`, `<ul>`/`<li>`, `<strong>`, `<em>` | Párrafo, lista, énfasis. | Requisitos de contraseña (`<ul>` + `<li>`). |
| `<a href>` | **Enlace** (navega a otra URL). | "Volver al inicio de sesión". |
| `<button type>` | **Botón** (ejecuta una acción). | "Mostrar contraseña", "Enviar". |
| `<form>`, `<label>`, `<input>`, `<select>`, `<textarea>` | Formularios. | Todo el registro. |
| `<img src alt>` | Imagen. | Portadas de eventos. |
| `<svg>` | Gráfico vectorial en línea. | Íconos. |
| `<div>`, `<span>` | Contenedores **sin significado** (bloque / en línea). | Cuando ninguna otra etiqueta aplica. |

**`<a>` o `<button>`:** un **enlace navega**; un **botón actúa**. Poner `<a>` para un botón (o viceversa) confunde a los lectores de pantalla y al teclado. En Enexia, "Volver a iniciar sesión" es un `<a>` y "Enviar enlace" es un `<button>`.

**Elementos de bloque y en línea:** los de **bloque** (`div`, `p`, `h1`, `section`) ocupan todo el ancho y apilan; los **en línea** (`span`, `a`, `strong`) fluyen dentro del texto. CSS puede cambiarlo (`display`).

---

## 2. Formularios

El formulario es **la entrada de datos**. `pages/auth/password-reset.html`:

```html
<form id="formPedido" novalidate>
    <div class="campo" id="campoEmail">
        <label class="campo__etiqueta" for="email">Correo electr&oacute;nico</label>
        <div class="entrada">
            <svg class="entrada__icono" viewBox="0 0 16 16" aria-hidden="true"> ... </svg>
            <input type="email" id="email" name="email" maxlength="150"
                   placeholder="correo@ejemplo.com" autocomplete="email" required>
        </div>
        <span class="campo__error"></span>
    </div>

    <button type="submit" class="boton" id="btnPedir">Enviar enlace de recuperaci&oacute;n</button>
</form>
```

| Elemento / atributo | Qué hace |
|---|---|
| `<form>` | Agrupa los campos y dispara el evento `submit`. |
| `novalidate` | Desactiva la validación **automática del navegador** (con mensajes de burbuja en su idioma). Enexia valida **por su cuenta** y muestra errores con su propio estilo (JS + backend). |
| **`<label for="email">`** | **Etiqueta asociada al campo** por `id`. Al hacer clic en el texto se enfoca el campo, y un lector de pantalla lo anuncia. **Todo campo debe tener su `label`**: es también lo que usan los tests (`page.getByLabel("Correo electrónico")`, B06). |
| `type="email"` / `"password"` / `"text"` / `"date"` / `"number"` / `"checkbox"` / `"radio"` | Tipo de entrada: cambia el teclado en celular y el comportamiento (`password` oculta los caracteres). |
| `id` | Identificador **único** en la página (lo usa `label for`, CSS y JavaScript: `getElementById('email')`). |
| `name` | El nombre del dato al enviarse (en este proyecto el envío lo hace JS con `fetch`, pero se mantiene por buenas prácticas). |
| `placeholder` | Texto de ayuda dentro del campo vacío. **No reemplaza al `label`.** |
| `required`, `maxlength`, `min`, `max`, `pattern` | Reglas que el navegador entiende (comodidad; **el backend valida siempre**). |
| `autocomplete="email"` / `"new-password"` / `"current-password"` / `"username"` | Ayuda al **gestor de contraseñas** del navegador: `new-password` al elegir una clave, `current-password` al iniciar sesión. |
| `<button type="submit">` | Envía el formulario. `type="button"` **no** lo envía (para botones auxiliares como "mostrar contraseña"). **Siempre declará el `type`**: el valor por defecto es `submit`. |

### 2.1 Mostrar/ocultar contraseña: accesible

```html
<button type="button" class="entrada__accion" id="verClave" aria-label="Mostrar contrase&ntilde;a">
    <svg ... aria-hidden="true"> ... </svg>
</button>
```

El botón solo tiene un ícono, sin texto: su **nombre accesible** viene de `aria-label`. El ícono se marca `aria-hidden="true"` (decorativo: el lector no lo lee). Y el JavaScript **actualiza** el `aria-label` según el estado (C02 §6.2).

### 2.2 Un contenedor de error por campo

Cada campo trae su `<span class="campo__error"></span>` **vacío**: allí `UI.pintarErrores` coloca el mensaje de validación del backend, y CSS lo muestra solo cuando el contenedor tiene la clase `campo--invalido`.

---

## 3. Accesibilidad (a11y)

Una pantalla accesible la puede usar **cualquier persona**: con lector de pantalla, solo con teclado, con baja visión o daltonismo. No es un extra: muchas prácticas son gratis si usás bien HTML.

| Práctica | Cómo |
|---|---|
| **Etiquetas de formulario** | `label for` en cada campo (§2). |
| **Texto alternativo** | `<img alt="...">` describe la imagen; `alt=""` si es decorativa. |
| **Íconos decorativos** | `aria-hidden="true"`; íconos **con** significado, `aria-label`. |
| **Un solo `h1`** y jerarquía ordenada | Estructura legible para lectores. |
| **Anuncios dinámicos** | `role="alert"` en los avisos de error (se anuncian solos); `aria-live="polite"` en los requisitos de contraseña que cambian mientras escribís. |
| **Foco visible** | Nunca quitar el contorno de foco sin reemplazarlo (`:focus-within`, `:focus-visible`). |
| **Teclado** | Todo debe poder hacerse con Tab/Enter: por eso `<button>` y `<a>` reales, no `<div onclick>`. |
| **Contraste** | Ver §10: el texto debe distinguirse del fondo (≥ 4,5:1). |
| **Idioma** | `<html lang="es">`. |
| **No depender solo del color** | Un error se marca con **texto** además de rojo. |

El atributo **`hidden`** merece mención: oculta un elemento **del todo** (también para lectores). Las pantallas lo alternan desde JS (`el.hidden = true`) para mostrar un estado u otro (formulario / "correo enviado"). El CSS del proyecto incluye `[hidden] { display: none !important; }` para que ninguna regla de `display` lo anule.

---

## 4. SVG en línea

Los íconos son **gráficos vectoriales** (`<svg>`) escritos **directamente en el HTML**: se ven nítidos a cualquier tamaño, no piden otro pedido de red y **se pueden pintar con CSS**:

```html
<svg class="entrada__icono" viewBox="0 0 16 16" fill="none" aria-hidden="true">
    <g stroke="currentColor" stroke-width="1.33333" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3.33333 7.33333H12.6667C13.4025 ... "/>
    </g>
</svg>
```

- `viewBox="0 0 16 16"` define el **sistema de coordenadas** interno; el tamaño real lo da CSS (`width: 16px`).
- **`stroke="currentColor"`**: el trazo toma **el color del texto** (`color` de CSS). Así el ícono sigue la paleta (`.entrada__icono { color: var(--acento); }`) **sin escribir un color dentro del SVG**.
- La excepción: el isotipo de la marca usa colores literales (`fill="#F28C1E"`), porque **es** la marca.

---

## 5. CSS: el diseño

**CSS** (*Cascading Style Sheets*) describe **cómo se ve** el HTML. Una hoja es una lista de **reglas**:

```css
.boton {                       /* selector: A QUÉ elementos */
    padding: 14px 0;           /* declaración: propiedad: valor; */
    border-radius: var(--radio-campo);
    background: var(--accion);
}
```

### 5.1 Selectores

| Selector | Elige | Ejemplo real |
|---|---|---|
| `etiqueta` | todos los elementos de esa etiqueta | `body`, `a` |
| **`.clase`** | los que tienen `class="clase"` | `.boton`, `.tarjeta` |
| `#id` | el que tiene ese `id` (evitalo para estilos) | `#mensajeError` |
| `a b` | `b` **dentro** de `a` | `.requisitos ul` |
| `a > b` | `b` **hijo directo** de `a` | |
| `a + b` | `b` **justo después** de `a` | `.tipo input:checked + .tipo__opcion` |
| `a, b` | cualquiera de los dos | |
| `[atributo]` | por atributo | `[hidden]` |
| `:estado` (*pseudo-clase*) | por estado | `:hover`, `:focus`, `:focus-within`, `:disabled`, `:checked`, `:first-child` |
| `::parte` (*pseudo-elemento*) | una parte virtual | `.separador::before` |

**`:focus-within`** merece un ejemplo: el contenedor `.entrada` cambia de borde **cuando el input que contiene tiene el foco**:

```css
.entrada:focus-within {
    border-color: var(--acento);
    box-shadow: var(--foco-accion);
}
```

**`::before` / `::after`** crean elementos "de adorno" desde CSS, con `content`:

```css
.separador::before,
.separador::after {
    content: "";            /* un elemento vacío que se dibuja */
    flex: 1 1 0;
    height: 1px;
    background: var(--borde);
}
```

Dos líneas a los costados del texto "o": **sin tocar el HTML**.

### 5.2 La cascada y la especificidad

Cuando **varias reglas** aplican al mismo elemento, gana una. El criterio, en orden:

1. **`!important`** (evitarlo: rompe la lógica; el proyecto solo lo usa en `[hidden]`).
2. **Especificidad:** `#id` (alta) > `.clase`, `[atributo]`, `:pseudo-clase` (media) > `etiqueta` (baja).
3. **Orden:** ante empate, **la última** que se declaró.

Por eso **el orden de los `<link>`** importa (los tokens primero, después los estilos de la página) y por eso se usan **clases** (especificidad media y uniforme) en vez de ids o selectores larguísimos.

**Herencia:** algunas propiedades (`color`, `font-family`) **pasan de padre a hijo** solas; otras (`border`, `padding`) no.

### 5.3 El modelo de caja

Todo elemento es una **caja** con capas, de adentro hacia afuera:

```
┌──────────────── margin (afuera, transparente) ───────────────┐
│  ┌──────────── border (el borde) ─────────────┐              │
│  │  ┌──────── padding (espacio interno) ────┐ │              │
│  │  │            contenido                   │ │              │
│  │  └────────────────────────────────────────┘ │              │
│  └─────────────────────────────────────────────┘              │
└────────────────────────────────────────────────────────────────┘
```

Por defecto, `width` mide **solo el contenido** (y el padding y borde se suman: una caja de 100 % + padding se sale). La regla que Enexia aplica en **toda** hoja:

```css
* { box-sizing: border-box; }
```

Con `border-box`, `width` **incluye** padding y borde: lo que declarás es lo que ocupa. Es la primera línea de casi cualquier CSS moderno.

### 5.4 Unidades

| Unidad | Qué es | Uso en el proyecto |
|---|---|---|
| `px` | píxeles fijos | bordes, íconos, radios |
| `%` | relativo al contenedor | anchos fluidos |
| `rem` / `em` | relativo al tamaño de fuente | tamaños escalables |
| `vw` / `vh` | % del ancho / alto de la ventana | `min-height: 100vh` |
| `fr` | "fracción" del espacio en `grid` | `grid-template-columns: 1fr 1fr` |
| **`clamp(min, ideal, max)`** | un valor que **crece con la pantalla** entre un mínimo y un máximo | `padding: clamp(24px, 5vw, 32px)` |

```css
.encabezado__titulo { font-size: clamp(24px, 4vw, 30px); }
```

El título mide 24 px en un celular, 30 px como máximo en una pantalla grande, y en el medio crece con el ancho: **responsivo sin `@media`**.

### 5.5 Flexbox: acomodar en una dimensión

`display: flex` hace que los **hijos** se alineen en fila (o columna) y se repartan el espacio. Es la herramienta más usada del proyecto:

```css
.entrada {
    display: flex;
    align-items: center;      /* eje vertical (cruzado) */
    gap: 12px;                /* separación entre hijos */
}

.barra {
    display: flex;
    align-items: center;
    justify-content: space-between;   /* eje horizontal (principal): extremos opuestos */
}

.centro {
    display: flex;
    flex-direction: column;           /* en columna */
    align-items: center;
    justify-content: center;          /* centrado en ambos ejes */
}
```

| Propiedad (en el contenedor) | Hace |
|---|---|
| `flex-direction: row \| column` | Eje principal. |
| `justify-content` | Reparte a lo largo del eje principal (`flex-start`, `center`, `space-between`). |
| `align-items` | Alinea en el eje cruzado (`center`, `stretch`). |
| `gap` | Separación. |
| `flex-wrap: wrap` | Permite saltar de línea. |
| **En un hijo:** `flex: 1 1 0` | "crecé para llenar el espacio, podés achicarte, base 0". `flex: 0 0 16px` = "ancho fijo de 16 px". |

En `.entrada`, el ícono tiene `flex: 0 0 16px` (fijo) y el `<input>` `flex: 1 1 0; min-width: 0` (ocupa el resto; `min-width: 0` evita que el campo desborde su contenedor).

### 5.6 Grid: acomodar en dos dimensiones

```css
.seccion {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 16px;
}
```

Lectura: *"tantas columnas como entren, cada una de **al menos 220 px** y repartiéndose el resto en partes iguales"*. Con una pantalla ancha hay 4 columnas; en un celular, 1: **sin una sola `@media`**. Es el patrón de las grillas de formulario y de tarjetas del catálogo (el JS del `index.html` cuenta las columnas leyendo `gridTemplateColumns`, C02 §11.2).

`grid-column: 1 / -1` hace que un elemento **ocupe todas las columnas** (de la línea 1 a la última).

### 5.7 Posicionamiento

| `position` | Comportamiento |
|---|---|
| `static` (por defecto) | Flujo normal. |
| `relative` | Flujo normal, pero **sirve de referencia** para sus hijos absolutos. |
| `absolute` | Se saca del flujo y se ubica respecto del ancestro `relative` más cercano (`top`, `left`, `inset: 0`). |
| `fixed` | Fijo respecto de la ventana (barras flotantes, *toasts*). |
| `sticky` | Normal hasta que el scroll lo "pega". |

Las capas de fondo del login/recuperación se arman así: `.pantalla { position: relative }` y la foto, el velo y los halos son `position: absolute; inset: 0`, apilados con `z-index`. El contenido va encima con `position: relative; z-index: 1`.

### 5.8 Tipografía y color

```css
body {
    font-family: var(--sans);       /* pila de fuentes: la primera que exista */
    color: var(--texto-secundario);
    -webkit-font-smoothing: antialiased;
}
```

La **pila de fuentes** (`--sans: 'DM Sans', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif`) lista alternativas **en orden**; el navegador usa la primera que tenga. Las fuentes (DM Sans, Space Grotesk) se cargan desde Google Fonts con un `<link>` en el `<head>`.

Formas de escribir un color: `#7c3aed` (hexadecimal), `rgb(124, 58, 237)`, `rgba(124, 58, 237, .20)` (con transparencia), `hsl(...)`. **En Enexia ninguno se escribe directo**: se referencian por nombre (`var(--acento)`), ver §6.

---

## 6. Variables CSS y el sistema de tokens

### 6.1 Variables CSS (*custom properties*)

```css
:root {
    --acento: #7c3aed;
}
.enlace { color: var(--acento); }
```

- Se declaran con `--nombre: valor` dentro de un selector; **`:root`** (el elemento raíz) las hace **globales**.
- Se usan con **`var(--nombre)`**; admiten un valor por defecto: `var(--nombre, rojo)`.
- A diferencia de una constante del preprocesador, **se resuelven en el navegador** y **se pueden cambiar en ejecución** (por ejemplo, un modo oscuro).

### 6.2 `tokens.css`: la única fuente de verdad

`enexia/src/main/resources/static/pages/css/tokens.css` define **todas** las decisiones visuales del producto. Su encabezado fija la regla: *"Ninguna otra hoja ni página debe escribir un color en hexadecimal: si hace falta un tono nuevo, se agrega acá."* Secciones:

| Sección | Ejemplos de tokens |
|---|---|
| **1. Marca** | `--accion` (naranja del botón), `--acento` (morado de enlaces, íconos y foco). |
| **2. Neutros** | `--lienzo` (fondo), `--superficie` (tarjetas), `--borde`, `--texto`, `--texto-secundario`. |
| **3. Escenario** | `--velo`, `--halo-1`, `--foto-filtro`. |
| **4–6. Estados y categorías** | `--ok`/`--error`/`--alerta`/`--info` (+ `-tinte`, `-borde`), `--cat-cultural`... |
| **7. Layout** | `--ancho-contenido`. |
| **8. Forma** | `--radio-campo`, `--radio-tarjeta`, `--sombra-tarjeta`. |
| **9. Tipografía** | `--sans`, `--marca`, `--mono`. |

**Por qué es valioso:**
1. **Un solo lugar** para cambiar la identidad visual.
2. **Coherencia:** nadie "inventa" un morado distinto en una pantalla.
3. **Contraste verificado:** cada color de texto fue **medido** contra WCAG 2.1 AA (hay un auditor en `js/auditoria-contraste.js`) y el resultado está anotado al lado del valor (`#c2410c; /* blanco encima: 5.18:1 */`).

### 6.3 Roles de color (decisión de diseño)

El comentario de `tokens.css` lo cuenta: **el naranja es exclusivo de la acción** (el botón principal es el único elemento cálido de la pantalla, así no compite con nada) y **el morado ocupa todo lo demás** (enlaces, íconos, foco, chips). El naranja del isotipo (`#F28C1E`) **no sirve como relleno de botón**: con texto blanco da un contraste de 2,46:1, y el requisito es 4,5:1; por eso se eligió `#C2410C` (5,18:1). Un buen ejemplo de **diseñar con datos**.

### 6.4 Convención: el hover **oscurece**, nunca aclara

`--accion-hover: #9a330a; /* 7.36:1 - oscurece, NUNCA aclara */`. Un naranja más claro bajaría el contraste con el texto blanco: la regla evita romper la accesibilidad en el estado hover.

### 6.5 Las reglas de diseño vigentes del proyecto (`CLAUDE.md`)

- **Esquinas cuadradas, en todas partes.** Solo se usan los tokens `--radio-campo` (4 px), `--radio-tarjeta` (6 px) y `--radio-sm` (6 px). **Nunca** valores sueltos (12/14/16/24 px) ni píldoras `999px`. `50%` solo para avatares, puntos de estado y botones redondos de ícono.
- **Paleta clara** (violeta y naranja sobre fondos claros), no la oscura de algunos prototipos de Figma.
- **Sin "+"** en los botones "Crear Evento".
- **Sin nombres internos de tablas** en formularios (`Tabla: ...`).
- Para saber más, abrí un archivo reciente y mirá cómo usa solamente `var(--...)`.

---

## 7. Estados, transiciones y animación

```css
.boton {
    background: var(--accion);
    transition: background .15s ease, transform .15s ease;   /* anima estos cambios en 150 ms */
}
.boton:hover   { background: var(--accion-hover); }
.boton:active  { transform: translateY(1px); }               /* se "hunde" al presionar */
.boton:disabled { opacity: .6; cursor: not-allowed; transform: none; }
```

- **`:hover`** (el mouse encima), **`:active`** (presionado), **`:focus` / `:focus-visible`** (con el teclado), **`:disabled`**.
- **`transition`** suaviza el cambio entre dos estados; es barata y mejora la percepción. Se animan propiedades que no fuerzan *relayout* (`transform`, `opacity`, `background`, `box-shadow`).
- Los estados del formulario se **alternan con clases desde JS** (`campo--invalido`) y CSS reacciona:

```css
.campo--invalido .entrada { border-color: var(--error); }
.campo__error { display: none; color: var(--error); }
.campo--invalido .campo__error { display: block; }
```

El JavaScript solo **agrega/quita una clase**; **toda la presentación vive en CSS**. Es la separación correcta de responsabilidades.

---

## 8. Diseño adaptable (*responsive*)

Las pantallas se usan en un celular, una tableta y un monitor ancho. El enfoque de Enexia:

1. **Meta viewport** en el HTML (§1.2).
2. **Unidades y funciones flexibles** (`%`, `clamp`, `minmax`, `auto-fit`) para que el diseño **se adapte solo**.
3. **`@media`** (*media queries*) para los cambios que no se pueden resolver con fluidez:

```css
@media (max-width: 560px) {
    .barra { padding: 16px 20px; }
    .volver span { display: none; }        /* en celular, solo el ícono de volver */
}
```

Se lee: *"cuando la ventana mida 560 px o menos, aplicá estas reglas"*. Los **puntos de quiebre** habituales del proyecto: **1024 px** (de dos columnas a una) y **560 px** (celular).

**Enfoque *mobile-first*** (recomendado en `CLAUDE.md`): escribir primero el estilo base para pantalla chica y **ampliar** con `min-width`. Varias hojas del proyecto hacen lo contrario (`max-width`), válido pero más propenso a sobrescrituras.

**Probalo:** en las DevTools (F12) activá el modo dispositivo (ícono de celular) y cambiá el tamaño.

---

## 9. Nombres de clases: BEM

Las clases de Enexia siguen una variante de **BEM** (*Block – Element – Modifier*):

```
.tarjeta-evento                 ← BLOQUE: un componente independiente
.tarjeta-evento__nombre         ← ELEMENTO: una parte del bloque (dos guiones bajos)
.tarjeta-evento__pie
.boton--secundario              ← MODIFICADOR: una variante (dos guiones medios)
.campo--invalido                ← MODIFICADOR de estado
.requisito--ok
```

- **Bloque:** `tarjeta-evento`, `campo`, `boton`, `entrada`, `aviso`.
- **Elemento:** `__` + nombre (`campo__etiqueta`, `campo__error`, `entrada__icono`).
- **Modificador:** `--` + variante (`boton--secundario`).

**Por qué:**
- **Cero ambigüedad:** `.campo__error` solo puede ser "el error de un campo".
- **Baja especificidad uniforme** (todas son una clase): no hay guerras de especificidad.
- **Se lee el HTML:** `class="tarjeta-evento__nombre"` te dice a qué componente pertenece.
- **Seguro de renombrar:** buscás el bloque y ves todo.

Regla complementaria: las clases con prefijo **`js-`** son **ganchos de JavaScript** (`js-btn-inscribir`), **no tienen estilo**. Así se puede cambiar el diseño sin romper el comportamiento, y viceversa.

---

## 10. Contraste y accesibilidad visual (WCAG)

**WCAG 2.1 AA** exige un **contraste mínimo** entre el texto y su fondo: **4,5:1** para texto normal y **3:1** para texto grande o componentes de interfaz. El ratio se calcula a partir de la **luminancia** de los dos colores (de 1:1, iguales, a 21:1, negro sobre blanco).

En Enexia el contraste **se mide**, no se supone. En `tokens.css` cada color de texto lleva anotado su ratio (`--texto: #1f1a2e; /* 16.86:1 sobre blanco */`, `--texto-secundario: #5c5470; /* 7.10:1 */`) y el archivo `js/auditoria-contraste.js` es un **auditor** que recorre la página renderizada y compone capas (foto, filtro, velos, halos) para comprobarlo de verdad: *"fueron medidos contra WCAG 2.1 AA sobre la página renderizada, no sobre estimaciones"*.

Otras decisiones: no usar solo el color para transmitir un estado, tamaños de texto legibles (≥ 12 px en secundarios, 14–16 px en cuerpo) y áreas de clic cómodas (los botones tienen `padding` generoso).

---

## 11. Organización de las hojas de estilo

Orden de carga, de lo más general a lo más específico (**la cascada lo exige**):

```html
<link rel="stylesheet" href="../css/tokens.css">         <!-- 1. variables: SIEMPRE primero -->
<link rel="stylesheet" href="../css/styles.css">          <!-- 2. componentes reutilizables -->
<link rel="stylesheet" href="../css/recuperacion.css">    <!-- 3. estilos compartidos por un grupo de pantallas -->
<style> /* 4. lo exclusivo de ESTA pantalla */ </style>
```

| Archivo | Qué contiene |
|---|---|
| `pages/css/tokens.css` | Variables: color, forma, tipografía. **Nada de reglas.** |
| `pages/css/styles.css` | Componentes comunes (barra, formulario, botones, tarjetas, consola de pruebas). **No define colores.** |
| `pages/css/recuperacion.css` | Estilos compartidos por las dos pantallas de recuperación de contraseña (se creó para **no duplicar** CSS entre ambas). |
| `<style>` dentro de cada página | Layout y componentes **propios** de la pantalla (el login define su escenario y su tarjeta ahí). |

La decisión entre "hoja compartida" y "estilo en la página" es de **reutilización**: si dos pantallas comparten un componente, va a una hoja; si es único, queda local. (Las pantallas de recuperación se crearon con una hoja compartida precisamente porque eran dos pantallas con los mismos componentes.)

**Variables locales:** una página puede **redefinir** una variable solo en su alcance (el login definía `--login-bg-image`), pero **no debe sobrescribir los tokens de forma** (`--radio-tarjeta`): antes lo hacían varias pantallas del panel y se corrigió porque rompía la consistencia de esquinas.

---

## 12. Un componente completo, de punta a punta

El **campo de formulario** de Enexia reúne HTML + CSS + JS:

**HTML** (estructura y accesibilidad):
```html
<div class="campo" id="campoPassword">
    <label class="campo__etiqueta" for="password">Nueva contrase&ntilde;a</label>
    <div class="entrada">
        <svg class="entrada__icono" aria-hidden="true">...</svg>
        <input type="password" id="password" maxlength="72" autocomplete="new-password" required>
        <button type="button" class="entrada__accion" data-ver="password" aria-label="Mostrar contrase&ntilde;a">...</button>
    </div>
    <span class="campo__error"></span>
</div>
```

**CSS** (presentación, con tokens):
```css
.campo { margin-bottom: 20px; }
.entrada {
    display: flex; align-items: center; gap: 12px;
    padding: 13px 17px;
    border: 1px solid var(--borde-campo);
    border-radius: var(--radio-campo);
    background: var(--superficie);
    transition: border-color .15s ease, box-shadow .15s ease;
}
.entrada:focus-within { border-color: var(--acento); box-shadow: var(--foco-accion); }
.campo--invalido .entrada { border-color: var(--error); }
.campo--invalido .campo__error { display: block; }
```

**JavaScript** (comportamiento, sin tocar estilos):
```js
function errorCampo(idCampo, mensaje) {
    const campo = document.getElementById(idCampo);
    campo.classList.toggle('campo--invalido', !!mensaje);      // solo alterna UNA clase
    campo.querySelector('.campo__error').textContent = mensaje || '';
}
```

Tres lenguajes, **tres responsabilidades**: HTML = qué es, CSS = cómo se ve, JS = qué hace.

---

## 13. Herramientas de depuración

- **DevTools → Elements:** inspeccioná un elemento: ves su HTML, **las reglas CSS que le aplican** (y cuáles se tachan por perder la cascada), el modelo de caja y los valores de las variables.
- **Computed:** el valor final de cada propiedad.
- **Modo dispositivo:** simula un celular.
- **Lighthouse** (pestaña): auditoría de accesibilidad y rendimiento.
- **Console:** `getComputedStyle(el).borderRadius` para consultar un valor.

---

## 14. Errores típicos

| Error | Síntoma | Remedio |
|---|---|---|
| Falta `meta viewport`. | En el celular todo se ve diminuto. | Agregarla en el `<head>`. |
| Sin `box-sizing: border-box`. | Cajas que se salen de su contenedor. | `* { box-sizing: border-box; }`. |
| `<div onclick>` en vez de `<button>`. | No funciona con teclado ni lector de pantalla. | Elementos nativos. |
| Campo sin `label`. | Inaccesible y difícil de testear. | `label for` / `getByLabel`. |
| Colores escritos a mano. | Paleta inconsistente; contraste sin verificar. | `var(--token)`. |
| `border-radius: 12px` suelto. | Rompe la regla de esquinas cuadradas. | Los tokens de forma. |
| Orden de hojas equivocado. | Variables "no definidas" (`var(--x)` vacío). | `tokens.css` primero. |
| Estilos por `id` o selectores largos. | Especificidad imposible de sobrescribir. | Una clase BEM. |
| Ocultar con `display: none` algo que debe leerse. | El lector lo ignora (a veces deseado, a veces no). | Decidir si es contenido o decoración. |

---

## 15. Para practicar

1. Abrí `password-reset.html` con las DevTools y localizá el `.entrada`. Cambiá en vivo `--acento` en `:root` y mirá qué cambia.
2. Escribí el HTML y el CSS de un **chip de estado** (`.chip`, `.chip--ok`, `.chip--error`) usando solo tokens y esquinas `--radio-sm`.
3. Calculá el contraste de `#5c5470` sobre `#ffffff` con un verificador en línea y compará con el ratio anotado en `tokens.css`.
4. Reescribí la regla `@media (max-width: 560px)` de `recuperacion.css` en versión **mobile-first** (con `min-width`).
5. Armá una grilla de tarjetas con `repeat(auto-fit, minmax(240px, 1fr))` y comprobá cuántas columnas muestra a 1200, 800 y 400 px.

**Siguiente (Parte D):** los módulos del sistema, uno por uno. Empezá por [D1 — Módulo 1: Autenticación](./D1_modulo_1_autenticacion.md).
