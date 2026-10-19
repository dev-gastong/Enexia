# D8 — Módulo 8: Membresías, planes y estadísticas por cronograma

> **Parte D · Los módulos.** Este es el único módulo que **no está terminado**: el **lado del administrador** (RF-6.4) existe; el **lado del organizador** —contratar el plan Pro, cancelarlo, ver estadísticas avanzadas— está **especificado y sin programar**. Por eso este capítulo es distinto: enseña **cómo se programaría** lo que falta, con el mismo método que se usó en todo el proyecto, y deja **bocetos de código** (marcados *propuesta*) para quien continúe.
>
> Los **DFD de lo pendiente** están en [`docs/tempDFD/`](../tempDFD/README.md) y **las decisiones de producto** en su README (D1 a D7).

---

## 1. Qué hay y qué falta

| Pieza | Estado |
|---|---|
| Entidades `Suscripcion`, `SuscripcionEstado`, `HistorialEstadoSuscripcion`, `Pago` | ✅ existen |
| Panel del administrador: listar, resumen, `REVOCAR` / `CAMBIAR_PLAN` / `RENOVAR` (RF-6.4) | ✅ D6 §5 |
| Pasarela de pago simulada | ✅ D3 §3.5 |
| **8.1** Contratar / cancelar / vencer la suscripción Pro (lado organizador) | ❌ [tempDFD 8.1](../tempDFD/modulo_8_membresias/8.1_upgrade_suscripcion_pago.md) |
| **8.2** Servicio que resuelve el plan vigente | ❌ [tempDFD 8.2](../tempDFD/modulo_8_membresias/8.2_control_cuotas_plan.md) |
| **2.10** Estadísticas **por cronograma** (básicas, todos) | ❌ [tempDFD 2.10](../tempDFD/modulo_2_estadisticas/2.10_estadisticas_por_cronograma.md) |
| **8.3** Estadísticas **avanzadas** por cronograma (solo Pro) | ❌ [tempDFD 8.3](../tempDFD/modulo_8_membresias/8.3_restriccion_features_pro.md) |
| Tope de eventos por plan | 🗑️ **eliminado** el 2026-10-19: *ningún plan limita los eventos* |
| Nada se vence solo: **ningún proceso pasa una suscripción a `VENCIDA`** | ❌ (flujo C de 8.1) |

---

## 2. El modelo, y lo que ya enseña

```
Usuario (organizador) ──< Suscripcion >── Suscripcion_Estado   PENDIENTE · ACTIVA · VENCIDA · CANCELADA
                              │   (tipo_plan "FREE"|"PRO", fecha_inicio, fecha_fin)
                              ├── Historial_Estado_Suscripcion  (estado, fecha_cambio, motivo_cambio)
                              └── Pago (1:1)  (monto, metodo_pago, token_operacion, estado COMPLETADO|FALLIDO|REEMBOLSADO)
```

Decisiones heredadas que hay que respetar:

- **No existe una fila "FREE" por cada cuenta.** *Sin suscripciones, el plan es Gratuito* (ADR-26): evita sembrar una fila por cada usuario.
- **El plan vigente es el de la última suscripción** — y para ser **Pro** debe cumplirse **a la vez**: `tipo_plan = PRO`, estado `ACTIVA` y `fecha_fin >= hoy`. La **fecha se comprueba aunque el job de vencimiento no haya corrido**: el plan no puede quedar "Pro de más" por un día de atraso del job.
- `Pago.id_suscripcion` es **`@OneToOne`**: **una suscripción tiene, a lo sumo, un pago**. Un pago rechazado deja el `Pago` en `FALLIDO` y la suscripción en `PENDIENTE`; **el reintento** necesita decidir entre *reusar* la suscripción (reemplazando el pago) o *crear otra* (sugerencia S8).
- Facturación **mensual**: `fecha_fin = hoy + 1 mes` (la misma regla que ya usa `renovarCobertura` del panel).

---

## 3. 8.2 primero: el servicio que resuelve el plan

Es la pieza base: 8.1 y 8.3 la consultan, y **el panel de administración debería pasar a usarla** (hoy repite la regla en `AdminSuscripcionService.esPro`).

### 3.1 Boceto (*propuesta, no está en el repositorio*)

```java
public enum PlanVigente { GRATUITO, PRO }

@Service
@RequiredArgsConstructor
public class PlanService {

    private final SuscripcionRepository suscripcionRepository;

    @Transactional(readOnly = true)
    public PlanVigente resolver(Long idUsuario) {
        return suscripcionRepository.buscarUltimaDeUsuario(idUsuario)        // Optional<Suscripcion>
                .filter(s -> "PRO".equalsIgnoreCase(s.getTipoPlan()))
                .filter(s -> "ACTIVA".equals(s.getEstadoSuscripcion().getNombreEstado()))
                .filter(s -> s.getFechaFin() != null && !s.getFechaFin().isBefore(LocalDate.now()))
                .map(s -> PlanVigente.PRO)
                .orElse(PlanVigente.GRATUITO);
    }
}
```

