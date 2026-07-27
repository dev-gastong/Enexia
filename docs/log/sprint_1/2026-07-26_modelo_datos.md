# Sprint 1 · Día 2026-07-26 — Codificación del Modelo de Datos (Entidades JPA)

**Duración:** sesión única
**Responsables:** Usuario (dev) + Claude Code
**Condición de cierre:** ✅ Compilación exitosa + 37 tablas generadas y verificadas en MariaDB.

---

## 🎯 Tarea
Codificar las entidades, atributos y relaciones del modelo de datos (DER/MER en `docs/diseño_bd/`) usando anotaciones de persistencia JPA, y crear la base de datos (`CREATE DATABASE`).

## ✅ Qué se realizó

### 1. Base de datos
```sql
CREATE DATABASE IF NOT EXISTS enexia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
- Motor: **MariaDB 10.4.32** (XAMPP), puerto 3306, usuario `root` sin contraseña.

### 2. Entidades JPA (37 entidades + 2 clases de ID compuesto)
Ubicación: `enexia/src/main/java/com/enexia/rg/model/`

**Catálogo / Lookup (14):** `Pais`, `Provincia`, `Ciudad`, `Ubicacion`, `Rol`, `Categoria`, `TipoTicket`, `UsuarioEstadoSistema`, `UsuarioEstado`, `EventoEstadoSistema`, `EventoEstadoOrganizador`, `InscripcionEstado`, `PagoEstado`, `SuscripcionEstado`.

**Persona / Usuario (9):** `Persona`, `PersonaFisica` (`@MapsId`), `PersonaJuridica`, `Usuario`, `UsuarioRolId` + `UsuarioRol` (`@EmbeddedId`), `MiembrosOrganizacionId` + `MiembrosOrganizacion` (`@EmbeddedId`), `HistorialEstadoUsuario`.

**Evento (6):** `Evento`, `EventoDetalle` (`@MapsId`), `EventoCronograma`, `EventoMultimedia`, `HistorialEstadoEvento`, `CronogramaTicket`.

**Participación / Pagos / Suscripción / Auditoría (10):** `Inscripcion`, `HistorialEstadoInscripcion`, `Valoracion`, `Visita`, `PasswordResetToken`, `Suscripcion`, `HistorialEstadoSuscripcion`, `Pago`, `HistorialEstadoPago`, `HistorialInteracciones`.

### 3. Configuración
`application.properties`: datasource MySQL, `ddl-auto=update`, `show-sql=true`, `open-in-view=false`.

## 🔧 Cómo se verificó
1. `./gradlew.bat compileJava` → **BUILD SUCCESSFUL**.
2. `./gradlew.bat bootRun` → **Started EnexiaApplication** (Hibernate ejecutó el DDL sin errores).
3. Verificación en BD:
   - `SHOW TABLES` → **37 tablas**.
   - PK compuesta confirmada en `usuario_rol` (id_usuario+id_rol) y `miembros_organizacion` (id_usuario+id_persona_juridica).
   - PK=FK compartida confirmada en `persona_fisica` (→persona) y `evento_detalle` (→evento).
   - FKs de `usuario` → `persona_fisica`, `usuario_estado`, `usuario_estado_sistema` correctas.

## 🗂️ Mapa de tipos (MER → Java)
| MER | Java |
|-----|------|
| `int` PK | `Long` + `@GeneratedValue(IDENTITY)` |
| `int` FK/atributo | `Long` / `Integer` |
| `varchar` | `String` |
| `text` | `String` + `@Column(columnDefinition="TEXT")` |
| `decimal` | `BigDecimal` |
| `date` | `LocalDate` |
| `time` | `LocalTime` |
| `datetime` / `timestamp` | `LocalDateTime` |
| `id_pais` (varchar PK) | `String` (sin `@GeneratedValue`) |

## ⚠️ Desviaciones / Notas
- `emailCorporativo`/`telefonoContacto` (camelCase en MER) → normalizados a `email_corporativo`/`telefono_contacto` por la estrategia de nombres de Hibernate. Consistente con las demás tablas.
- `Usuario` NO tiene `intentos_fallidos`, `fecha_desbloqueo_cooldown`, `requiere_captcha`, `fecha_registro` (no están en el MER). **Pendiente decidir** antes de la Fase 3 (Login).

## Estructura de directorios actual (backend)
```
enexia/src/main/java/com/enexia/rg/
├── EnexiaApplication.java
└── model/                      # 37 entidades + 2 @Embeddable (ESTE DÍA)
enexia/src/main/resources/
└── application.properties      # datasource + JPA (ESTE DÍA)
```
