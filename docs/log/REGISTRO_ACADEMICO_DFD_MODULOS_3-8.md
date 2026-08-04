# 📚 REGISTRO ACADÉMICO: DFD Nivel 1 y 2 — Módulos 3 a 8

**Fecha:** 2026-08-04
**Sprint:** Sprint 1 — Cierre de Documentación de Flujos (paralelo a Fase 3 Backend)
**Participantes:** Gaston (dev.gastong@gmail.com)
**Referencia:** `CLAUDE.md`, `docs/diseño_bd/MER.md`, `docs/requisitos/requisitos_funcionales/modulo_{3..8}.md`, `docs/diagrams/` (estilo base)

---

### 📑 Registro Académico del Proyecto

- **Decisión Técnica:** Se generaron **26 diagramas Mermaid** (6 de Nivel 1 — descomposición por módulo — y 20 de Nivel 2 — flujo detallado con puntos de decisión) cubriendo los Módulos 3 a 8 (Participación, Interfaz Pública, Moderación, Panel Admin, Perfiles de Organización y Membresías), replicando el estilo ya validado en `login.md` y los diagramas de `gestion_de_eventos/`. Se almacenaron temporalmente en `docs/tempDFD/` a la espera de su revisión y promoción a `docs/diagrams/`.

- **Concepto Académico / Patrón:** **Data Flow Diagram (DFD) por Niveles de Abstracción** — Nivel 0 (contexto), Nivel 1 (descomposición en subprocesos numerados con almacenes de datos), Nivel 2 (flujo procedimental con nodos de decisión, equivalente a un *Flowchart* de proceso de negocio). Complementado con verificación de **Reglas Arquitectónicas Transversales** (RBAC, Concurrencia Transaccional, Borrado Lógico, Moderación Síncrona) vía la skill `check-rules`.

- **Píldora Teórica:**
  - Un **DFD Nivel 1** descompone el proceso central en subprocesos numerados (ej. `3.1`, `3.2`...) y expone los *almacenes de datos* (tablas) con los que interactúa cada uno, sin detallar la lógica interna — responde "¿qué módulos existen y qué datos tocan?".
  - Un **DFD Nivel 2** (aquí modelado como *flowchart* `graph TD`) desciende a nivel de algoritmo: valida JWT, aplica reglas de negocio mediante nodos de decisión (`{...}`) y unifica todas las salidas en un único nodo `FIN`, patrón que evita fugas de flujo de control sin documentar.
  - La **concurrencia sobre `cupo_actual`** (RF-3.1) se modeló explícitamente con `SELECT ... FOR UPDATE` dentro de una transacción atómica, representando en el diagrama la futura anotación `@Transactional` del Service — el diagrama es un contrato que el código de Fase 3 deberá cumplir.
  - La **validación del CUIT** (RF-7.3) se documentó como un sub-algoritmo aislado (módulo 11 con secuencia de multiplicadores `5,4,3,2,7,6,5,4,3,2`), permitiendo que el diagrama funcione como pseudocódigo reutilizable para el futuro `CuitValidator.java`.

- **Justificación:** Diagramar el flujo **antes** de escribir el Service layer (Fase 3) permite detectar reglas de negocio ambiguas o contradictorias sobre papel, donde corregir es gratis, en vez de durante la implementación, donde corregir cuesta refactor. Frente a la alternativa de "codificar directamente desde los RF en prosa", el DFD obliga a explicitar cada validación (orden de checks, camino de error, qué tabla se toca) — reduciendo el riesgo de que el Service omita, por ejemplo, el control atómico de cupos o la revalidación RBAC en backend que el Módulo 4.6 explícitamente advierte que el frontend por sí solo no garantiza.

---

## 🗂️ Inventario de Diagramas Generados (`docs/tempDFD/`)

