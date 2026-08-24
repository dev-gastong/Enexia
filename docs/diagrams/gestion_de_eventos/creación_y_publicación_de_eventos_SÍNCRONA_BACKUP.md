```mermaid
graph TD
    %% Punto de Inicio Único
    INICIO([INICIO: Solicitud de Creación]) --> Org([Organizador vía HTTP / Postman])
    Org -->|Petición con Formulario + Cabecera Authorization Bearer JWT| P2_0[2.0: Validar Firma y Vigencia del Token JWT]
    
    %% Almacenes de Datos
    subgraph Almacenes de Seguridad y Eventos
        D20_Sus[(Suscripcion)]
        D10_Ev[(Evento)]
        D17_Det[(EventoDetalle)]
        D11_Cro[(Evento_Cronograma)]
        D12_Mul[(Evento_Multimedia)]
        D13_Tkt[(Cronograma_Ticket)]
        D14_TTk[(Tipo_Ticket)]
        D6_Logs[(Historial_Interacciones)]
    end

    %% Entidades Externas
    API_Mod([API Externa de Moderación: Cloudinary / IA])

    %% 0. CONTROL DE AUTENTICACIÓN JWT
    P2_0 --> C_Token{¿El JWT es Válido, Firmado y Vigente?}
    C_Token -- No --> Err_Auth([Error 401: No Autorizado / Token Inválido])
    C_Token -- Sí --> P2_1[2.1: Verificar Límites de Suscripción]

    %% 1. CONTROL DE PLAN 
    P2_1 -->|1. Solicitar Plan de Usuario| D20_Sus
    D20_Sus -->|2. Retornar Estado y Tipo de Plan| P2_1
    P2_1 --> C_Plan{¿Supera Límite de Eventos Activos?}
    C_Plan -- Sí --> Err_Plan([Mensaje: Límite Excedido. Actualizá a Pro])
    C_Plan -- No --> P2_2[2.2: Validar Fechas y Horarios]

    %% 2. VALIDACION DE FECHAS Y CAMPOS LOCALES
    P2_2 --> C_FechaInicio{¿Fecha Inicio es Posterior a Hoy?}
    C_FechaInicio -- No --> Err_Fechas([Mensaje: Fechas u Horarios Inválidos])
    C_FechaInicio -- Sí --> C_FechaFin{¿Fecha Fin es Posterior o Igual a Inicio?}
    C_FechaFin -- No --> Err_Fechas
    C_FechaFin -- Sí --> P2_3[2.3: Validar Campos Obligatorios y Formatos]

    P2_3 --> C_Campos{¿Campos Obligatorios Están Completos?}
    C_Campos -- No --> Err_Campos([Mensaje: Verifique los Campos Obligatorios])
    C_Campos -- Sí --> C_Cupo{¿El Cupo Máximo es Mayor a Cero?}
    C_Cupo -- No --> Err_Campos
    C_Cupo -- Sí --> P2_3A[2.3A: Enviar Multimedia y Texto a API de Moderación]

    %% 3. LLAMADA A LA API EXTERNA
    P2_3A -->|1. Enviar Imagen Binaria y Descripciones| API_Mod
    API_Mod -->|2. Retornar Resultado del Análisis| P2_3A
    P2_3A --> C_Sensible{¿La API detectó Contenido Sensible?}
    
    %% CAMINO NEGATIVO: Se persiste como RECHAZADO directamente
    C_Sensible -- Sí --> P2_4_Err[2.4_Err: Registrar Rechazo Automático]
    P2_4_Err -->|1. Marcar Intento como RECHAZADO| D10_Ev
    P2_4_Err -->|2. Registrar Infracción en Auditoría| D6_Logs
    P2_4_Err --> Err_Sensible([Mensaje: Contenido Rechazado por la API de Seguridad])
    
    %% CAMINO POSITIVO: Se guarda como APROBADO de una
    C_Sensible -- No --> P2_4[2.4: Procesar y Persistir Evento Limpio]

    %% 4. PERSISTENCIA EN CASCADA ACTUALIZADA
    P2_4 -->|1. Guardar Registro Base Estado APROBADO| D10_Ev
    P2_4 -->|2. Guardar Descripción Ampliada y Ubicación| D17_Det
    P2_4 -->|3. Guardar Fechas y Horarios del Show| D11_Cro
    P2_4 -->|4. Guardar URLs Finales de Cloudinary| D12_Mul
    P2_4 -->|5. Consultar Catálogo de Tipos de Entradas| D14_TTk
    P2_4 -->|6. Registrar Precios y Cupos Específicos| D13_Tkt
    P2_4 -->|7. Registrar Operación Exitosa| D6_Logs
    
    P2_4 -->|Response 200 OK| Success_Ev([Evento Publicado: Ya está visible en cartelera])

    %% Unificación en un Único Punto de Fin
    Err_Auth --> FIN([FIN])
    Err_Plan --> FIN
    Err_Fechas --> FIN
    Err_Campos --> FIN
    Err_Sensible --> FIN
    Success_Ev --> FIN
