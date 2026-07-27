# 📅 Línea de Tiempo del Proyecto Enexia

## Sesión: 2026-07-26 (Sprint 1 · Fase 1 — Codificación del Modelo de Datos)

### 🗄️ Modelo de Datos JPA + Base de Datos
* **Tarea:** Codificar entidades, atributos y relaciones del DER/MER con anotaciones JPA, y crear la BD.
* **Base de datos:** `CREATE DATABASE enexia` en MariaDB 10.4.32 (XAMPP, root sin password). El usuario inició el servidor MySQL manualmente.
* **Entidades creadas:** 37 entidades `@Entity` + 2 `@Embeddable` de clave compuesta, en `enexia/src/main/java/com/enexia/rg/model/`.
  - 1:1 con PK compartida (`@MapsId`): `PersonaFisica`→`Persona`, `EventoDetalle`→`Evento`.
  - Tablas de unión con `@EmbeddedId`: `UsuarioRol`, `MiembrosOrganizacion` (con `rol_en_empresa`).
  - `Pago` con FK opcionales `@OneToOne` (`Inscripcion`/`Suscripcion`, 1:0..1).
  - `Pais` con PK `String` (varchar) sin `@GeneratedValue`.
* **Verificación:**
  - `./gradlew compileJava` → BUILD SUCCESSFUL.
  - `./gradlew bootRun` → arranque OK, Hibernate generó DDL sin errores.
  - `SHOW TABLES` → **37 tablas** con todas las FK; PKs compuestas y compartidas confirmadas.
* **Config:** `application.properties` con datasource MySQL + `ddl-auto=update`, `show-sql=true`, `open-in-view=false`.
* **Decisiones clave:** PKs numéricas como `Long`; estados como entidades relacionadas (no enums) fiel al MER; solo lado propietario `@ManyToOne` (sin colecciones inversas por ahora).
* **⚠️ Gap flagged:** `Usuario` no tiene los campos de seguridad del login (`intentos_fallidos`, `fecha_desbloqueo_cooldown`, `requiere_captcha`, `fecha_registro`) porque no están en el MER. **Pendiente decidir** antes de Fase 3 (Login).
* **Auditoría check-rules:** Sin violaciones en capa `model/` (reglas de DTO/RBAC/moderación/concurrencia aplican a Controller/Service, aún no existentes). Entidades soportan borrado lógico (`fecha_baja`), cupos (`cupo_actual`/`cupo_maximo`) y estados de moderación (FK).
* **Logs generados:** `docs/log/SPRINT_LOG.md` (maestro) y `docs/log/sprint_1/2026-07-26_modelo_datos.md` (detalle del día).

### 🔒 Resolución del gap de seguridad de `Usuario` (opción A)
* **Decisión del usuario:** actualizar primero la documentación (fuente de verdad) y luego el código.
* **MER actualizado** (`docs/diseño_bd/MER.md`): se agregaron a `Usuario` los campos `intentos_fallidos` (int), `requiere_captcha` (boolean) y `fecha_desbloqueo_cooldown` (datetime), con comentarios explicativos del RF-1.4 / DFD Login. `fecha_registro` NO se agregó (ya existe en `Persona`). El DER (`graph TD` conceptual) no lista atributos → sin cambios.
* **Entidad `Usuario.java` actualizada** con los 3 campos (`Integer`, `Boolean`, `LocalDateTime`).
* **Verificación:** `compileJava` OK; `bootRun` ejecutó `ALTER TABLE usuario`; `DESCRIBE usuario` confirma las 3 columnas nuevas.

---

## Sesión: 2026-07-25 (Sprint 1 Planning & Decisiones Arquitectónicas)

### 🎯 Sprint 1 Kick-off Meeting
* **14:00 - Revisión exhaustiva de documentación existente**
  - *Archivos revisados*: Toda la carpeta `docs/` (requisitos, diseño BD, diagramas)
  - *Impacto*: Comprensión completa de especificaciones funcionales y modelo de datos

* **14:30 - Definición de Arquitectura Backend (Confirmada)**
  - *Estructura*: Controller → Service → DTO → Repository + Config/Anotaciones/Excepciones/Utils/Logs
  - *Directorio*: `backend/src/main/java/com/enexia/` con carpetas específicas
  - *Impacto*: Hoja de ruta clara para estructura de código Spring Boot

