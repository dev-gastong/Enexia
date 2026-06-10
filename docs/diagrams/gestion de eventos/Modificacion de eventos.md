```mermaid
graph TD
    %% Puntos de Control de Extremos
    INICIO([INICIO: Solicitud de Modificación]) --> Org([Organizador vía HTTP / Postman])
    Org -->|Petición PUT con id_evento + JWT + Datos Nuevos| P2_2_0[2.2.0: Validar Firma y Vigencia del Token JWT]
    
    %% Almacenes de Datos
    subgraph Almacenes de Datos
        D10_Ev[(Evento)]
        D17_Det[(EventoDetalle)]
        D11_Cro[(Evento_Cronograma)]
        D12_Mul[(Evento_Multimedia)]
        D13_Tkt[(Evento_Ticket)]
        D14_TTk[(Tipo_Ticket)]
        D6_Logs[(Historial_Interacciones)]
    end

    %% Entidades Externas
    API_Mod([API Externa de Moderación: Cloudinary / IA])

    %% 0. CONTROL DE AUTENTICACIÓN
    P2_2_0 --> C_Token{¿El JWT es Válido, Firmado y Vigente?}
    C_Token -- No --> Err_Auth([Error 401: No Autorizado / Token Inválido])
    C_Token -- Sí --> P2_2_1[2.2.1: Verificar Propiedad del Evento]

    %% 1. CONTROL DE PERTENENCIA
    P2_2_1 -->|1. Consultar id_organizador del Evento| D10_Ev
    D10_Ev -->|2. Retornar Dueño del Evento| P2_2_1
    P2_2_1 --> C_Dueno{¿El id_usuario del JWT coincide con el id_organizador?}
    C_Dueno -- No --> Err_Permiso([Error 403: Prohibido. No tenés permisos])
    C_Dueno -- Sí --> P2_2_2[2.2.2: Validar Formatos, Fechas y Cupos]

    %% 2. VALIDACIONES LOCALES
    P2_2_2 --> C_FechaInicio{¿Nueva Fecha Inicio es Posterior a Hoy?}
    C_FechaInicio -- No --> Err_Fechas([Mensaje: Fechas u Horarios Inválidos])
    C_FechaInicio -- Sí --> C_FechaFin{¿Nueva Fecha Fin es Posterior o Igual a Inicio?}
    C_FechaFin -- No --> Err_Fechas
    C_FechaFin -- Sí --> C_Campos{¿Campos Obligatorios Están Completos?}
    C_Campos -- No --> Err_Campos([Mensaje: Verifique los Campos Obligatorios])
    C_Campos -- Sí --> C_Cupo{¿El Nuevo Cupo es Mayor a Cero?}
    C_Cupo -- No --> Err_Campos
    C_Cupo -- Sí --> P2_2_3[2.2.3: Enviar Cambios a API de Moderación]

    %% 3. RE-MODERACIÓN DE CONTENIDO
    P2_2_3 -->|1. Enviar Nuevos Textos e Imágenes| API_Mod
    API_Mod -->|2. Retornar Resultado del Análisis| P2_2_3
    P2_2_3 --> C_Sensible{¿La API detectó Contenido Sensible?}
    
    %% CAMINO RECHAZADO: Cambia el estado a RECHAZADO y quita el evento de cartelera
    C_Sensible -- Sí --> P2_2_Err[2.2_Err: Registrar Infracción en Modificación]
    P2_2_Err -->|1. Forzar Estado RECHAZADO en BD| D10_Ev
    P2_2_Err -->|2. Registrar Intento Malicioso| D6_Logs
    P2_2_Err --> Err_Sensible([Mensaje: Cambios Rechazados por la API de Seguridad])
    
    %% CAMINO LIMPIO: Guarda y mantiene estado APROBADO
    C_Sensible -- No --> P2_2_4[2.2.4: Actualizar Registro de Evento]

    %% 4. PERSISTENCIA DE ACTUALIZACIÓN ACTUALIZADA
    P2_2_4 -->|1. Actualizar Manteniendo Estado APROBADO| D10_Ev
    P2_2_4 -->|2. Actualizar Detalles en BD| D17_Det
    P2_2_4 -->|3. Actualizar Fechas en Cronograma| D11_Cro
    P2_2_4 -->|4. Actualizar URLs en Multimedia| D12_Mul
    P2_2_4 -->|5. Validar Tipos de Tickets Modificados| D14_TTk
    P2_2_4 -->|6. Actualizar Tarifas y Cupos Máximos| D13_Tkt
    P2_2_4 -->|7. Registrar Log de Modificación| D6_Logs
    
    P2_2_4 -->|Response 200 OK| Success_Mod([Modificación Publicada Exitosamente])

    %% Unificación de Salidas
    Err_Auth --> FIN([FIN])
    Err_Permiso --> FIN
    Err_Fechas --> FIN
    Err_Campos --> FIN
    Err_Sensible --> FIN
    Success_Mod --> FIN
