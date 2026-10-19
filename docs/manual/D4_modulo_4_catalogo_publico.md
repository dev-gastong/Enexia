# D4 — Módulo 4: Interfaz pública (catálogo, búsqueda, ficha y visitas)

> **Parte D · Los módulos.** La **vidriera**: cualquier persona —sin cuenta— ve los eventos, los busca, los filtra y abre su ficha. Es el único grupo de endpoints **sin autenticación obligatoria**, y la pantalla **más visitada**. Por eso concentra decisiones de **visibilidad**, **consultas con filtros opcionales**, **sesión opcional** y **métricas pasivas** que no deben poder tumbar la página.

**Requisitos que cubre** (`requisitos_funcionales/modulo_4.md`)

| RF | Resumen |
|---|---|
| RF-4.1 | Catálogo **paginado** y accesible de forma anónima. |
| RF-4.2 | Búsqueda por texto. |
| RF-4.3 | Filtros combinables: categoría, provincia/ciudad, rango de fechas. |
| RF-4.4 | **Ficha técnica**: descripción, portada, ubicación, agenda con tickets, reseñas. |
| RF-4.5 | **Registro pasivo de visitas** (anónimas o con usuario). |
| RF-4.6 | Navbar condicional según la sesión. |

DFD: [`modulo_4_interfaz_publica/`](../diagrams/modulo_4_interfaz_publica/nivel1_interfaz_publica.md).

---

## 1. La regla de visibilidad: **dos condiciones a la vez**

Un evento aparece en el catálogo solo si **ambos ejes de estado** (D2 §1.2) lo permiten:

```
(a) estado_sistema ∈ { APROBADO_SISTEMA, APROBADO_MANUAL }      ← la moderación lo aprobó
        Y
(b) estado_organizador = PUBLICADO                                ← su dueño lo mantiene publicado
```

Comprobar **una sola** dejaría entrar eventos cancelados o aún en revisión. Y lo más importante: **la regla vive en la consulta del repositorio, no en el servicio** — por eso **no se puede saltear pidiendo un id directo**: `buscarPublicoPorId` aplica la misma condición.

```java
// EventoRepository
List<String> ESTADOS_VISIBLES = List.of("APROBADO_SISTEMA", "APROBADO_MANUAL");
```

Una constante de la **interfaz** del repositorio (A02 §4): compartida por todas las consultas que necesitan "visible".

**Una excepción deliberada:** la **ficha** de un evento `FINALIZADO` sigue abierta (`PUBLICADO` **o** `FINALIZADO`), aunque ya no aparezca en la grilla: *"ahí se lo valora (RF-3.4)"*. La grilla muestra lo vigente; la ficha permite llegar a las reseñas de lo terminado.

**404 único** para "no existe" y para "existe pero no es público": distinguirlos permitiría **descubrir qué eventos fueron rechazados** probando ids (B04 §7.5).

---

## 2. Sesión **opcional**, no obligatoria

Es la diferencia de fondo con el resto de la API:

- `SecurityConfig` declara `GET /api/publico/** → permitAll()`.
- **Pero el filtro JWT corre igual**: si el pedido trae un token válido, `Principal` llega poblado; si no, es `null`. El controller **acepta ambos casos**.
- Para qué sirve: **atribuir la visita** a un usuario y **adaptar la ficha** (¿ya está inscripto? ¿puede valorar?) **sin exigir login**.

```java
public EventoDetalleResponse verFicha(Long idEvento, String emailUsuario) { ... }   // emailUsuario puede ser null
```

Un diseño sutil de B04 §4.3: *"este filtro nunca rechaza: separar 'identificar' de 'autorizar'"* es lo que permite este comportamiento.

---

## 3. El catálogo (RF-4.1 a 4.3)

### 3.1 `GET /api/publico/eventos`

