# 📝 DECISIÓN ARQUITECTÓNICA - Separación de Estados de Moderación

**Fecha:** 2026-08-20  
**Decisor:** Gaston Govino  
**Estado:** 🔵 APROBADO - Pendiente desarrollo en DFDs

---

## 🎯 Propuesta: DOS Tablas de Estado en Paralelo

### Estado Actual del MER (línea 81-89):

```sql
Evento {
    id_evento PK
    id_organizador FK
    id_categoria FK
    id_estado_sistema FK          ← Ya existe (FK a Evento_Estado_Sistema)
    id_estado_organizador FK      ← Ya existe (FK a Evento_Estado_Organizador)
    nombre, url_portada
}

Evento_Estado_Sistema {           ← Tabla existente (línea 117-121)
    id_estado_sistema PK
    estado_sistema VARCHAR
    motivo_codigo VARCHAR
}

Evento_Estado_Organizador {       ← Tabla existente (línea 122-125)
    id_estado_organizador PK
    estado_organizador VARCHAR
}
```

### Problema Identificado:
- **Estado Sistema** y **Estado Organizador** existen pero **no están claras sus responsabilidades**
- RF-2.7: Eventos PUBLICADOS con cambios rechazados **no tienen estado específico**
- Panel admin necesita distinguir entre:
  - Eventos NUEVOS rechazados por IA (falso positivo potencial)
  - Eventos PUBLICADOS con cambios rechazados (mantener visible, revisar cambios)

### Solución Propuesta:

**OPCIÓN ELEGIDA: Generalizar con 1 atributo discriminador**

1. **Redefinir** `Evento_Estado_Sistema` para incluir AMBOS (IA + Admin)
2. **Agregar 1 atributo** `tipo_agente` para distinguir quién decidió
3. **NO crear tablas nuevas** → Mantener modelo simple

**Cambio en modelo:**
```sql
ALTER TABLE Evento_Estado_Sistema 
ADD COLUMN tipo_agente VARCHAR(20) NOT NULL DEFAULT 'SISTEMA';
ALTER TABLE Evento_Estado_Sistema 
ADD CONSTRAINT chk_tipo_agente CHECK (tipo_agente IN ('SISTEMA', 'ADMIN'));
```

**Nueva estructura:**
```
Evento_Estado_Sistema {
  id_estado_sistema PK
  estado_sistema VARCHAR         -- EN_PROCESO, RECHAZADO, APROBADO, CAMBIO_RECHAZADO
  motivo_codigo VARCHAR          -- IA_CONTENIDO, IA_CAMBIOS, ADMIN_REVISO, etc.
  tipo_agente VARCHAR ← NUEVO    -- SISTEMA (IA) | ADMIN (decisión manual)
}
```

**Evento mantiene 2 FKs (sin cambios):**
```sql
-- Ya existen en Evento:
id_estado_organizador FK → Evento_Estado_Organizador
id_estado_sistema FK     → Evento_Estado_Sistema
```

---

## 📊 Matriz Completa de Estados (DEFINITIVA)

### **Evento_Estado_Organizador** (Control del organizador)
| Estado | Significado |
|--------|---|
| BORRADOR | Evento en creación |
| PUBLICADO | Evento visible en catálogo |
| CANCELADO_ORGANIZADOR | Cancelado por organizador |
| DADO_DE_BAJA | Borrado lógico |

### **Evento_Estado_Sistema** (Control de IA/Admin - CON DISCRIMINADOR)
| Estado | tipo_agente | motivo_codigo | Caso de Uso | Evento Visible |
|--------|---|---|-----------|---|
| EN_PROCESO | SISTEMA | — | RF-2.2: validando inicial | ❌ |
| APROBADO | SISTEMA | IA_CONTENIDO | IA aprobó | ✅ |
| APROBADO | ADMIN | ADMIN_REVISO | Admin revirtió rechazo | ✅ |
| RECHAZADO | SISTEMA | IA_CONTENIDO | IA rechazó (falso positivo?) | ❌ |
| RECHAZADO | ADMIN | ADMIN_REVISO | Admin ratificó rechazo | ❌ |
| CAMBIO_RECHAZADO | SISTEMA | IA_CAMBIOS | ⭐ RF-2.7: cambios rechazados | ✅ (versión anterior) |
| CAMBIO_RECHAZADO | ADMIN | ADMIN_REVISO | Admin rechazó cambios propuestos | ✅ (versión anterior) |
| CAMBIO_APROBADO | SISTEMA | IA_CAMBIOS | IA aprobó cambios | ✅ |
| CAMBIO_APROBADO | ADMIN | ADMIN_REVISO | Admin aprobó cambios rechazados | ✅ |

---

## 🔄 Flujos Resultantes

### **Caso 1: Evento NUEVO rechazado (RF-2.2)**
```
1. Organizador crea evento
2. IA rechaza contenido
   → Estado: EN_PROCESO + RECHAZADO_SISTEMA
   → Evento NO visible en catálogo
   → Panel admin: evento para revisión manual (falso positivo?)

3. Admin aprueba
   → Estado: EN_PROCESO + APROBADO
   → Evento pasa a PUBLICADO + APROBADO
```

### **Caso 2: Evento PUBLICADO, cambios rechazados (RF-2.7)** ⭐
```
1. Evento PUBLICADO + APROBADO
2. Organizador edita (título/descripción/imágenes)
   → Estado: PUBLICADO + EN_REVISION_CAMBIOS
   → Evento sigue VISIBLE (versión anterior)
   → Cambios en borrador

3. IA rechaza cambios
   → Estado: PUBLICADO + CAMBIO_RECHAZADO
   → Evento sigue VISIBLE (versión anterior)
   → Cambios descartados
   → Panel admin: evento para revisar (falso positivo?)

4a. Admin APRUEBA cambios rechazados
   → Estado: PUBLICADO + CAMBIO_APROBADO (→ APROBADO)
   → Cambios se publican

4b. Admin RECHAZA cambios
   → Estado: PUBLICADO + APROBADO (sin cambios)
   → Se notifica al organizador
```

---

## 🛠️ Impacto en DFDs

### **M6.1 se divide en dos ramas:**

**6.1A - Moderación de Eventos Nuevos (RECHAZADO_SISTEMA)**
- Admin revisa evento NUEVO rechazado
- Decide: APROBAR o RECHAZAR

**6.1B - Moderación de Cambios (CAMBIO_RECHAZADO)** ⭐ NUEVA
- Admin revisa cambios rechazados en evento PUBLICADO
- Decide:
  - APROBAR: cambios se publican (CAMBIO_APROBADO)
  - RECHAZAR: evento mantiene versión anterior (notificar organizador)

---

## 📋 Tareas Pendientes

- [ ] Crear tabla `Evento_Estado_Moderacion` en modelo de datos
- [ ] Actualizar DFD 6.1 → dividir en 6.1A y 6.1B
- [ ] Crear DFD 5.2B o 5.4 para RF-2.7 (re-moderación de cambios)
- [ ] Actualizar RF-2.7 en requisitos (mencionar CAMBIO_RECHAZADO)
- [ ] Definir DTOs para ambos casos (6.1A y 6.1B)

---

**Próximo paso:** Desarrollar esta arquitectura en los DFDs cuando retomemos.
