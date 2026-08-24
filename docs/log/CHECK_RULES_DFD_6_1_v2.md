# ✅ CHECK-RULES: DFD 6.1 v2 vs RF-2.2 / RF-2.7

**Fecha:** 2026-08-20  
**DFD:** 6.1_moderacion_manual_eventos_v2.md  
**RFs:** RF-2.2 (Moderación Inicial), RF-2.7 (Re-moderación de Cambios)  
**Resultado:** ⚠️ APROBABLE CON 3 OBSERVACIONES

---

## ✅ VERIFICACIÓN POR DOMINIO

### 1️⃣ SEGURIDAD (JWT + RBAC)

**RF Requisito:** Admin debe validar JWT y rol ADMINISTRADOR

**DFD Implementa:**
- ✅ 6.1.0: Validar firma y vigencia del JWT
- ✅ 6.1.1: Validar rol ADMINISTRADOR con @PreAuthorize
- ✅ Error 401 si JWT inválido
- ✅ Error 403 si rol no es ADMINISTRADOR

**Veredicto:** ✅ **BIEN** - RBAC implementado correctamente

---

### 2️⃣ REGLAS DE NEGOCIO (RF-2.2)

**RF-2.2 Requisito:**
> "Si el algoritmo [IA] detecta lenguaje ofensivo... el sistema mutará el estado del evento a RECHAZADO_SISTEMA... la remitirá al Panel de Administración para revisión manual"

**DFD Implementa:**
- ✅ 6.1.2: Verifica que estado sea RECHAZADO_SISTEMA
- ✅ 6.1.3: Admin decide: APROBAR o RECHAZAR
- ✅ 6.1.4A (Aprobar): Evento pasa a APROBADO
- ✅ 6.1.4B (Rechazar): Se mantiene RECHAZADO

**Veredicto:** ✅ **BIEN**

---

### 3️⃣ REGLAS DE NEGOCIO (RF-2.7)

**RF-2.7 Requisito:**
> "Cualquier cambio en contenido sensible (título, descripción, imágenes) de un evento PUBLICADO dispara automáticamente un nuevo ciclo de moderación asíncrona... Si la re-moderación es exitosa, los cambios se publican; si es rechazada, se revierte a la versión anterior"

**DFD Implementa:**
- ✅ 6.1.2: Valida CAMBIO_RECHAZADO (además de RECHAZADO_SISTEMA)
- ✅ 6.1.4A: Si aprueba → CAMBIO_APROBADO (cambios visibles)
- ✅ 6.1.4B: Si rechaza → se revierte a versión anterior

**Veredicto:** ✅ **BIEN**

---

### 4️⃣ INTEGRIDAD DE DATOS

**¿Qué sucede con datos del evento?**

**RF-2.2 especifica:**
> "NO persistirá datos del evento (título, descripción, ubicación, cronogramas, multimedia, tickets)" [si IA rechaza]

**¿El DFD 6.1 lo respeta?**

⚠️ **OBSERVACIÓN 1: Datos parciales en RECHAZADO_SISTEMA**
- RF-2.2 dice: Si IA rechaza texto en Fase 1 → NO persiste datos completos
- Pero, ¿qué pasa si admin APRUEBA en 6.1?
- DFD 6.1.4A dice: "Reincorporar al Catálogo Público"
- **Problema:** ¿El evento "skeleton" EN_PROCESO se completa con datos? ¿De dónde vienen?

**Pregunta Critical:** 
- ¿Cuando admin APRUEBA un evento RECHAZADO_SISTEMA, el backend:
  A) Activa datos que fueron almacenados en borrador? (RF-2.2 dice que NO se persistieron)
  B) Pide al organizador que re-cargue datos? (No está en el DFD)
  C) ¿Qué datos exactamente se "Reincorporan"?

**Veredicto:** ⚠️ **REVISAR** - El flujo de datos post-aprobación no está claro

---

### 5️⃣ BORRADO LÓGICO / SOFT DELETES

**¿Se preserve historial?**

**DFD Implementa:**
- ✅ 6.1.5: Registra en Historial_Estado_Evento
- ✅ 6.1.5: Registra en Historial_Interacciones
- ✅ Nunca elimina registro de Evento (solo cambia estado)

**Veredicto:** ✅ **BIEN** - Historial completo preservado

---

### 6️⃣ ESTADOS Y TRANSICIONES

**¿Las transiciones de estado son válidas?**

