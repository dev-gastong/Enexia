# 📋 REVISIÓN DE MÓDULOS 6, 7, 8 - DFDs PENDIENTES

**Fecha:** 2026-08-20  
**Revisor:** Claude Code  
**Estado:** ⏳ PENDIENTE APROBACIÓN FINAL  

---

## 🎯 RESUMEN EJECUTIVO

| Módulo | DFDs | Total | Estado | Acción Requerida |
|--------|------|-------|--------|------------------|
| **M6 Admin** | 5 | 5 | ⚠️ REVISAR | 2 problemas críticos |
| **M7 Perfiles** | 4 | 4 | ✅ APROBABLE | 2 observaciones menores |
| **M8 Membresías** | 4 | 4 | ⚠️ REVISAR | 3 clarificaciones |
| **TOTAL** | 13 | 11* | — | **7 hallazgos** |

*Nota: M5 Moderación tiene 5 DFDs pero está en diagrams/ (completado). Estos 11 son nuevos pendientes.

---

## 📊 MÓDULO 6 - ADMINISTRACIÓN (5 DFDs)

### ✅ 6.1 - Moderación Manual de Eventos (APROBABLE con revisiones)

**FORTALEZAS:**
- ✅ Validación JWT y RBAC bien estructurada (solo ADMINISTRADOR)
- ✅ Bifurcación clara: Aprobar (revertir rechazo) vs. Ratificar rechazo
- ✅ Auditoría completa con Historial_Estado_Evento + Historial_Interacciones
- ✅ Estados finales correctos (APROBADO_MANUAL | RECHAZADO_MANUAL)

**⚠️ PUNTOS CRÍTICOS A REVISAR:**

1. **Inconsistencia con RF-2.7 (Re-moderación de Cambios)**
   - **Problema:** El DFD solo contempla eventos en `RECHAZADO_SISTEMA`
   - **RF-2.7 menciona:** Eventos PUBLICADOS con cambios van a `EN_REVISIÓN` (no RECHAZADO_SISTEMA)
   - **Acción:** ¿El diagrama 6.1 solo maneja rechazo automático por IA? ¿La revisión manual de cambios es otro flujo (6.1B)?
   - **Recomendación:** Aclarar en el DFD nivel1 o dividir en 6.1A (rechazo IA) y 6.1B (revisión de cambios)

2. **Campo motivo_codigo no está definido en el modelo**
   - **Problema:** Se persiste `motivo_codigo` en Evento (línea 38, 44)
   - **Acción:** Verificar si existe en modelo Usuario o necesita crearse en Evento
   - **Recomendación:** Crear campo `motivo_admin_codigo` en Evento (VARCHAR 20)

**DTO SUGERIDO (Backend):**
```java
@Data
public class ModeracionManualRequest {
    @NotNull(message = "id_evento requerido")
    private Long idEvento;
    
    @NotNull(message = "decisión requerida")
    @Pattern(regexp = "APROBAR|RECHAZAR")
    private String decision;
    
    @Size(max = 500)
    private String motivo;  // Opcional, para documentar razón
}

@Data
public class ModeracionManualResponse {
    private Long idEvento;
    private String nuevoEstado;  // APROBADO_MANUAL | RECHAZADO_MANUAL
    private LocalDateTime fechaResolucion;
    private String mensajeAlOrganizador;
}
```

---

### ✅ 6.2 - Gestión Disciplinaria de Cuentas (APROBABLE con aclaración)

**FORTALEZAS:**
- ✅ Validación JWT + RBAC clara
- ✅ Estados bien definidos (SUSPENDIDO | BANEADO | ACTIVO)
- ✅ Notificación por email integrada
- ✅ Auditoría con Historial_Estado_Usuario

**⚠️ PUNTOS A ACLARAR:**

1. **Tabla Usuario_Estado redundante**
   - **Observación:** El almacén menciona D1_UsrEst (Usuario_Estado) pero en CLAUDE.md no se define
   - **Pregunta:** ¿Es tabla separada o una columna de enumeración en Usuario?
   - **Recomendación:** Confirmar que `estado_usuario` en Usuario sea de tipo ENUM (@Enumerated)

2. **"Inhabilitar emisión de JWT en próximos logins" (línea 39)**
   - **Cómo funciona:** Al cambiar estado a SUSPENDIDO/BANEADO, el LoginController debe rechazar logins
   - **DTO sugerido:**

