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
    Pun[Valoración]
    V[Visita]
    PRT[Password Reset Token]

    %% Módulo de Tickets Local (Actualizado a Cronograma Ticket)
    TT[Tipo Ticket]
    CT[Cronograma Ticket]
    
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
    R4{crea / organiza}
    R_PJ_Ev{respalda} 
    R5{realiza}
    R8{solicita}
    R9{posee}
    R_UR_R{asignado}
    R10{clasifica}
    R11{detalla}

    %% Tickets Nativos
    R_Ev_CT{ofrece}
    R_TT_CT{se parametriza como}
    R_CT_Ins{asigna cupo en}

    %% Corporativos
    R_U_MO{pertenece a}
    R_PJ_MO{tiene miembro}
    
    %% Eventos y Ubicaciones
    R_Ev_Cro{agenda}
    R_Ev_Mul{contiene}
    R_Ev_EES{modera}
    R_Ev_EEO{gestiona}
    R_ED_Ubi{ubica}
    R_PJ_Ubi{registra}
    R_Ubi_Ciu{contiene}
    R_Ciu_Pro{contiene}
    R_Pro_Pa{contiene}

    %% Ajustado: Valoraciones
    R_EC_Pun{recibe}
    R_Us_Pun{da}
    
    %% Visitas y Auditoría
    R_Ev_Vis{registra}
    R_Us_Vis{hace}
    R_U_Hist{rastrea}

    %% Ajustado: Pagos Opcionales (0..1)
    R_Ins_Pag{genera}
    R_Sus_Pag{genera}
    R_Us_Sus{adquiere}

    %% === CONEXIONES ===

    %% Jerarquía de Identidad y Seguridad
    P ---|"1"| R1 -->|"1"| PF
    PF ---|"1"| R3 -->|"1"| U
    U ---|"1"| R9 -->|"N"| UR
    R ---|"1"| R_UR_R -->|"N"| UR

    %% Miembros de Organización (Independiente y opcional para el Usuario)
    U ---|"1"| R_U_MO -->|"N"| MO
    PJ ---|"1"| R_PJ_MO -->|"N"| MO

    %% Gestión y Clasificación de Eventos (Soporte Humano + Empresa Opcional)
    U ---|"1"| R4 -->|"N"| E
    PJ ---|"0..1"| R_PJ_Ev -->|"N"| E
    C ---|"1"| R10 -->|"N"| E
    EES ---|"1"| R_Ev_EES -->|"N"| E
    EEO ---|"1"| R_Ev_EEO -->|"N"| E

    %% Componentes del Evento
    E ---|"1"| R_Ev_Cro -->|"N"| EC
    E ---|"1"| R_Ev_Mul -->|"N"| EM
    E ---|"1"| R11 -->|"1"| ED

    %% Relación de Tickets vinculados al Cronograma (Arreglado de ET a CT)
    EC ---|"1"| R_Ev_CT -->|"N"| CT
    TT ---|"1"| R_TT_CT -->|"N"| CT

    %% Normalización Geográfica
    Ubi ---|"1"| R_ED_Ubi -->|"1"| ED
    Ciu ---|"1"| R_Ubi_Ciu -->|"N"| Ubi
    Pro ---|"1"| R_Ciu_Pro -->|"N"| Ciu
    Pa ---|"1"| R_Pro_Pa -->|"N"| Pro
    PJ ---|"N"| R_PJ_Ubi ---> |"1"| Ubi

    %% Inscripciones, Visitas y Tokens
    U ---|"1"| R5 -->|"N"| I
    CT ---|"1"| R_CT_Ins -->|"N"| I
    
    U ---|"1"| R_Us_Vis -->|"N"| V
    E ---|"1"| R_Ev_Vis -->|"N"| V
    U ---|"1"| R8 -->|"N"| PRT

    %% CORREGIDO: Un Usuario da "N" valoraciones en total a la app, pero "1" por Cronograma
    U ---|"1"| R_Us_Pun -->|"N"| Pun
    EC ---|"1"| R_EC_Pun -->|"N"| Pun

    %% CORREGIDO: Pagos diferidos (0..1) para evitar bloqueos transaccionales
    I ---|"1"| R_Ins_Pag -->|"0..1"| Pag
    U ---|"1"| R_Us_Sus -->|"N"| S
    S ---|"1"| R_Sus_Pag -->|"0..1"| Pag

    %% Auditoría
    U ---|"1"| R_U_Hist -->|"N"| H
