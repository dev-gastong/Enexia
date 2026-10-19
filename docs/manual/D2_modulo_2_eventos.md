# D2 — Módulo 2: Gestión de eventos

> **Parte D · Los módulos.** El corazón de Enexia: un **organizador** crea, edita, consulta y da de baja sus eventos. Es el módulo que más conceptos junta: validación en cascada, **moderación antes de persistir**, trabajo **asíncrono**, **dos ejes de estado**, edición con **reglas de cupo**, subida de archivos y **estadísticas**.

**Requisitos que cubre** (`requisitos_funcionales/modulo_2.md`)

| RF | Resumen |
|---|---|
| RF-2.1 | Publicar a título personal o **bajo una organización**. |
| RF-2.2 | Creación con **moderación asíncrona**: se guarda un esqueleto y el contenido recién tras aprobarse. |
| RF-2.3 | Multimedia (imágenes) con tope de peso y cantidad. |
| RF-2.4 / 2.5 / 2.6 | Agenda de **cronogramas** y **tickets** (tipo, precio, cupo) por cronograma. |
| RF-2.7 | Edición de un evento **ya aprobado**, con reglas de cupo y precio. |
| RF-2.8 | Dashboard paginado con búsqueda y filtros. |
| RF-2.9 | Baja **lógica**. |
| RF-2.10 | Estadísticas (hoy por evento; por cronograma está especificado en `tempDFD`). |

DFD: [`creación_y_publicación_de_eventos.md`](../diagrams/gestion_de_eventos/creación_y_publicación_de_eventos.md), [modificación](../diagrams/gestion_de_eventos/modificacion_de_eventos.md), [baja lógica](../diagrams/gestion_de_eventos/baja_logica_de_evento.md), [historial](../diagrams/gestion_de_eventos/historial_de_eventos.md), [estadísticas](../diagrams/gestion_de_eventos/consultar_estadisticas.md).

---

## 1. Modelo de datos

```
Usuario (organizador) ──< Evento >── Categoria
                          │   └── Persona_Juridica (opcional: "a nombre de")
                          ├── Evento_Estado_Sistema      (eje 1: moderación)
                          ├── Evento_Estado_Organizador  (eje 2: dueño)
                          ├── Historial_Estado_Evento    (quién cambió qué y cuándo)
                          ├── Evento_Detalle ── Ubicacion ── Ciudad ── Provincia ── País
                          ├── Evento_Multimedia          (imágenes aprobadas)
                          ├── Visita                      (id_evento, id_usuario?, fecha_visita)
                          └──< Evento_Cronograma >──< Cronograma_Ticket >── Tipo_Ticket
                               (fecha, hora_inicio,      (precio, cupo_maximo, cupo_actual)
                                hora_fin)
```

### 1.1 Un evento, muchas "funciones": `Evento_Cronograma`

Un evento tiene **una o más fechas** (`cronogramas`). Cada **cronograma** es una función con su fecha y horario, y **cada uno tiene sus propios tickets** con su propio cupo. Esta jerarquía (**evento → cronograma → ticket**) es la más importante del modelo: la **inscripción** apunta a un **ticket de un cronograma**, y la **valoración** a un **cronograma** (D3).

### 1.2 Dos ejes de estado, independientes (decisión central)

```
estado_sistema (lo mueve el sistema / el admin)      estado_organizador (lo mueve el dueño)
  EN_PROCESO                                           PUBLICADO
    ├─► APROBADO_SISTEMA   ◄─ visible                  CANCELADO
    ├─► RECHAZADO_SISTEMA                              DADO_DE_BAJA
    └─► APROBADO_MANUAL    ◄─ visible                  FINALIZADO
        RECHAZADO_MANUAL
```

El catálogo público exige que **AMBOS ejes lo habiliten**. Son independientes a propósito: un evento aprobado por el sistema puede estar cancelado por su organizador, y uno publicado por su organizador puede ser suspendido por un administrador. Modelarlo como **un solo estado** habría obligado a combinar ("APROBADO_PERO_CANCELADO") y a un crecimiento explosivo de valores.

`motivo_codigo` vive **en la tabla de estados del sistema**: `RECHAZADO_SISTEMA` tiene **una fila por motivo** (`MODERACION_TEXTO`, `MODERACION_IMAGEN`, `SIN_IMAGENES_VALIDAS`, `ERROR_PIPELINE`).

### 1.3 El "esqueleto" (RF-2.2): moderar antes de persistir

