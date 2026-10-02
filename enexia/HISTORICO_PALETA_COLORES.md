# 📋 Histórico de Paleta de Colores - Enexia

## 🎨 Paleta "Atardecer Fueguino" (naranja & morado)
**Definida en:** `css/tokens.css`  
**Usada en:** `login-desktop-claro.html` y todos los HTMLs futuros  
**Referencia Figma:** Frame "Login Desktop" (node 77:884)  
**Fecha:** Septiembre 2026

---

## 📐 1. MARCA - Acción y Acento

### Acción (CTA - Botones)
Naranja del isotipo Enexia, ajustado para cumplir WCAG 2.1 AA.

| Token | Color | Contraste | Uso |
|-------|-------|-----------|-----|
| `--accion` | `#c2410c` | 5.18:1 (blanco encima) | Relleno botón primario |
| `--accion-hover` | `#9a330a` | 7.36:1 | Estado hover (oscurece, NO aclara) |
| `--accion-tinte` | `#fbeae1` | - | Fondo suave del botón en estado reposo |
| `--accion-borde` | `#f0c9b4` | - | Borde del botón |
| `--sobre-accion` | `#ffffff` | - | Texto sobre naranja |

### Acento (Enlaces, iconos, foco, chips)
Morado de la marca Enexia.

| Token | Color | Contraste | Uso |
|-------|-------|-----------|-----|
| `--acento` | `#7c3aed` | 5.70:1 (blanco) | Enlaces, iconos primarios |
| `--acento-fuerte` | `#6d28d9` | 7.10:1 (blanco); 6.17:1 (tinte); 4.73:1 (foto hero) | Énfasis, links visitados |
| `--acento-tinte` | `#f3ecfd` | - | Fondo suave (chips, inputs enfocados) |
| `--acento-borde` | `#d9c7fb` | - | Borde de elementos acentuados |

### Marca Literal (Decorativo)
Del isotipo original (no para UI).

| Token | Color | Contraste | Uso |
|-------|-------|-----------|-----|
| `--naranja-marca` | `#f28c1e` | - | Decorativo (NUNCA lleva texto) |
| `--morado-marca` | `#9b3fb5` | 5.57:1 (blanco si es necesario) | Decorativo |

### Efectos de Acción
| Token | Valor | Uso |
|-------|-------|-----|
| `--sombra-accion` | `0 2px 10px rgba(194, 65, 12, .30)` | Sombra del botón |
| `--foco-accion` | `0 0 0 3px rgba(124, 58, 237, .20)` | Anillo de foco (keyboard nav) |

---

## 🖤 2. NEUTROS - Base Clara

Deliberadamente claros: en un catálogo de eventos, el protagonista es la imagen del organizador, no el chrome de la interfaz.  
Llevan tinte violáceo apenas perceptible para que el gris no se sienta frío al lado del morado.

| Token | Color | Contraste | Uso |
|-------|-------|-----------|-----|
| `--blanco` | `#ffffff` | - | Blanco puro |
| `--lienzo` | `#faf8fa` | - | Fondo de página |
| `--superficie` | `#ffffff` | - | Fondo de tarjetas, inputs |
| `--superficie-alt` | `#f5f2f7` | - | Zonas "hundidas" dentro de una tarjeta |
| `--borde` | `#eae5ee` | - | Borde suave |
| `--borde-campo` | `#d8d1de` | - | Borde de campos de entrada |
| `--borde-fuerte` | `#bdb4c6` | - | Borde enfatizado |
| `--texto` | `#1f1a2e` | 16.86:1 (blanco) | Texto principal |
| `--texto-secundario` | `#5c5470` | 7.10:1 (blanco) | Texto de ayuda, etiquetas |
| `--texto-sobre-foto` | `#423a55` | - | Más oscuro (vive sobre foto lavada) |

---

## 🏔️ 3. Escenario del Login

Capas de fondo + velos compuestos (foto, filtro, degradados, halos).

| Token | Valor | Uso |
|-------|-------|-----|
| `--foto-filtro` | `saturate(.68) brightness(1.05)` | Lavado de la foto de cordillera |
| `--velo` | `linear-gradient(118deg, ...)` | Atardecer: violeta frío → blanco cálido ambarino |
| `--velo-hero` | `linear-gradient(to bottom, ...)` | Velo sobre el texto de bienvenida |
| `--halo-1` | `radial-gradient(circle, rgba(124, 58, 237, .10)...)` | Halo morado sup-izq (detrás de tarjeta) |
| `--halo-2` | `radial-gradient(circle, rgba(194, 65, 12, .10)...)` | Halo naranja inf-der (detrás de tarjeta) |

