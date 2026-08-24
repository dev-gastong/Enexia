```mermaid
graph TD
    %% Punto de Inicio Único
    INICIO([INICIO: Solicitud de Creación]) --> Org([Organizador vía HTTP])
    Org -->|Petición con Formulario + JWT Bearer| P2_0[2.0: Validar JWT]
    
    %% Almacenes de Datos
    subgraph Almacenes
        D20_Sus[(Suscripcion)]
        D10_Ev[(Evento)]
        D17_Det[(EventoDetalle)]
        D11_Cro[(Evento_Cronograma)]
        D12_Mul[(Evento_Multimedia)]
        D13_Tkt[(Cronograma_Ticket)]
        D14_TTk[(Tipo_Ticket)]
        D6_Logs[(Historial_Interacciones)]
        D22_Est[(Evento_Estado_Sistema)]
    end

    %% Entidades Externas
    API_Mod([API Externa: Perspective / Cloudinary / IA])
    Notif([Servicio de Notificaciones - Email/In-App])

    %% ===== FASE SÍNCRONA (Inmediata) =====
    P2_0 --> C_Token{¿JWT válido y vigente?}
    C_Token -- No --> Err_Auth([Error 401: No Autorizado])
    C_Token -- Sí --> P2_1[2.1: Verificar Límites de Suscripción]

    P2_1 -->|Consultar plan| D20_Sus
    D20_Sus -->|Retornar estado plan| P2_1
    P2_1 --> C_Plan{¿Supera limite de eventos?}
    C_Plan -- Sí --> Err_Plan([Error: Límite Excedido. Upgrade a Pro])
    C_Plan -- No --> P2_2[2.2: Validar Fechas y Horarios]

    P2_2 --> C_Fechas{¿Fechas válidas y futuras?}
    C_Fechas -- No --> Err_Fechas([Error: Fechas u horarios inválidos])
    C_Fechas -- Sí --> P2_3[2.3: Validar Campos Obligatorios]

    P2_3 --> C_Campos{¿Campos obligatorios completos?}
    C_Campos -- No --> Err_Campos([Error: Campos incompletos])
    C_Campos -- Sí --> P2_4[2.4: Guardar Evento EN_PROCESO]

    %% ===== PERSISTENCIA INMEDIATA: SKELETON ONLY (RF-2.2) =====
    %% RFC-2.2: Crear registro minimo con metadatos solamente
    P2_4 -->|1. Crear Evento Skeleton| D10_Ev
    P2_4 -->|   - id_evento PK| D10_Ev
    P2_4 -->|   - id_organizador FK| D10_Ev
    P2_4 -->|   - estado_sistema = EN_PROCESO| D10_Ev
    P2_4 -->|   - fecha_creacion| D10_Ev
    P2_4 -->|2. Registrar Auditoría de Creación| D6_Logs
    
    P2_4 --> P2_4_Success[2.4: TRANSACCIÓN EXITOSA]
    P2_4_Success -->|Response 200 OK| Success_Sync([Evento creado: Estado EN_PROCESO - Visible en dashboard])

    %% ===== DISPARO ASÍNCRONO (Background Task) =====
    P2_4_Success --> P2_5_ASYNC["🔄 ASYNC: 2.5 Disparar Moderación en Background"]

    %% ===== FASE ASÍNCRONA (Background) =====
    P2_5_ASYNC --> P2_5A[2.5A: Enviar Título, Descripción e Imágenes a API]
    P2_5A -->|POST a Perspective/Cloudinary| API_Mod
    API_Mod -->|Retorna score de toxicidad/análisis| P2_5A
    
    P2_5A --> C_Sensible{¿Detectó contenido sensible?}

    %% Rama A: Contenido Limpio - Persistir datos completos (RF-2.2)
    C_Sensible -- No --> P2_5B[2.5B: Persistir Datos Completos del Evento]
    P2_5B -->|Guardar EventoDetalle| D17_Det
    P2_5B -->|Guardar Cronogramas| D11_Cro
    P2_5B -->|Guardar Multimedia URLs| D12_Mul
    P2_5B -->|Guardar Tipos de Ticket| D14_TTk
    P2_5B -->|Guardar Cronograma_Ticket| D13_Tkt
    P2_5B --> P2_5B_STATE[2.5B: Cambiar Estado a APROBADO_SISTEMA]
    P2_5B_STATE -->|Actualizar Evento Estado=APROBADO| D10_Ev
    P2_5B_STATE -->|Registrar aprobación en auditoría| D6_Logs
    P2_5B_STATE --> P2_5B_NOTIF[2.5B: Enviar Notificación al Organizador]
    P2_5B_NOTIF -->|Tu evento fue aprobado y ya es visible| Notif
    P2_5B_NOTIF --> FIN_APPROVED([✅ Evento PUBLICADO en catálogo])

    %% Rama B: Contenido Sensible
    C_Sensible -- Sí --> P2_5C[2.5C: Cambiar Estado a RECHAZADO_SISTEMA]
    P2_5C -->|Actualizar Evento Estado=RECHAZADO| D10_Ev
    P2_5C -->|Guardar motivo_codigo de infracción| D10_Ev
    P2_5C -->|Registrar rechazo en auditoría| D6_Logs
    
    P2_5C --> P2_5C_NOTIF[2.5C: Notificar Organizador + Admin]
    P2_5C_NOTIF -->|Email: Tu evento fue rechazado por...| Notif
    P2_5C_NOTIF -->|Admin ve en panel de revisión| Notif
    
    P2_5C --> FIN_REJECTED([❌ Evento RECHAZADO - En espera de revisión manual])

    %% ===== NOTA IMPORTANTE =====
    NOTA["⚠️ RFC-6.1: Admin puede REVERTIR decisión automática<br/>(cambiar RECHAZADO → APROBADO_MANUAL)"]

    %% Unificación de Salidas (Punto de Fin Único)
    Err_Auth --> FIN([FIN])
    Err_Plan --> FIN
    Err_Fechas --> FIN
    Err_Campos --> FIN
    FIN_APPROVED --> FIN
    FIN_REJECTED --> FIN
    NOTA -.-> FIN

    style P2_4_Success fill:#eef,stroke:#333
    style P2_5_ASYNC fill:#fef,stroke:#f33,stroke-dasharray: 5 5
    style FIN_APPROVED fill:#efe,stroke:#3a3
    style FIN_REJECTED fill:#fee,stroke:#a33
    style NOTA fill:#fff,stroke:#f90,stroke-dasharray: 3 3
```