Una **cadena de `Optional.filter`** (A03 §4): cada condición, si falla, **vacía** el `Optional`, y `orElse(GRATUITO)` resuelve el caso por defecto. Tres comprobaciones, **cero `if` anidados**.

Detalles que hay que cuidar al programarlo:
- **"La última"**: ordenar por `fecha_inicio` y `id` descendentes (`findFirstByUsuarioIdUsuarioOrderByFechaInicioDescIdSuscripcionDesc`).
- **Zona horaria:** `LocalDate.now()` es la fecha de la JVM; usar la zona `enexia.zona-horaria` (B05 §7.3).
- **Un test por rama** (B06): sin suscripciones, `PRO`+`ACTIVA`+vigente, `PRO`+`ACTIVA`+vencida por fecha, `PRO`+`CANCELADA`, `FREE`+`ACTIVA`.

---

## 4. 8.1: el ciclo de vida de la suscripción Pro

Los tres flujos (A upgrade, B cancelar, C vencer) están en el DFD. Lo que **conviene aprender** de cada uno, porque **reutilizan patrones ya resueltos** en el proyecto:

### 4.1 Upgrade = el patrón de `RegistroInscripcionService` (D3 §3.2)

Es **el mismo problema**: un pago rechazado debe **persistirse** (suscripción `PENDIENTE` + `Pago` `FALLIDO`) y a la vez **devolver 402**. La solución es la misma:

```java
// Propuesta: InscripcionService / RegistroInscripcionService, en versión "suscripción"
// SuscripcionService.contratar(...)            ← SIN @Transactional
//     └─► SuscripcionTransaccional.registrar(...)   ← otro bean, UNA transacción que SIEMPRE confirma
//             · bloquear la fila del usuario o validar "una ACTIVA por usuario"
//             · crear PENDIENTE + historial
//             · pasarela.procesar(precioMensual, metodoPago)
//             · aprobado → Pago COMPLETADO, ACTIVA, fecha_inicio = hoy, fecha_fin = hoy.plusMonths(1)
//             · rechazado → Pago FALLIDO, queda PENDIENTE
//             · devuelve un record ResultadoUpgrade(aprobado, motivo)
//     recién después: si !aprobado → throw new PagoRechazadoException(...)   (402)
```

**No inventes otro mecanismo**: copiá esa estructura. Y usá `enexia.planes.pro.precio-mensual` (ya existe).

### 4.2 "Una ACTIVA por usuario" bajo concurrencia

Dos pedidos simultáneos de upgrade podrían crear **dos** suscripciones. Un chequeo en el servicio **no alcanza** (B03 §7.2). Opciones, de la más simple a la más robusta:
1. **Bloqueo pesimista** sobre la fila del `Usuario` (`bloquearParaActualizarSeguridad`, que ya existe) antes de consultar y crear.
2. Un **índice único** sobre una columna generada (`activa_unica`) que valga el id del usuario solo para filas `ACTIVA` (MariaDB admite columnas generadas): la base garantiza la regla.

### 4.3 Cancelar

`POST /api/organizador/suscripcion/cancelar`: bloqueo de la suscripción `ACTIVA`, pasa a `CANCELADA`, historial con motivo "Cancelación del titular", **inmediato** (vuelve a Gratuito en el acto: decisión por defecto S1; la alternativa —mantener hasta `fecha_fin`— exige un campo nuevo).

### 4.4 Vencer: un *scheduler* + un servicio (B05 §7)

```java
// Propuesta — copiar la estructura de FinalizacionEventosJob + FinalizacionEventosService
@Component
@RequiredArgsConstructor
public class VencimientoSuscripcionesJob {
    private final VencimientoSuscripcionesService servicio;

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Argentina/Ushuaia")     // 00:05 todos los días
    public void barrido() {
        try {
            servicio.vencerLasQueCorresponda();
        } catch (RuntimeException ex) {
            log.error("Fallo el vencimiento de suscripciones", ex);   // un fallo no cancela los siguientes
        }
    }
}
```

- **El `@Scheduled` va en un bean aparte** del servicio transaccional (B03 §6.6): cada suscripción se vence **en su propia transacción** (a través del proxy) para que una que falle no deshaga las demás.
- `cron = "0 5 0 * * *"` = segundo 0, minuto 5, hora 0, todos los días; `zone` evita la trampa de la zona de la JVM.
- **Idempotente** por construcción: solo toma las `ACTIVA` con `fecha_fin < hoy`; correrlo dos veces no hace nada la segunda.
- Historial con `usuario = null` (lo decide el sistema), igual que `FINALIZADO` de los eventos.

