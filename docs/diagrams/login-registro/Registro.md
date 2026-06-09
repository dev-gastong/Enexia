```mermaid
graph TD
    %% Entidades y Entradas
    User([Organizador / Participante]) -->|Formulario Registro| P1_1[1.1.1: Validar Credenciales de Acceso]
    
    %% Almacenes de Datos
    subgraph Almacenes de Datos
        D2[(Usuario)]
        D1[(Persona_Fisica)]
        D3[(Usuario_Rol)]
        D4[(Persona_Juridica)]
        D5[(Miembros_Organizacion)]
    end

    %% Ida y Vuelta Formal con la Base de Datos
    P1_1 -->|1. Solicitar Consulta Email/Nickname| D2
    D2 -->|2. Retornar Estado de Existencia| P1_1
    
    %% Rombo de Decisión Post-Respuesta
    P1_1 --> C1{¿Credenciales<br>Válidas y Únicas?}
    C1 -->|No| Return1([Mensaje de Error])
    C1 -->|Sí| P1_2[1.1.2: Validar Datos Personales]

    %% Proceso 1.1.2 y su Rombo de Decisión
    P1_2 --> C2{¿Datos Humanos<br>Válidos?}
    C2 -->|No| Return2([Mensaje de Error])
    C2 -->|Sí| P1_3{¿Es Persona<br>Jurídica?}

    %% Bifurcación Persona Jurídica / Física e Identificador Fiscal
    P1_3 -->|Sí| P1_4[1.1.3: Validar Identificador Fiscal CUIT]
    P1_3 -->|No| P1_5[1.1.4: Encriptar Contraseña con BCrypt]

    %% Proceso 1.1.3 y su Rombo de Decisión
    P1_4 --> C3{¿CUIT<br>Válido?}
    C3 -->|No| Return3([Mensaje de Error])
    C3 -->|Sí| P1_5

    %% Proceso 1.1.4: Encriptación y Persistencia
    P1_5 -->|Hash Generado| P1_6[1.1.5: Registrar Identidad Humana]
    P1_6 -->|Escribir Datos Personales| D1
    D1 -->|id_persona_fisica Generado| P1_7[1.1.6: Registrar Cuenta de Usuario]
    
    P1_7 -->|Persistir Cuenta Estado ACTIVO| D2
    P1_7 -->|Asignar Rol| D3

    %% Rombo de Ente Corporativo
    P1_7 -->|id_usuario Generado| P1_8{¿Habilitar Ente<br>Corporativo?}
    P1_8 -->|Sí| P1_9[1.1.7: Registrar Ente Corporativo]
    P1_9 -->|Escribir Datos Empresa| D4
    P1_9 -->|Vincular Usuario como ADMIN| D5
    
    %% Salida del Rombo de Decisión hacia Fin de Proceso
    P1_8 -->|No| Success([Registro Exitoso])
    D5 --> Success
