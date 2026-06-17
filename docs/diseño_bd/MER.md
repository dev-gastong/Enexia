```mermaid
erDiagram
    Persona {
        int id_persona PK
        varchar tipo_persona
        timestamp fecha_registro
    }
    Rol {
        int id_rol PK
        varchar nombre_rol
    }
    Usuario_Rol {
        int id_usuario FK
        int id_rol FK
    }
    Persona_Fisica {
        int id_persona PK, FK
        varchar dni
        varchar nombre
        varchar apellido
        date fecha_nacimiento
    }
    Usuario {
        int id_usuario PK
        int id_persona_fisica FK
        varchar email
        varchar password
        varchar nickname
        varchar estado
        datetime fecha_baja
    }
    Persona_Juridica {
        int id_persona_juridica PK
        varchar razon_social
        varchar cuit
        int id_ubicacion FK
        varchar emailCorporativo
        varchar telefonoContacto
    }
    Miembros_Organizacion {
        int id_usuario PK, FK
        int id_persona_juridica PK, FK
        varchar rol_en_empresa
    }
    Categoria {
        int id_categoria PK
        varchar nombre_categoria
    }
    Evento {
        int id_evento PK
        int id_organizador FK
        int id_categoria FK
        int id_estado_sistema FK
        int id_estado_organizador FK
        varchar nombre
        varchar url_portada
    }
    Tipo_Ticket {
        int id_tipo_ticket PK
        varchar nombre
    }
    Cronograma_Ticket {
        int id_cronograma_ticket PK
        int id_cronograma FK
        int id_tipo_ticket FK
        decimal precio
        int cupo_maximo
        int cupo_actual
    }
    Evento_Cronograma {
        int id_cronograma PK
        int id_evento FK
        date fecha
        time hora_inicio
        time hora_fin
    }
    Evento_Multimedia {
        int id_multimedia PK
        int id_evento FK
        varchar tipo_archivo
        varchar url_archivo
        int orden
        timestamp fecha_subida
    }
    Evento_Estado_Sistema {
        int id_estado_sistema PK
        varchar estado_sistema
        varchar motivo_codigo
    }
    Evento_Estado_Organizador {
        int id_estado_organizador PK
        varchar estado_organizador
    }
    EventoDetalle {
        int id_evento PK, FK
        int id_ubicacion FK
        text descripcion
    }
    Ubicacion {
        int id_ubicacion PK
        varchar calle
        varchar numero_exterior
        varchar numero_interior
        int id_ciudad FK
        decimal latitud
        decimal longitud
    }
    Pais {
        varchar id_pais PK
        varchar nombre
    }
    Provincia {
        int id_provincia PK
        varchar nombre
        varchar id_pais FK
    }
    Ciudad {
        int id_ciudad PK
        varchar nombre
        int id_provincia FK
    }
    Inscripcion {
        int id_inscripcion PK
        int id_cronograma_ticket FK
        int id_usuario FK
        date fecha_inscripcion
        decimal precio_abonado
        varchar estado
        varchar codigo_qr_hash
    }
    Valoracion {
        int id_puntuacion PK
        int id_cronograma FK
        int id_usuario FK
        int valor
        varchar comentario
        date fecha
    }
    Visita {
        int id_visita PK
        int id_evento FK
        int id_usuario FK
        datetime fecha_visita
    }
    PasswordResetToken {
        int id_token PK
        varchar token
        int id_usuario FK
        datetime fecha_expiracion
    }
    Pago {
        int id_pago PK
        int id_inscripcion FK
        int id_suscripcion FK
        decimal monto
        varchar metodo_pago
        varchar estado_pago
        varchar token_operacion
        timestamp fecha_pago
    }
    Suscripcion {
        int id_suscripcion PK
        int id_usuario FK
        varchar tipo_plan
        date fecha_inicio
        date fecha_fin
        varchar estado
    }
    Historial_Interacciones {
        int id_historial PK
        int id_usuario FK
        varchar accion
        varchar modulo
        varchar endpoint
        varchar metodo_http
        text detalles
        varchar ip_origen
        varchar user_agent
        timestamp fecha_interaccion
    }

    %% Relaciones del Modelo Normalizado
    Persona ||--|| Persona_Fisica : "es"
    Persona_Fisica ||--|| Usuario : "da origen a"
    Usuario ||--o{ Miembros_Organizacion : "pertenece a"
    Usuario ||--o{ Usuario_Rol : "posee"
    Persona_Juridica ||--o{ Miembros_Organizacion : "tiene como miembro"
    Rol ||--o{ Usuario_Rol : "asignado"
    
    Usuario ||--o{ Evento : "organiza"
    Categoria ||--o{ Evento : "clasifica"
    Evento_Estado_Sistema ||--o{ Evento : "modera"
    Evento_Estado_Organizador ||--o{ Evento : "gestiona"
    
    Evento ||--o{ Evento_Cronograma : "agenda"
    Evento ||--o{ Evento_Multimedia : "contiene"
    Evento ||--|| EventoDetalle : "detalla"
    
    %% Relación de la Entidad Débil de Tickets Nativos
    Evento_Cronograma ||--o{ Cronograma_Ticket : "ofrece"
    Tipo_Ticket ||--o{ Cronograma_Ticket : "se parametriza como"
    
    Ubicacion ||--|| EventoDetalle : "ubica"
    Ubicacion ||--|| Persona_Juridica : "ubica"
    Ciudad ||--o{ Ubicacion : "contiene"
    Provincia ||--o{ Ciudad : "contiene"
    Pais ||--o{ Provincia : "contiene"
    
    %% Ajuste en Inscripción para apuntar a la intermedia de tickets
    Cronograma_Ticket ||--o{ Inscripcion : "asigna cupo en"
    Usuario ||--o{ Inscripcion : "realiza"
    
    %% CAMBIO 2: Valoración conectada a Cronograma (1:N) y a Usuario (1:1)
    Evento_Cronograma ||--o{ Valoracion : "recibe"
    Usuario ||--o{ Valoracion : "da"
    
    Evento ||--o{ Visita : "registra"
    Usuario ||--o{ Visita : "hace"
    
    Usuario ||--o{ PasswordResetToken : "solicita"
    Usuario ||--o{ Suscripcion : "adquiere"

    %% CAMBIO 1: Ajuste de cardinalidades de Pago a 1:1 absoluto (||--||)
    Inscripcion ||--o| Pago : "genera"
    Suscripcion ||--o| Pago : "genera"
    
    Usuario ||--o{ Historial_Interacciones : "rastrea"
