# Ficha de evento: reconstrucción contra el Figma real (RF-4.4)

**Fecha:** 2026-09-10 (continuación del mismo día de [2026-09-10_landing_publica.md](./2026-09-10_landing_publica.md))
**Motivo:** el día anterior se construyó `evento-detalle.html` sin Figma de referencia para el estado "evento encontrado" — el usuario solo había traído el estado 404. Hoy trajo el Figma real de esa pantalla (mismo mecanismo del plugin "Figma to Code") y pidió explícitamente reskinearlo a la paleta clara y **eliminar** la versión inventada.
**Resultado:** ✅ estado "encontrado" reconstruido contra el diseño real · ✅ 0 endpoints nuevos, 0 cambios de backend · ✅ verificado end-to-end contra datos reales

---

## 1. Qué cambió

Se reescribió por completo el CSS y la función `pintarFicha()` del estado "encontrado" de `evento-detalle.html`. El estado 404 **no se tocó** — ya venía de un Figma real de la sesión anterior y seguía vigente.

Cero cambios de backend: todo sigue consumiendo `GET /api/publico/eventos/{id}` (`EventoDetalleResponse`) tal cual ya existía desde el Módulo 4.

## 2. Honestidad de datos: lo que el Figma real traía y el backend no respalda

El Figma resultó bastante más ambicioso que la ficha técnica que el Módulo 4 expone hoy. Se omitió en vez de inventar en cada uno de estos puntos:

1. **Perfil extendido del organizador** — avatar grande, badge "verificado", ciudad propia del organizador, "14 eventos organizados", iconos de redes sociales, botón "Ver perfil". No existe ningún endpoint público de perfil de organizador ni esos campos en ningún DTO. Se dejó lo único respaldado por datos reales: el nombre (`EventoDetalleResponse.organizador`, ya resuelto por RF-7.4) con un avatar de iniciales — el mismo patrón que ya usa el navbar en el resto del sitio.
2. **"Opiniones de la Comunidad" completa** — rating agregado (4.7), "38 reseñas", formulario para publicar una opinión con estrellas, dos reseñas de ejemplo con avatar/nombre/fecha relativa. El Módulo 5 (`Valoracion`) tiene entidad y repositorio (`model/Valoracion.java`, `repository/ValoracionRepository.java`) pero **ningún service ni endpoint** lo expone todavía. Se omitió la sección entera.
3. **"1.240 visitas"** — `Visita` y `VisitaService` existen y registran la visita en este mismo GET (RF-4.5), pero el conteo nunca se devuelve en `EventoDetalleResponse`. Mismo patrón que "inscritos" en `mis-eventos.html` de la sesión anterior: la tabla existe, el DTO consumido no la expone. Se omitió.
4. **Texto de fase por cronograma** — el Figma mostraba "Fase de clasificación", "Semifinales y Final", "Ceremonia de premiación" junto a cada fecha. `EventoCronogramaResponse` no tiene ningún campo de fase o etiqueta — es copy fijo del mockup, no un dato. Se reemplazó por un dato real derivado: "N tipos de entrada" (el tamaño de la lista de tickets de ese cronograma).
5. **Chip "Presencial"** — a diferencia de los puntos anteriores, este quedó **fijo a propósito**: no hay campo de modalidad en el modelo porque no existe el concepto de evento virtual en el MER (todo evento tiene una `Ubicacion` obligatoria). No es un dato inventado, es una afirmación siempre verdadera dado el alcance actual del sistema.

## 3. ADR: por qué el layout de 2 columnas quedó distinto al del Figma

El Figma real ponía en la columna **izquierda** la galería (con un botón de play superpuesto que sugiere video — esa feature no existe), la tarjeta de perfil del organizador y las opiniones; y en la columna **derecha**, en un solo flujo vertical, la info principal (título, descripción, cronograma, ubicación) seguida de la tarjeta de tickets al final de esa misma columna — no como sidebar fijo.

