```mermaid
graph TD
    %% Entidades y Entradas
    User([Usuario en Login]) -->|Petición con Email, Password, IP| P1_2_1[1.2.1: Control de Rate Limiting General por IP]
    
    %% Almacenes de Datos
    subgraph Almacenes de Seguridad
        D2_User[(Usuario)]
        D3_Roles[(Usuario_Rol)]
        D6_Logs[(Historial_Interacciones)]
        D7_Tokens[(PasswordResetToken)]
    end

    %% Entidades Externas de Salida
    Email_Sys([Servidor de Correo SMTP])

    %% 1. CONTROL DE SEGURIDAD POR TRAFICO GENERAL (IP)
    P1_2_1 -->|1. Solicitar Historial de la IP| D6_Logs
    D6_Logs -->|2. Retornar Peticiones Recientes| P1_2_1
    P1_2_1 --> C_Rate{¿IP en Lista Negra<br>por Fuerza Bruta?}
    C_Rate -->|Sí| Err_429([Error 429: IP Bloqueada Temporalmente])
    C_Rate -->|No| P1_2_2[1.2.2: Validar Sesión Activa Existente]

    %% 2. CONTROL DE SESION ACTIVA
    P1_2_2 --> C_Sesion{¿Ya existe JWT<br>Activo en Cliente?}
    C_Sesion -->|Sí| Err_Sesion([Error: Sesión ya Iniciada en Dispositivo])
    C_Sesion -->|No| P1_2_3[1.2.3: Validar Formatos de Texto]

    %% 3. VALIDACION DE TEXTO BASE
    P1_2_3 --> C_Format{¿Formatos de Entrada<br>Válidos?}
    C_Format -->|No| Err_Gen1([Mensaje Genérico: Email o Contraseña Incorrectos])
    C_Format -->|Sí| P1_2_4[1.2.4: Verificar Cuenta y Recuperar Campos de Control]

    %% 4. LECTURA DE DATOS DE CONTROL
    P1_2_4 -->|1. Consultar Email, Intentos, Captcha y Cooldown| D2_User
    D2_User -->|2. Retornar Registro del Usuario| P1_2_4
    
    P1_2_4 --> C_Estado{¿Usuario Existe y<br>Estado es ACTIVO?}
    C_Estado -->|No / Cuenta Bloqueada| Err_Gen2([Mensaje Genérico: Email o Contraseña Incorrectos])
    C_Estado -->|Sí| C_Cooldown{¿Superó Fecha de<br>desbloqueo_cooldown?}
    
    C_Cooldown -->|No| Err_Cool([Error: Cuenta penalizada temporalmente. Aguarde])
    C_Cooldown -->|Sí| C_ReqCaptcha{¿El campo<br>requiere_captcha es True?}

    %% 5. LOGICA DEL CAPTCHA BASADO EN EL CAMPO NUEVO
    C_ReqCaptcha -->|Sí| P1_2_4A[1.2.4A: Validar Token Captcha con API Externa]
    P1_2_4A --> C_VerifCap{¿Captcha Resuelto<br>Correctamente?}
    C_VerifCap -->|No| Err_Cap([Error: Verificación Captcha Fallida])
    C_VerifCap -->|Sí| P1_2_5[1.2.5: Validar Contraseña con BCrypt]
    C_ReqCaptcha -->|No| P1_2_5

    %% 6. COMPROBACIÓN DE CREDENCIALES Y ACTUALIZACIÓN EN CASCADA
    P1_2_5 --> C_Pass{¿Contraseña<br>Correcta?}
    
    %% CASO A: Falla la contraseña
    C_Pass -->|No| P1_2_6[1.2.6: Evaluar y Procesar Intento Fallido]
    P1_2_6 -->|Registrar Log de Auditoría| D6_Logs
    
    %% Rombos Atómicos de Penalización Secuencial (Corregido)
    P1_2_6 --> C_TresFallos{¿Suma Exactamente<br>3 Intentos?}
    C_TresFallos -->|Sí| P1_2_6A[1.2.6A: Modificar intentos_fallidos, requiere_captcha=True y fecha_desbloqueo_cooldown +5m]
    C_TresFallos -->|No| C_SeisFallos{¿Suma Exactamente<br>6 Intentos?}
    
    C_SeisFallos -->|Sí| P1_2_6B[1.2.6B: Modificar intentos_fallidos y fecha_desbloqueo_cooldown +30m]
    C_SeisFallos -->|No| P1_2_7[1.2.7: Cambiar estado a BLOQUEADO]
    
    %% Escrituras finales a la BD
    P1_2_6A -->|Actualizar Campos de Control| D2_User
    P1_2_6B -->|Actualizar Campos de Control| D2_User
    P1_2_7 -->|Actualizar Estado de Cuenta| D2_User
    
    P1_2_6A --> Err_Gen1
    P1_2_6B --> Err_Gen1
    P1_2_7 -->|Disparar Hilo de Alerta| P1_2_7A[1.2.7A: Enviar Email de Seguridad]
    P1_2_7A -->|Enviar Alerta| Email_Sys
    P1_2_7 --> Err_Block([Mensaje: Cuenta Bloqueada por Seguridad])

    %% CASO B: Contraseña Correcta (Reseteo de campos)
    C_Pass -->|Sí| P1_2_5A[1.2.5A: Resetear intentos_fallidos=0 y requiere_captcha=False]
    P1_2_5A -->|Limpiar Campos de Control| D2_User
    P1_2_5A --> P1_2_8[1.2.8: Generar e Iniciar Flujo 2FA]

    %% 7. VERIFICACION DE DOS PASOS (2FA)
    P1_2_8 -->|Registrar Token Temporal| D7_Tokens
    P1_2_8 -->|Enviar Código de Acceso| Email_Sys
    Email_Sys -.->|Código por Mail| User
    User -->|Ingresa Código Recibido| P1_2_9[1.2.9: Validar Código 2FA]
    
    P1_2_9 -->|1. Consultar Token Temporal| D7_Tokens
    D7_Tokens -->|2. Retornar Token y Expiración| P1_2_9
    P1_2_9 --> C_2FA{¿Código Válido<br>y No Vencido?}
    C_2FA -->|No| Err_2FA([Error: Código Inválido o Expirado])
    
    %% 8. LOGIN EXITOSO Y JWT
    C_2FA -->|Sí| P1_2_10[1.2.10: Generar Token JWT]
    D3_Roles -->|Extraer Roles Asignados| P1_2_10
    P1_2_10 -->|Registrar Acceso Exitoso en Logs| D6_Logs
    P1_2_10 --> Success_JWT([Login Exitoso: Retornar JWT firmado])