| Parámetro | Efecto |
|---|---|
| `texto` | Coincidencia **parcial** sobre el nombre, sin distinguir mayúsculas. |
| `idCategoria` | Una categoría. |
| `idProvincia`, `idCiudad` | Filtro geográfico en dos niveles. |
| `desde`, `hasta` | Rango de fechas **inclusive**; basta que **alguna** función caiga dentro. |
| `orden` | `recientes` (por defecto) o `antiguos`, sobre **la misma columna**. |
| `pagina`, `tamano` | Paginación (defecto 12, **tope 50**). |

Todos son **opcionales** y combinables: sin ninguno devuelve la primera página del catálogo. Validación de negocio: `hasta` anterior a `desde` → `ReglaNegocioException` (400).

### 3.2 Una consulta para 16 combinaciones: el patrón `:param IS NULL OR ...`

```java
@Query("""
        SELECT e FROM Evento e
        WHERE e.estadoSistema.estadoSistema IN :estadosVisibles
          AND e.estadoOrganizador.estadoOrganizador = :estadoPublicado
          AND (:texto IS NULL OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :texto, '%')))
          AND (:idCategoria IS NULL OR e.categoria.idCategoria = :idCategoria)
          AND (:idCiudad IS NULL OR EXISTS (
                SELECT 1 FROM EventoDetalle d
                WHERE d.evento = e AND d.ubicacion.ciudad.idCiudad = :idCiudad))
          AND (:desde IS NULL OR EXISTS (
                SELECT 1 FROM EventoCronograma c WHERE c.evento = e AND c.fecha >= :desde))
        """)
```

- **`:parametro IS NULL OR condición`**: si el filtro no se usó (`null`), esa línea es siempre verdadera y **se ignora**. Un solo método cubre **todas las combinaciones**. La alternativa (*Criteria API* / *Specifications*) es más flexible pero mucho menos legible, y acá los filtros son fijos y conocidos. Costo: la base evalúa igual las condiciones nulas; con índices el plan sigue siendo bueno.
- **`EXISTS` en vez de `JOIN`** para las fechas: con un `JOIN`, **un evento con tres fechas dentro del rango aparecería tres veces** en la grilla. `EXISTS` pregunta "¿existe alguna?" y no multiplica filas.
- **`@EntityGraph`** trae categoría, estados, organizador y organización en la misma consulta (sin N+1: B03 §2.5).
- **Por qué se ordena por `fechaCreacion` y no por la fecha del evento:** la fecha vive en `evento_cronograma` (tabla hija); ordenar por ella exigiría un `JOIN` con agregación que **rompe la paginación**. La **próxima fecha** viaja en cada tarjeta y el frontend puede reordenar la página.
- **Reutilización:** `EventoService.mapearPagina` arma las tarjetas, igual que el dashboard del organizador (misma tarjeta, mismas **tres consultas por página**).

### 3.3 Catálogos auxiliares

`GET /api/publico/categorias`, `/provincias`, `/provincias/{id}/ciudades` alimentan los `<select>` de los filtros. `categorias` devuelve **solo las activas** (RF-6.3).

---

## 4. La ficha (RF-4.4)

`GET /api/publico/eventos/{id}`:

1. **Existencia y visibilidad en una sola consulta** (`buscarPublicoPorId`).
2. **Componer** detalle + ubicación, **cronogramas ordenados** (`OrderByFechaAscHoraInicioAsc`) y **tickets de todos los cronogramas en una sola consulta** (`buscarPorCronogramas(ids)`).
3. **Con sesión**, resolver el **estado de participación en TODOS los cronogramas a la vez** — tres `Set<Long>` en lote (A02 §8, `EstadoParticipante`):
   - `idsInscriptoActivo` → no ofrecer "Inscribirme" donde ya tiene lugar;
   - `idsConfirmado` → piso para poder valorar;
   - `idsYaValorado` → techo para no ofrecer valorar de nuevo.
   Así el frontend **no ofrece botones que va a fallar** en lugar de enterarse al recibir el error.
4. `EventoMapper.aFicha` arma la respuesta; por cronograma calcula **`finalizado`**, **`iniciado`** y **`puedeValorar`**:

```java
boolean puedeValorar = finalizado
        && estadoParticipante.idsConfirmado().contains(cronograma.getIdCronograma())
        && !estadoParticipante.idsYaValorado().contains(cronograma.getIdCronograma());
```

(Es la **segunda aplicación** de "valoración por cronograma": el botón se decide **función por función**.)

5. El header muestra **promedio y cantidad** del evento: `promedioDeEvento` devuelve `Double` **`null`** si no hay valoraciones (*"todavía sin calificar", no "0 estrellas"*) y `countByCronogramaEventoIdEvento`.
6. **Registrar la visita** (§5).

El **corte de inscripción** también se calcula acá (`iniciado = !inicio.isAfter(ahora)`), con el mismo umbral que `RegistroInscripcionService`: el frontend lo refleja, el backend lo **vuelve a exigir** (D3 §3).

---

## 5. Visitas (RF-4.5): una métrica que **no puede** tumbar la página

### 5.1 La regla

Cada carga de la ficha inserta **una fila** en `visita` (`id_evento`, `id_usuario` o `NULL`, `fecha_visita`). **Sin deduplicar**: la unicidad se calcula **al leer** (`COUNT DISTINCT`, RF-2.10). Deduplicar al escribir obligaría a una consulta previa **en la página más cargada del sitio**, y se perdería el dato de **cuántas veces volvió** la misma persona.

### 5.2 El error real que enseña `VisitaService`

La visita vivía como método de `CatalogoPublicoService` y se llamaba con `this.registrarVisita(...)` desde `verFicha`, que es `@Transactional(readOnly = true)`. Como la llamada **no cruzaba el proxy** (B03 §6.6), el `REQUIRES_NEW` **quedaba sin efecto** y el `INSERT` caía **dentro de la transacción de solo lectura**, que quedaba marcada **rollback-only**:

```
UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

Y lo peor: el `try/catch` alrededor del `save()` **no lo atrapaba**, porque el fallo ocurre **al confirmar**, ya fuera del método. **La ficha entera respondía 500 por culpa de una métrica.**

**Solución:** mover el registro a **otro bean** (`VisitaService`) con `REQUIRES_NEW`: la visita se escribe en **su propia transacción**, independiente de la lectura. Además usa `getReferenceById(idEvento)`, que devuelve un **proxy sin consultar la base**: alcanza para poblar la clave foránea y evita un `SELECT` en cada carga.

> **Lección:** una métrica secundaria debe estar **aislada** del camino principal, y `@Transactional` no se puede dar por hecho cuando el llamador está en la misma clase.

---

## 6. Reseñas en la ficha

`GET /api/publico/eventos/{id}/valoraciones?pagina&tamano` (defecto 5, tope 20; una página chica porque es un **bloque embebido**, no la grilla). Detalle en D3 §6.3: paginado, **nickname** (no nombre civil) y **a qué cronograma** corresponde cada reseña.

---

## 7. Pantallas

### 7.1 `index.html` (catálogo)

| Pieza | Técnica (capítulo) |
|---|---|
| **Navbar condicional** (`pintarNavbar`) | Se arma con un *template literal* según `Auth.hasRole(...)`: participante ve "Mis Inscripciones"; organizador, "Crear Evento"; administrador, "Panel Admin"; visitante, "Iniciar sesión" (C02 §5, §10.3). RF-4.6. |
| **Filtros** | `<select>` de categorías y ciudades cargados con `API.categorias()` y `API.ciudadesDeProvincia()`; la provincia se resuelve **por nombre** (`find(p => /tierra del fuego/i.test(p.nombre))`) en vez de asumir la primera. Un `<input>` de texto con **Enter** para buscar. |
| **Grilla** | `CSS Grid` con `auto-fit`/`minmax` (C03 §5.6). Cada tarjeta es un `<article class="tarjeta-evento">` creado con `createElement` + `innerHTML` **con `UI.esc`** en todo texto de usuario (C02 §12). |
| **"Cargar más"** | `pagina++; cargar(false)`; **tamaño de página = múltiplo de las columnas visibles** (`getComputedStyle(...).gridTemplateColumns`) para completar filas. |
| **Tres estados** | cargando / error / vacío (*"No encontramos eventos con estos filtros"*). |

### 7.2 `evento-detalle.html` (ficha, 1.000 líneas)

`cargarEvento()` pide la ficha y `pintarFicha(ev)` arma **todo el HTML** con un template; luego pinta **opiniones paginadas**, **eventos relacionados** (misma categoría, **solo a futuro**: `desde = hoy`) y el **lightbox** de la galería. Detalles:

- La inscripción usa **delegación de eventos sobre `document`** porque `pintarFicha` **reconstruye** `#contenido` y los botones se recrean (C02 §7.2).
- El enlace a una función "Selección de cronograma" hace *scroll* y **resalta** solo las entradas de esa fecha en "Opciones de acceso".
- Un administrador **no ve** el botón de inscripción ni su aviso (regla de diseño: *"admin accounts never see participant actions"*).
- `index.html` y esta ficha **ya escapan** los textos de usuario; antes no lo hacían: era un **XSS almacenado** (corregido el 2026-10-19: C02 §12.3).

