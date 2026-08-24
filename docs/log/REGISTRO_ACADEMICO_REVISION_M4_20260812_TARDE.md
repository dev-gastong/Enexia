# 📚 REGISTRO ACADÉMICO: Revisión y Promoción Módulo 4 — 2026-08-12 (Tarde)

**Fecha:** 2026-08-12  
**Hora:** ~15:00 - 16:30  
**Participante:** Gaston (dev.gastong@gmail.com)  
**Tema Principal:** Validación de código roles + Análisis/Corrección DFDs Módulo 4 + Limpieza tempDFD

---

## 🎯 Qué se hizo hoy (Sesión Tarde)

### 1️⃣ **Verificación: Implementación de JWT Multi-Roles** ✅
**Ubicación:** `enexia/src/main/java/com/enexia/rg/model/Usuario.java` + `UsuarioLoginResponse.java`

**Validaciones:**
- ✅ **Usuario.java Línea 47-48:** Relación `@OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)` con `Set<UsuarioRol>` presente
- ✅ **Usuario.java Línea 72-79:** Método `getRoles()` implementado correctamente:
  ```java
  public List<String> getRoles() {
      if (usuarioRoles == null || usuarioRoles.isEmpty()) {
          return List.of();
      }
      return usuarioRoles.stream()
          .map(ur -> ur.getRol().getNombreRol())
          .collect(Collectors.toList());
  }
  ```
- ✅ **UsuarioLoginResponse.java Línea 19:** Campo `private List<String> roles;` presente
- ✅ **Fetch Strategy:** `EAGER` (correcto para evitar lazy loading en serialización JWT)
- ✅ **gradle bootJar:** Compilación exitosa

**Conclusión:** Multi-roles completamente implementado, listo para JWT multi-rol en Fase 3.

---

### 2️⃣ **Limpieza tempDFD: Módulo 3** ✅
**Acción Ejecutada:**
- ✅ Borrados 5 archivos duplicados de `modulo_3_participacion/` (ya confirmados en `docs/diagrams/`)
- ✅ Borrada carpeta vacía `modulo_3_participacion/`

**Archivos Eliminados:**
- nivel1_participacion.md
- 3.1_3.2_inscripcion_y_pago.md
- 3.3_cancelacion_inscripcion.md
- 3.4_3.5_valoracion_y_moderacion.md
- 3.6_historial_inscripciones.md

**Estado:** tempDFD ahora solo contiene módulos 4-8 (no confirmados).

---

### 3️⃣ **Análisis y Corrección: Módulo 4 (4 DFDs)** ✅

#### **4.1 / 4.2 / 4.3 — Catálogo, Búsqueda, Filtros**
- **RF Cubiertos:** 4.1, 4.2, 4.3
- **Estado Inicial:** ✅ Correcto
- **Acción:** Movido directamente a `docs/diagrams/modulo_4_interfaz_publica/`
- **Descripción:** Catálogo paginado → búsqueda LIKE → filtros (categoría, fecha, ubicación)

#### **4.4 / 4.5 — Ficha Técnica + Auditoría de Visitas**
- **RF Cubiertos:** 4.4, 4.5
- **Hallazgo 1:** DFD original validaba UNIQUE en inserción (no está en RF)
- **Acción Tomada:** Revertir a **simple** (registrar todas las visitas sin validación de unicidad)
- **Corrección Implementada:**
  - Registra TODAS las visitas (autenticadas + anónimas)
  - Usa `id_usuario = NULL` para visitas anónimas
  - **Unicidad se calcula en LECTURA** (panel del organizador: `COUNT DISTINCT id_usuario`)
  - ✅ **Escalable:** Futuro = validar UNIQUE en inserción, separar por plan (Gratuito = total, PRO = desglose)
- **Validación MER:** Confirmado que Visita usa NULL en `id_usuario` para diferenciar (sin campo explícito `is_anonymous`)
- **Acción:** Movido a diagrams

#### **4.6 — Renderizado Condicional del Navbar**
- **RF Cubierto:** 4.6
- **Hallazgo 2:** DFD trataba roles como **mutuamente excluyentes** (PARTICIPANTE **o** ORGANIZADOR)
- **Problema:** Con multi-rol implementado (2026-08-12), un usuario puede ser AMBOS
- **Acción Tomada:** Reescribir DFD con **lógica acumulativa**:
  - ¿ADMINISTRADOR? → Agregar panel admin
  - ¿ORGANIZADOR? → Agregar panel de eventos (NO reemplaza participante)
  - ¿PARTICIPANTE? → Agregar inscripción + perfil
  - **Resultado:** UN navbar único con todos los botones acumulados
- **Ejemplo:** Organizador + Participante verá: Inscripción + Mi Perfil + Panel Propio
- **Corrección:** Removidos corchetes `[]` del label que rompían Mermaid
- **Acción:** Movido a diagrams

#### **Nivel 1 — Descomposición Módulo 4**
- **Tipo:** Diagrama de contexto (6 sub-procesos orquestados)
- **Estado:** ✅ Correcto
- **Acción:** Movido a diagrams

