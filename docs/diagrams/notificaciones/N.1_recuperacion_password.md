# DFD Nivel 2 — N.1 Recuperación de contraseña por correo (RF-1.5)

Descompone el proceso **N.1** del [Nivel 1](./nivel1_notificaciones.md) para el caso de la recuperación pedida por el propio usuario. El bloqueo por intentos (RF-1.4) comparte la emisión y el consumo del token.

**Principio rector:** el endpoint público no puede revelar si un correo está registrado. La respuesta, su contenido **y su tiempo** deben ser idénticos exista o no la cuenta.

```mermaid
graph TD
    INICIO([INICIO: Titular olvidó su contraseña]) --> Tit([Titular vía HTTP])

    subgraph Almacenes
        D1[(D1: Usuario)]
        D6T[(D6: PasswordResetToken - guarda SHA-256)]
        D6H[(D6: Historial_Interacciones)]
        D7[(D7: Notificacion_Enviada)]
    end
    Brevo([API de correo - Brevo])

    %% ---------- A. SOLICITUD ----------
    Tit -->|POST /api/auth/recuperacion con email| P1[N.1.1 Validar formato de email]
    P1 --> C_Fmt{¿Email con formato válido?}
    C_Fmt -->|No| Err400([Error 400: formato inválido])
    C_Fmt -->|Sí| P2[N.1.2 Responder y delegar al hilo asíncrono]
    P2 --> Resp([Response 202: Si el correo está registrado, recibirás un enlace - SIEMPRE el mismo cuerpo])
    P2 -.->|Trabajo posterior, fuera del tiempo de respuesta| P3[N.1.3 Buscar cuenta activa por email]

    P3 -->|Consultar por email| D1
    D1 -->|Cuenta o vacío| P3
    P3 --> C_Existe{¿Existe cuenta activa?}
    C_Existe -->|No| Log0([Log interno: email no registrado. Sin envío])
    C_Existe -->|Sí| P4[N.1.4 Verificar límite por destinatario]

    P4 -->|Contar RECUPERACION de las últimas 24 h| D7
    P4 --> C_Lim{¿Superó 3 envíos en 24 h<br>o hay uno de menos de 2 min?}
    C_Lim -->|Sí| Log1([Log interno: límite alcanzado. Sin envío])
    C_Lim -->|No| P5[N.1.5 Emitir token]

    P5 -->|1. Borrar tokens previos de la cuenta| D6T
    P5 -->|2. Generar 32 bytes con SecureRandom, Base64 URL| P5
    P5 -->|3. Guardar solo el hash SHA-256 + vencimiento 30 min| D6T
    P5 -->|Orden RECUPERACION con token en claro| P6[N.1.6 Despachar correo - ver N.5]
    P6 -->|Registrar envío| D7
    P6 -->|POST /v3/smtp/email| Brevo

    %% ---------- B. CONSUMO ----------
    Tit -->|Abre enlace del correo| Pag[Página recuperar.html]
    Pag -->|Lee el token y lo QUITA de la barra con history.replaceState| Pag
    Pag -->|POST /api/auth/recuperacion/confirmar token + password + confirmación| P7[N.1.7 Validar contraseñas]
    P7 --> C_Pw{¿Coinciden y cumplen política?}
    C_Pw -->|No| Err400b([Error 400: contraseñas inválidas])
    C_Pw -->|Sí| P8[N.1.8 Buscar token por hash]

    P8 -->|Hashear y consultar| D6T
    P8 --> C_Tok{¿Existe y no venció?}
    C_Tok -->|No: inexistente, vencido o usado| ErrTok([Error 400: enlace no válido o ya utilizado - mismo texto para los tres casos])
    C_Tok -->|Sí| P9[N.1.9 Aplicar nueva contraseña]

    P9 -->|BCrypt, intentos_fallidos = 0| D1
    P9 -->|El estado de la cuenta NO se toca: una BLOQUEADA solo la reactiva un administrador| D1
    P9 -->|Borrar token: un solo uso| D6T
    P9 -->|Auditoría| D6H
    P9 -->|Orden PASSWORD_CAMBIADA| P10[N.1.10 Despachar aviso de seguridad]
    P10 -->|Correo al titular: tu contraseña fue cambiada| Brevo
    P9 --> Ok([Response 200: contraseña actualizada])

    Err400 --> FIN([FIN])
    Resp --> FIN
    Log0 --> FIN
    Log1 --> FIN
    Err400b --> FIN
    ErrTok --> FIN
    Ok --> FIN
```

## Por qué cada decisión

| Decisión | Amenaza que cierra |
|---|---|
| **N.1.2 responde antes de buscar la cuenta** y el resto corre asíncrono | *Oráculo de tiempo*: con cuenta se hacen escrituras (borrar y guardar token); sin cuenta no. Si eso ocurriera dentro de la petición, la diferencia de milisegundos enumeraría cuentas. |
| **Mismo cuerpo `202`** exista o no el correo | *Enumeración de cuentas* por contenido de respuesta. |
| **N.1.4 límite por destinatario, no por IP** | *Email bombing* a una casilla ajena y agotamiento de la cuota diaria del proveedor. Un límite por IP reintroduciría el problema de CGNAT por el que se eliminó el rate limiting por IP. |
| **Token de 256 bits, solo el hash en base** | Un dump de la tabla no entrega enlaces utilizables. |
| **Un token vigente por cuenta** | Enlaces viejos reenviados o filtrados dejan de servir apenas se pide uno nuevo. |
| **Token fuera de la barra de direcciones** (`replaceState`) y `Referrer-Policy: no-referrer` | Filtración por historial del navegador o cabecera `Referer`. |
| **Mismo error** para token inexistente, vencido o usado | No ayudar a distinguir un token inventado de uno caducado. |
| **No tocar el estado de la cuenta** (desde el 2026-10-19) | Una sola puerta para desbloquear (el panel de administración, auditada) y que ninguna sanción (`SUSPENDIDA`) ni bloqueo se levante pidiendo un reset. |
| **N.1.10 aviso "contraseña cambiada"** | Si alguien tomó la cuenta, el titular legítimo se entera por el único canal que el atacante no controla. |
| **URL base desde configuración**, no del header `Host` | *Host header poisoning*: un enlace de recuperación apuntando a un dominio del atacante. |

> **Limitación conocida (fuera de este DFD):** los JWT ya emitidos siguen siendo válidos hasta su vencimiento aunque cambie la contraseña. La revocación se trata en la etapa de seguridad.
