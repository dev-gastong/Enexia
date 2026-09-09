# DFD 1.2 — Inicio de Sesión

> **Revisado 2026-09-09.** Reemplaza la versión anterior, que describía control de tráfico por IP,
> una escalera de penalización 3/6/9, verificación CAPTCHA y un segundo factor por correo (2FA).
> Ninguno de esos cuatro mecanismos forma parte del alcance vigente:
>
> | Mecanismo anterior | Estado | Motivo |
> |---|---|---|
> | Rate limiting por IP (`1.2.1`) | ❌ eliminado | Detrás de CGNAT bloquea a cientos de usuarios legítimos por un solo atacante (RF-1.4) |
> | Escalera 3 → 6 → 9 fallos | ❌ reemplazada | Decisión final: **bloqueo directo al 3° fallo** (RF-1.4) |
> | CAPTCHA (`1.2.4A`) | ⏸️ diferido | Fuera del alcance actual |
> | 2FA por correo (`1.2.8`/`1.2.9`) | ⏸️ diferido | Fuera del alcance actual |
> | `Err_Block` ("Cuenta Bloqueada por Seguridad") | ❌ eliminado | Revelaba el estado de la cuenta al atacante; ahora el rechazo es **indistinguible** |

## Principio rector: **una sola salida de error**

Todas las rutas de rechazo convergen en `Err_Generico`. Desde fuera son idénticas en
código HTTP (401), código de aplicación (`CREDENCIALES_INVALIDAS`), mensaje, cabeceras y
**tiempo de respuesta**. El motivo real se bifurca únicamente hacia el log y la auditoría.

