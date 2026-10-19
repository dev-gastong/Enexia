# tempDFD — DFD pendientes de programar

> **Qué hay acá:** únicamente los diagramas de flujo de datos de lo que **todavía no está construido**. Cuando una pieza se programa y el código coincide con su DFD, el archivo se mueve a [`docs/diagrams/`](../diagrams/README.md). Es la regla que se aplicó el 2026-10-19 con los DFD del Módulo 6 (6.3 y 6.4) y del Módulo 7, que ya estaban implementados y pasaron a `diagrams/`.

**Cierre del proyecto (2026-10-19).** Estos DFD son el material de partida para quien continúe el desarrollo. Cada uno trae: el flujo, las reglas, los endpoints propuestos, los datos que ya existen en el modelo y **qué se puede reutilizar del código actual**.

---

## Decisiones de producto ya tomadas (vigentes para todos los DFD de esta carpeta)

| # | Decisión | Dónde impacta |
|---|---|---|
| D1 | **Suscripción básica: dos planes.** `GRATUITO` (por defecto: no hay fila de `Suscripcion`) y `PRO` (de pago, simulado, **mensual**). | 8.1, 8.2 |
| D2 | **Ningún plan limita la cantidad de eventos.** El tope de eventos se eliminó del código el 2026-10-19 (`EventoService.verificarLimiteDePlan`, la propiedad `enexia.eventos.limite-plan-gratuito` y `enexia.planes.free.eventos-permitidos`). RF-8.2 deja de ser un "tope" y pasa a ser "resolver el plan vigente". | 8.2 |
| D3 | **Las estadísticas son por cronograma.** Cada cronograma (función con fecha y horario) tiene sus propias métricas. Las visitas son la excepción: se registran por **evento**, porque la ficha se visita antes de elegir una función (ver 2.10). | 2.10, 8.3 |
| D4 | **Las valoraciones son por cronograma** (ya cumplido en modelo, base y servicio: `UNIQUE(id_usuario, id_cronograma)`). El promedio "del evento" es un agregado derivado de las valoraciones de sus cronogramas. | 2.10, 8.3 |
| D5 | **Todos los organizadores ven las estadísticas básicas por cronograma; el plan Pro agrega las avanzadas** (distribución de puntajes, series por día, conversión, ingresos, exportación). | 2.10, 8.3 |
| D6 | **El plan pertenece al usuario organizador, no a la organización.** Las estadísticas de un evento se habilitan según el plan de quien lo creó (`Evento.organizador`). | 8.2, 8.3 |
| D7 | **Desbloqueo de cuentas: solo el panel de administración** (ya implementado el 2026-10-19; ver [DFD 6.2](../diagrams/modulo_6_admin/6.2_gestion_cuentas_usuario.md)). | — |

---

## Índice y orden sugerido de programación

| Orden | DFD | RF | Tamaño | Depende de | Qué reutiliza del código actual |
|---|---|---|---|---|---|
| 1 | [8.2 Plan vigente (sin tope)](./modulo_8_membresias/8.2_control_cuotas_plan.md) | RF-8.2 | S | — | `SuscripcionRepository`, `AdminSuscripcionService.esPro/aFila` |
| 2 | [8.1 Suscripción Pro: upgrade, cancelar, vencer](./modulo_8_membresias/8.1_upgrade_suscripcion_pago.md) | RF-8.1 | M | 8.2 | `PasarelaPagoSimuladaService`, `Pago`, `HistorialEstadoSuscripcion`, patrón de `RegistroInscripcionService` y de `FinalizacionEventosJob` |
| 3 | [2.10 Estadísticas básicas por cronograma](./modulo_2_estadisticas/2.10_estadisticas_por_cronograma.md) | RF-2.10 | M | — | `EventoService.estadisticas`, `CronogramaTicketRepository`, `ValoracionRepository`, `VisitaRepository` |
| 4 | [8.3 Estadísticas avanzadas Pro por cronograma](./modulo_8_membresias/8.3_restriccion_features_pro.md) | RF-8.3 | L | 1, 2 y 3 | lo anterior + `Inscripcion.fechaInscripcion` |
| 5 | [6.5 Otorgar el rol ADMINISTRADOR](./modulo_6_admin/6.5_otorgar_rol_administrador.md) | RF-6.2+ | S | — | `AdminUsuarioService`, `Usuario.getRoles()`, `UsuarioRol` |

Vista de conjunto del Módulo 8: [nivel1_membresias.md](./modulo_8_membresias/nivel1_membresias.md).

> **Preguntas abiertas y sugerencias** que quedaron sin resolver (para que las decida quien continúe): [SUGERENCIAS_PENDIENTES.md](../SUGERENCIAS_PENDIENTES.md).