| Módulo | Nivel 1 | Diagramas Nivel 2 | RF Cubiertos |
|--------|---------|--------------------|--------------| 
| **M3 — Participación** | `nivel1_participacion.md` | `3.1_3.2_inscripcion_y_pago.md`, `3.3_cancelacion_inscripcion.md`, `3.4_3.5_valoracion_y_moderacion.md`, `3.6_historial_inscripciones.md` | RF-3.1 a RF-3.6 |
| **M4 — Interfaz Pública** | `nivel1_interfaz_publica.md` | `4.1_4.2_4.3_catalogo_busqueda_filtros.md`, `4.4_4.5_ficha_tecnica_y_visitas.md`, `4.6_renderizado_condicional_navbar.md` | RF-4.1 a RF-4.6 |
| **M5 — Moderación** | `nivel1_moderacion.md` | `5.1_moderacion_texto_ia.md`, `5.2_5.3_moderacion_multimedia.md`, `5.4_degradacion_automatica_evento.md` | RF-5.1 a RF-5.4 |
| **M6 — Panel Admin** | `nivel1_admin.md` | `6.1_moderacion_manual_eventos.md`, `6.2_gestion_cuentas_usuario.md`, `6.3_abm_categorias.md`, `6.4_gestion_suscripciones_admin.md` | RF-6.1 a RF-6.4 |
| **M7 — Perfiles Organización** | `nivel1_perfiles_organizacion.md` | `7.1_7.2_registro_fisica_juridica.md`, `7.3_validacion_cuit.md`, `7.4_firma_organizador.md` | RF-7.1 a RF-7.4 |
| **M8 — Membresías** | `nivel1_membresias.md` | `8.1_upgrade_suscripcion_pago.md`, `8.2_control_cuotas_plan.md`, `8.3_restriccion_features_pro.md` | RF-8.1 a RF-8.3 |

**Total:** 6 diagramas Nivel 1 + 20 diagramas Nivel 2 = **26 diagramas**.

---

## 🔍 Validación con `check-rules`

Se auditaron los 26 diagramas contra las 5 reglas arquitectónicas del proyecto:

| Regla | Resultado |
|-------|-----------|
| **1. DTOs** (no exponer entidades) | N/A a nivel de diagrama de flujo; aplica en Fase 3 al implementar Controllers. |
| **2. Concurrencia (`@Transactional`)** | ✅ Modelado explícitamente en `3.1_3.2` (SELECT FOR UPDATE) y `3.3` (decremento atómico de `cupo_actual`). |
| **3. Borrado Lógico** | ✅ Cumplido en Inscripción, Usuario, Evento (mutación de estado, nunca DELETE). ⚠️ **Hallazgo:** `6.3_abm_categorias.md` modela la baja de `Categoria` como `DELETE` físico — la entidad no tiene `fecha_baja` en el MER. Documentado como desviación menor de la política general de `CLAUDE.md`, mitigada por el control de integridad referencial (no permite borrar categorías con eventos asociados). Queda pendiente decidir si se agrega soft-delete a `Categoria` en el MER. |
| **4. RBAC / JWT en endpoints privados** | ✅ Todos los diagramas de M6 (Admin) validan explícitamente `Rol ADMINISTRADOR`. Se corrigieron 4 diagramas (3.3, 3.4/3.5, 3.6, 8.3) que solo validaban "JWT vigente" sin exigir el rol específico (Participante/Organizador), alineándolos con RF-1.3. |
| **5. Moderación de texto antes de persistir** | ✅ Modelado como sub-proceso delegado (`5.1`) invocado desde Registro (M1), Eventos (M2) y Valoraciones (`3.4/3.5`), consistente con el DFD Nivel 1 global existente. |

---

## ⏭️ Próximos Pasos

1. **Revisión del usuario** de los 26 diagramas en `docs/tempDFD/`.
2. Mover (o descartar) los diagramas aprobados a `docs/diagrams/` con su estructura de carpetas definitiva por módulo, y actualizar `docs/diagrams/README.md` con el índice ampliado.
3. Decidir sobre el hallazgo de `Categoria` (¿agregar `fecha_baja`/estado, o mantener DELETE físico por ser tabla de catálogo?).
4. Usar estos DFD como contrato de referencia al implementar el Service Layer de Fase 3 (especialmente `InscripcionService` y las validaciones RBAC/`@PreAuthorize` de `AdminService`).

---

**Generado:** 2026-08-04
**Por:** Claude Code + skill `documentar-avance` + skill `check-rules`
**Próxima actualización:** Post-revisión y promoción de diagramas a `docs/diagrams/`