---

## ⚠️ 4. Estados Semánticos

Cada estado es un par **texto + tinte**, ambos verificados contra WCAG 2.1 AA.

| Estado | Texto | Contraste | Tinte | Borde | Hover |
|--------|-------|-----------|-------|-------|-------|
| **OK** | `#065f46` | 6.78:1 | `#d1fae5` | `#6ee7b7` | - |
| **ERROR** | `#991b1b` | 6.80:1 | `#fee2e2` | `#fca5a5` | `#7f1616` |
| **ALERTA** | `#92400e` | 6.37:1 | `#fef3c7` | `#fcd34d` | - |
| **INFO** | `#1e40af` | 7.15:1 | `#dbeafe` | `#93c5fd` | - |

---

## 🎭 5. Categorías de Evento (Módulos 2 y 3)

Cinco dominios codificados por color. **IMPORTANTE:** Ninguna usa naranja ni morado (ya reservados para acción/acento).

| Categoría | Texto | Contraste | Tinte | Uso |
|-----------|-------|-----------|-------|-----|
| **Cultural** | `#be185d` | 5.13:1 | `#fce7f0` | Eventos culturales |
| **Educativo** | `#1e40af` | 7.25:1 | `#e4eafb` | Charlas, cursos |
| **Social** | `#0e7490` | 4.65:1 | `#e0f2f7` | Eventos sociales |
| **Deportivo** | `#166534` | 6.17:1 | `#e4f2e9` | Deportes |
| **Gaming** | `#334155` | 8.73:1 | `#e8ecf1` | Videojuegos, esports |

---

## 👤 6. Chips de Rol (Módulo 1)

Estados de usuarios en la interfaz.

| Rol | Texto | Contraste | Tinte | Uso |
|-----|-------|-----------|-------|-----|
| **Participante** | `#1e40af` | 7.15:1 | `#dbeafe` | Usuario como participante |
| **Organizador** | `#5b21b6` | 7.57:1 | `#ede9fe` | Usuario como organizador |
| **Admin** | `#92400e` | 6.37:1 | `#fef3c7` | Usuario como administrador |
| **Neutro** | `#374151` | 8.33:1 | `#e5e7eb` | Estado neutro/desconocido |

---

## 📏 7. Layout y Forma

| Token | Valor | Uso |
|-------|-------|-----|
| `--ancho-contenido` | `1560px` | Tope máximo de la composición |
| `--radio-sm` | `6px` | Radio pequeño (inputs, tags) |
| `--radio` | `10px` | Radio estándar |
| `--radio-campo` | `12px` | Radio de campos de entrada |
| `--radio-tarjeta` | `28px` | Radio de tarjetas |
| `--radio-pildora` | `999px` | Radio píldora (botones redondeados) |

### Sombras

| Token | Valor | Uso |
|-------|-------|-----|
| `--sombra` | `0 1px 2px rgba(31, 26, 46, .06), 0 1px 3px rgba(31, 26, 46, .10)` | Sombra suave (default) |
| `--sombra-md` | `0 4px 12px rgba(31, 26, 46, .08)` | Sombra media |
| `--sombra-tarjeta` | `0 12px 40px rgba(31, 26, 46, .11), 0 2px 6px rgba(31, 26, 46, .05)` | Sombra tarjeta (elevada) |

---

## 🔤 8. Tipografía

| Token | Fuentes | Uso |
|-------|---------|-----|
| `--sans` | `'DM Sans', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif` | Tipografía principal (body, UI) |
| `--marca` | `'Space Grotesk', 'DM Sans', system-ui, sans-serif` | Logotipo, titulares |
| `--mono` | `ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace` | Código, tokens, monoespaciada |

**Font Imports:**
```html
<link href="https://fonts.googleapis.com/css2?family=DM+Sans:opsz,wght@9..40,400;9..40,500;9..40,700&family=Space+Grotesk:wght@700&display=swap" rel="stylesheet">
```

---

## 🖥️ 9. Consola del Banco de Pruebas

Superficie oscura intencional (imita terminal), **NO sigue la paleta clara del producto**.

| Token | Color | Uso |
|-------|-------|-----|
| `--consola-fondo` | `#0d1526` | Fondo de consola |
| `--consola-borde` | `#1e2d47` | Borde de consola |
| `--consola-texto` | `#cbd5e1` | Texto de consola |
| `--consola-tenue` | `#64748b` | Texto tenue |
| `--consola-vacia` | `#475569` | Estado vacío |
| `--consola-acento` | `#7dd3fc` | Acento de consola |