* **15:00 - Sprint 1 Scope Definition (MVP Autenticación)**
  - *Sprint 1 includes*:
    - ✅ Registro de Usuarios (Persona Física)
    - ✅ Login con JWT
    - ✅ Rate Limiting por IP (auditoría en tabla)
    - ✅ Bloqueo en 3 intentos + Cooldown 5 min
    - ✅ Moderación de texto (better-profanity)
    - ✅ BCrypt + RBAC en backend
    - ✅ JUnit 5 + Mockito testing (80%+ cobertura)
  - *Sprint 1 excludes*:
    - ❌ 2FA, CAPTCHA, Password Reset
    - ❌ Persona Jurídica / CUIT validation
    - ❌ Frontend (backend solo)
  - *Impacto*: Claridad total sobre MVP vs. iteraciones futuras

* **15:30 - Decisiones de Integraciones Externas**
  - **Moderación de Texto**: `better-profanity` (librería Java, free, offline)
  - **Email**: Mailtrap (10k/mes free) O Gmail App Password
  - **Validación CUIT**: Solo formato inicialmente (AFIP API para Sprint 2+)
  - **Almacenamiento**: No required en Sprint 1 (para Sprint 2+ events)
  - *Impacto*: Stack de dependencias exacto definido

* **16:00 - Base de Datos Setup**
  - *Creación*: `CREATE DATABASE enexia CHARACTER SET utf8mb4`
  - *Auto-create tables*: Spring JPA/Hibernate desde @Entity
  - *Tablas prioritarias Sprint 1*: Persona_Fisica, Usuario, Usuario_Rol, Rol, Historial_Interacciones
  - *Impacto*: MySQL ready, no scripts SQL manuales necesarios

* **16:30 - Testing Strategy Defined**
  - *Framework*: JUnit 5 + Mockito
  - *Cobertura target*: 80%+ (critical paths)
  - *Test cases*: Success + error paths (registration, login, blocking, cooldown)
  - *Impacto*: Quality assurance clara desde día 1

* **17:00 - Documentation & Git Workflow**
  - Actualización de `CLAUDE.md` con arquitectura decidida
  - Creación de `CLAUDE_es-ES.md` (sincronización)
  - Nuevo archivo: `project-timeline.md` (línea de tiempo del proyecto)
  - Actualización: `historial.md` (este documento)
  - *Impacto*: Documentación actualizada, decisiones registradas

### 📋 Decisiones Formalizadas

| Aspecto | Decisión |
|---------|----------|
| **Arquitectura Backend** | Controller→Service→DTO→Repository+Config |
| **Sprint 1 Focus** | Backend autenticación MVP (no frontend) |
| **Seguridad MVP** | Rate limiting + bloqueo 3 intentos + cooldown |
| **Moderación Texto** | better-profanity (libre, offline) |
| **Email** | Mailtrap o Gmail App Password (gratuita) |
| **BD** | MySQL + JPA auto-create |
| **Testing** | JUnit 5 + Mockito, 80%+ cobertura |
| **Frontend** | Después Sprint 1, HTML vanilla sin frameworks |
| **VPS/Hardening** | Después Sprint 5 completo |

---

## Sesión: 2026-07-20 (Análisis y Configuración Inicial)

### 🚀 Inicio / Setup
* **14:00 - Exploración del proyecto y stack tecnológico**
  - *Archivos clave involucrados*: `README.md`, `CLAUDE.md`, documentación en `docs/`
  - *Impacto*: Identificación de 8 módulos funcionales y arquitectura completa del proyecto

* **14:30 - Creación de CLAUDE.md (v1)**
  - *Archivos clave involucrados*: `CLAUDE.md`
  - *Impacto*: Documento central que guía futuras instancias de Claude Code en el proyecto

* **15:00 - Ejecución del comando `/init`**
  - *Archivos clave involucrados*: Análisis de `README.md`, `docs/requisitos/`, `docs/diseño_bd/`
  - *Impacto*: Análisis de arquitectura e identificación de tecnologías disponibles

