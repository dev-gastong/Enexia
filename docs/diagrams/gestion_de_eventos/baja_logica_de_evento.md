```mermaid
graph TD
    %% Puntos de Control de Extremos
    INICIO([INICIO: Solicitud de Baja]) --> Org([Organizador vía HTTP / Postman])
    Org -->|Petición DELETE con id_evento + JWT| P2_3_0[2.3.0: Validar Firma y Vigencia del Token JWT]
    
    %% Almacenes de Datos
    subgraph Almacenes de Datos Relacionales
        D10_Ev[(Evento)]
        D6_Logs[(Historial_Interacciones)]
    end

    %% 0. CONTROL DE AUTENTICACIÓN
    P2_3_0 --> C_Token{¿El JWT es Válido,<br>Firmado y Vigente?}
    C_Token -->|No| Err_Auth([Error 401: No Autorizado / Token Inválido])
    C_Token -->|Sí| P2_3_1[2.3.1: Verificar Propiedad antes de Cancelar]

    %% 1. CONTROL DE PERTENENCIA
    P2_3_1 -->|1. Consultar id_organizador| D10_Ev
    D10_Ev -->|2. Retornar Dueño del Evento| P2_3_1
    
    P2_3_1 --> C_Dueno{¿El id_usuario del JWT<br>coincide con el id_organizador?}
    C_Dueno -->|No| Err_Permiso([Error 403: Prohibido. No podés dar de baja este evento])
    C_Dueno -->|Sí| P2_3_2[2.3.2: Procesar Baja Lógica]

    %% 2. ACTUALIZACIÓN DE ESTADO (Baja Lógica)
    P2_3_2 -->|1. Cambiar estado_evento a CANCELADO o BAJA| D10_Ev
    P2_3_2 -->|2. Registrar Cancelación de Evento| D6_Logs
    
    P2_3_2 -->|Response 200 OK| Success_Baja([Evento Cancelado Correctamente])

    %% Unificación de Salidas (Punto de Fin Único)
    Err_Auth --> FIN([FIN])
    Err_Permiso --> FIN
    Success_Baja --> FIN