---

## 8. Pruebas y verificación

- `EventoMapperTest`: `aFicha` (finalizado / iniciado / `puedeValorar` / `promedio null`).
- Tests de API (`EventoApiTest`): el recorrido creación → catálogo con la base real.
- La **suite masiva** (`EventosMasivosApiTest`) crea 30 eventos para ejercitar filtros y paginación con volumen.
- Verificación manual recomendada: **con y sin token** sobre `GET /api/publico/eventos/{id}`, y con el id de un evento `RECHAZADO` (debe dar 404 idéntico al de un id inexistente).

---

## 9. Límites y pendientes

1. **Búsqueda por texto = `LIKE '%x%'`**: no usa índice y no ordena por relevancia. Para un catálogo grande convendría un índice de texto completo.
2. **Sin caché**: cada carga de la grilla consulta la base (el RNF pide ≤ 2 s en lecturas, hoy se cumple con volumen de prueba).
3. **Una fila por visita**: la tabla crece sin límite; a escala habría que **agregarla** (por día) o **purgarla**.
4. **Visitas por función no existen**: el modelo las atribuye al **evento** (la ficha se visita antes de elegir una función): ver `tempDFD` 2.10.

---

## 10. Conceptos aplicados

| Concepto | Dónde |
|---|---|
| Autenticación opcional / filtros que identifican sin rechazar | B04 §4.3 |
| JPQL con filtros opcionales, `EXISTS`, `@EntityGraph` | B03 §5.3, §2.5 |
| Paginación saneada | B02 §7 |
| `REQUIRES_NEW` y el proxy: el bug de `VisitaService` | B03 §6.6 |
| Lotes con `Set`/`Map` | A03 §1.2, §3.3 |
| Template literals + `UI.esc`, delegación, `FormData` no aplica | C02 |
| `grid auto-fit`, tarjetas BEM | C03 §5.6, §9 |

---

## 11. Para practicar

1. Escribí (en JPQL) la condición para un nuevo filtro opcional `precioMaximo` (que alguna función tenga un ticket con precio ≤ al valor). ¿Usás `JOIN` o `EXISTS`? ¿Por qué?
2. Pedí el catálogo con `?hasta=2020-01-01&desde=2030-01-01`. ¿Qué código y mensaje recibís? ¿En qué capa se detecta?
3. ¿Qué pasaría con `GET /api/publico/eventos/{id}` de un evento `DADO_DE_BAJA`? ¿Y de uno `FINALIZADO`? Justificá con `verFicha`.
4. Reproducí (en papel) el bug de `VisitaService`: dibujá las transacciones y explicá por qué el `try/catch` no servía.
5. Agregá (en papel) una cuarta marca a `EstadoParticipante` ("esta persona **es la organizadora** del evento") y listá todos los archivos que habría que tocar.

**Siguiente capítulo:** [D5 — Módulo 5: Moderación de contenido](./D5_modulo_5_moderacion.md)