### 🛠️ Decisiones Tecnológicas
* **15:30 - Especificación de stack frontend**
  - Frontend: HTML5, CSS3, JavaScript Vanilla (SIN frameworks)
  - Arquitectura: Multi-página (NO SPA)
  - RBAC: Páginas separadas por rol (participant, organizer, admin)
  - *Impacto*: Actualización de CLAUDE.md con stack correcto

* **16:00 - Especificación de build tool backend**
  - Backend: Gradle (no Maven)
  - *Impacto*: Actualización de comandos y configuración en CLAUDE.md

### 📄 Documentación y Memoria
* **16:15 - Configuración de memoria del proyecto**
  - *Archivos clave involucrados*: `memory/github_permissions.md`, `memory/tech_stack.md`, `memory/MEMORY.md`
  - *Impacto*: Sistema de memoria persistente entre sesiones configurado

* **16:45 - Investigación exhaustiva de Playwright**
  - *Archivos clave involucrados*: `memory/playwright_research.md`
  - *Impacto*: Documentación completa de Playwright (arquitectura, APIs, MCP integration potential)
  - *Resultado*: Playwright calificado como "altamente viable" para integración MCP

* **17:00 - Traducción de documentos al español**
  - *Archivos actualizados*:
    - `github_permissions.md` → `permisos_github`
    - `tech_stack.md` → `stack_tecnologico`
    - `playwright_research.md` → `investigacion_playwright`
  - *Impacto*: Todo historial y logs redactados en español (convención establecida en CLAUDE.md)

### ⚙️ Backend - Spring Boot
* **17:30 - Generación de proyecto Spring Boot**
  - *Archivos clave involucrados*: 
    - `enexia/build.gradle` (Gradle configuration)
    - `enexia/src/main/java/com/enexia/rg/EnexiaApplication.java`
    - `enexia/src/main/resources/application.properties`
  - *Dependencias agregadas*:
    - Spring Boot 4.1.0
    - Spring Data JPA
    - Spring Security
    - Validation
    - Lombok
    - MySQL Connector
    - JWT (io.jsonwebtoken 0.12.3)
    - Cloudinary (1.33.0)
  - *Impacto*: Backend listo con todas las dependencias necesarias para desarrollo

### 🎨 Frontend - Análisis de Diseño
* **18:00 - Conexión con Figma y descarga de diseños**
  - *Archivos descargados*:
    - Catálogo de eventos (1440x2045px)
    - Detalle de evento (1466x1871px)
  - *Impacto*: Diseños en alta resolución para guiar la estructura HTML

* **18:30 - Análisis comparativo: Figma vs. Requisitos**
  - *Archivos clave involucrados*: `analisis/ANALISIS_FIGMA_VS_REQUISITOS.md`
  - *Resultados principales*:
    - ✅ Cobertura de requisitos: 97% (33 de 34 cubiertos)
    - 📊 47 pantallas identificadas en total
    - ✓ 8/8 módulos funcionales tienen soporte en diseño
    - 🚨 5 gaps críticos identificados (moderation queue, error states, upsell pro)
  - *Impacto*: Hoja de ruta clara para fase de desarrollo frontend

---

## Sesión Anterior: Configuración de Documentación (antes de 2026-07-20)

### 📄 Documentación Base
* **Commits relevantes**: 
  - `3dbde49` - Update DER documentation with new entity descriptions
  - `f44ea16` - Update relationships in MER.md for Ubicacion
  - `240217b` - Enhance database schema with state tracking
  - `9f4dbb7` - Create DFD LVL 1.md
  - `fa45430` - Create DFD LVL 0.md

* **Impacto**: 8 módulos funcionales documentados, esquema de BD completo (MER/DER), diagramas de flujo

---

## 📊 Resumen de Actividad - Sesión Actual

### 📈 Estadísticas
| Métrica | Cantidad |
|---------|----------|
| Commits nuevos | 2 |
| Archivos de memoria creados/actualizados | 4 |
| Análisis completados | 1 |
| Pantallas de Figma identificadas | 47 |
| Gaps críticos identificados | 5 |
| Requisitos cubiertos | 97% |

