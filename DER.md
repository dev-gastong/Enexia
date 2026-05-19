```mermaid
graph TD
    %% Estilos para mejorar la visualización en GitHub
    classDef entidad fill:#e1f5fe,stroke:#03a9f4,stroke-width:2px;
    classDef relacion fill:#fff,stroke:#757575,stroke-width:1px,stroke-dasharray: 3 3;

    %% Entidades Base
    P[Persona]:::entidad
    PF[Persona Física]:::entidad
    PJ[Persona Jurídica]:::entidad
    U[Usuario]:::entidad
    R[Rol]:::entidad
    Perm[Permiso]:::entidad
    C[Categoría]:::entidad
    E[Evento]:::entidad
    ED[Evento Detalle]:::entidad
    I[Inscripción]:::entidad
    Pun[Puntuación]:::entidad
    V[Visita]:::entidad
    PRT[Password Reset Token]:::entidad

    %% NUEVA ENTIDAD: Imágenes del Evento
    UIE[Urls_Img_Evento]:::entidad
    
    %% Entidades de Pagos e Historial
    S[Suscripción]:::entidad
    Pag[Pago]:::entidad
    H[Historial de Interacciones]:::entidad

    %% Relaciones Base
    R1{es}:::relacion
    R2{es}:::relacion
    R3{tiene}:::relacion
    R4{organiza}:::relacion
    R5{se inscribe}:::relacion
    R8{solicita}:::relacion
    R9{posee}:::relacion
    R10{clasifica}:::relacion
    R11{tiene}:::relacion
    R12{registra}:::relacion
    R13{contiene}:::relacion

    %% NUEVA RELACIÓN: Imágenes del Evento (1:N)
    R_Ev_Img{tiene}:::relacion

    %% Conexión de las Imágenes del Evento
    E ---|"1"| R_Ev_Img
    R_Ev_Img ---|"N"| UIE
    
    %% Relaciones de Puntuación y Visita
    R_Ev_Pun{tiene}:::relacion
    R_Us_Pun{realiza}:::relacion
    R_Ev_Vis{tiene}:::relacion
    R_Us_Vis{realiza}:::relacion

    %% NUEVAS RELACIONES (Pagos y Suscripciones)
    R_Us_Sus{adquiere}:::relacion
    R_Ins_Pag{genera}:::relacion
    R_Sus_Pag{genera}:::relacion
    R_Us_Hist{registra}:::relacion

    %% Conexiones Herencia y Usuario
    P ---|"1"| R1
    R1 ---|"1"| PF
    P ---|"1"| R2
    R2 ---|"1"| PJ
    P ---|"1"| R3
    R3 ---|"1"| U

    %% Flujo de Eventos y Organizador
    U ---|"1"| R4
    R4 ---|"N"| E

    %% Flujo 1 de Pago: Inscripción
    U ---|"1"| R5
    R5 ---|"N"| I
    E ---|"1"| R12
    R12 ---|"N"| I
    I ---|"1"| R_Ins_Pag
    R_Ins_Pag ---|"1..N"| Pag

    %% Flujo 2 de Pago: Suscripción
    U ---|"1"| R_Us_Sus
    R_Us_Sus ---|"1"| S
    S ---|"1"| R_Sus_Pag
    R_Sus_Pag ---|"1..N"| Pag

    %% Historial de Interacciones
    U ---|"1"| R_Us_Hist
    R_Us_Hist ---|"N"| H

    %% Puntuaciones, Visitas y Tokens
    E ---|"1"| R_Ev_Pun
    R_Ev_Pun ---|"N"| Pun
    U ---|"1"| R_Us_Pun
    R_Us_Pun ---|"N"| Pun

    E ---|"1"| R_Ev_Vis
    R_Ev_Vis ---|"N"| V
    U ---|"1"| R_Us_Vis
    R_Us_Vis ---|"N"| V

    U ---|"1"| R8
    R8 ---|"N"| PRT

    %% Roles y Permisos
    U ---|"1"| R9
    R9 ---|"M"| R
    R ---|"N"| R13
    R13 ---|"M"| Perm

    %% Categorías y Detalles
    C ---|"1"| R10 
    R10 ---|"N"| E
    E ---|"1"| R11
    R11 ---|"1"| ED