Al crear, **solo se guarda un esqueleto**: organizador, organización, los dos estados y `fecha_creacion`. **Ni el título, ni la descripción, ni la agenda, ni las imágenes** tocan la base hasta que la moderación aprueba (`NOMBRE`, `CATEGORIA` y `URL_PORTADA` quedan `NULL`). Razón: *"si un título ofensivo se guardara aquí, quedaría en la base aunque después se rechace"*. Un test lo protege (`noPersisteContenido`, B06 §4.6).

---

## 2. Creación (RF-2.1 a RF-2.6)

### 2.1 La ruta completa

```
evento-form.html ── API.crearEvento(datos, imagenes) ──► POST /api/organizador/eventos  (multipart)
       EventoOrganizadorController.crear()   @PreAuthorize("hasRole('ORGANIZADOR')")
         · @Valid @RequestPart("datos") EventoCrearRequest        → 400 con errores por campo
         · leerImagenes(): copia los BYTES antes de responder
       EventoService.crear()   @Transactional                     ← FASE SÍNCRONA
         2.0  resolver al organizador
         2.1  (sin tope de eventos desde 2026-10-19)
         2.2  validarAgenda()                    ┐
         2.3  categoría activa + nº de imágenes  ├ reglas que @Valid no puede expresar
         2.4  INSERT del ESQUELETO (EN_PROCESO + PUBLICADO) + historial + auditoría
         2.5  publishEvent(new EventoCreadoEvent(...))     ──┐
       ◄── 202 Accepted + EventoResponse (nombre = "(Evento en validación)")    │
                                                                                 │ después del COMMIT
       ModeracionEventoService.alCrearseElEvento()  @Async + @TransactionalEventListener(AFTER_COMMIT)
         FASE 1: moderar texto        (ModeracionTextoService)
         FASE 2: subir y moderar cada imagen   (CloudinaryService)
         VEREDICTO: ≥ 1 imagen aprobada → aprobarYPersistir()   (APROBADO_SISTEMA)
                    si no            → rechazar(motivo)          (RECHAZADO_SISTEMA)
                    cualquier fallo  → ERROR_PIPELINE            (red de contención)
         → correo MODERACION_RESULTADO al organizador (D9)
```

### 2.2 DTO en cascada

`EventoCrearRequest` contiene `UbicacionRequest` y `List<CronogramaRequest>`, y cada cronograma una `List<TicketRequest>`: **cuatro niveles**. `@Valid` en cada atributo anidado hace que **todo el árbol** se valide (B02 §5.3). Highlights:

```java
// CronogramaRequest
@NotNull @FechaFuturaValida(message = "...")        // anotación PROPIA (B02 §5.5)
@JsonFormat(pattern = "yyyy-MM-dd")                  // cómo (de)serializa Jackson esta fecha
private LocalDate fecha;

@NotEmpty @Size(max = 10) @Valid
private List<TicketRequest> tickets;

private Long idCronograma;                           // solo se manda al EDITAR (§4)

// TicketRequest
@Pattern(regexp = "^(?=.{3,20}$)\\S+( \\S+)*$", ...)   // A04 §2.4
private String tipoTicket;

@NotNull @DecimalMin("0.0") @DecimalMax("50000000") @Digits(integer = 10, fraction = 2)
private BigDecimal precio;                           // 0 = entrada gratuita

@NotNull @Min(1) @Max(1_000_000)
private Integer cupoMaximo;
```

Detalles: el **precio es `BigDecimal`** (A01 §3.4) y `@Digits(fraction = 2)` limita a centavos; `@JsonFormat` fija el formato de fecha y hora en el JSON.

### 2.3 Reglas que `@Valid` no puede expresar (`EventoService.crear`)

- **`validarAgenda`**: que `horaFin` sea posterior a `horaInicio` (`isAfter`, no `equals`: una duración cero no tiene sentido), que **fecha + hora de inicio** sean posteriores **al momento actual** (no solo al día) y que no haya **dos funciones con la misma fecha y hora de inicio** (un doble clic en "agregar fecha"). Dependen de **varios campos a la vez**, por eso no las cubre `@Valid`.
- **Categoría activa** (`existsByIdCategoriaAndActivaTrue`): una categoría desactivada por un administrador (RF-6.3) no se ofrece para eventos **nuevos**.
- **`validarCantidadDeImagenes`**: mínimo 1, máximo `enexia.multimedia.max-imagenes` (3).
- **`personaJuridicaService.resolverOrganizacionHabilitada`**: si se publica a nombre de una organización, el usuario debe ser **miembro** y la organización estar habilitada (D7).