---

### 4️⃣ **Hallazgo: Módulo 5.1 — Moderación de Texto con IA** ⏳
**Ubicación:** `docs/tempDFD/modulo_5_moderacion/5.1_moderacion_texto_ia.md`

**Análisis:**
- ✅ Estructura general correcta (sanitización → API → validación → registro)
- ⚠️ **HALLAZGO CRÍTICO:** Llamada **SÍNCRONA** a API externa (Perspective API / OpenAI)
  - Bloquea transacción hasta respuesta
  - Si API lenta (>500ms) → ralentiza registro/evento/valoración
  - Fallback a Error 503 si API no responde

**Decision:** **PENDIENTE** — Requiere análisis de performance + decisión sobre cambiar a asíncrona

---

## 📊 Resumen Ejecutivo

| Tarea | Estado | Detalles |
|-------|--------|----------|
| ✅ Verificar roles | COMPLETADO | Multi-rol implementado correctamente, compilación OK |
| ✅ Limpiar tempDFD M3 | COMPLETADO | 5 archivos + carpeta borrados |
| ✅ Revisar M4.1/4.2/4.3 | COMPLETADO | Movido a diagrams |
| ✅ Revisar M4.4/4.5 | COMPLETADO | Corregido (visitas simples, escalable), movido a diagrams |
| ✅ Revisar M4.6 | COMPLETADO | Corregido (roles acumulativos, multi-rol), movido a diagrams |
| ✅ Revisar M4 Nivel 1 | COMPLETADO | Movido a diagrams |
| ⏳ Revisar M5.1 | PENDIENTE | Hallazgo de API síncrona, requiere decisión |

---

## 🔑 Conceptos Clave Documentados

### **1. Visitas Escalables (M4.5)**
El modelo de visitas fue diseñado para ser escalable a futuro:
- **Sprint 1 (Actual):** Registrar todas las visitas, unicidad en lectura
- **Sprint N (Futuro Plan Pro):** Validar UNIQUE en inserción, separar autenticadas/anónimas
- **Beneficio:** No requiere refactor de código, solo agregar validación

### **2. Roles Acumulativos (M4.6)**
La lógica del navbar fue reescrita para reflejar la arquitectura multi-rol implementada:
- Roles **no son mutuamente excluyentes**
- Navbar acumula capacidades según roles presentes
- Ejemplo: Organizador vuelto Participante = ve botones de ambos
- **Seguridad:** Backend SIEMPRE revalida via `@PreAuthorize` (botones son UI, no control)

### **3. API Síncrona vs Asíncrona (M5.1)**
Hallazgo a resolver: llamadas a APIs externas deben evaluar:
- ¿Latencia tolerable (<200ms)?
- ¿Fallback claustro o degradación?
- ¿Necesario bloqueo o puede ser asíncrono?

---

## 📁 Archivos Modificados/Creados

### Creados
- ✅ `REGISTRO_ACADEMICO_REVISION_M4_20260812_TARDE.md` (este archivo)

### Movidos a `docs/diagrams/modulo_4_interfaz_publica/`
- ✅ `4.1_4.2_4.3_catalogo_busqueda_filtros.md`
- ✅ `4.4_4.5_ficha_tecnica_y_visitas.md` (corregido)
- ✅ `4.6_renderizado_condicional_navbar.md` (corregido)
- ✅ `nivel1_interfaz_publica.md`

### Borrados de tempDFD
- ✅ `modulo_3_participacion/` (5 archivos + carpeta)

### Pendientes
- ⏳ `docs/tempDFD/modulo_5_moderacion/5.1_moderacion_texto_ia.md` (análisis API síncrona)

---

## ⏭️ Próximos Pasos

### Inmediatos
1. Decidir sobre M5.1: ¿Mantener API síncrona o cambiar a asíncrona?
2. Continuar con M5.2/5.3 (Moderación multimedia), M5.4 (Degradación automática)
3. Revisar M6, M7, M8

### Para Fase 3 (Backend Implementation)
1. Implementar `JwtUtil.generateToken()` con `roles[]` multi-rol
2. Implementar `EventoService.crearEvento()` con persistencia cascada atómica
3. Implementar `VisitaService.registrarVisita()` con estrategia escalable
4. Implementar `NavbarService` con lógica acumulativa de roles (backend)

### Para Documentación
1. Actualizar `CLAUDE.md` con ejemplo de roles acumulativos en navbar
2. Crear diagrama de flujo "Futura Escalabilidad de Visitas" (Plan Gratuito vs Pro)

---

**Sesión completada:** ✅  
**Archivos procesados:** 8 (verificación + limpieza + 4 DFDs)  
**Hallazgos:** 3 (roles multi, visitas escalables, API síncrona)  
**Líneas modificadas:** ~50  
**Estado tempDFD:** 16 archivos (M5-M8) pendientes de revisión

---

**Próxima sesión:** Resolver M5.1 (API síncrona) y continuar M5.2+

¡Listo para documentación! 📚
