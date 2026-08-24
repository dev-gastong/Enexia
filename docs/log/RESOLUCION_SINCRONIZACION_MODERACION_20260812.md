# 📋 RESOLUCIÓN: Sincronización Moderación — Asíncrona vs Síncrona

**Fecha:** 2026-08-12  
**Hora:** ~17:00  
**Participante:** Gaston (dev.gastong@gmail.com)  
**Tema:** Alineación documentación RFs con prototipo Figma (flujo asíncrono de moderación)

---

## 🔍 Hallazgo

**Conflicto identificado:**
- **Documentación (RFs):** Decía "SÍNCRONA" para moderación con APIs externas
- **Prototipo Figma:** Muestra flujo ASÍNCRONO (evento se guarda como EN_PROCESO → luego cambia a APROBADO/RECHAZADO)

**Decisión:** Sincronizar documentación hacia **ASÍNCRONO** (modelo más realista y escalable)

---

## 📝 RFs Modificados

### **1. M2.2 — Moderación de Texto en Creación de Eventos**

**ANTES:**
```
RF-2.2: Moderación Síncrona de Texto en la Carga

"Durante los procesos de creación o edición de un evento, el backend 
debe interceptar el título y la descripción para procesarlos mediante 
el filtro de seguridad nativo. Si el algoritmo detecta lenguaje ofensivo, 
discriminatorio o inapropiado, el sistema bloqueará la persistencia 
en la base de datos..."
```
**Problema:** Bloquea creación inmediatamente → user experience pobre

**DESPUÉS:**
```
RF-2.2: Moderación Asíncrona de Texto en la Carga

"Durante los procesos de creación o edición de un evento, el backend 
debe guardar el evento con estado "EN_PROCESO" y disparar de forma 
asíncrona la interceptación del título y la descripción para procesarlos 
mediante el filtro de seguridad nativo. Si el algoritmo detecta lenguaje 
ofensivo, discriminatorio o inapropiado, el sistema mutará el estado 
del evento a un código de rechazo por auditoría (Evento_Estado_Sistema 
= "RECHAZADO_SISTEMA"), notificará la infracción al usuario y la remitirá 
al Panel de Administración para revisión manual. Si la validación es 
exitosa, el estado transitará a "APROBADO_SISTEMA" y el evento será 
visible en el catálogo público."
```
**Beneficios:**
- ✅ Evento se guarda inmediatamente (EN_PROCESO)
- ✅ Moderación ocurre en background
- ✅ User ve en dashboard: "Tu evento está siendo validado..."
- ✅ Admin ve evento en cola para revisar
- ✅ Escalable: futuro puede agregar revisión manual

---

### **2. M5.1 — Moderación de Texto con APIs IA (Perspective/OpenAI)**

**ANTES:**
```
RF-5.1: Integración con APIs de Moderación de Texto Basadas en IA

"Durante los procesos de creación/edición de eventos o envío de 
valoraciones, la capa de servicios interceptará los textos y consumirá 
de forma síncrona estas APIs. Si el servicio externo retorna métricas 
que superen los umbrales tolerables de toxicidad, insultos o 
discriminación, el sistema rechazará la solicitud..."
```
**Problema:** Bloquea while API responde → timeout risk

**DESPUÉS:**
```
RF-5.1: Integración con APIs de Moderación de Texto Basadas en IA (Asíncrona)

"Durante los procesos de creación/edición de eventos o envío de 
valoraciones, el sistema persiste el contenido con estado "EN_PROCESO" 
y dispara de forma asíncrona la interceptación de los textos hacia 
la capa de servicios que consumirá estas APIs. Si el servicio externo 
retorna métricas que superen los umbrales tolerables de toxicidad, 
insultos o discriminación, el sistema mutará el estado a 
"RECHAZADO_SISTEMA", notificará la infracción al usuario y enviará 
el registro al Panel de Administración para revisión manual. Si la 
validación es exitosa, el estado transitará a "APROBADO_SISTEMA"."
```
**Beneficios:**
- ✅ No bloquea por timeout de API
- ✅ Fallback: si API cae, evento queda EN_PROCESO (recoverable)
- ✅ Admin puede forzar aprobación manualmente
- ✅ Auditoría completa en historial

---

### **3. M5.4 — Degradación Automática de Eventos**

**ANTES:**
```
RF-5.4: Degradación de Estado y Bloqueo Automatizado de Eventos

"En caso de que un evento publicado en la plataforma dispare alertas 
por vulneración de directrices en las respuestas de las APIs de 
moderación, el backend ejecutará una acción correctiva inmediata: 
mutará el valor relacional en la tabla Evento... e inhabilitará 
automáticamente el evento del catálogo público de forma síncrona."
```
**Problema:** "Inmediata" puede desactivar evento sin aviso