```mermaid
graph TD
    INICIO([INICIO: Solicitud de Autenticación]) --> User([Usuario en Login])
    User -->|Petición con Email y Password| P1_2_1[1.2.1: Validar Formatos de Entrada]

    %% Almacenes de Datos
    subgraph Almacenes de Seguridad
        D2_User[(Usuario)]
        D3_Roles[(Usuario_Rol)]
        D6_Logs[(Historial_Interacciones)]
        D7_Tokens[(PasswordResetToken)]
    end

    Email_Sys([Servidor de Correo SMTP])

    %% 1. VALIDACION DE FORMATO
    P1_2_1 --> C_Format{¿Formatos de Entrada<br>Válidos?}
    C_Format -->|No| M_EmailFmt[/motivo interno:<br>FORMATO_INVALIDO/]
    C_Format -->|Sí| P1_2_2[1.2.2: Recuperar Usuario y Campos de Control]

    %% 2. LECTURA DE DATOS DE CONTROL
    P1_2_2 -->|1. Consultar Email, Estado e Intentos| D2_User
    D2_User -->|2. Retornar Registro del Usuario| P1_2_2

    P1_2_2 --> C_Existe{¿Existe la Cuenta?}
    C_Existe -->|No| P_Senuelo[1.2.2A: Comparar contra Hash Señuelo<br>coste temporal constante]
    P_Senuelo --> M_NoExiste[/motivo interno:<br>EMAIL_INEXISTENTE/]

    C_Existe -->|Sí| C_Estado{¿estado_usuario<br>es ACTIVO?}
    C_Estado -->|BLOQUEADO| P_Senuelo2[1.2.2B: Comparar contra Hash Señuelo]
    C_Estado -->|SUSPENDIDO / DE_BAJA| P_Senuelo2
    P_Senuelo2 --> M_Estado[/motivo interno:<br>CUENTA_BLOQUEADA /<br>CUENTA_SUSPENDIDA/]

    C_Estado -->|ACTIVO| P1_2_3[1.2.3: Validar Contraseña con BCrypt]

    %% 3. VERIFICACION DE CREDENCIAL
    P1_2_3 --> C_Pass{¿Contraseña<br>Correcta?}

    %% CASO A: FALLA
    C_Pass -->|No| P1_2_4["1.2.4: Incrementar intentos_fallidos<br>(SELECT ... FOR UPDATE)"]
    P1_2_4 -->|Actualizar Contador| D2_User
    P1_2_4 --> C_Tres{¿intentos_fallidos<br>alcanzó 3?}

    C_Tres -->|No| M_PassMal[/motivo interno:<br>PASSWORD_INCORRECTA/]

    C_Tres -->|Sí| P1_2_5[1.2.5: Cambiar estado_usuario a BLOQUEADO]
    P1_2_5 -->|Actualizar Estado de Cuenta| D2_User
    P1_2_5 -->|Emitir token de un solo uso| D7_Tokens
    P1_2_5 -->|Disparar Hilo Asíncrono de Aviso| P1_2_5A["1.2.5A: Enviar Email de Seguridad<br>con enlace /unlock-account?token=XYZ"]
    P1_2_5A -->|Enviar Alerta| Email_Sys
    Email_Sys -.->|Enlace de desbloqueo| Titular([Titular de la Cuenta])
    P1_2_5 --> M_Bloqueo[/motivo interno:<br>CUENTA_BLOQUEADA/]

    %% CONVERGENCIA DE MOTIVOS: al log, NO a la respuesta
    M_EmailFmt --> P_Audit[1.2.6: Registrar Motivo Real en Auditoría]
    M_NoExiste --> P_Audit
    M_Estado --> P_Audit
    M_PassMal --> P_Audit
    M_Bloqueo --> P_Audit
    P_Audit -->|Persistir código específico| D6_Logs
    P_Audit --> Err_Generico(["SALIDA ÚNICA DE ERROR — 401 CREDENCIALES_INVALIDAS<br>'Email o contraseña incorrectos'<br>sin cabeceras extra · tiempo constante"])

    %% CASO B: EXITO
    C_Pass -->|Sí| P1_2_7[1.2.7: Resetear intentos_fallidos = 0]
    P1_2_7 -->|Limpiar Campos de Control| D2_User
    P1_2_7 --> P1_2_8[1.2.8: Generar Token JWT]
    D3_Roles -->|Extraer Roles Asignados| P1_2_8
    P1_2_8 -->|Registrar Acceso Exitoso| D6_Logs
    P1_2_8 --> Success_JWT([Login Exitoso: Retornar JWT firmado])

    %% FIN
    Err_Generico --> FIN([FIN])
    Success_JWT --> FIN
```

## DFD 1.5 — Desbloqueo de Cuenta por Enlace de Correo

> **Especificado, pendiente de implementación.** Cubre el consumo del enlace que RF-1.4 envía al titular.

```mermaid
graph TD
    INICIO([INICIO: Clic en enlace del correo]) --> P1_5_1[1.5.1: Recibir token desde /unlock-account]

    subgraph Almacenes
        D2_User[(Usuario)]
        D7_Tokens[(PasswordResetToken)]
        D6_Logs[(Historial_Interacciones)]
    end

    P1_5_1 -->|1. Consultar token| D7_Tokens
    D7_Tokens -->|2. Retornar token, expiración y uso| P1_5_1

    P1_5_1 --> C_Token{¿Token existe,<br>vigente y sin usar?}
    C_Token -->|No| Err_Token([Error: Enlace inválido o vencido])

    C_Token -->|Sí| P1_5_2["1.5.2: Reactivar Cuenta<br>estado_usuario = ACTIVO<br>intentos_fallidos = 0<br>fecha_desbloqueo_cooldown = NULL"]
    P1_5_2 -->|Actualizar Usuario| D2_User
    P1_5_2 --> P1_5_3[1.5.3: Invalidar Token de un Solo Uso]
    P1_5_3 -->|Marcar token como consumido| D7_Tokens
    P1_5_3 -->|Registrar desbloqueo| D6_Logs
    P1_5_3 --> Ok([Cuenta reactivada: iniciar sesión con las credenciales habituales])

    Err_Token --> FIN([FIN])
    Ok --> FIN
```