### Sintaxis JSON
| Token | Color | Uso |
|-------|-------|-----|
| `--json-clave` | `#7dd3fc` | Claves JSON |
| `--json-texto` | `#86efac` | Strings |
| `--json-num` | `#fbbf24` | Números |
| `--json-bool` | `#c4b5fd` | Booleanos |
| `--json-null` | `#64748b` | null |

### Estados HTTP
| Token | Valor | Uso |
|-------|-------|-----|
| `--http-ok-fondo` | `rgba(5, 150, 105, .18)` | Fondo 200 OK |
| `--http-ok-texto` | `#6ee7b7` | Texto 200 OK |
| `--http-alerta-fondo` | `rgba(217, 119, 6, .18)` | Fondo 3xx/4xx |
| `--http-alerta-texto` | `#fcd34d` | Texto 3xx/4xx |
| `--http-error-fondo` | `rgba(220, 38, 38, .18)` | Fondo 5xx |
| `--http-error-texto` | `#fca5a5` | Texto 5xx |
| `--http-error-fuerte` | `rgba(220, 38, 38, .25)` | Fondo error fuerte |
| `--http-red-fondo` | `rgba(100, 116, 139, .25)` | Fondo red timeout |
| `--http-red-texto` | `#94a3b8` | Texto red timeout |

### Pulsos (Indicadores de estado)
| Token | Color | Uso |
|-------|-------|-----|
| `--pulso-ok` | `#34d399` | Indicador success |
| `--pulso-ok-halo` | `rgba(52, 211, 153, .2)` | Halo success |
| `--pulso-mal` | `#f87171` | Indicador error |
| `--pulso-mal-halo` | `rgba(248, 113, 113, .2)` | Halo error |

### Barra de navegación
| Token | Color | Uso |
|-------|-------|-----|
| `--barra-fondo` | `#3b1d63` | Fondo barra (morado profundo) |
| `--barra-texto` | `rgba(255, 255, 255, .82)` | Texto barra |
| `--barra-marca` | `#c4a5f5` | Logo en barra |

---

## 🔗 10. Alias de Compatibilidad

Nombres heredados de `styles.css`. **En código nuevo, usa los nombres de arriba.**

| Alias Antiguo | Apunta a |
|---------------|----------|
| `--fondo` | `--lienzo` |
| `--texto-suave` | `--texto-secundario` |
| `--primario` | `--accion` |
| `--primario-hover` | `--accion-hover` |
| `--primario-tinte` | `--accion-tinte` |

---

## 📌 Cómo Aplicar a Nuevos HTMLs desde Figma

1. **Siempre importar `tokens.css` PRIMERO:**
   ```html
   <link rel="stylesheet" href="/css/tokens.css">
   <link rel="stylesheet" href="/css/styles.css">
   ```

2. **Usar variables CSS, nunca hexadecimales hardcodeados:**
   ```css
   /* ✅ CORRECTO */
   background: var(--accion);
   color: var(--texto);
   border-color: var(--borde);
   
   /* ❌ INCORRECTO */
   background: #c2410c;
   color: #1f1a2e;
   border-color: #eae5ee;
   ```

3. **Jerarquía de colores por importancia:**
   - **Acción (naranja)**: Botones CTA, únicas acciones calientes
   - **Acento (morado)**: Enlaces, iconos, enfoque, chips
   - **Neutros**: Backgrounds, bordes, texto
   - **Semánticos**: Estados de error/ok/alerta/info
   - **Categorías**: Solo en chips/badges de eventos

4. **Si falta un color:**
   - Agregar a `tokens.css` con comentario de WCAG si aplica
   - NUNCA hardcodear en el HTML/CSS
   - Documentar en esta sección

---

## 🎯 Principios Clave

- ✅ **WCAG 2.1 AA:** Todos los colores están medidos contra estándares
- ✅ **Intención de marca:** Naranja = acción, Morado = acento (conviven en isotipo)
- ✅ **Legibilidad:** Base clara deliberada (eventos es el protagonista, no UI)
- ✅ **Composabilidad:** Pares texto+tinte verificados como duplas
- ✅ **Escalabilidad:** Tokens centrales, no hardcodeados en 50 archivos

---

**Última actualización:** 2026-09-03  
**Versión:** 1.0 (Atardecer Fueguino)