### 2.4 El pipeline asíncrono, en una vista

El **mecanismo** (evento + `AFTER_COMMIT` + `@Async` + pool propio) se explica a fondo en **B05 §4**. Lo específico del módulo:

- **Dos fases secuenciales**: texto primero (barato, local); recién después las imágenes (red). Si el texto falla, **no se sube ninguna imagen**.
- **Permisivo con las imágenes**: *"alcanza con UNA imagen aprobada para publicar"* (RF-2.3). Castigar el evento entero porque una de tres imágenes falló *"penaliza al organizador por un error menor"*.
- **Cloudinary** (`CloudinaryService`): sin credenciales funciona en **modo simulado** (aprueba todo, salvo que el nombre del archivo contenga `rechazar`: así se prueba la rama de rechazo sin cuenta). Con credenciales sube, pide el complemento de moderación y **borra** una imagen rechazada para no dejar contenido inapropiado accesible por URL directa. El estado `pending` del complemento se trata como aprobado provisional (el repaso lo hace el panel admin).
- **`PublicacionEventoService`**: las escrituras finales son **otro bean**, con `REQUIRES_NEW` + `READ_COMMITTED`, para que **ninguna transacción de base de datos esté abierta mientras se habla con Cloudinary** (una conexión retenida varios segundos agotaría el *pool* de conexiones y el sitio caería por culpa de un tercero lento).

### 2.5 El "skeleton" y el estado visible para el organizador

Mientras la moderación corre, el dashboard muestra el evento como **"Validando..."**. Si `RECHAZADO_SISTEMA`, `GET /{id}/moderacion` explica el motivo; si lo suspendió un administrador, incluye su **observación** (que **nunca** viaja en la respuesta que ve el público: B02).

### 2.6 La pantalla (`pages/organizador/evento-form.html`, 1.400 líneas)

Es el formulario más complejo del frontend. Lo que conviene mirar:

- **Zona de arrastre** (`dropzone`): escucha `dragover`, `dragleave` y `drop` (los eventos nativos de arrastrar y soltar), muestra una **vista previa** leyendo el archivo con `FileReader` y valida tipo (`image/jpeg`, `image/png`) y peso (`MAX_BYTES = 2 * 1024 * 1024`) **antes** de enviar. (El backend vuelve a validar: C02 §12.4.)
- **Agenda dinámica**: el organizador agrega/quita cronogramas y tickets; el JS arma el árbol `cronogramas[].tickets[]` que espera el DTO.
- **Envío**: `API.crearEvento(datos, imagenes)` → `FormData` con la parte `datos` (un `Blob` JSON) y las `imagenes` (C02 §11.5). Maneja `202` (éxito: va al dashboard) y los errores (`400` con detalle por campo vía `UI.pintarErrores`, y el resto vía `UI.mensajeDeError`).
- Sin "+" en el botón "Crear Evento" y sin nombres de tablas en los rótulos (reglas de diseño del `CLAUDE.md`).

---

## 3. Dashboard del organizador (RF-2.8)

`GET /api/organizador/eventos?texto&idCategoria&estado&pagina&tamano` → `Page<EventoResponse>`.

- **Parámetros saneados** en el controller: `Math.max(0, pagina)` y `Math.min(Math.max(1, tamano), TAMANO_PAGINA_MAXIMO)` (B02 §7).
- **Orden por defecto:** `fechaCreacion` descendente.
- **`finalizacionEventosService.finalizarVencidosDe(email)` se llama antes de listar**, en el **controller** y no dentro del servicio. El comentario del código explica una sutileza de **aislamiento**: el servicio de listado ya abre una transacción de lectura, y con `REPEATABLE READ` el cambio confirmado por la finalización **no sería visible en esa misma transacción** (B03 §6.4).
- **`EventoMapper.aTarjeta`** arma la tarjeta; es una clase `@Component` de **lógica pura**, por lo que los tests usan la real.
- **N+1 evitado:** `mapearPagina` resuelve, **en lote**, ubicación, próxima fecha y conteos para toda la página (B03 §2.5).
- Pantallas: `dashboard.html` (resumen + últimos 5 + **modal de estadísticas**) y `mis-eventos.html` (tabla con filtros y acciones).

---

## 4. Edición (RF-2.7)

`PUT /api/organizador/eventos/{id}` (multipart). **A diferencia de crear, es SÍNCRONA y devuelve 200.** Por qué:

> Un evento YA aprobado está **visible**. La moderación asíncrona del alta existe porque *"no hay nada que mostrar todavía"*. Acá, bloquear la respuesta es justamente lo que permite **no tocar la versión vigente** hasta tener el veredicto: si la edición se rechaza, el método sale con una excepción y **ninguna fila cambió**; el evento viejo ni se enteró.

El costo (una petición que espera la subida a Cloudinary) cabe en el presupuesto de escritura (4 s) para 1–3 imágenes.

### 4.1 Orden de las comprobaciones (importa)

1. `buscarPropio` (404 si es ajeno) → 2. **bloqueo de fila** (`bloquearParaActualizar`) → 3. `verificarEditable` (solo `APROBADO_*` y no `DADO_DE_BAJA`/`CANCELADO`/`FINALIZADO`) → 4. organización → 5. agenda → 6. categoría (puede **conservar** una categoría desactivada) → 7. **`validarCupoYPrecio`** → 8. **moderar** → 9. `reemplazarContenido` (recién ahora se escribe).

**Moderar al final de las reglas baratas:** se evita subir imágenes a Cloudinary para una edición que de todos modos iba a ser rechazada por una regla local.

### 4.2 Reglas de cupo y precio

Un ticket que **ya tiene inscripciones** está protegido:

- El **cupo máximo no puede bajar** del que ya tenía (`"El cupo de 'General' no puede bajar de 100"`).
- El **precio no se puede cambiar** si el ticket tiene inscripciones activas (`cupoActual > 0`).
- **No se puede quitar** un sector (ticket) con alguna inscripción asociada: la base lo rechazaría por la clave foránea, y esa excepción genérica terminaba como un incomprensible *"Ya existe una cuenta con esos datos"* (bug real que motivó la regla).

La **correspondencia** entre un ticket del formulario y uno guardado se hace por `idCronogramaTicket`; sin ese id (o con uno ajeno), el ticket se trata como **nuevo**, sin piso que respetar.

### 4.3 Reconciliar, no borrar y recrear

`reemplazarContenido` → `reconciliarAgenda` **compara** lo guardado con lo recibido: **actualiza** lo que sigue, **crea** lo nuevo y **borra** lo que ya no viene. Dos `Map<Long, ...>` (A03 §3.3) y dos `Set<Long>` de ids conservados hacen el cruce. Borrar-y-recrear todo **perdería los ids** de los cronogramas y tickets que las inscripciones ya referencian.

---

## 5. Baja lógica (RF-2.9)

`DELETE /api/organizador/eventos/{id}` **no borra**: pone `estado_organizador = DADO_DE_BAJA`. Reglas:

- Si ya estaba dado de baja → `409`. Si está `FINALIZADO` o `CANCELADO` → `409`: *"es historia, no algo que se retire; darlo de baja borraría de la vista del organizador un evento con inscriptos y valoraciones"*.
- **Bloqueo de fila + `READ_COMMITTED`** (el pipeline puede estar escribiendo el mismo evento: B03 §7.2).
- **`@DynamicUpdate`** en `Evento` es lo que evita que el pipeline **pise** el `estado_organizador` (el *lost update* que motivó el cambio: B03 §7.2).
- **Avisa a los inscriptos** (`avisoInscriptosService.avisarEventoCancelado`): el correo sale **después del commit** (D9).
- Historial + auditoría.

---

## 6. Finalización automática (`FINALIZADO`)

`FINALIZADO` es un **estado persistido, no derivado** (ADR-21): derivarlo habría roto los filtros SQL del dashboard, la guarda de edición y el catálogo. Se persiste con historial (`usuario = null`: lo decide el sistema). Hay **dos caminos** que lo setean —el scheduler `FinalizacionEventoScheduler` cada 5 minutos y `FinalizacionEventosService` (que también corre al listar los eventos del organizador)—; **ambos** disparan la invitación a valorar y el **libro de correos** evita el doble envío (B05 §6). Cada evento se finaliza **en su propia transacción** (B01 §8).

> **Deuda conocida:** `FinalizacionEventoScheduler` usa `LocalDateTime.now()` (zona de la JVM): en un servidor UTC finalizaría los eventos 3 h antes. Antes de desplegar, usar `now(zona)` como el recordatorio.

---

## 7. Estadísticas (RF-2.10)