| Caso | Desde | Hacia | Válido | Nota |
|------|-------|-------|--------|------|
| Aprobar evento nuevo | RECHAZADO_SISTEMA | APROBADO | ✅ | RF-2.2 permite reverso |
| Rechazar evento nuevo | RECHAZADO_SISTEMA | RECHAZADO_SISTEMA | ✅ | Se mantiene (admin ratifica) |
| Aprobar cambios | CAMBIO_RECHAZADO | CAMBIO_APROBADO | ✅ | RF-2.7 permite publicar |
| Rechazar cambios | CAMBIO_RECHAZADO | CAMBIO_RECHAZADO | ✅ | Se revierte a versión anterior |

**Veredicto:** ✅ **BIEN** - Transiciones lógicas

---

### 7️⃣ DISCRIMINADOR tipo_agente

**¿Se marca correctamente quién decidió?**

**DFD Implementa:**
- ✅ 6.1.4A (Aprobar): tipo_agente = ADMIN
- ✅ 6.1.4B (Rechazar): tipo_agente = ADMIN
- ✅ 6.1.5: Registra en historial

**Veredicto:** ✅ **BIEN** - Auditoría clara

---

### 8️⃣ NOTIFICACIONES AL ORGANIZADOR

**¿Se notifica al organizador?**

**RF-2.2 especifica:**
> "notificará la infracción al usuario" [si IA rechaza]

**RF-2.7 especifica:**
> "se notifica al organizador" [si admin rechaza cambios]

**DFD Implementa:**
- ❌ NO hay paso de notificación
- ❌ NO hay integración con email/push

**Veredicto:** ❌ **FALTA** - No implementa notificaciones

---

### 9️⃣ CONCURRENCIA

**¿Qué pasa si dos admins revisan el mismo evento simultáneamente?**

**DFD Implementa:**
- ⚠️ No hay validación de versión (no hay versionado de eventos)
- ⚠️ Podría haber race condition: admin A aprueba, admin B rechaza → último gana

**Veredicto:** ⚠️ **REVISAR** - Sin optimistic locking o versionado

---

### 🔟 DTOs

**¿Están definidos los DTOs?**

**DFD NO menciona:**
- Request DTO: ¿Qué campos envía el admin? (solo id_evento + decisión?)
- Response DTO: ¿Qué retorna el endpoint?

**Ejemplo faltante:**
```java
// DTO de entrada
@Data
class ModeracionManualRequest {
    @NotNull Long idEvento;
    @Pattern(regexp = "APROBAR|RECHAZAR") String decision;
    @Size(max=500) String motivoAdmin;  // ¿Opcional?
}

// DTO de salida
@Data
class ModeracionManualResponse {
    Long idEvento;
    String nuevoEstado;  // APROBADO | RECHAZADO | CAMBIO_APROBADO | CAMBIO_RECHAZADO
    LocalDateTime fechaDecision;
    String mensajeAlOrganizador;
}
```

**Veredicto:** ⚠️ **FALTA** - DTOs no definidos en DFD

---

## 🔴 RESUMEN DE HALLAZGOS

| # | Categoría | Hallazgo | Severidad | Estado |
|----|-----------|----------|-----------|--------|
| 1 | Integridad | Flujo de datos post-aprobación no especificado (¿dónde vienen los datos?) | CRÍTICA | ⚠️ REVISAR |
| 2 | Negocio | No implementa notificación al organizador | ALTA | ❌ FALTA |
| 3 | Concurrencia | Sin optimistic locking / versionado de eventos | MEDIA | ⚠️ REVISAR |
| 4 | API | DTOs no definidos en diagrama | MEDIA | ⚠️ FALTA |

---

## ✅ APROBACIÓN RECOMENDADA

**Estado Actual:** ⚠️ **CONDICIONAL**

**Acciones Requeridas Antes de Mover a diagrams/:**

1. **[CRÍTICA]** Aclarar: ¿Cómo se completan los datos cuando admin aprueba evento RECHAZADO_SISTEMA?
   - Opción A: Datos fueron almacenados en borrador → se activan
   - Opción B: Admin debe pedir re-carga → agregar paso
   - Opción C: ¿Otra solución?
   
2. **[ALTA]** Agregar paso 6.1.X: Notificar al Organizador
   - Email: "Tu evento fue aprobado" o "Tu evento fue rechazado"
   - Por cada rama (Aprobar y Rechazar)

3. **[MEDIA]** Considerar optimistic locking:
   - Agregar versión (`version_num`) a Evento_Estado_Sistema
   - O usar timestamp de última modificación

4. **[MEDIA]** Documentar DTOs esperados en comentario del DFD

---

**Próximo Paso:** Retocar DFD 6.1 v2 con estos puntos antes de aprobación final.
