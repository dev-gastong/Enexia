# MER reducido: registro, login y crear evento

Subconjunto minimo de [MER.md](./MER.md): solo las tablas sin las cuales no se pueden ejecutar estos flujos.

- `POST /api/auth/registro` (Persona Fisica)
- `POST /api/auth/login`
- `POST /api/organizador/eventos` (evento a titulo personal)

Las columnas son copia literal del MER completo.

```mermaid
erDiagram
    Persona {
        int id_persona PK
        timestamp fecha_registro
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
        int id_estado_usuario_sistema FK
        int id_estado_usuario FK
        varchar email
        varchar password
        varchar nickname
        int intentos_fallidos
        datetime fecha_baja
    }
    Rol {
        int id_rol PK
        varchar nombre_rol
    }
    Usuario_Rol {
        int id_usuario FK
        int id_rol FK
    }
    Usuario_Estado_Sistema {
        int id_estado_usuario_sistema PK
        varchar estado_usuario_sistema
    }
    Usuario_Estado {
        int id_estado_usuario
        varchar estado_usuario
    }

    Evento {
        int id_evento PK
        int id_organizador FK
        int id_categoria FK
        int id_estado_sistema FK
        int id_estado_organizador FK
        varchar nombre
        varchar url_portada
        timestamp fecha_creacion
    }
    EventoDetalle {
        int id_evento PK, FK
        int id_ubicacion FK
        text descripcion
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
    Categoria {
        int id_categoria PK
        varchar nombre_categoria
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
    Ubicacion {
        int id_ubicacion PK
        varchar calle
        varchar numero_exterior
        varchar numero_interior
        int id_ciudad FK
        decimal latitud
        decimal longitud
    }
    Ciudad {
        int id_ciudad PK
        varchar nombre
        int id_provincia FK
    }
    Provincia {
        int id_provincia PK
        varchar nombre
        varchar id_pais FK
    }
    Pais {
        varchar id_pais PK
        varchar nombre
    }

    Persona ||--|| Persona_Fisica : "es"
    Persona_Fisica ||--|| Usuario : "da origen a"
    Usuario ||--o{ Usuario_Rol : "posee"
    Rol ||--o{ Usuario_Rol : "asignado"
    Usuario_Estado_Sistema ||--o{ Usuario : "modera"
    Usuario_Estado ||--o{ Usuario : "gestiona"

    Usuario ||--o{ Evento : "organiza"
    Categoria ||--o{ Evento : "clasifica"
    Evento_Estado_Sistema ||--o{ Evento : "modera"
    Evento_Estado_Organizador ||--o{ Evento : "gestiona"
    Evento ||--|| EventoDetalle : "detalla"
    Evento ||--o{ Evento_Cronograma : "agenda"
    Evento ||--o{ Evento_Multimedia : "contiene"

    Ubicacion ||--|| EventoDetalle : "ubica"
    Ciudad ||--o{ Ubicacion : "contiene"
    Provincia ||--o{ Ciudad : "contiene"
    Pais ||--o{ Provincia : "contiene"
```

## Por que cada tabla

| Flujo | Tablas |
|---|---|
| Registro y login | `Persona`, `Persona_Fisica`, `Usuario`, `Usuario_Estado_Sistema`, `Usuario_Estado` (el estado ACTIVO/BLOQUEADO se consulta en el login), `Rol`, `Usuario_Rol` (los roles viajan en el JWT) |
| Crear evento | `Evento`, `EventoDetalle`, `Evento_Cronograma`, `Evento_Multimedia`, `Categoria`, `Evento_Estado_Sistema`, `Evento_Estado_Organizador`, `Ubicacion`, `Ciudad`, `Provincia`, `Pais` |

## Recortes respecto de la version anterior

- **Organizaciones:** se quitaron `Persona_Juridica`, `Miembros_Organizacion`, sus catalogos e historial, y la columna `Evento.id_persona_juridica` (era nullable). Con esto el evento solo puede ser a titulo personal, y el registro es solo `POST /api/auth/registro`, sin `registro/organizacion`.
- **Historiales:** se quitaron `Historial_Estado_Usuario`, `Historial_Estado_Evento` e `Historial_Interacciones`. Son auditoria, no condicion para que los flujos funcionen. El codigo actual probablemente los escribe, asi que si se implementa contra este modelo habra que desactivar ese paso.
- **Tickets:** se quitaron `Cronograma_Ticket` y `Tipo_Ticket`. El evento queda con fecha y horario, pero sin cupos ni precios.
- **Geografia:** `Ciudad`, `Provincia` y `Pais` se mantienen aunque parezcan accesorias, porque `Ubicacion.id_ciudad` es FK y la cadena llega hasta `Pais`.