`GET /api/organizador/eventos/{id}/estadisticas` devuelve **visitas únicas** (`COUNT DISTINCT` de usuarios identificados), **visitas totales**, cantidad de cronogramas y **cupo ofrecido y ocupado** del evento. Dos decisiones: se exponen las **únicas y las totales por separado** (miden alcance e interés: *"publicar una sola invita a leerla como si fuera la otra"*) y un evento ajeno responde **404** (no 403).

El **desglose por cronograma** (inscriptos, ocupación y promedio de valoraciones de cada función) **no está programado**: está especificado en [`tempDFD/modulo_2_estadisticas/2.10_estadisticas_por_cronograma.md`](../tempDFD/modulo_2_estadisticas/2.10_estadisticas_por_cronograma.md), con las **consultas agrupadas** (una por métrica, no una por cronograma) y la validación de que **las valoraciones son por cronograma** (D3).

La pantalla **ya existe en su versión por evento**: el modal de `dashboard.html` (`abrirModalStats`) hace `API.get('/api/organizador/eventos/' + id + '/estadisticas')` y pinta cuatro números con un template literal. Ampliarla a una tarjeta por cronograma es cambiar ese template.

---

## 8. Pruebas del módulo

| Clase | Qué protege |
|---|---|
| `EventoServiceTest` | Esqueleto sin contenido; el pipeline se dispara **por evento**; validaciones; edición (cupo/precio/sectores); baja (409 doble); **25 eventos seguidos sin tope**. |
| `ModeracionEventoServiceTest` | Las dos fases, el veredicto permisivo, `ERROR_PIPELINE` como red de contención. |
| `EventoMapperTest` | Armado de tarjetas y ficha (lógica pura, sin mocks). |
| `FinalizacionEventoServiceTest` | Finalización, historial y aviso. |
| `EventoApiTest`, `EventosMasivosApiTest` | De punta a punta con la base real; **Awaitility** para esperar la moderación; la suite masiva crea 30 eventos con 1–3 cronogramas y 1–2 tickets al azar. |
| `CrearEventoTest` | El formulario con un navegador real. |

---

## 9. Límites y pendientes

1. **Estadísticas por cronograma** y **Plan Pro**: especificadas, sin programar (`tempDFD`).
2. El pipeline vive **en memoria**: si el proceso se reinicia con tareas pendientes, esos eventos quedan `EN_PROCESO` (documentado en `AsyncConfig`). El paso natural es una cola externa (RabbitMQ/Redis).
3. Zona horaria del scheduler de finalización (§6).
4. **No hay endpoint de "cancelar" evento** del organizador con reembolso orquestado; `CANCELADO` existe como estado y como motivo de aviso, pero el único retiro disponible hoy es la **baja lógica**.

---

## 10. Conceptos aplicados

| Concepto | Dónde |
|---|---|
| Validación en cascada y anotación propia | B02 §5 |
| Multipart + `FormData`/`Blob` | B02 §8, C02 §11.5 |
| Eventos de aplicación, `AFTER_COMMIT`, `@Async`, pools | B05 |
| Bloqueo pesimista, `READ_COMMITTED`, `@DynamicUpdate`, `REQUIRES_NEW` | B03 §6–§7 |
| N+1 y `Map` en lote | B03 §2.5, A03 §1.2 |
| Excepciones de negocio → 400/404/409 | A04 §1.7 |
| Estados como tablas + historial | B03 §2.6–2.7 |
| Drag & drop, `dataset`, delegación | C02 §6–§7 |

---

## 11. Para practicar

1. Dibujá **dos líneas de tiempo** (una para crear, otra para editar) y marcá en cuál el organizador **espera** la moderación y por qué.
2. Un evento tiene un ticket "General" con cupo 100 y 40 inscriptos. ¿Qué pasa si el organizador, al editar, pone cupo 30? ¿Y precio distinto? ¿Y lo quita del formulario? Indicá la excepción y el código HTTP de cada caso.
3. Simulá (en un test con Mockito) el caso "ninguna imagen aprobada": ¿qué estado y motivo debe quedar? Compará con `ModeracionEventoServiceTest`.
4. ¿Por qué `PublicacionEventoService` es una clase **aparte** de `ModeracionEventoService`? Explicalo en términos de transacciones y conexiones.
5. Escribí el JSON del cuerpo de `datos` para un evento con dos cronogramas y tres tickets en total, respetando las validaciones de `EventoCrearRequest`.

**Siguiente capítulo:** [D3 — Módulo 3: Participación (inscripción, pago y valoración)](./D3_modulo_3_participacion.md)
