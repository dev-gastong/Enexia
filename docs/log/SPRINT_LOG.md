# 📖 Registro Maestro de Sprints — Enexia

Este archivo indexa el progreso por Sprints. El detalle diario vive en subcarpetas `docs/log/sprint_N/`.

---

## Sprint 1 — Backend: Autenticación MVP + Modelo de Datos

**Estado:** 🟡 En progreso
**Inicio:** 2026-07-25

### 🎯 Objetivos del Sprint
- Definir arquitectura backend (Controller → Service → DTO → Repository).
- **Codificar el modelo de datos completo (entidades JPA) según DER/MER.** ✅
- Implementar registro y login con seguridad (rate limiting, bloqueo, JWT, BCrypt).
- Testing con JUnit 5 + Mockito.

### ✅ Entregables Completados
| Fecha | Entregable | Detalle |
|-------|-----------|---------|
| 2026-07-26 | **Modelo de datos JPA** | 37 entidades + 2 clases `@Embeddable` de clave compuesta, mapeadas 1:1 con el MER. Ver [sprint_1/2026-07-26_modelo_datos.md](./sprint_1/2026-07-26_modelo_datos.md) |
| 2026-07-26 | **Base de datos `enexia`** | Creada en MariaDB (XAMPP). 37 tablas generadas por Hibernate con todas las FK. |
| 2026-07-31 | **Repositories + DTOs (esqueleto Fase 2)** | 37 `JpaRepository` + 10 DTOs Request/Response. Ver [REGISTRO_ACADEMICO_FASE2_REPOSITORIES_DTOs.md](./REGISTRO_ACADEMICO_FASE2_REPOSITORIES_DTOs.md) |
| 2026-08-04 | **DFD Nivel 1 y 2 — Módulos 3 a 8** | 26 diagramas Mermaid (Participación, Interfaz Pública, Moderación, Admin, Perfiles Organización, Membresías) en `docs/tempDFD/`, validados con `check-rules`. Ver [REGISTRO_ACADEMICO_DFD_MODULOS_3-8.md](./REGISTRO_ACADEMICO_DFD_MODULOS_3-8.md) |

### 💡 Decisiones de Arquitectura (ADR)
- **ADR-01 — PKs numéricas como `Long`**: El MER indica `int`, pero se usa `Long` con `@GeneratedValue(IDENTITY)` (práctica estándar Java/JPA y consistente con el ejemplo de `CLAUDE.md`).
- **ADR-02 — Estados como entidades relacionadas, NO enums**: El MER normaliza los estados en tablas propias (`usuario_estado`, `evento_estado_sistema`, etc.) con FK. Se respetó el MER en lugar del enum sugerido en el borrador de `CLAUDE.md`.
- **ADR-03 — Relaciones 1:1 con PK compartida vía `@MapsId`**: `persona_fisica`→`persona` y `evento_detalle`→`evento` comparten PK/FK exactamente como el MER (`id_persona PK, FK`).
- **ADR-04 — Tablas de unión como entidades con `@EmbeddedId`**: `usuario_rol` y `miembros_organizacion` usan clave compuesta (esta última con atributo extra `rol_en_empresa`), fiel al MER.
- **ADR-05 — `Pago` con FK opcionales `@OneToOne`**: Las relaciones `Inscripcion ||--o| Pago` y `Suscripcion ||--o| Pago` (1:0..1) se modelan como `@OneToOne` en el lado `Pago`.
- **ADR-06 — Solo lado propietario (`@ManyToOne`)**: Cada relación se mapea una vez, en el lado que posee la FK. No se agregaron colecciones inversas `@OneToMany` (se añadirán cuando un caso de uso las requiera).
- **ADR-07 — `spring.jpa.hibernate.ddl-auto=update`** en desarrollo (genera/actualiza tablas sin borrar datos). Cambiar a `validate` en producción.

### ⚠️ Bloqueos / Lecciones Aprendidas
- **Servidor MySQL apagado**: El servicio MariaDB de XAMPP no estaba corriendo; el usuario lo inició manualmente. Root sin contraseña (default XAMPP).
- **Normalización de nombres camelCase**: El MER escribió `emailCorporativo` y `telefonoContacto` en camelCase (inconsistente con el resto). La estrategia de nombres físicos de Hibernate los normalizó a `email_corporativo` / `telefono_contacto`, quedando consistentes con las otras 36 tablas. **Desviación menor documentada.**
- **✅ Gap de login RESUELTO (opción A)**: El MER de `Usuario` no incluía los campos de control del login. Decisión del usuario: **actualizar primero la documentación**. Se agregaron al MER (`docs/diseño_bd/MER.md`) y a la entidad `Usuario`: `intentos_fallidos` (int), `requiere_captcha` (boolean), `fecha_desbloqueo_cooldown` (datetime). `fecha_registro` NO se agregó a `Usuario` porque ya existe en `Persona` (normalizado). Columnas verificadas en la BD tras `bootRun`.

### ⏭️ Próximos Pasos (Backlog)
1. ✅ ~~Decidir sobre los campos de seguridad faltantes en `Usuario`~~ — Resuelto (opción A: MER + entidad actualizados).
2. Crear capa `repository/` (interfaces `JpaRepository`) para las entidades core de auth.
3. Fase 2: Registro de usuarios (DTOs, validaciones, BCrypt, moderación `better-profanity`).
4. Fase 3: Login seguro (rate limiting, bloqueo, JWT, auditoría).
5. Fase 4: Testing (JUnit 5 + Mockito).

---