```java
@Data
public class CambioEstadoUsuarioRequest {
    @NotNull
    private Long idUsuario;
    
    @NotNull
    @Pattern(regexp = "ACTIVO|SUSPENDIDO|BANEADO")
    private String nuevoEstado;
    
    @Size(max = 500)
    private String motivoSancion;  // Por qué fue sancionado
}
```

---

### ✅ 6.3 - ABM de Categorías (APROBABLE)

**FORTALEZAS:**
- ✅ Integridad referencial correcta (no elimina si hay eventos asociados)
- ✅ Validación de duplicados en ALTA
- ✅ Auditoría del cambio registrada
- ✅ Estados HTTP claros (409 para conflictos)

**⚠️ OBSERVACIÓN MENOR:**

1. **Conteo de eventos vinculados (línea 39-40)**
   - **Pregunta:** ¿Se cuentan eventos CANCELADOS o solo ACTIVOS/PUBLICADOS?
   - **Recomendación:** Aclarar lógica: `eventos WHERE estado NOT IN (CANCELADO_ORGANIZADOR, CANCELADO_SISTEMA)`

**DTO SUGERIDO:**
```java
@Data
public class CategoriaRequest {
    @NotBlank(message = "nombre_categoria requerido")
    @Size(min=3, max=100)
    private String nombreCategoria;
    
    @Size(max=500)
    private String descripcion;
}
```

---

### ✅ 6.4 - Gestión Manual de Suscripciones (APROBABLE con transacciones)

**FORTALEZAS:**
- ✅ Intervenciones administrativas bien separadas (cambiar plan, ajustar vigencia, revocar)
- ✅ Historial con motivo del cambio
- ✅ Auditoría completa

**⚠️ PUNTOS CRÍTICOS:**

1. **Falta garantía transaccional**
   - **Problema:** Las 3 ramas (6.4.4A, 6.4.4B, 6.4.4C) hacen cambios sin transacción atómica
   - **Riesgo:** Si falla el UPDATE de Suscripcion después de actualizar fecha, queda inconsistente
   - **Recomendación:** Marcar como `@Transactional` en el Service

2. **"Forzar Estado CANCELADA" (línea 40)**
   - **Pregunta:** ¿Qué pasa con suscripciones ACTIVAS? ¿Se pierde acceso inmediato a PRO?
   - **Acción:** Registrar auditoría de revocación

**DTO SUGERIDO:**
```java
@Data
public class IntervenciónSuscripciónRequest {
    @NotNull
    private Long idSuscripcion;
    
    @NotNull
    @Pattern(regexp = "CONMUTAR_PLAN|AJUSTAR_VIGENCIA|REVOCAR")
    private String tipoIntervención;
    
    // Opcional según tipo
    private String nuevoPlan;  // FREE o PRO
    private LocalDate nuevaFechaFin;  // Para ajustar vigencia
    
    @NotBlank(message = "motivo requerido para auditoría")
    @Size(max=500)
    private String motivo;
}
```

**Acción recomendada:** Agregar `@Transactional` al service 6.4

---

### 📈 Nivel 1 Admin (GRÁFICO GENERAL) ✅

- ✅ Contexto correcto: P5 (Moderación) → P6.1 (revisión manual)
- ✅ Flujos bidireccionales (Admin invoca procesos y recibe confirmaciones)
- ✅ Auditoría global pasiva bien representada

---

## 📊 MÓDULO 7 - PERFILES DE ORGANIZADOR (4 DFDs)

### ✅ 7.1 - Registro Persona Física (APROBABLE)

**FORTALEZAS:**
- ✅ Validación de unicidad de DNI
- ✅ Separación clara: Persona → Persona_Fisica
- ✅ Auditoría registrada
- ✅ Validación de campos obligatorios

**⚠️ OBSERVACIÓN:**

1. **Falta validación de contenido (better-profanity)**
   - **RF-1.0 menciona:** Moderación de texto en registro (nombre, apellido)
   - **Acción:** Agregar validación de nombre/apellido contra base de palabras prohibidas
   - **Recomendación:** Antes de 7.1.2, insertar paso 7.1.X: validar nombre/apellido con better-profanity

**Sugerencia de mejora del flujo:**
```
7.1.1 → (Validar campos)
7.1.1A → (NEW: Validar nombre/apellido contra palabras prohibidas)
  → Si detecta: Err_ContentProfanity
7.1.2 → (Validar DNI único)
```

