```mermaid
graph TD
    %% Punto de Inicio Único
    INICIO([INICIO: Solicitud de Mis Eventos]) --> Org([Organizador en su Panel])
    Org -->|Petición con token| P2_5_0[2.5.0: Validar Firma y Vigencia del Token JWT]
    
    %% Almacenes de Datos (Tu Base de Datos Relacional)
    subgraph Almacenes de Datos
        D10_Ev[(Evento)]
    end

    %% 0. CONTROL DE SEGURIDAD (Protección contra Postman)
    P2_5_0 --> C_Token{¿El JWT es Válido,<br>Firmado y Vigente?}
    C_Token -->|No| Err_Auth([Error 401: No Autorizado / Token Inválido])
    C_Token -->|Sí| P2_5_1[2.5.1: Extraer id_usuario del JWT y Consultar]

    %% 1. CONSULTA FILTRADA A LA BASE DE DATOS
    P2_5_1 -->|1. Consulta que trae los eventos del usuario| D10_Ev
    D10_Ev -->|2. Retornar Lista de Eventos del Usuario| P2_5_1
    
    P2_5_1 --> Success_Fetch([Retornar Lista Completa de sus Eventos])

    %% Unificación en un Único Punto de Fin
    Err_Auth --> FIN([FIN])
    Success_Fetch --> FIN