**DESPUÉS:**
```
RF-5.4: Degradación de Estado y Bloqueo Automatizado de Eventos (Asíncrona)

"En caso de que un evento publicado en la plataforma dispare alertas 
por vulneración de directrices en las respuestas de las APIs de 
moderación, el backend ejecutará una acción correctiva de forma 
asíncrona: mutará el valor relacional en la tabla Evento... 
El sistema guardará el código de la infracción retornado por la API 
en el campo motivo_codigo, notificará al organizador sobre la acción 
correctiva, e inhabilitará automáticamente el evento del catálogo 
público. El registro quedará disponible en el Panel de Administración 
para revisión manual o reversión de la decisión automatizada."
```
**Beneficios:**
- ✅ Organizador es notificado
- ✅ Admin puede revertir decisión
- ✅ Auditoría completa
- ✅ Más justo/transparente

---

## 📊 Matriz Comparativa

| RF | Tipo | ANTES | DESPUÉS | Impacto |
|----|----|--------|---------|---------|
| **M2.2** | Texto | Síncrona ❌ | Asíncrona ✅ | Evento EN_PROCESO visible en dashboard |
| **M5.1** | Texto IA | Síncrona ❌ | Asíncrona ✅ | No bloquea por timeout API |
| **M5.4** | Degradación | Síncrona ❌ | Asíncrona ✅ | Notifica organizador, permite reversión |

---

## 🔗 Archivos Modificados

**Documentación:**
- ✅ `docs/requisitos/requisitos_funcionales/modulo_2.md` — M2.2 actualizado
- ✅ `docs/requisitos/requisitos_funcionales/modulo_5.md` — M5.1 y M5.4 actualizados

**DFD a Revisar/Actualizar:**
- ⏳ `docs/diagrams/gestion_de_eventos/creación_y_publicación_de_eventos.md` 
  - Necesita reflejar: Crear evento (EN_PROCESO) → Moderación async → Cambio estado

---

## 🎯 Próximos Pasos

1. **Revisar DFD M2 (Creación de Eventos):**
   - Actualizar para mostrar flujo asíncrono
   - Agregar estado "EN_PROCESO" como paso inicial
   - Agregar branching: APROBADO_SISTEMA ↔ RECHAZADO_SISTEMA
   - Mostrar notificación a organizador

2. **Revisar DFD M5.1 (Moderación Texto):**
   - Cambiar de síncrono a asíncrono
   - Agregar timeout/fallback
   - Incluir notificación a admin

3. **Actualizar DFD M5.4 (Degradación):**
   - Mostrar notificación a organizador
   - Agregar opción "Admin override" para reversión

4. **Tests (Sprint N):**
   - `test_evento_queda_en_proceso_tras_crear()`
   - `test_notificacion_enviada_si_rechazado()`
   - `test_admin_puede_revertir_decision()`

---

## 💡 Concepto: Asíncrono vs Síncrono en Moderación

### **Síncrono (ANTES — Problemático):**
```
User → Create Event → Validate API → Block/Approve → Response
                      ↑ 500ms-2s wait ↑
```
**Problema:** User espera, si API lenta → timeout

### **Asíncrono (DESPUÉS — Correcto):**
```
User → Create Event (EN_PROCESO) → Response 200 OK
         ↓
         [Background Task] → Validate API → Notify Admin
```
**Ventaja:** User ve respuesta inmediata, moderación ocurre después

---

## ✅ Estado

| Tarea | Status |
|-------|--------|
| ✅ Identificar conflicto doc/prototipo | COMPLETADO |
| ✅ Modificar M2.2 a asíncrona | COMPLETADO |
| ✅ Modificar M5.1 a asíncrona | COMPLETADO |
| ✅ Modificar M5.4 a asíncrona | COMPLETADO |
| ✅ Documentar cambios | COMPLETADO |
| ⏳ Revisar/actualizar DFDs | PENDIENTE (próxima sesión) |
| ⏳ Implementar en Fase 3 Backend | PENDIENTE |

---

**Sesión completada:** ✅  
**RFs modificadas:** 3  
**Documentación actualizada:** 2 archivos  
**DFDs a revisar:** 3  

---

**Referencia Figma (descrita por usuario):**
- Evento se guarda como "EN PROCESO"
- State transitions: EN PROCESO → APROBADO/RECHAZADO
- Admin ve en panel de revisión
- User notificado cuando cambia estado

¡Listo para próxima sesión! 📚
