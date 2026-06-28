
```mermaid
graph TD
    %% === ENTIDADES BASE ACTUALIZADAS ===
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

    %% Módulo de Tickets Local
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

    %% Entidades de Pagos e Historial General
    S[Suscripción]
    Pag[Pago]
    H[Historial de Interacciones]

    %% Entidades de Estados Específicos e Historiales de Transición
    UES[Usuario Estado Sistema]
    UE[Usuario Estado]
    HEU[Historial Estado Usuario]
    HEE[Historial Estado Evento]
    IE[Inscripción Estado]
    HEI[Historial Estado Inscripción]
    PE[Pago Estado]
    HEP[Historial Estado Pago]
    SE[Suscripción Estado]
    HES[Historial Estado Suscripción]

    %% === ROMBOS DE RELACIÓN ===
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

    %% Valoraciones, Visitas y Auditoría General
    R_EC_Pun{recibe}
    R_Us_Pun{da}
    R_Ev_Vis{registra}
    R_Us_Vis{hace}
    R_U_Hist{rastrea}

    %% Pagos Opcionales y Suscripciones
    R_Ins_Pag{genera}
    R_Sus_Pag{genera}
    R_Us_Sus{adquiere}

    %% Rombos de Relaciones de Estados e Historiales de Transición
    R_UES_U{modera actual}
    R_UE_U{gestiona actual}
    R_U_HEU{registra H}
    R_UES_HEU{asienta sys}
    R_UE_HEU{asienta usr}
    
    R_E_HEE{registra H}
    R_EEO_HEE{asienta org}
    R_EES_HEE{asienta sys}
    R_U_HEE{realiza cambio}

    R_IE_I{asigna actual}
    R_I_HEI{registra H}
    R_IE_HEI{asienta est}

    R_PE_P{monitorea actual}
    R_P_HEP{registra H}
    R_PE_HEP{asienta est}

    R_SE_S{controla actual}
    R_S_HES{registra H}
    R_SE_HES{asienta est}

    %% === CONEXIONES ===

    %% Jerarquía de Identidad y Seguridad
    P ---|"1"| R1 -->|"1"| PF
    PF ---|"1"| R3 -->|"1"| U
    U ---|"1"| R9 -->|"N"| UR
    R ---|"1"| R_UR_R -->|"N"| UR

    %% Relaciones de Estado Actual e Historial de Usuario
    UES ---|"1"| R_UES_U --->|"N"| U
    UE ---|"1"| R_UE_U --->|"N"| U
    U ---|"1"| R_U_HEU -->|"N"| HEU
    UES ---|"1"| R_UES_HEU -->|"N"| HEU
    UE ---|"1"| R_UE_HEU -->|"N"| HEU

    %% Miembros de Organización
    U ---|"1"| R_U_MO -->|"N"| MO
    PJ ---|"1"| R_PJ_MO -->|"N"| MO

    %% Gestión y Clasificación de Eventos
    U ---|"1"| R4 -->|"N"| E
    PJ ---|"0..1"| R_PJ_Ev -->|"N"| E
    C ---|"1"| R10 -->|"N"| E
    EES ---|"1"| R_Ev_EES -->|"N"| E
    EEO ---|"1"| R_Ev_EEO -->|"N"| E

    %% Historial de Estados del Evento
    E ---|"1"| R_E_HEE -->|"N"| HEE
    EEO ---|"1"| R_EEO_HEE -->|"N"| HEE
    EES ---|"1"| R_EES_HEE -->|"N"| HEE
    U ---|"1"| R_U_HEE -->|"N"| HEE

    %% Componentes del Evento
    E ---|"1"| R_Ev_Cro -->|"N"| EC
    E ---|"1"| R_Ev_Mul -->|"N"| EM
    E ---|"1"| R11 -->|"1"| ED

    %% Relación de Tickets vinculados al Cronograma
    EC ---|"1"| R_Ev_CT -->|"N"| CT
    TT ---|"1"| R_TT_CT -->|"N"| CT

    %% Normalización Geográfica
    Ubi ---|"1"| R_ED_Ubi -->|"1"| ED
    Ciu ---|"1"| R_Ubi_Ciu -->|"N"| Ubi
    Pro ---|"1"| R_Ciu_Pro -->|"N"| Ciu
    Pa ---|"1"| R_Pro_Pa -->|"N"| Pro
    PJ ---|"N"| R_PJ_Ubi ---> |"1"| Ubi

    %% Inscripciones, Estados e Historial
    U ---|"1"| R5 -->|"N"| I
    CT ---|"1"| R_CT_Ins -->|"N"| I
    IE ---|"1"| R_IE_I --->|"N"| I
    I ---|"1"| R_I_HEI -->|"N"| HEI
    IE ---|"1"| R_IE_HEI -->|"N"| HEI
    
    %% Visitas y Tokens
    U ---|"1"| R_Us_Vis -->|"N"| V
    E ---|"1"| R_Ev_Vis -->|"N"| V
    U ---|"1"| R8 -->|"N"| PRT

    %% Valoraciones
    U ---|"1"| R_Us_Pun -->|"N"| Pun
    EC ---|"1"| R_EC_Pun -->|"N"| Pun

    %% Pagos, Estados e Historial
    I ---|"1"| R_Ins_Pag -->|"0..1"| Pag
    S ---|"1"| R_Sus_Pag -->|"0..1"| Pag
    PE ---|"1"| R_PE_P --->|"N"| Pag
    Pag ---|"1"| R_P_HEP -->|"N"| HEP
    PE ---|"1"| R_PE_HEP -->|"N"| HEP

    %% Suscripciones, Estados e Historial
    U ---|"1"| R_Us_Sus -->|"N"| S
    SE ---|"1"| R_SE_S --->|"N"| S
    S ---|"1"| R_S_HES -->|"N"| HES
    SE ---|"1"| R_SE_HES -->|"N"| HES

    %% Auditoría General Técnica
    U ---|"1"| R_U_Hist -->|"N"| H