### 4.5 Pantalla del organizador (propuesta)

`pages/organizador/suscripcion.html` con tres estados (C02 §11.1): *Plan Gratuito* con el botón "Pasar a Pro"; *Plan Pro activo* con vigencia y "Cancelar"; *Pro vencido* con "Volver a contratar". Reutiliza el sidebar del panel y los tokens (C03). **El modal de pago** es solo un selector de método (la pasarela es simulada: un método que contenga `rechazar` fuerza el 402, útil para demostrar el error).

---

## 5. Estadísticas **por cronograma** (2.10 y 8.3)

### 5.1 La validación previa (hecha el 2026-10-19)

Antes de especificar, se **verificó que las valoraciones sean de un cronograma y no de un evento** (D3 §6.1): `valoracion.id_cronograma` con `UNIQUE (id_usuario, id_cronograma)`. Eso **hace posible** un promedio por función. Las **visitas**, en cambio, son del **evento** (`Visita.id_evento`): se registran al abrir la ficha, **antes** de que la persona elija una función, así que **no se pueden atribuir** a un cronograma.

### 5.2 Qué mide cada nivel

| Nivel | Métrica | Fuente | Plan |
|---|---|---|---|
| **Evento** | visitas únicas y totales | `Visita` | todos (ya existe) |
| **Cronograma** | cupo total, inscriptos confirmados, disponibles, % ocupación, detalle por ticket | `Cronograma_Ticket`, `Inscripcion` | todos |
| **Cronograma** | promedio y cantidad de valoraciones (solo finalizados) | `Valoracion` | todos |
| **Cronograma** | distribución de puntajes 1–5, tasa de cancelación, ingresos, serie diaria de inscripciones | `Valoracion`, `Inscripcion`, `Pago` | **Pro** |
| **Evento** | serie diaria de visitas, conversión (inscriptos / visitas únicas) | `Visita`, `Inscripcion` | **Pro** |
| **Cronograma** | exportación CSV de asistentes (solo `nickname`, ticket, fecha, estado — **nunca DNI ni email**) | `Inscripcion`, `Usuario` | **Pro** |

### 5.2.1 Las consultas: **agrupar en la base, unir en memoria** (A03 §1.2, B03 §2.5)

Para evitar **N+1** (una consulta por cronograma) se hacen **tres consultas agrupadas por evento** y se unen por `idCronograma` con un `Map`:

```java
// Propuesta: ValoracionRepository
@Query("""
        SELECT v.cronograma.idCronograma, AVG(v.valor), COUNT(v)
        FROM Valoracion v
        WHERE v.cronograma.evento.idEvento = :idEvento
        GROUP BY v.cronograma.idCronograma
        """)
List<Object[]> promedioPorCronograma(@Param("idEvento") Long idEvento);

// Propuesta: InscripcionRepository
@Query("""
        SELECT t.cronograma.idCronograma, COUNT(i)
        FROM Inscripcion i JOIN i.cronogramaTicket t
        WHERE t.cronograma.evento.idEvento = :idEvento
          AND i.estadoInscripcion.nombreEstado = 'CONFIRMADA'
        GROUP BY t.cronograma.idCronograma
        """)
List<Object[]> contarConfirmadasPorCronograma(@Param("idEvento") Long idEvento);
```

En el servicio:

```java
Map<Long, Long> confirmadas = new HashMap<>();
for (Object[] fila : inscripcionRepository.contarConfirmadasPorCronograma(idEvento)) {
    confirmadas.put((Long) fila[0], (Long) fila[1]);
}
// ... y por cada cronograma: confirmadas.getOrDefault(c.getIdCronograma(), 0L)
```

El **promedio** que devuelve `AVG` es un `Double`; si el cronograma no tiene filas, **no aparece** en el resultado: hay que mapear la ausencia a `null` ("sin calificar", **nunca 0**: B03 §5.3).

### 5.3 La restricción por plan (8.3)

```java
// Propuesta
public EstadisticasAvanzadasResponse avanzadas(String email, Long idEvento) {
    Usuario organizador = /* ... */;
    Evento evento = buscarPropio(idEvento, organizador);          // 404 si es ajeno  ← PRIMERO
    if (planService.resolver(evento.getOrganizador().getIdUsuario()) != PlanVigente.PRO) {
        auditoria...("ESTADISTICAS_AVANZADAS_DENEGADO");
        throw new PlanProRequeridoException();                    // → 403, código PLAN_PRO_REQUERIDO
    }
    // ... agregaciones
}
```

