```mermaid
graph TD
    %% Puntos de Control de Extremos (Inicio/Fin Únicos)
    INICIO([INICIO: Solicitud de Registro]) --> User([Organizador / Participante])
    User -->|Formulario Registro: Datos + Credenciales| P1_1[1.1.1: Validar Credenciales de Acceso]
    
    %% Almacenes de Datos
    subgraph Almacenes de Datos
        D2[(Usuario)]
        D1[(Persona_Fisica)]
        D3[(Usuario_Rol)]
        D4[(Persona_Juridica)]
        D5[(Miembros_Organizacion)]
        D6_Logs[(Historial_Interacciones)]
    end

    %% Entidades Externas
    API_Mod([API Externa de Moderación: Filtro de Texto])

    %% 1. VALIDACIÓN DE CREDENCIALES LOCALES
    P1_1 -->|1. Solicitar Consulta Email/Nickname| D2
    D2 -->|2. Retornar Estado de Existencia| P1_1
    
    P1_1 --> C1{¿Credenciales<br>Válidas y Únicas?}
    C1 -->|No| Return1([Mensaje: Email o Nickname ya en uso])
    C1 -->|Sí| P1_1A[1.1.1A: Moderar Texto Sensible de Perfil]

    %% 2. FILTRO DE TEXTO SENSIBLE (Tu sugerencia)
    P1_1A -->|1. Enviar Nombres, Nickname y Descripciones| API_Mod
    API_Mod -->|2. Retornar Resultado del Filtro| P1_1A
    
    P1_1A --> C_Sensible{¿Contiene Lenguaje<br>Inapropiado u Ofensivo?}
    C_Sensible -->|Sí| P1_1_Err[1.1.1_Err: Registrar Intento Ofensivo]
    P1_1_Err -->|Registrar Rechazo en Auditoría| D6_Logs
    P1_1_Err --> Return_Sensible([Mensaje: Registro rechazado por términos de vocabulario])
    
    C_Sensible -->|No| P1_2[1.1.2: Validar Datos Personales]

    %% 3. VALIDACIÓN DE DATOS HUMANOS Y FISCALES
    P1_2 --> C2{¿Datos Humanos<br>Válidos?}
    C2 -->|No| Return2([Mensaje: Datos Personales Inválidos])
    C2 -->|Sí| P1_3{¿Es Persona<br>Jurídica?}

    P1_3 -->|Sí| P1_4[1.1.3: Validar Identificador Fiscal CUIT]
    P1_3 -->|No| P1_5[1.1.4: Encriptar Contraseña con BCrypt]

    P1_4 --> C3{¿CUIT<br>Válido?}
    C3 -->|No| Return3([Mensaje: CUIT Inválido o Inexistente])
    C3 -->|Sí| P1_5

    %% 4. ENCRIPTACIÓN Y PERSISTENCIA DE LA IDENTIDAD
    P1_5 -->|Hash Generado| P1_6[1.1.5: Registrar Identidad Humana]
    P1_6 -->|Escribir Datos Personales| D1
    D1 -->|id_persona_fisica Generado| P1_7[1.1.6: Registrar Cuenta de Usuario]
    
    P1_7 -->|Persistir Cuenta Estado ACTIVO| D2
    P1_7 -->|Asignar Rol| D3

    %% 5. BIFURCACIÓN DE ENTE CORPORATIVO
    P1_7 -->|id_usuario Generado| P1_8{¿Habilitar Ente<br>Corporativo?}
    P1_8 -->|Sí| P1_9[1.1.7: Registrar Ente Corporativo]
    P1_9 -->|Escribir Datos Empresa| D4
    P1_9 -->|Vincular Usuario como ADMIN| D5
    
    P1_8 -->|No| Success([Registro Exitoso: Cuenta Creada])
    D5 --> Success

    %% Embudo de Unificación de Salidas (Punto de Fin Único)
    Return1 --> FIN([FIN])
    Return_Sensible --> FIN
    Return2 --> FIN
    Return3 --> FIN
    Success --> FIN