Como dos de los tres bloques de la columna izquierda (perfil extendido, opiniones) no tienen datos reales detrás por los puntos 1 y 2 de la sección anterior, conservar esa columna tal cual habría dejado un hueco vacío enorme al lado del contenido real. Se optó por el layout de 2 columnas más convencional para una ficha de evento: columna principal ancha (portada, chips, título, organizador, descripción, galería, cronograma, ubicación) + sidebar angosto **sticky** a la derecha con solo la tarjeta de tickets/opciones de acceso. Es el mismo patrón de layout que ya tenía la versión inventada del día anterior, pero ahora con contenido y estilos extraídos del Figma real en los lugares donde hay dato real detrás.

**Es una decisión de layout, no de datos**: no se inventó ningún campo nuevo, se reorganizó la disposición porque la del Figma dependía de contenido que el sistema no tiene.

## 4. Dos mejoras reales que no estaban en el Figma

No estaban en el diseño, pero se construyeron porque usan datos 100% reales, no inventados:

- **"Eventos Relacionados por Categoría"**, al final de la página: reusa el mismo `GET /api/publico/eventos?idCategoria=X&tamano=4` que ya consume `index.html`, excluye el evento actual del resultado y reusa el mismo componente de tarjeta que la grilla del catálogo. Si la categoría no tiene otros eventos, la sección entera queda `hidden` en vez de mostrar un estado vacío.
- **Barra de capacidad por ticket**: `TicketResponse` sí trae `cupoMaximo` y `cupoDisponible` reales — la versión inventada del día anterior solo los usaba para un texto "agotado"/"N disponibles". Ahora cada ticket muestra una barra de progreso real además del texto.

El botón de compra sigue sin ser funcional: se mantuvo el disclaimer ya establecido ("Las inscripciones online están próximamente disponibles") en vez del botón "Inscribirse al Evento" que el Figma sí mostraba — el Módulo 3 (Inscripción) sigue sin existir.

## 5. Verificación end-to-end

Probado contra el backend real corriendo (`gradle bootRun`, puerto 8080) con datos de sesiones anteriores:

| Escenario | Resultado |
|---|---|
| Ficha real (id=204, "Festival de Invierno Fueguino", Cultural, organizador Ana Gomez) | Breadcrumb, chips, título, organizador, descripción, cronograma (sábado 10 de octubre, 2 tipos de entrada), ubicación completa (Maipu 505, Ushuaia, Tierra del Fuego, Argentina) |
| Sidebar de tickets | 2 tickets reales: Inscripcion General (gratis, 200/200 cupos) y Pase VIP ($15.000,5 ARS, 30/30 cupos), ambos con barra de capacidad |
| Eventos relacionados | 3 eventos reales de categoría Cultural, excluyendo el evento actual |
| Estado 404 (id=999999) | Intacto, no se tocó |
| Responsive mobile (375px) | Sin overflow horizontal; breadcrumb y chips wrappean correctamente |

**Nota sobre la herramienta de testing:** el screenshot automatizado repitió el mismo artefacto visual ya documentado en la sesión anterior — navbar/footer con `backdrop-filter` sticky se ven superpuestos o con huecos en blanco al hacer scroll en la captura. Se confirmó que no es un bug real inspeccionando el DOM directamente (`getBoundingClientRect` de `.ficha`, `.ficha__grilla`, `.tarjeta-lateral`, `.relacionados` y `footer`): todas las secciones tienen alturas y posiciones consistentes, sin huecos reales, y `get_page_text` confirma que todo el contenido está presente y en el orden correcto.

## 6. Deuda de diseño

| Antes | Ahora |
|---|---|
| ~~La ficha "evento encontrado" no tiene Figma de referencia~~ | ✅ Resuelto hoy |
| — | 🆕 Perfil público extendido de organizador (verificación, cantidad de eventos, redes sociales): no existe ni el dato ni el endpoint |
| — | 🆕 Módulo 5 (Valoracion/reseñas): tiene modelo y repositorio pero cero service/endpoint — toda la sección "Opiniones de la Comunidad" del Figma quedó sin construir por esto |
| Perfil de Participante puro no existe | Sin cambios |
| Módulo 3/Inscripción no existe (botón de compra no funcional) | Sin cambios |