- **Orden de comprobaciones: autenticación → propiedad → plan.** El **404 va antes que el 403**: un organizador Gratuito **no puede usar el 403 para averiguar si un evento ajeno existe** (B04 §7.5).
- Se evalúa el plan del **dueño del evento** (D6 en `tempDFD`), siempre **contra la base, no desde el JWT** (un token emitido antes de contratar mostraría el plan viejo hasta vencer: mismo razonamiento que ADR-20).
- Una **excepción propia** nueva (`PlanProRequeridoException`) con su `@ExceptionHandler` → `403` + `PLAN_PRO_REQUERIDO` (A04 §1.7–1.8). El código estable permite que la pantalla muestre **el candado** y el botón "Pasar a Pro" en vez de un error genérico.

### 5.4 Pantalla

El modal de estadísticas de `dashboard.html` (`abrirModalStats`) hoy muestra **cuatro números del evento**. Se amplía a **una tarjeta por cronograma** (fecha, barra de ocupación, promedio con "N opiniones") y, para Pro, una pestaña "Avanzadas". Si la respuesta es `403 PLAN_PRO_REQUERIDO`, se muestra el **candado**. **Atención:** todo texto que venga del servidor (nombres de tickets) se inserta con `UI.esc` (C02 §12).

---

## 6. Lista de tareas para quien programe (orden recomendado)

1. `PlanService.resolver` + tests (§3). *Opcional:* que `AdminSuscripcionService` lo use.
2. Endpoints del organizador `GET/POST upgrade/POST cancelar` (§4.1–4.3) + índice/bloqueo de "una ACTIVA" + tests de servicio y de API.
3. `VencimientoSuscripcionesJob` + servicio (§4.4) + test (con un *Clock* o fecha inyectada).
4. `EventoEstadisticasResponse` + `cronogramas[]` (§5.2.1) + tests; ampliar el modal de `dashboard.html`.
5. `GET .../estadisticas/avanzadas` + `PlanProRequeridoException` + 403 (§5.3) + exportación CSV con columnas mínimas.
6. Pantalla `suscripcion.html` (§4.5).
7. **Documentar**: mover los DFD de `tempDFD` a `diagrams`, actualizar `CLAUDE.md`, el log del sprint y `SUGERENCIAS_PENDIENTES.md`.

**Antes de empezar**, revisá las decisiones abiertas S1 a S8 en [`SUGERENCIAS_PENDIENTES.md`](../SUGERENCIAS_PENDIENTES.md): están resueltas **por defecto**, pero son del producto.

---

## 7. Conceptos que se reutilizan (nada nuevo hay que inventar)

| Necesidad | Ya resuelto en |
|---|---|
| Pago rechazado que persiste y devuelve 402 | `RegistroInscripcionService` — D3 §3.2 |
| Cadena de `Optional.filter` | A03 §4 |
| `@Scheduled` + servicio aparte + una transacción por elemento | `FinalizacionEventosJob` — B05 §7, B01 §8 |
| Bloqueo pesimista + `READ_COMMITTED` | B03 §7.2 |
| 404 antes que 403; plan desde la base, no desde el JWT | B04 §7.5, ADR-20 |
| Consultas agrupadas + `Map`, `AVG` null ≠ 0 | A03 §1.2, B03 §5.3 |
| Excepción propia + `@ExceptionHandler` | A04 §1.7–1.8 |
| Correos (`despachar`/`despacharUnico`) | D9 |
| Modales, tarjetas, `UI.esc`, tres estados | C02, C03 |

---

## 8. Para practicar (sobre lo que falta)

1. Escribí **los cinco tests** de `PlanService.resolver` (nombre `@DisplayName` y `when(...)` de cada uno), sin programar el servicio todavía.
2. Diseñá la consulta (JPQL) del **ingreso por cronograma**: ¿suma `Inscripcion.precioAbonado` de las confirmadas o `Pago.monto` de las `COMPLETADO`? ¿Cuál es más fiel ante un reembolso?
3. Escribí el `@ExceptionHandler` para `PlanProRequeridoException`. ¿Con qué `HttpStatus` y qué `construir(...)`?
4. Dos pedidos de upgrade simultáneos del mismo organizador: dibujá la línea de tiempo **sin** y **con** el bloqueo sobre el `Usuario`.
5. Proponé el JSON de `GET /estadisticas` con dos cronogramas (uno finalizado con 3 valoraciones, otro futuro) respetando los *null* de "sin calificar".

**Siguiente capítulo:** [D9 — Notificaciones por correo (prefijo N)](./D9_modulo_N_notificaciones.md)
