# 📅 Línea de Tiempo del Proyecto Enexia

## Fase 1: Documentación y Especificación

### 2026-07-25 - Sprint 1 Planning Session
* **10:00 - 🚀 [Inicio / Planificación]**: Sesión de planificación con revisión completa de documentación.
  - *Archivos revisados*: `docs/README.md`, `docs/diseño_bd/DER.md`, `docs/diseño_bd/MER.md`, `docs/requisitos/**/*.md`, `CLAUDE.md`
  - *Decisiones clave*:
    - ✅ Arquitectura confirmada: Controller → Service → DTO → Repository + Config/Anotaciones/Excepciones/Utilidades/Logs
    - ✅ Sprint 1 scope: Backend Autenticación MVP (sin 2FA, sin password reset en primera iteración)
    - ✅ Medidas de seguridad incluidas: Rate limiting por IP, bloqueo en 3 intentos, cooldown de 5 minutos
    - ✅ Integraciones: `better-profanity` (moderación texto), Mailtrap/Gmail App Password (email), validación de formato CUIT
    - ✅ Testing: JUnit 5 + Mockito desde el inicio
    - ✅ Frontend: Después de completar backend, HTML vanilla sin frameworks
  - *Impacto*: Definición clara del MVP y estructura de sprints para 5 entregas.

### 2026-01-03 a 2026-07-25 - Documentación Base (Historial previo)
* **📄 [Doc]**: Creación de especificaciones funcionales (8 módulos).
  - *Archivos*: `docs/requisitos/requisitos_funcionales/modulo_1.md` a `modulo_8.md`
  - *Impacto*: Base de requisitos completa y detallada.

* **🗄️ [Base de Datos]**: Diseño del modelo entidad-relación.
  - *Archivos*: `docs/diseño_bd/DER.md`, `docs/diseño_bd/MER.md`
  - *Impacto*: 20+ entidades con relaciones normalizadas.

* **📄 [Doc]**: Diagramas de flujo de datos (DFD Nivel 0, Nivel 1, Procesos específicos).
  - *Archivos*: `docs/diagrams/DFD LVL 0.md`, `docs/diagrams/DFD LVL 1.md`, diagramas de login/registro
  - *Impacto*: Claridad en flujos de procesos críticos (Login, Registro, Gestión de Eventos).

* **🚀 [Setup]**: Configuración inicial del repositorio.
  - *Archivos*: `CLAUDE.md`, `CLAUDE_es-ES.md`, `.gitignore`, documentación de arquitectura
  - *Impacto*: Guía para desarrolladores y estándares de proyecto.

* **⚙️ [Backend]**: Creación de estructura base de Spring Boot.
  - *Archivos*: `backend/build.gradle`, `backend/src/main/resources/application.yml`
  - *Impacto*: Proyecto Gradle listo para desarrollo.

---

## Fase 2: Sprint 1 - Backend Autenticación MVP (En Progreso)

### 2026-07-25 - Sprint 1 Kick-off
* **14:00 - 🎯 [Planificación]**: Definición oficial del Sprint 1 con 4 fases.
  - *Fases confirmadas*:
    - **Fase 1**: Setup Infraestructura (1-2 días)
    - **Fase 2**: Registro de Usuarios (2-3 días)
    - **Fase 3**: Login Seguro + Rate Limiting (2-3 días)
    - **Fase 4**: Testing + Auditoría (1-2 días)
  - *Impacto*: Hoja de ruta clara para las próximas 1-2 semanas.

---

## 📊 Resumen de Actividad

| Categoría | Cantidad |
|-----------|----------|
| 📄 Documentos Especificación | 8 módulos funcionales |
| 🗄️ Entidades de BD | 20+ (Persona, Usuario, Evento, Inscripción, etc.) |
| 📊 Diagramas (DFD) | 7 (Nivel 0, Nivel 1, Login, Registro, 3x Eventos) |
| ⚙️ Módulos Backend | 1 (en progreso: Autenticación) |
| 🎨 Módulos Frontend | 0 (después de Sprint 1) |

---

## 🔮 Próximos Pasos (Roadmap)

### Sprint 1 (Actual)
- [ ] Fase 1: Setup Infraestructura (Dependencias, estructura de carpetas, MySQL)
- [ ] Fase 2: Registro de Usuarios (DTO, validaciones, BCrypt, moderación)
- [ ] Fase 3: Login Seguro (JWT, rate limiting, bloqueo, auditoría)
- [ ] Fase 4: Testing (Unit + Integration tests)

### Sprint 2
- [ ] Módulo 2: Gestión de Eventos (CRUD, cronogramas, tickets)
- [ ] Módulo 5: Moderación de Contenido (integración de APIs)

### Sprint 3
- [ ] Módulo 3: Participación (Inscripciones, valoraciones)
- [ ] Módulo 4: Interfaz Pública (Catálogo, búsqueda)

### Sprint 4
- [ ] Módulo 6: Panel de Administración Global

### Sprint 5
- [ ] Módulos 7 + 8: Perfiles Jurídicos y Membresías

### Post-MVP
- [ ] Frontend (HTML vanilla, responsive)
- [ ] Despliegue en VPS con hardening de seguridad
- [ ] 2FA, Password Reset, CAPTCHA

---

**Última actualización**: 2026-07-25 14:30
**Estado**: Sprint 1 Kick-off ✅
