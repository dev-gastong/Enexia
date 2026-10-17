# DFD Nivel 2 — N.5 Despachador de correo

Descompone el proceso **N.5** del [Nivel 1](./nivel1_notificaciones.md). Es el **único punto del sistema que habla con el proveedor de correo**, de modo que el escapado de HTML, la idempotencia y el manejo de fallos viven en un solo lugar.

```mermaid
graph TD
    INICIO([INICIO: Orden de notificación publicada tras COMMIT]) --> Ord([Proceso de origen N.1 a N.4])

    subgraph Almacenes
        D1[(D1: Usuario / Persona_Fisica)]
        D2[(D2: Evento / Evento_Cronograma / Ubicacion)]
        D7[(D7: Notificacion_Enviada)]
    end
    Brevo([API de correo - Brevo])
    Cfg([Configuración: BREVO_API_KEY y remitente verificado])

    Ord -->|Orden: tipo + id_usuario + id_referencia| P1[N.5.1 Recibir en AFTER_COMMIT - hilo asíncrono]

    P1 --> P2[N.5.2 Resolver destinatario]
    P2 -->|Email y nombre por id_usuario| D1
    D1 --> C_Dest{¿Usuario existe<br>y tiene email?}
    C_Dest -->|No| Omitir([Log: destinatario inexistente. Se descarta])

    C_Dest -->|Sí| P3[N.5.3 Verificar idempotencia]
    P3 -->|Buscar tipo + id_usuario + id_referencia| D7
    D7 --> C_Dup{¿Ya fue enviada?}
    C_Dup -->|Sí| Dup([Se descarta: no se duplica])

    C_Dup -->|No| P4[N.5.4 Componer contenido]
    P4 -->|Datos del evento: nombre, fecha, lugar| D2
    P4 -->|Datos libres ESCAPADOS: nombre del evento, motivo, observación| P4
    P4 -->|Asunto sin saltos de línea. htmlContent + textContent| P5[N.5.5 Verificar modo]

    P5 --> C_Key{¿Hay BREVO_API_KEY?}
    C_Key -->|No: desarrollo| Sim([Log del correo simulado. Sin envío de red])
    C_Key -->|Sí| P6[N.5.6 Enviar]

    Cfg -.->|Clave y remitente| P6
    P6 -->|POST /v3/smtp/email, header api-key, timeouts 5 s y 10 s| Brevo
    Brevo --> C_Res{¿Respuesta?}
    C_Res -->|201 + messageId| P7[N.5.7 Registrar resultado]
    C_Res -->|4xx / 5xx / timeout| P7
    P7 -->|ENVIADO con messageId, o FALLIDO con código HTTP| D7
    P7 -->|Log sin cuerpo ni token| Fin1([Fin: el flujo de negocio ya había terminado])

    Omitir --> FIN([FIN])
    Dup --> FIN
    Sim --> FIN
    Fin1 --> FIN
```

## Reglas del despachador

1. **Después del commit, nunca antes.** Un correo no puede salir por una transacción que se revierte, ni referirse a una fila que el hilo aún no ve.
2. **Asíncrono siempre.** Si el envío fuera síncrono, el intento de login que dispara el bloqueo tardaría más que los anteriores y esa diferencia de tiempo delataría que la cuenta existe.
3. **Un fallo de envío jamás propaga.** Se registra `FALLIDO` en D7 y el flujo que lo originó ya terminó.
4. **Escapado en un solo lugar.** El nombre de evento, el motivo y la observación del admin los escribe un usuario; si llegan sin escapar a `htmlContent` son una inyección de HTML por correo. El asunto se sanea de saltos de línea.
5. **Idempotencia por `(tipo, id_usuario, id_referencia)`.** El Scheduler puede reintentar sin duplicar correos.
6. **Los logs no contienen el cuerpo.** Algunos correos llevan un token de recuperación, que en un log es una credencial en claro.
7. **Sin `BREVO_API_KEY` el sistema arranca igual**, en modo simulado que vuelca el correo al log. Es lo que permite desarrollar y probar sin cuenta del proveedor.

## Límite del plan gratuito

El plan gratuito del proveedor tiene un tope diario (del orden de 300 correos, a confirmar en su panel). Un evento con muchos inscriptos que se cancela, o una invitación a valorar masiva, pueden agotarlo. Por eso los envíos masivos se hacen **de a uno con una pausa corta** y los fallidos quedan en `FALLIDO` para poder reintentarlos al día siguiente.