---

## 📋 CAMBIOS CLAVE vs VERSIÓN SÍNCRONA

### **ANTES (Síncrona - Problemática):**
```
User crea evento
    ↓
Valida JWT, plan, fechas
    ↓
Espera API de moderación (500ms-2s) ← BLOQUEA
    ↓
Si aprobado → guarda APROBADO
Si rechazado → guarda RECHAZADO
    ↓
Response al user
```

### **DESPUÉS (Asíncrona - Correcta):**
```
User crea evento
    ↓
Valida JWT, plan, fechas (rápido)
    ↓
Guarda inmediatamente con estado EN_PROCESO
    ↓
Response 200 OK → "Tu evento está siendo validado..."
    ↓
[Background] API de moderación corre en paralelo
    ↓
Resultado → Cambia estado a APROBADO/RECHAZADO
    ↓
Notifica al usuario del cambio
    ↓
Si rechazado → Admin lo ve en panel para revisar
```

---

## 🎯 Ventajas del Flujo Asíncrono

| Aspecto | Síncrona (ANTES) | Asíncrona (DESPUÉS) |
|--------|--------|--------|
| **Experiencia del User** | Espera 500ms-2s | Feedback inmediato (200 OK) |
| **Dashboard del Org** | Vacío hasta que se aprueba | Muestra evento EN_PROCESO inmediatamente |
| **Timeout de API** | Bloquea creación | No afecta (ocurre después) |
| **Auditoría** | Parcial | Completa (aprobado + notificado + estado) |
| **Admin Override** | Difícil de revertir | Fácil (RF-6.1: cambiar estado) |
| **Escalabilidad** | Baja (bloquea) | Alta (background tasks) |

---

## 💡 Flujo de Estados del Evento

```
INICIO (en formulario)
    ↓
EN_PROCESO (creado, validando)
    ├─→ APROBADO_SISTEMA (moderación OK) ← visible en catálogo
    ├─→ RECHAZADO_SISTEMA (moderación falló) ← oculto, en panel admin
    └─→ APROBADO_MANUAL (admin lo revierte)
        RECHAZADO_MANUAL (admin confirma rechazo)
```

---

## 📝 Notas de Implementación

1. **Background Task:** Usar Job Queue (sidekiq, celery, etc.) para disparar moderación
2. **Notificaciones:** Enviar email/in-app cuando estado cambie
3. **Admin Panel:** Debe mostrar eventos EN_PROCESO y RECHAZADO_SISTEMA para revisión
4. **Rollback:** Si API cae → evento queda EN_PROCESO (manual recovery)
5. **Timeout:** Si moderación >N minutos → timeout, marcar como EN_REVISIÓN_MANUAL

---

**Versión:** PROPUESTA para revisión 2026-08-12  
**Estado:** Pendiente aprobación  
**Cambios respecto a DFD original:** Cambio a asíncrono (RF-2.2 actualizado)
```

