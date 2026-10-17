# DFD Nivel 2 — N.4 Notificación por alta en el equipo de una organización

Descompone el proceso **N.4** del [Nivel 1](./nivel1_notificaciones.md): el aviso `AGREGADO_A_EQUIPO` cuando un administrador de una organización suma a otra persona desde "Mi Equipo" (RF-7.2). Quitar a un miembro **no** genera correo.

```mermaid
graph TD
    INICIO([INICIO: POST miembros de la organización]) --> Org([Organizador - administrador de la organización])

    subgraph Almacenes
        D1[(D1: Usuario / Persona_Fisica)]
        D1b[(D1b: Miembros_Organizacion / Persona_Juridica)]
        D6[(D6: Historial_Interacciones)]
    end
    N5[[N.5 Despachador de correo]]
    Nuevo([Persona agregada])

    Org -->|ID de la organización + email del miembro + rol opcional| M1[N.4.1 Resolver al solicitante]
    M1 -->|Buscar cuenta activa del solicitante| D1
    M1 --> M2{¿El solicitante es<br>ADMINISTRADOR de la organización?}
    M2 -->|Leer membresía| D1b
    M2 -->|No| Err1([Rechazo: solo un administrador gestiona el equipo])
    M2 -->|Sí| M3[N.4.2 Buscar la cuenta a agregar por email]
    M3 -->|Cuenta activa con ese email| D1
    M3 --> M4{¿Existe una cuenta activa?}
    M4 -->|No| Err2([Rechazo 404: no existe ninguna cuenta activa con ese email])
    M4 -->|Sí| M5{¿Ya es miembro<br>de esta organización?}
    M5 -->|Consultar membresía| D1b
    M5 -->|Sí| Err3([Rechazo 409: ya es miembro])
    M5 -->|No| M6[N.4.3 Registrar membresía con su rol, por defecto MIEMBRO]
    M6 -->|Alta en Miembros_Organizacion| D1b
    M6 -->|Auditoría MIEMBRO_AGREGADO| D6
    M6 -->|Orden AGREGADO_A_EQUIPO: email, razón social y rol| N5
    N5 -->|Correo: te sumaron al equipo de la organización| Nuevo
    M6 -->|MiembroResponse| Org

    Err1 --> FIN([FIN])
    Err2 --> FIN
    Err3 --> FIN
    Org --> FIN
```

## Reglas

| Regla | Motivo |
|---|---|
| **Solo un `ADMINISTRADOR` de la organización agrega miembros.** Cualquier miembro puede listar el equipo. | Es la misma regla que gobierna quitar miembros; el aviso sale únicamente de la acción autorizada. |
| **Solo se puede agregar una cuenta activa existente.** No hay invitaciones a emails sin cuenta. | El correo se manda a una persona ya registrada, y no existe un flujo de invitación pendiente que consumir. |
| **El destinatario del aviso es la persona agregada,** no el administrador. El administrador ya conoce la acción: la ve en pantalla. | Es la contraparte que podría no esperar el alta. |
| **El correo ofrece una salida:** si no esperaba la invitación, que contacte a la administración. | Quien agrega miembros escribe el email a mano; un error de tipeo no debería sumar a un tercero sin que se entere. |
| **Sin libro de idempotencia (D7).** Es un aviso por acción puntual de una persona (`despachar`). | La restricción de membresía única ya impide el segundo alta; la repetición no puede ocurrir. |
| **Sale después del commit.** Un alta que se revierte por conflicto de concurrencia no envía el aviso. | Regla general del despachador N.5. |
| **Quitar a un miembro no avisa.** Y nunca se puede quitar al último administrador. | Decisión de alcance: el catálogo de notificaciones solo cubre el alta. Si más adelante se agrega, se suma un tipo nuevo al enum, sin renombrar los existentes. |
