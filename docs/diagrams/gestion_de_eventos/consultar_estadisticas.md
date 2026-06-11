```mermaid
graph TD
    %% Puntos de Control de Extremos (Inicio/Fin Únicos)
    INICIO([INICIO: Solicitud de Métricas]) --> Org([Organizador en su Panel])
    Org -->|Petición GET /api/estadisticas con JWT + TipoConsulta| P2_6_0[2.6.0: Validar Firma y Vigencia del Token JWT]
    
    %% Almacenes de Datos (Tu Base de Datos Relacional)
    subgraph Almacenes de Datos y Seguridad
        D20_Sus[(Suscripcion)]
        D10_Ev[(Evento)]
        D6_Logs[(Historial_Interacciones)]
    end

    %% 0. CONTROL DE AUTENTICACIÓN Y PRIVACIDAD
    P2_6_0 --> C_Token{¿El JWT es Válido,<br>Firmado y Vigente?}
    C_Token -->|No| Err_Auth([Error 401: Token Inválido o Expirado])
    C_Token -->|Sí| P2_6_1[2.6.1: Verificar Propiedad del Evento / Acceso]

    P2_6_1 -->|1. Consultar id_organizador| D10_Ev
    D10_Ev -->|2. Retornar Dueño| P2_6_1
    
    P2_6_1 --> C_Dueno{¿El id_usuario del JWT<br>es el Dueño o ADMIN?}
    C_Dueno -->|No| Err_Permiso([Error 403: Prohibido. No podés ver estas métricas])
    C_Dueno -->|Sí| P2_6_2[2.6.2: Evaluar Tipo de Consulta]

    %% 1. BIFURCACIÓN DE PANTALLA (Generales/Detalle vs Sección Pro)
    P2_6_2 --> C_TipoVista{¿Solicita sección de<br>Analítica Avanzada / Pro?}
    
    %% CAMINO BÁSICO (Dashboard General o Detalle de un Evento)
    C_TipoVista -->|No: Dashboard/Detalle| P2_6_3[2.6.3: Calcular Métricas Básicas]
    P2_6_3 -->|1. SELECT COUNT/SUM Visitas y Cupos| D6_Logs
    P2_6_3 -->|2. SELECT AVG Estrellas Reseñas| D10_Ev
    P2_6_3 --> Success_Basic([Response 200 OK: Retornar Números Netos de Visitas, Cupos y Estrellas])

    %% CAMINO AVANZADO (Sección de Gráficos y Tasas)
    C_TipoVista -->|Sí: Sección Pro| P2_6_4[2.6.4: Verificar Nivel de Plan del Usuario]
    P2_6_4 -->|1. Consultar Nivel de Plan| D20_Sus
    D20_Sus -->|2. Retornar Tipo de Plan| P2_6_4
    
    P2_6_4 --> C_Plan{¿El Plan del Organizador<br>es PLAN PRO?}
    
    %% REBOTE PLAN GRATIS (Candado en la UI)
    C_Plan -->|No: Plan Gratis| Err_Pro([Error 403: Acción Bloqueada. Requiere Plan Pro])
    
    %% ACCESO CONFIGURADO PARA PLAN PRO
    C_Plan -->|Sí: Plan Pro| P2_6_5[2.6.5: Procesar Agregaciones Complejas]
    P2_6_5 -->|1. Consultar Serie Temporal de Tráfico| D6_Logs
    P2_6_5 -->|2. Calcular Tasa de Conversión y Velocidad| D10_Ev
    P2_6_5 --> Success_Pro([Response 200 OK: Retornar Arreglos de Gráficos y Métricas Avanzadas])

    %% Embudo de Unificación de Salidas (Punto de Fin Único)
    Err_Auth --> FIN([FIN])
    Err_Permiso --> FIN
    Err_Pro --> FIN
    Success_Basic --> FIN
    Success_Pro --> FIN