---

### ✅ 7.2 - Registro Persona Jurídica (APROBABLE)

**FORTALEZAS:**
- ✅ Delega validación de CUIT al subproceso 7.3 (separación correcta)
- ✅ Valida campos fiscales obligatorios
- ✅ Persiste domicilio fiscal mediante Ubicacion

**⚠️ OBSERVACIÓN:**

1. **Nombre de Fantasía (línea 49)**
   - **Pregunta:** ¿Es obligatorio u opcional?
   - **RF-7.2:** Menciona "Nombre Fantasía opcional"
   - **Estado actual:** El DFD inserta correctamente (optional field)
   - ✅ **Está bien**

2. **Falta validación de contenido (mejor-profanity)**
   - **Similar a 7.1:** Razón Social y Nombre Fantasía necesitan moderación
   - **Recomendación:** Agregar paso 7.2.1A para moderar campos de texto

---

### ✅ 7.3 - Validación Algorítmica del CUIT (EXCELENTE)

**PERFECTO:**
- ✅ Algoritmo módulo 11 correctamente implementado
- ✅ Casos especiales bien manejados (resto=0 → 9, resto=11 → 0)
- ✅ Separación clara de responsabilidades
- ✅ Punto de entrada/salida único

**Sin observaciones críticas.** Diagrama listo para mover a diagrams/.

---

### ✅ 7.4 - Firma del Organizador (APROBABLE)

**FORTALEZAS:**
- ✅ Resuelve correctamente tipo de persona (Física → Nombre+Apellido, Jurídica → Fantasía o Razón Social)
- ✅ Integración con proceso 4.4 (Ficha Técnica) clara
- ✅ Lógica de fallback correcta (si no hay fantasía, usa razón social)

**Está bien** y listo para diagrams/.

---

### 📈 Nivel 1 Perfiles (GRÁFICO GENERAL) ✅

- ✅ Flujos correctos: P1 (Auth) delega a P7, que delega a P7.3
- ✅ Conexión con P4 (Ficha Técnica) para resolver firma
- ✅ Datos de entrada/salida claros

---

## 📊 MÓDULO 8 - MEMBRESÍAS / PLANES (4 DFDs)

### ⚠️ 8.1 - Upgrade a Plan Pro (REVISAR)

**FORTALEZAS:**
- ✅ Flujo de pago bien estructurado: PENDIENTE → ACTIVA
- ✅ Cascada transaccional correcta
- ✅ Auditoría de upgrade registrada

**🔴 PUNTOS CRÍTICOS:**

1. **No valida suscripción PENDIENTE anterior**
   - **Problema (línea 27):** Solo valida `ACTIVA`, no `PENDIENTE`
   - **Escenario:** Si user solicita upgrade → Estado PENDIENTE, luego intenta upgrade de nuevo, ¿se crea otra PENDIENTE?
   - **Riesgo:** Múltiples registros PENDIENTE orfandos
   - **Acción:** Línea 25 debe buscar `PENDIENTE OR ACTIVA`, no solo `ACTIVA`

**Sugerencia de fix:**
```
P8_1_1 -->|Consultar Suscripción (ACTIVA O PENDIENTE) del Usuario| D20_Sus
D20_Sus -->|Retornar Estado Actual| P8_1_1
P8_1_1 --> C_YaPro{¿Ya Posee Suscripción<br>PRO (ACTIVA o PENDIENTE)?}
C_YaPro -->|Sí| Err_YaPro([Error 409: Upgrade ya en curso o Plan Pro activo])
```

2. **Falta validación de vigencia**
   - **Problema:** No se valida si la suscripción anterior expiró
   - **Acción:** Si fecha_fin es anterior a HOY, permitir nuevo upgrade FREE → PRO

---

### ⚠️ 8.2 - Control de Cuotas Free vs Pro (REVISAR)

**FORTALEZAS:**
- ✅ Lógica clara: FREE tiene límite, PRO sin límite
- ✅ Plan Free por defecto si no hay suscripción
- ✅ Bloquea con error 403

**⚠️ PUNTOS A ACLARAR:**

1. **¿Dónde está parametrizado el límite Free? (línea 30)**
   - **Pregunta:** "Límite Parametrizado Free" ¿es configuración global (properties) o per-plan?
   - **Recomendación:** Aclarar en comentario: `// Ej: 3 eventos máximo para Free`
   - **Problema:** El DFD no muestra de dónde viene este valor