### 🎯 Módulos Completados Esta Sesión
- ✅ **Análisis de Arquitectura** - CLAUDE.md creado
- ✅ **Investigación de Tecnologías** - Stack definido (Gradle, Vanilla JS)
- ✅ **Backend Inicial** - Spring Boot + Gradle con todas las dependencias
- ✅ **Análisis de Diseño** - Figma mapeado contra requisitos
- ✅ **Sistema de Memoria** - Persistencia entre sesiones configurada
- ✅ **Convención de Idioma** - Todo log histórico en español

### 🔄 Estado Actual del Proyecto
```
[FASE 1: EXPLORACIÓN]             ✅ COMPLETADA
├─ Análisis de documentación existente
├─ Definición de stack tecnológico
└─ Investigación de herramientas (Playwright, Figma)

[FASE 2: PLANIFICACIÓN]           ✅ COMPLETADA
├─ Creación de CLAUDE.md
├─ Mapeo de requisitos vs. diseño
└─ Identificación de gaps

[FASE 3: CONFIGURACIÓN INICIAL]   ✅ COMPLETADA
├─ Generación de proyecto Spring Boot
├─ Descarga y análisis de diseños Figma
└─ Configuración de memoria del proyecto

[FASE 4: DESARROLLO FRONTEND]     ⏳ PRÓXIMA
├─ Generar estructura HTML vanilla
├─ Instalar y configurar Playwright
└─ Crear tests básicos

[FASE 5: DESARROLLO BACKEND]      ⏳ PRÓXIMA
├─ Crear entidades JPA (Persona, Usuario, Evento, etc.)
├─ Implementar controladores REST
└─ Configurar seguridad (JWT, BCrypt)
```

### 🚀 Próximos Pasos Lógicos - Sprint 1 (Backend Autenticación MVP)

**Fase 1: Setup Infraestructura (1-2 días)**
1. Crear estructura de carpetas: controller/, service/, dto/, repository/, model/, security/, exception/, config/, logger/
2. Configurar build.gradle con dependencias: Spring Web, JPA, MySQL, Lombok, JWT, BCrypt, better-profanity
3. Crear application.yml con datasource MySQL + JPA config
4. Inicializar SLF4J logging

**Fase 2: Registro de Usuarios (2-3 días)**
1. Crear DTOs: RegisterRequest, RegisterResponse
2. Implementar endpoint POST /api/auth/register
3. Validaciones: email único, password fuerte, DNI válido
4. Integrar better-profanity para moderación de texto (nombre, apellido, nickname)
5. Implementar BCrypt hashing
6. Persistencia: Persona_Fisica + Usuario + Usuario_Rol + Rol

**Fase 3: Login Seguro + Rate Limiting (2-3 días)**
1. Crear DTOs: LoginRequest, LoginResponse (incluye JWT token)
2. Implementar endpoint POST /api/auth/login
3. Rate Limiting: rastrear intentos fallidos por email en Historial_Interacciones
4. Bloqueo automático: después 3 intentos → estado = "BLOQUEADO"
5. Cooldown: 5 minutos de penalización (fecha_desbloqueo_cooldown)
6. Generar JWT con secret key y roles en claims
7. Auditoría: registrar cada intento en tabla

**Fase 4: Testing (1-2 días)**
1. AuthServiceTest: casos éxito + error (JUnit 5 + Mockito)
2. AuthControllerTest: integration tests de endpoints
3. Cobertura target: 80%+ en clases críticas

**Post-Sprint 1 (Futuro):**
1. Persona Jurídica + validación CUIT (Sprint 2)
2. 2FA, CAPTCHA, Password Reset (Sprint 2+)
3. Frontend HTML vanilla (después de Sprint 1)
4. VPS hardening (después Sprint 5)

---

## 📌 Notas Importantes

- **Rama:** `main` (directo a main, no feature branches aún)
- **Remoto:** `origin` → `https://github.com/dev-gastong/Enexia.git`
- **Convención:** Todo log histórico (docs, análisis, reportes) en **español**
- **Código:** Java, JavaScript pueden estar en inglés o español
- **Testing:** Playwright configurado (pending setup) para E2E testing con accesibilidad trees

---

**Última actualización:** 2026-07-20 18:45  
**Por:** Claude Code  

