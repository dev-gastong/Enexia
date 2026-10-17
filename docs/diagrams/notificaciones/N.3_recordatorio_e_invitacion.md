# DFD Nivel 2 — N.3 Notificaciones a inscriptos disparadas por el sistema

Descompone el proceso **N.3** del [Nivel 1](./nivel1_notificaciones.md) para los correos que **no los dispara una persona sino el reloj**: el recordatorio de 10 minutos y la invitación a valorar (RF-3.4). Reutiliza el Scheduler que ya existe para `FINALIZADO`.

```mermaid
graph TD
    INICIO([INICIO: Tick del Scheduler]) --> Reloj([Temporizador del sistema])

    subgraph Almacenes
        D2[(D2: Evento / Evento_Cronograma)]
        D3[(D3: Inscripcion / Cronograma_Ticket)]
        D1[(D1: Usuario)]
        D7[(D7: Notificacion_Enviada)]
    end
    N5[[N.5 Despachador de correo]]

    %% ---------- A. RECORDATORIO ----------
    Reloj -->|Cada 60 s| R1[N.3.1 Buscar funciones por comenzar]
    R1 -->|Cronogramas con inicio entre ahora y ahora + 10 min<br>de eventos PUBLICADOS y aprobados| D2
    D2 --> R2[N.3.2 Buscar inscripciones vigentes de esas funciones]
    R2 -->|Inscripciones confirmadas por cronograma| D3
    D3 --> R3{¿Hay inscripciones<br>sin RECORDATORIO_10MIN registrado?}
    R3 -->|No| Nada1([Sin trabajo en este tick])
    R3 -->|Sí| R4[N.3.3 Emitir orden por cada inscripción pendiente]
    R4 -->|Consultar D7: tipo + usuario + id_inscripcion| D7
    R4 -->|Orden RECORDATORIO_10MIN, una por inscripción, con pausa entre envíos| N5

    %% ---------- B. INVITACIÓN A VALORAR ----------
    Reloj -->|Ya existente: FinalizacionEventoService| F1[N.3.4 Pasar a FINALIZADO los eventos cuya última función terminó]
    F1 -->|UPDATE estado_organizador + historial, una transacción por evento| D2
    F1 -->|Publicar EventoFinalizado AFTER_COMMIT| F2[N.3.5 Resolver quiénes asistieron]
    F2 -->|Inscripciones confirmadas del evento| D3
    D3 --> F3{¿Tiene inscripciones<br>confirmadas?}
    F3 -->|No| Nada2([Sin invitaciones])
    F3 -->|Sí| F4[N.3.6 Emitir INVITACION_VALORAR por inscripción]
    F4 -->|Orden INVITACION_VALORAR con enlace a la ficha del evento| N5

    %% ---------- C. EVENTO CANCELADO / BAJA ----------
    Org([Organizador]) -->|DELETE evento o cancelación| C1[N.3.7 Cambiar estado del evento - ya existente]
    C1 -->|Estado organizador CANCELADO o DADO_DE_BAJA| D2
    C1 -->|Publicar EventoCancelado AFTER_COMMIT| C2[N.3.8 Resolver inscriptos afectados]
    C2 -->|Inscripciones confirmadas del evento| D3
    C2 -->|Orden EVENTO_CANCELADO por inscripto| N5

    %% ---------- D. INSCRIPCIÓN CONFIRMADA ----------
    Part([Participante]) -->|Se inscribe y paga - ya existente| I1[N.3.9 Confirmar inscripción]
    I1 -->|Inscripción CONFIRMADA + pago| D3
    I1 -->|Publicar InscripcionConfirmada AFTER_COMMIT| I2[N.3.10 Emitir INSCRIPCION_CONFIRMADA]
    I2 -->|Orden con evento, función y tipo de ticket| N5

    Nada1 --> FIN([FIN])
    Nada2 --> FIN
```

## Reglas

| Regla | Motivo |
|---|---|
| **Ventana de 10 minutos, no instante exacto.** Se busca `inicio ∈ (ahora, ahora + 10 min]` y se excluye lo ya enviado. | Si el Scheduler pierde un tick (reinicio, carga), el recordatorio igual sale en el siguiente. Una comparación de igualdad exacta lo perdería para siempre. |
| **Idempotencia en D7** por `(tipo, id_usuario, id_inscripcion)`. | Con ticks cada minuto, una inscripción caería en la ventana ~10 veces. Sin la restricción única recibiría 10 correos. |
| **Una función, no un evento.** El recordatorio es por `Evento_Cronograma`. | Un evento con varias fechas debe avisar antes de cada una, no solo de la primera. |
| **Solo inscripciones confirmadas** de eventos `PUBLICADO` + `APROBADO_*`. | No avisar de un evento suspendido, cancelado o rechazado, ni a quien canceló su inscripción. |
| **Si el evento se cancela dentro de la ventana,** el recordatorio ya no sale. | La consulta de N.3.1 filtra por estado vigente en el momento de cada tick. |
| **Eventos publicados con `AFTER_COMMIT`** (N.3.5, N.3.8, N.3.10). | Mismo criterio de CLAUDE.md para el pipeline asíncrono: el hilo que envía debe ver la fila ya confirmada. |
| **Los administradores no reciben estos correos como participantes.** | Un administrador es solo admin y no puede inscribirse (regla de roles de 2026-10-15). |
| **Envío masivo con pausa** entre correos. | Cuidar la cuota diaria del proveedor y evitar ráfagas. |

> **Probar sin esperar:** el adelanto es configurable en segundos (`enexia.notificaciones.recordatorio.segundos-antes`, 600 por defecto). Para una prueba manual alcanza con `30` y `intervalo-ms=5000`; el intervalo debe ser menor que el adelanto o una funcion puede empezar entre dos pasadas sin avisarse.

> **Zona horaria:** el cálculo de la ventana usa la hora del servidor con la zona `America/Argentina/Ushuaia`, la misma que declara la conexión a la base. Un desfase aquí adelantaría o atrasaría todos los recordatorios.