2. **Evento CANCELADO no cuenta en cuota**
   - **Pregunta (línea 28):** "eventos WHERE estado_organizador != CANCELADO"
   - **Acción:** ¿Incluye CANCELADO_SISTEMA y CANCELADO_ORGANIZADOR? ¿O solo uno?
   - **Recomendación:** Especificar: `estado NOT IN (CANCELADO_ORGANIZADOR, CANCELADO_SISTEMA, RECHAZADO_SISTEMA)`

3. **No previene cambios de plan**
   - **Escenario:** ¿Qué pasa si un usuario Free con 3 eventos intenta bajarse a... (ya está en Free)?
   - **Caso de uso:** ¿Pro usuario cancela suscripción, se convierte a Free? ¿Se bloquean sus eventos?
   - **Recomendación:** Este DFD cubre creación, pero ¿hay otro para cambio de plan durante vigencia?

**DTO sugerido:**
```java
@Data
public class ValidaciónCuotaRequest {
    // Interceptor: no necesita DTO de entrada
    // Solo JWT del usuario
}

@Data
public class ValidaciónCuotaResponse {
    private String estado;  // AUTORIZADO | BLOQUEADO
    private Integer eventosActuales;
    private Integer límiteDisponible;
    private String mensaje;  // "Límite Free alcanzado" o vacío si autorizado
}
```

---

### ✅ 8.3 - Restricción de Features Pro (APROBABLE)

**FORTALEZAS:**
- ✅ Validación clara: solo PRO activo accede
- ✅ Auditoría de intentos denegados (seguridad)
- ✅ Error 403 correcto para acceso prohibido
- ✅ Validación INDEPENDIENTE de claim en JWT (seguridad extra)

**Bien diseñado.** Listo para diagrams/.

---

### 📈 Nivel 1 Membresías (GRÁFICO GENERAL) ✅

- ✅ Flujos correctos: P8.1 (upgrade) → P8.2 (control cuotas en P2)
- ✅ P8.3 (features avanzadas en P4)
- ✅ Almacenes de datos bien enlazados

---

## 🛠️ RESUMEN DE ACCIONES RECOMENDADAS

### **BLOQUEADORES (Requieren fix antes de mover a diagrams/):**

| Módulo | DFD | Problema | Acción |
|--------|-----|----------|--------|
| **M6** | 6.1 | Inconsistencia con RF-2.7 (EN_REVISIÓN vs RECHAZADO_SISTEMA) | Aclarar en DFD si hay 6.1A y 6.1B o si solo maneja rechazo IA |
| **M6** | 6.4 | Falta @Transactional en intervenciones | Documentar en Service layer |
| **M8** | 8.1 | No valida PENDIENTE anterior | Cambiar búsqueda a `ACTIVA OR PENDIENTE` |
| **M8** | 8.2 | Límite parametrizado no especificado | Aclarar origen del valor (properties, tabla Plan, etc.) |

### **MEJORAS SUGERIDAS (Post-aprobación, Sprint 2+):**

| Módulo | DFD | Mejora | Prioridad |
|--------|-----|--------|-----------|
| **M7** | 7.1 | Agregar validación better-profanity a nombre/apellido | MEDIA |
| **M7** | 7.2 | Agregar validación better-profanity a razón social/fantasía | MEDIA |
| **M8** | 8.2 | Documtar flujo de cambio de plan durante vigencia | MEDIA |

---

## ✅ APROBACIÓN RECOMENDADA

**Estado Actual:**
- ✅ **M6:** CONDICIONAL - Requiere fixes en 6.1 y 6.4
- ✅ **M7:** APROBABLE - Mejoras sugeridas no bloquean (best-effort)
- ⚠️ **M8:** CONDICIONAL - Requiere clarificaciones en 8.1 y 8.2

**Próximo Paso:**
1. Resolver 4 bloqueadores identificados
2. Mover M7 completo a `docs/diagrams/modulo_7_perfiles_organizacion/`
3. Mover M8 (tras fixes) a `docs/diagrams/modulo_8_membresias/`
4. Mover M6 (tras fixes) a `docs/diagrams/modulo_6_admin/`
5. Actualizar checkpoint: 23/34 → 34/34 ✅ (100% completo)

---

**Fin del reporte de revisión.**
