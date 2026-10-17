# DFD Nivel 1 — Notificaciones por correo

Subsistema transversal: no tiene pantalla propia. Cada módulo **emite una orden de notificación** y un único despachador (N.5) compone, protege y envía el correo a través de la API HTTPS del proveedor (Brevo).

> **Prefijo `N`:** el "Módulo 7" de la documentación funcional es *Organizaciones*; para no pisar su numeración, este subsistema usa `N.x`.

```mermaid
graph TD
    %% --- ENTIDADES EXTERNAS ---
    Titular([Titular de la cuenta])
    Part([Participante])
    Org([Organizador])
    Admin([Administrador])
    Reloj([Temporizador del sistema - Scheduler])
    Brevo([API de correo - Brevo HTTPS])

    %% --- ALMACENES ---
    D1[(D1: Usuario / Persona_Fisica / Miembros_Organizacion)]
    D2[(D2: Evento / Evento_Cronograma / Historial_Estado_Evento)]
    D3[(D3: Inscripcion / Cronograma_Ticket)]
    D6[(D6: Historial_Interacciones / PasswordResetToken)]
    D7[(D7: Notificacion_Enviada)]

    %% --- PROCESOS DE ORIGEN (emiten la orden) ---
    N1((N.1 Seguridad de cuenta))
    N2((N.2 Moderación de eventos))
    N3((N.3 Inscripciones y ciclo de vida del evento))
    N4((N.4 Equipo de organización))
    N5((N.5 Despachador de correo))

    %% N.1 Seguridad de cuenta
    Titular -->|Solicita enlace de recuperación / intenta login / confirma nueva contraseña| N1
    Admin -->|Suspende cuenta| N1
    N1 <-->|Leer y mutar cuenta, emitir y consumir token| D1
    N1 <-->|Token hasheado + auditoría| D6
    N1 -->|Orden: RECUPERACION, BLOQUEO, PASSWORD_CAMBIADA, CUENTA_SUSPENDIDA| N5

    %% N.2 Moderación de eventos
    Org -->|Crea o modifica evento| N2
    Admin -->|Suspende, reactiva o aprueba| N2
    N2 <-->|Estado del evento + historial| D2
    N2 -->|Orden: MODERACION_RESULTADO, EVENTO_SUSPENDIDO, EVENTO_REACTIVADO| N5

    %% N.3 Inscripciones y ciclo de vida
    Part -->|Se inscribe| N3
    Org -->|Cancela o da de baja evento| N3
    Reloj -->|Tick cada minuto: inicio próximo / evento finalizado| N3
    N3 <-->|Inscripciones y tickets| D3
    N3 -->|Leer cronograma y estado| D2
    N3 -->|Orden: INSCRIPCION_CONFIRMADA, EVENTO_CANCELADO, RECORDATORIO_10MIN, INVITACION_VALORAR| N5

    %% N.4 Equipo
    Org -->|Agrega miembro a la organización| N4
    N4 <-->|Alta en Miembros_Organizacion| D1
    N4 -->|Orden: AGREGADO_A_EQUIPO| N5

    %% N.5 Despachador
    N5 -->|Resolver email destino y nombre| D1
    N5 <-->|Idempotencia y límite por destinatario| D7
    N5 -->|POST /v3/smtp/email con api-key| Brevo
    Brevo -->|201 messageId / 4xx 5xx| N5
    Brevo -.->|Correo entregado| Titular
    Brevo -.->|Correo entregado| Part
    Brevo -.->|Correo entregado| Org
```

## Catálogo de notificaciones

| Código | Destinatario | Origen | Estado |
|---|---|---|---|
| `RECUPERACION` | Titular | N.1, a pedido del usuario | Existe (se endurece) |
| `BLOQUEO` | Titular | N.1, 3er intento fallido | Existe |
| `PASSWORD_CAMBIADA` | Titular | N.1, al consumir el token | **Nuevo** |
| `CUENTA_SUSPENDIDA` | Titular | N.1, acción de Admin (RF-6.2) | **Nuevo** |
| `MODERACION_RESULTADO` | Organizador | N.2, pipeline automático | Existe |
| `EVENTO_SUSPENDIDO` | Organizador | N.2, Admin (motivo + observación) | **Nuevo** |
| `EVENTO_REACTIVADO` | Organizador | N.2, Admin revierte | **Nuevo** |
| `INSCRIPCION_CONFIRMADA` | Participante | N.3, al confirmarse la inscripción | **Nuevo** |
| `EVENTO_CANCELADO` | Participantes inscriptos | N.3, cancelación / baja del evento | **Nuevo** |
| `RECORDATORIO_10MIN` | Participantes inscriptos | N.3, Scheduler | **Nuevo** |
| `INVITACION_VALORAR` | Participantes inscriptos | N.3, evento pasa a `FINALIZADO` (RF-3.4) | **Nuevo** |
| `AGREGADO_A_EQUIPO` | Miembro agregado | N.4, "Mi Equipo" | **Nuevo** |

## Flujos de datos

- **Órdenes de notificación** (N.1–N.4 → N.5): contienen solo `tipo`, `id_usuario` destino y un `id_referencia` (evento, inscripción, organización). **Nunca llevan el cuerpo del correo ni datos libres ya renderizados:** el despachador los compone, así existe un único punto donde se escapa el HTML.
- **D7 Notificacion_Enviada:** guarda `(tipo, id_usuario, id_referencia, fecha, estado)`. Cumple dos funciones: evitar duplicados (un recordatorio por inscripción, una invitación por inscripción) y contar envíos recientes para el límite por destinatario.
- **Disparo post-commit:** N.1–N.4 publican un evento de aplicación y el despachador reacciona en `AFTER_COMMIT`. Un correo nunca sale por una transacción que luego se revierte (ver CLAUDE.md, "Async pipeline").
- **Fallo del proveedor:** se registra en D7 como `FALLIDO` y el flujo de negocio que lo originó **no se entera**.
