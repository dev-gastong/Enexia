```mermaid
graph TD
    %% Entidades Base Actualizadas
    P[Persona]
    PF[Persona Física]
    PJ[Persona Jurídica]
    U[Usuario]
    R[Rol]
    MO[Miembros Organización]
    
    UR[Usuario Rol]
    C[Categoría]
    E[Evento]
    ED[Evento Detalle]
    I[Inscripción]
    Pun[Puntuación]
    V[Visita]
    PRT[Password Reset Token]

    
    EC[Evento Cronograma]
    EM[Evento Multimedia]
    EES[Evento Estado Sistema]
    EEO[Evento Estado Organizador]
    Ubi[Ubicación]
    Ciu[Ciudad]
    Pro[Provincia]
    Pa[País]

    %% Entidades de Pagos e Historial
    S[Suscripción]
    Pag[Pago]
    H[Historial de Interacciones]

    %% Rombos de Relación Base y Nuevos
    R1{es}
    
    R3{tiene}
    R4{organiza}
    R5{realiza}
    R8{solicita}
    R9{posee}
    R_UR_R{asignado}
    R10{clasifica}
    R11{detalla}
    R12{recibe}

    %% Rombos para la Intermedia Corporativa
    R_U_MO{pertenece a}
    R_PJ_MO{tiene miembro}
    
    %% Rombos para Estructura de Eventos y Ubicaciones
    R_Ev_Cro{agenda}
    R_Ev_Mul{contiene}
    R_Ev_EES{modera}
    R_Ev_EEO{gestiona}
    R_ED_Ubi{ubica}
    R_PJ_Ubi{registra}
    R_Ubi_Ciu{contiene}
    R_Ciu_Pro{contiene}
    R_Pro_Pa{contiene}

    %% Rombos de Puntuación, Visita y Auditoría
    R_Ev_Pun{recibe}
    R_Us_Pun{da}
    R_Ev_Vis{registra}
    R_Us_Vis{hace}
    R_U_Hist{rastrea}

    %% Rombos de Pagos y Suscripciones 
    R_Us_Sus{adquiere}
    R_Ins_Pag{genera}
    R_Sus_Pag{genera}

    %% === CONEXIONES ===

    %% Jerarquía de Identidad y Seguridad
    P ---|"1"| R1 -->|"1"| PF
    PF ---|"1"| R3 -->|"1"| U
    U ---|"1"| R9 -->|"N"| UR
    R ---|"1"| R_UR_R -->|"N"| UR

    %% Nueva Estructura: Miembros de Organización Intermedia
    U ---|"1"| R_U_MO -->|"N"| MO
    PJ ---|"1"| R_PJ_MO -->|"N"| MO

    %% Gestión y Clasificación de Eventos
    U ---|"1"| R4 -->|"N"| E
    C ---|"1"| R10 -->|"N"| E
    EES ---|"1"| R_Ev_EES -->|"N"| E
    EEO ---|"1"| R_Ev_EEO -->|"N"| E

    %% Componentes del Evento
    E ---|"1"| R_Ev_Cro -->|"N"| EC
    E ---|"1"| R_Ev_Mul -->|"N"| EM
    E ---|"1"| R11 -->|"1"| ED

    %% Normalización Geográfica de la Ubicación
    Ubi ---|"1"| R_ED_Ubi -->|"1"| ED
    Ciu ---|"1"| R_Ubi_Ciu -->|"N"| Ubi
    Pro ---|"1"| R_Ciu_Pro -->|"N"| Ciu
    Pa ---|"1"| R_Pro_Pa -->|"N"| Pro

    PJ ---|"N"| R_PJ_Ubi ---> |"1"| Ubi

    %% Flujo de Inscripciones e Interacciones Operativas 
    U ---|"1"| R5 -->|"N"| I
    E ---|"1"| R12 -->|"N"| I
    
    U ---|"1"| R_Us_Pun -->|"N"| Pun
    E ---|"1"| R_Ev_Pun -->|"N"| Pun
    
    U ---|"1"| R_Us_Vis -->|"N"| V
    E ---|"1"| R_Ev_Vis -->|"N"| V
    
    U ---|"1"| R8 -->|"N"| PRT

    %% Flujo de Pagos y Suscripciones 
    I ---|"1"| R_Ins_Pag -->|"1..N"| Pag
    U ---|"1"| R_Us_Sus -->|"1"| S
    S ---|"1"| R_Sus_Pag -->|"1..N"| Pag

    %% Auditoría de Actividad
    U ---|"1"| R_U_Hist -->|"N"| H
