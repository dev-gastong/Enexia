# DFD Nivel 2 — N.2 Notificaciones por moderación de eventos

Descompone el proceso **N.2** del [Nivel 1](./nivel1_notificaciones.md): los correos al organizador cuando cambia el veredicto sobre su evento. Hay dos orígenes: el **pipeline automático** (RF-2.2, RF-5.1 a 5.3) y la **decisión de un administrador** (RF-6.1).

```mermaid
graph TD
    INICIO([INICIO: Evento nuevo o decisión de Admin])

    Org([Organizador])
    Admin([Administrador])

    subgraph Almacenes
        D1[(D1: Usuario - email del organizador)]
        D2[(D2: Evento / Evento_Estado_Sistema / Historial_Estado_Evento)]
        D6[(D6: Historial_Interacciones)]
    end
    N5[[N.5 Despachador de correo]]

    %% ---------- A. PIPELINE AUTOMATICO ----------
    INICIO --> Org
    Org -->|Crea el evento: datos + imágenes| A1[N.2.1 Registrar evento EN_PROCESO]
    A1 -->|Evento + estado EN_PROCESO| D2
    A1 -->|Publicar EventoCreado AFTER_COMMIT| A2[N.2.2 Fase 1: moderar texto]
    A2 --> A3{¿Título o descripción<br>con lenguaje ofensivo?}
    A3 -->|Sí| A6[N.2.5 Rechazar: RECHAZADO_SISTEMA / MODERACION_TEXTO]
    A3 -->|No| A4[N.2.3 Fase 2: subir y moderar cada imagen]
    A4 --> A5{¿Al menos una<br>imagen aprobada?}
    A5 -->|No| A6b[N.2.5 Rechazar: MODERACION_IMAGEN o SIN_IMAGENES_VALIDAS]
    A5 -->|Sí| A7[N.2.4 Aprobar y publicar: APROBADO_SISTEMA]
    A7 -->|Estado + multimedia aprobada| D2
    A6 -->|RECHAZADO_SISTEMA + motivo| D2
    A6b -->|RECHAZADO_SISTEMA + motivo| D2
    A2 -.->|Cualquier excepción inesperada| A8[N.2.6 Red de contención: RECHAZADO_SISTEMA / ERROR_PIPELINE]
    A8 -->|Estado de error| D2
    A7 -->|Orden MODERACION_RESULTADO aprobado| N5
    A6 -->|Orden MODERACION_RESULTADO rechazado + motivo| N5
    A6b -->|Orden MODERACION_RESULTADO rechazado + motivo| N5
    A8 -->|Orden MODERACION_RESULTADO rechazado: revisará un administrador| N5

    %% ---------- B. DECISION DEL ADMINISTRADOR ----------
    INICIO --> Admin
    Admin -->|PATCH decisión: APROBAR o RECHAZAR + motivo + observación| B1[N.2.7 Bloquear la fila del evento y leer su estado]
    B1 <-->|Bloqueo pesimista + estado vigente| D2
    B1 --> B2{¿Decisión válida<br>para el estado actual?}
    B2 -->|No: en moderación, ya aprobado o ya rechazado| Err([Rechazo 409 sin cambios ni correo])
    B2 -->|Sí| B3[N.2.8 Aplicar nuevo estado + historial con observación]
    B3 -->|Estado sistema + fila de historial| D2
    B3 -->|Auditoría EVENTO_MODERADO_ADMIN| D6
    B3 --> B4{¿Qué decisión fue?}
    B4 -->|RECHAZAR sobre evento publicado| B5[N.2.9 Emitir EVENTO_SUSPENDIDO]
    B4 -->|APROBAR sobre RECHAZADO_MANUAL| B6[N.2.10 Emitir EVENTO_REACTIVADO]
    B4 -->|APROBAR sobre RECHAZADO_SISTEMA| B7[N.2.11 Emitir MODERACION_RESULTADO aprobado]
    B5 -->|Orden con nombre, motivo y observación| N5
    B6 -->|Orden con nombre del evento| N5
    B7 -->|Orden: el evento ya está publicado| N5

    %% ---------- DESTINO ----------
    B5 --> D1
    B6 --> D1
    B7 --> D1
    A7 --> D1
    N5 -->|Correo al organizador| Org

    Err --> FIN([FIN])
    N5 --> FIN
```

## Reglas

| Regla | Motivo |
|---|---|
| **El correo sale después del commit y nunca propaga fallos.** Lo garantiza el despachador (N.5), no cada origen. | Un correo no puede salir por una transacción revertida, ni un proveedor caído puede tirar abajo una decisión de moderación. |
| **Matriz de decisión del Admin:** APROBAR solo sobre `RECHAZADO_SISTEMA` o `RECHAZADO_MANUAL`; RECHAZAR solo sobre un evento publicado (`APROBADO_*`). | Sobre un evento en `EN_PROCESO` / `EN_REVISION` no hay veredicto firme que cambiar; aprobar uno ya aprobado o rechazar uno ya rechazado no cambia nada, así que tampoco se avisa. |
| **Aprobar un rechazo del sistema NO es una reactivación.** Se avisa con `MODERACION_RESULTADO` ("ya está publicado"). Solo se usa `EVENTO_REACTIVADO` al revertir una **suspensión** (`RECHAZADO_MANUAL`). | Un evento rechazado por el pipeline nunca estuvo al aire; decirle al organizador que "volvió" sería falso. |
| **Suspender exige motivo (`MotivoSuspensionAdmin`) y observación;** aprobar descarta la observación aunque el cliente la mande. | El organizador necesita saber qué corregir o apelar, y el historial no debe cargar texto que nadie pidió. |
| **Bloqueo de fila + `READ_COMMITTED`** en la decisión del Admin. | El pipeline o el propio organizador (editar, dar de baja) pueden tocar el mismo evento en paralelo. |
| **Sin libro de idempotencia (D7) en este grupo.** Son avisos de una acción puntual de una persona o del pipeline, que corre una sola vez por evento. | A diferencia del recordatorio, no hay un reloj que repita el disparo. Ver N.5: `despachar` frente a `despacharUnico`. |
| **El correo se arma con el nombre del evento ya escapado** por `CorreoHtml`; la observación del Admin es texto libre y nunca se vuelve enlace. | Único punto donde se escapa HTML (N.5). |
| **La edición de un evento ya aprobado (RF-2.7) NO genera correo.** Se modera de forma sincrónica y si falla, responde con error al organizador en la misma petición. | El organizador ve el resultado en pantalla; un correo duplicaría la respuesta HTTP. |

> **Cobertura de estados:** `ERROR_PIPELINE` es el único rechazo que el organizador no causó. El correo lo dice ("un administrador va a revisarlo") y deja el evento en una cola revisable en el panel de moderación en lugar de colgado en `EN_PROCESO`.
