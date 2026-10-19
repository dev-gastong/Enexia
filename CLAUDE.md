# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 📝 Important Note on Documentation Language

⚠️ **KEEP THIS FILE IN ENGLISH** — It's Claude's reference guide.

📄 **Spanish version available:** `CLAUDE_es-ES.md` (for the user to read)

**Sync instruction:** Whenever you update `CLAUDE.md`, also update `CLAUDE_es-ES.md` to keep them in sync. The content structure should be identical; only the language differs.

---

## Project Overview

**Enexia** is a web platform for centralized management and dissemination of cultural, educational, social, sports, and gaming events in Tierra del Fuego. It's a full-stack MVP project with a focus on user experience, security, and scalability.

### High-Level Architecture

```
┌────────────────────────────────────────────────────────┐
│          Frontend (HTML, CSS, JS Vanilla)              │
│  Multiple HTML Pages (role-based separation)           │
│  - pages/auth/* (login, register, password reset)      │
│  - pages/participant/* (catalog, registrations)        │
│  - pages/organizer/* (event management)                │
│  - pages/admin/* (user/event management)               │
└────────────────────┬─────────────────────────────────┘
                     │ REST API / JSON (Fetch)
                     ↓
┌────────────────────────────────────────────────────────┐
│            Backend (Spring Boot 3.x+)                  │
│  ┌──────────────────────────────────────────┐          │
│  │  Controller Layer                        │          │
│  │  (REST endpoints, JWT validation, RBAC)  │          │
│  └──────────────────────────────────────────┘          │
│  ┌──────────────────────────────────────────┐          │
│  │  Service Layer                           │          │
│  │  (Business logic, content moderation)    │          │
│  └──────────────────────────────────────────┘          │
│  ┌──────────────────────────────────────────┐          │
│  │  Repository Layer                        │          │
│  │  (Data access, JPA/Hibernate ORM)        │          │
│  └──────────────────────────────────────────┘          │
└────────────────────┬─────────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        ↓            ↓            ↓
      MySQL    Cloudinary     Email Service
       (BD)     (Images)       (Notifications)
```

### Role Assignment Strategy & Participation Model

**Sprint 1: Personas Físicas Only**
- **PARTICIPANTE** (Persona Física): Receives **PARTICIPANTE role only** — can browse, register, and participate in events.
- **ORGANIZADOR** (Persona Física): Receives **ORGANIZADOR + PARTICIPANTE roles** — can create/manage events AND participate in others' events.
  - **Rationale:** Avoids forcing organizers to create separate accounts for participation. Keeps the system lightweight for MVP.
- **ADMINISTRADOR** (2026-10-15): an administrator account is **admin-only** — it can never hold PARTICIPANTE or ORGANIZADOR. It cannot register for events, rate them, or organize. Enforced in one place, `Usuario.getRoles()`, which collapses the roles to `["ADMINISTRADOR"]` (so the JWT and Spring authorities never carry the others), plus `JwtAuthenticationFilter` for tokens issued before the rule. Don't re-add per-endpoint checks; if a role-granting flow is ever built (RF-6.2+), it must also refuse to combine them.

**Sprint 2: Personas Jurídicas as Administrative Containers**
- **Persona Jurídica** (Organization/Company): Created by an ORGANIZADOR Persona Física; not a login entity.
  - **Key principle:** Companies do not participate in events. Participation is always by Personas Físicas (people).
  - Members: Registered via `Miembros_Organizacion` table, linking Personas Físicas as organization members/admins.
  - Event authorship: Events created "under" an organization are attributed to the company name, but organized/managed by Persona Física members.
  - **Rationale:** Ensures conceptual coherence — all actual event participants are individual people (Personas Físicas), never abstract entities. This models real-world behavior: a company doesn't attend a concert; its employees do.

---

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Backend** | Spring Boot | 3.x+ |
| **Language (Backend)** | Java | 17+ (OpenJDK Temurin) |
| **Build Tool** | Gradle | Latest |
| **Frontend** | HTML5, CSS3, JavaScript Vanilla | ES6+ |
| **Database** | MySQL | TBD (version pending) |
| **Auth** | JWT (JSON Web Tokens) | - |
| **Password Hashing** | BCrypt | - |
| **Image Storage** | Cloudinary | 3rd-party integration |

---

## Core Entities & Domain Model

The system revolves around these key entities (see `docs/diseño_bd/MER.md` for full ERD):

- **Persona**: Identity of a HUMAN person only. Supertype of `Persona_Fisica` and the only identity that can own a `Usuario` (login).
  - **Sprint 2 change (2026-09-08):** the `tipo_persona` column was **removed**. It implied a `Persona -> (Fisica | Juridica)` hierarchy that never existed — the MER only declares `Persona ||--|| Persona_Fisica`, and `Persona_Juridica` has no FK to `Persona`. The column held `"FISICA"` in 100% of rows, and allowing `"JURIDICA"` would have permitted a legal person with no physical person attached, breaking the invariant that every `Usuario` is a human being.
- **Persona_Juridica**: Administrative container, **not a login identity**. Linked to people through `Miembros_Organizacion` (RF-7.2).
- **Usuario**: User account with roles (Participante, Organizador, Administrador). **Design note:** Organizadores receive both ORGANIZADOR + PARTICIPANTE roles to create events AND participate in others' events; only Personas Físicas can register directly. Personas Jurídicas manage participants through Miembros_Organizacion (Sprint 2).
- **Evento**: Event created by organizers with state tracking
- **Evento_Cronograma**: Multiple dates/times for a single event
- **Cronograma_Ticket**: Ticket types, pricing, and quota management per date
- **Inscripcion**: Participant registrations with quota control
- **Valoracion**: Ratings and reviews by participants
- **Visita**: View tracking for analytics
- **Ubicacion**: Geographic location data
- **Categoria**: Event categories

---

## Functional Modules (8 Modules)

Refer to `docs/requisitos/requisitos_funcionales/` for detailed specs:

1. **Módulo 1**: User Management & Authentication (JWT, roles, account states)
2. **Módulo 2**: Event Management (CRUD, multimedia, scheduling, quotas)
3. **Módulo 3**: Participant Dashboard & Catalog
4. **Módulo 4**: Ticket System & Registrations
5. **Módulo 5**: Ratings & Reviews
6. **Módulo 6**: Admin Panel
7. **Módulo 7**: Notifications & Email
8. **Módulo 8**: Analytics & Reporting

---

## Non-Functional Requirements

- **Response Times**: Max 2s for reads (catalog), 4s for writes (event creation)
- **Concurrency**: Support multiple simultaneous users
- **Security**: BCrypt password hashing, JWT auth, RBAC, content moderation
- **Availability**: Error handling with user-friendly messages + server-side logging
- **Usability**: "Rule of 3 clicks" for primary actions, progressive disclosure
- **Architecture**: Layered pattern (Controller → Service → Repository) for maintainability
- **Browsers**: Chrome, Firefox, Safari, Edge (responsive design for desktop & mobile)

---

## Setup & Commands

### Backend (Spring Boot + Gradle)

```bash
# Navigate to backend directory (when created)
cd backend/

# Build the project (Gradle)
gradle build

# Run tests
gradle test

# Run a specific test
gradle test --tests ClassName

# Run the application (development)
gradle bootRun

# Check dependencies
gradle dependencies
```

### Frontend (HTML, CSS, JavaScript Vanilla)

> ### ⚠️ NEVER start a separate static server for the frontend
> The frontend is **not** served from a standalone `http.server`/`http-server` process, and there is no `frontend/` directory — that was the Sprint 0 plan, superseded once the actual site landed in `enexia/src/main/resources/static/`. Spring Boot serves it directly as static resources, on the **same origin and the same port as the API** (`http://localhost:8080`), the moment `bootRun` is up.
>
> **Do not**, under any circumstance — not for a quick check, not "just to preview a page":
> - run `python -m http.server`, `http-server`, `live-server`, or any other static file server against `static/` or any subfolder of it;
> - open an HTML file directly from disk (`file://...`);
> - use the `preview_start` browser tool with a second dev-server config for the frontend.
>
> All of those put the page on a different origin than `http://localhost:8080`, and `SecurityConfig.corsConfigurationSource()` does not allow it — every `fetch()` in `api.js` fails silently with a CORS error, which looks exactly like "the backend is down" and wastes time chasing the wrong bug. The **only** correct way to view or test any page is to start the backend (`gradle bootRun` / `./gradlew bootRun`) and open `http://localhost:8080/<path>`, e.g. `http://localhost:8080/index.html` or `http://localhost:8080/pages/auth/login-desktop-claro.html`.

```bash
# Run the backend — this is also how you serve and preview the frontend.
# Static files under src/main/resources/static/ are exposed automatically
# at the same origin: http://localhost:8080/index.html,
# http://localhost:8080/pages/organizador/equipo.html, etc.
gradle bootRun

# No separate frontend command exists, and none should be added.
```

### Database

```bash
# When MySQL is set up, import the schema from the DER
# Schema scripts will be generated from docs/diseño_bd/DER.md
mysql -u root -p enexia < schema.sql
```

---

## Directory Structure (To Be Created)

```
enexia/
├── backend/
│   ├── src/
│   │   ├── main/java/com/enexia/
│   │   │   ├── controller/          # REST endpoints
│   │   │   ├── service/             # Business logic
│   │   │   ├── repository/          # Data access (JPA/Hibernate)
│   │   │   ├── model/               # Entity classes
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   ├── security/            # JWT, filters, auth logic
│   │   │   ├── exception/           # Custom exceptions
│   │   │   └── utils/               # Utilities (BCrypt, validators)
│   │   └── resources/
│   │       ├── application.yml      # Config
│   │       └── application-dev.yml  # Dev profile
│   └── pom.xml
├── frontend/
│   ├── index.html                   # Landing/home page
│   ├── pages/
│   │   ├── auth/
│   │   │   ├── login.html
│   │   │   ├── register.html
│   │   │   └── password-reset.html
│   │   ├── participant/
│   │   │   ├── dashboard.html       # Participant home
│   │   │   ├── event-catalog.html   # Browse events
│   │   │   ├── event-details.html   # View single event
│   │   │   ├── my-registrations.html # My inscriptions
│   │   │   └── profile.html
│   │   ├── organizer/
│   │   │   ├── dashboard.html       # Organizer home
│   │   │   ├── my-events.html       # List organizer's events
│   │   │   ├── create-event.html
│   │   │   ├── edit-event.html
│   │   │   └── event-stats.html
│   │   └── admin/
│   │       ├── dashboard.html       # Admin home
│   │       ├── manage-users.html
│   │       ├── manage-events.html
│   │       └── manage-categories.html
│   ├── css/
│   │   ├── styles.css               # Global styles
│   │   ├── responsive.css           # Mobile-first media queries
│   │   └── components.css           # Reusable component styles
│   ├── js/
│   │   ├── api.js                   # API client (fetch wrapper)
│   │   ├── auth.js                  # JWT token, login/logout, role check
│   │   ├── utils.js                 # Helper functions (formatDate, validate, etc.)
│   │   └── modules/
│   │       ├── events.js            # Event catalog, search, filter logic
│   │       ├── organizer.js         # Event creation, editing
│   │       ├── participant.js       # Registration, ratings
│   │       └── admin.js             # Admin-specific operations
│   └── assets/                      # Images, icons, logos
├── docs/                            # Existing documentation
│   ├── requisitos/
│   ├── diseño_bd/
│   └── diagrams/
└── CLAUDE.md                        # This file
```

---

## Backend Architecture (Spring Boot - Java)

### Directory Structure
```
backend/src/main/java/com/enexia/
├── config/              # @Configuration, JWT filters, CORS, security beans
├── controller/          # @RestController endpoints (REST API)
├── dto/                 # Request/Response DTOs (no entities exposed)
├── service/             # @Service business logic layer
├── repository/          # @Repository JPA (extends JpaRepository)
├── model/               # @Entity JPA classes (mapped to DB)
├── security/            # JWT utilities, BCrypt, RBAC logic
├── exception/           # Custom exceptions (BadCredentialsEx, etc.)
├── util/                # Validators, formatters, helpers (non-business logic)
└── logger/              # SLF4J logging via @Slf4j annotation
```

### Key Development Guidelines

#### Layered Architecture (Strict Separation)
- **Controller** (@RestController): HTTP endpoints, input validation, HTTP response mapping
- **Service** (@Service): Business logic, transactions, orchestration, security checks
- **DTO**: Request/Response objects (never expose entities directly)
- **Repository** (extends JpaRepository): CRUD + custom queries only
- **Model** (@Entity): JPA mappings only (no business logic)

#### DTO Pattern
- Create separate `*Request` and `*Response` DTOs for every endpoint
- Use `ModelMapper` or manual mapping to convert Entity ↔ DTO
- Never expose entities in API responses

#### Exception Handling
- Create custom exceptions extending `RuntimeException` (e.g., `BadCredentialsException`, `AccountBlockedException`)
- Use `@ControllerAdvice` with `@ExceptionHandler` to map exceptions to HTTP status codes
- Return consistent error JSON: `{ "error": "...", "timestamp": "...", "status": 400 }`

#### Security
- **Authentication**: JWT (JSON Web Tokens) issued on successful login with `roles[]` array (multi-role support)
- **Authorization**: Extract roles[] array from JWT; validate in `@PreAuthorize("hasAnyRole(...)")` on service methods or controller
- **Password**: BCrypt hashing, cost factor 12 (never plain text)

##### ⚠️ Login failure policy — REWRITTEN 2026-09-08 (user decision)

> **Login identifier changed 2026-09-30:** the username field is now **nickname**, not email. Email stays as the account's contact channel (recovery link, block notification) but is no longer what you type to sign in. See `UsuarioLoginRequest`, `AuthService.login`, `UsuarioRepository.buscarActivoPorNicknameConRoles`.

**1. IP-based rate limiting was REMOVED.**
Behind CGNAT or an institution's wifi, hundreds of legitimate devices share one public IP. Blocking that IP took a whole area offline because of a single attacker — a denial of service the attacker could trigger at will. `RateLimitService` and `RateLimitExcedidoException` were deleted.

> **Open risk, deliberately accepted:** with no per-IP control, *password spraying* (one common password against thousands of nicknames) has no dedicated brake, because no single account accumulates failures. **Resolved 2026-09-09 (ADR-13):** blocking on the 3rd failure per account, combined with a rejection that is indistinguishable in body, headers and timing — and a recovery endpoint that is equally opaque — denies the sprayer both the attempt budget and the enumeration signal it needs. Alerting on anomalous volume in `historial_interacciones`, **without** rejecting requests, remains a nice-to-have.

**2. Account blocking is now SILENT.**
Every login rejection answers identically: `401`, code `CREDENCIALES_INVALIDAS`, same message, no extra headers, and the same wall-clock cost. The attacker cannot tell "nickname doesn't exist" from "wrong password" from "account blocked" — not by body, status, headers, or timing.

- All auth failures extend `AutenticacionFallidaException`, which carries an **internal** code (`NICKNAME_INEXISTENTE`, `PASSWORD_INCORRECTA`, `CUENTA_BLOQUEADA`, `CUENTA_SUSPENDIDA`). That code goes to the log and `historial_interacciones` — **never** to the HTTP response.
- `GlobalExceptionHandler` has **one** handler for the whole family. **Do not add a more specific `@ExceptionHandler`** for any subclass: Spring would pick it and the response would start leaking account state again.
- Every rejection path pays one BCrypt comparison (decoy hash) so response time is constant.
- The `X-Reintentar-Despues` header was removed: read from DevTools, it confirmed the account exists and is penalized.
- **The legitimate owner is notified by email** with a single-use recovery link (`RecuperacionCuentaService`) — the one channel the attacker does not control.

**3. Blocking threshold — FINAL DECISION 2026-09-09: block on the 3rd failure.**
The former escalation ladder (3 → captcha + 5 min cooldown, 6 → 30 min cooldown, 9 → block) is gone, from both the spec and the code (aligned 2026-09-19). The rule is a single step, counted **per account** (`usuario.intentos_fallidos`), never per IP:

- 3 consecutive failures → `estado_usuario = BLOQUEADO` + security email to the owner
- Any successful login → counter back to 0

`Usuario` no longer has `requiere_captcha` / `fecha_desbloqueo_cooldown`: those fields only existed to support the ladder's intermediate steps and were removed along with it. `IntentosLoginService` now takes a single threshold (`enexia.security.login.intentos-bloqueo=3`).

This closes the RF-1.4 divergence: `docs/requisitos/requisitos_funcionales/modulo_1.md` and `docs/diagrams/login_registro/login.md` were both rewritten on 2026-09-09 and now agree, and the code matches them as of 2026-09-19. CAPTCHA and 2FA are out of scope and were removed from the login DFD.

> ### ✅ Decided 2026-10-19: unlocking an account is ADMIN-ONLY
> A `BLOQUEADO` account is reactivated **only** from the admin panel (`AdminUsuarioService`, action `REACTIVAR`, which also resets `intentos_fallidos`). The email link (`POST /api/auth/recuperacion/confirmar`) only sets a new password and **never touches the account state**; the security email says so. The old plan of a dedicated `/unlock-account?token=` endpoint was **discarded**: one door to unlock means one door to audit. Open follow-ups: no channel for a blocked owner to ask for the unlock, no email on reactivation (see `docs/SUGERENCIAS_PENDIENTES.md`, C1/C2).
- **Text Moderation**: `better-profanity` library for content filtering (Registro + Login + Events later)
- **Input Validation**: Use `@Valid` + `@NotNull`, `@Email`, `@Pattern` on DTOs

#### Logging
- Use **SLF4J** via `@Slf4j` (Lombok) on @Service/@Controller classes
- Log security events: login attempts, account blocks, invalid tokens
- Persist audit trail in `Historial_Interacciones` table (user_id, action, endpoint, IP, timestamp)

#### Database
- **JPA/Hibernate** for ORM (Spring Boot auto-creates tables via `@Entity`)
- Use `@ManyToOne`, `@OneToMany`, `@OneToOne` carefully (lazy-load preferred)
- Never use N+1 queries; use `@Query` with `JOIN FETCH` when needed
- Soft deletes: use `fecha_baja` or `estado` fields; never hard-delete

#### Testing
- **JUnit 5** for unit tests
- **Mockito** for mocking dependencies
- Test structure: `@DisplayName`, `@Test`, arrange-act-assert pattern
- Test both success cases and error cases (exceptions)
- For this sprint: test AuthService, login endpoint, rate limiting logic

### Frontend (HTML, CSS, JavaScript Vanilla)

- **ES6+ JavaScript**: Use modern JS (arrow functions, const/let, fetch API, async/await)
- **DOM Manipulation**: Use vanilla DOM methods (`querySelector`, `addEventListener`, `innerHTML`, etc.)
- **API Integration**: 
  - Create `js/api.js` with reusable fetch wrappers for HTTP requests
  - Always include JWT token in `Authorization: Bearer <token>` header
- **Authentication**: 
  - Store JWT in `sessionStorage` or `localStorage` (consider security implications)
  - Implement token validation and refresh logic in `js/auth.js`
  - JWT payload includes `roles[]` array (supports multiple roles per user)
- **Role-Based UI (RBAC)**:
  - Separate HTML pages per user role (e.g., `pages/organizer-dashboard.html`, `pages/participant-dashboard.html`, `pages/admin-dashboard.html`)
  - Backend validates roles; frontend serves role-specific pages
  - On login/redirect, JavaScript checks `roles[]` array from JWT and renders UI accordingly (navbar buttons, menu items)
  - Use helper functions: `hasRole(role)`, `hasAnyRole(...roles)` to check roles
  - Example: Participant clicks to view events → goes to `pages/event-catalog.html`; Organizer goes to `pages/organizer-dashboard.html`
- **Multi-Page Architecture** (Not SPA):
  - Each page has its own `.html` file (e.g., `pages/login.html`, `pages/event-details.html`)
  - Full page reloads when navigating between pages (no client-side routing)
  - Navigation via standard `<a>` tags or `window.location`
- **Responsive Design**: Mobile-first CSS with media queries (no CSS framework required)
- **Progressive Disclosure**: Use collapsible sections, expandable details, and modal dialogs
- **Error Handling**: Display user-friendly toast notifications or alert dialogs for API errors
- **State Management**: Use vanilla JS objects/classes or sessionStorage for simple state; no external library

---

## Sprint 2: Organizations, Events & Public Catalog (Current)

**Status (2026-10-19, project closed):** backend and frontend complete for M1–M7 and N; M8 (memberships) has its decisions and DFDs in `docs/tempDFD/` but is not programmed. Known gaps: the organization-registration screen and the "publish as organization" option of the event form are not wired to the (existing) backend; the ADMINISTRADOR role can't be granted from the app; `FinalizacionEventoScheduler` uses the JVM clock.

| Module | Scope delivered |
|---|---|
| **M1** — Auth | Silent account blocking, per-IP rate limiting removed, password reset by email (RF-1.5). **Unblocking is admin-only** (RF-6.2 *Reactivar*, 2026-10-19): the recovery link changes the password but never unlocks. |
| **M7** — Organizations | `Persona_Juridica` registration through both entry points, CUIT mod-11 validation (RF-7.3), `Miembros_Organizacion`. Alta gating removed 2026-09-09 (ADR-14) — an organization is `APROBADO`/`ACTIVO` and cleared to publish the instant its CUIT passes mod-11, both in the spec and in `PersonaJuridicaService.crearOrganizacion()`. |
| **M2** — Events | Creation with async moderation pipeline (RF-2.1 to RF-2.6), Cloudinary integration (RF-2.3), organizer dashboard (RF-2.8), logical delete (RF-2.9), statistics (RF-2.10) |
| **M4** — Public interface | Paginated catalog, text search, category/date/location filters, technical sheet, passive visit tracking (RF-4.1 to RF-4.5) |
| **M5** — Moderation | Text phase + image phase, sequential and asynchronous (RF-5.1 to RF-5.3) |
| **M6** — Admin panel | **Complete (2026-10-15).** RF-6.1 event moderation (reversible disciplinary suspension + 2nd-instance review), RF-6.2 account management (suspend/reactivate; admin accounts and one's own are protected), RF-6.3 category ABM (logical, reversible deactivation via `categoria.activa`, ADR-22), RF-6.4 subscription audit (`REVOCAR` / `CAMBIAR_PLAN` / `RENOVAR`, ADR-23/26/27). Four screens under `pages/admin/`: `moderacion-eventos`, `usuarios`, `categorias`, `suscripciones`. **Still open:** no way to grant the `ADMINISTRADOR` role from the app (only the SQL-promoted dev account). |
| **M8** — Memberships | **Only the admin side exists** (RF-6.4, read + exceptional intervention). **Decisions of 2026-10-19:** *basic subscription* (plans `GRATUITO` and `PRO`, monthly, simulated payment), **no plan limits the number of events** (the cap — `EventoService.verificarLimiteDePlan` and its properties — was **removed from the code**), and **statistics are per cronograma** (basic for everyone, advanced for Pro). Ratings were verified to be per cronograma (`UNIQUE(id_usuario, id_cronograma)`). **Not built:** the organizer side (upgrade/cancel/expiry job), the plan-resolution service, per-cronograma statistics and the Pro gate (403 `PLAN_PRO_REQUERIDO`). DFDs and task list: `docs/tempDFD/`. |

**Key endpoints**

```
POST   /api/auth/registro                    público   alta de Persona Física
POST   /api/auth/registro/organizacion       público   alta PF + organización (DFD 7.1/7.2)
POST   /api/auth/login                       público
POST   /api/auth/recuperacion                público   pide enlace (RF-1.5)
POST   /api/auth/recuperacion/confirmar      público   consume el enlace y desbloquea

POST   /api/organizador/organizaciones       ORGANIZADOR   alta de organización (RF-7.2)
GET    /api/organizador/organizaciones       ORGANIZADOR
GET    /api/organizador/organizaciones/{id}/miembros            ORGANIZADOR   "Mi Equipo": listar (cualquier miembro)
POST   /api/organizador/organizaciones/{id}/miembros            ORGANIZADOR   agregar miembro existente (solo ADMINISTRADOR)
DELETE /api/organizador/organizaciones/{id}/miembros/{idUsuario} ORGANIZADOR  quitar miembro (solo ADMINISTRADOR, nunca al último)
POST   /api/organizador/eventos              ORGANIZADOR   multipart: datos (JSON) + imagenes
GET    /api/organizador/eventos              ORGANIZADOR   dashboard paginado (RF-2.8)
DELETE /api/organizador/eventos/{id}         ORGANIZADOR   baja lógica (RF-2.9)
GET    /api/organizador/eventos/{id}/estadisticas         métricas (RF-2.10)

GET    /api/publico/eventos                  anónimo   catálogo + búsqueda + filtros
GET    /api/publico/eventos/{id}             anónimo   ficha técnica + registra visita
GET    /api/publico/categorias               anónimo
GET    /api/publico/provincias               anónimo
GET    /api/publico/provincias/{id}/ciudades anónimo

GET    /api/admin/eventos                    ADMINISTRADOR cola de moderación (todos los organizadores, RF-6.1)
PATCH  /api/admin/eventos/{id}                ADMINISTRADOR decisión: aprobar/ratificar/suspender/revertir (RF-6.1)
GET    /api/admin/usuarios[/resumen]          ADMINISTRADOR cuentas + contadores (RF-6.2)
PATCH  /api/admin/usuarios/{id}/estado        ADMINISTRADOR SUSPENDER / REACTIVAR (RF-6.2)
GET    /api/admin/categorias                  ADMINISTRADOR + POST (alta) / PUT /{id} (edición) / PATCH /{id}/estado (DESACTIVAR/ACTIVAR) (RF-6.3)
GET    /api/admin/suscripciones[/resumen]     ADMINISTRADOR organizadores con su plan vigente (RF-6.4)
PATCH  /api/admin/suscripciones/{id}          ADMINISTRADOR REVOCAR / CAMBIAR_PLAN / RENOVAR (RF-6.4)
```

**Event state machine** (two independent axes — the public catalog requires BOTH to allow):

```
estado_sistema (moderation)          estado_organizador (owner)
  EN_PROCESO                           PUBLICADO
    ├─→ APROBADO_SISTEMA   ← visible   CANCELADO
    ├─→ RECHAZADO_SISTEMA              DADO_DE_BAJA
    └─→ APROBADO_MANUAL    ← visible   FINALIZADO
        RECHAZADO_MANUAL
```

`motivo_codigo` lives in the `evento_estado_sistema` catalog (per the MER), so `RECHAZADO_SISTEMA` has **one row per reason**: `MODERACION_TEXTO`, `MODERACION_IMAGEN`, `SIN_IMAGENES_VALIDAS`, `ERROR_PIPELINE`.

**Cloudinary**: without `CLOUDINARY_CLOUD_NAME` the service runs in **simulated mode** — validates format/size, uploads nothing, approves by default. A filename containing `rechazar` is rejected, which is how the rejection branch is tested without credentials.

**Email notifications (2026-10-16)** — DFDs in `docs/diagrams/notificaciones/` (prefix `N`, because "Module 7" is Organizations). `EmailService` composes each notice; `BrevoClient` is the only exit to the provider (Brevo HTTPS API, `POST /v3/smtp/email` — SMTP is blocked on free hosting like Render). Without `BREVO_API_KEY` it runs in **simulated mode** (logs the email, sends nothing). Sender must be a verified sender: `EMAIL_REMITENTE`.

- **Always after commit, always async, never propagates a failure.** `EmailService` registers the send as `afterCommit` and runs it on its own pool (`ejecutorCorreo`, separate from the moderation pool so a burst of public recovery requests cannot starve it). Don't call the provider from a request thread: the login attempt that triggers the block would take longer and leak account existence by timing.
- **`notificacion_enviada` is the mail ledger** (migration `2026-10-16_notificacion_enviada.sql`): UNIQUE `(tipo, id_usuario, id_referencia)` gives idempotency (reminder every minute, two finalization paths) and counting recent rows gives the **per-recipient** recovery limit (3/24 h, 2 min apart — never per IP).
- **`solicitarRestablecimiento` only enqueues**: everything that depends on whether the account exists runs after the response, so neither body nor timing distinguishes the two cases.
- **User text is escaped** (`CorreoHtml`); URLs in user text are never made clickable — the only link is the one the system builds.
- Catalog: RECUPERACION, BLOQUEO, PASSWORD_CAMBIADA, CUENTA_SUSPENDIDA, MODERACION_RESULTADO, EVENTO_SUSPENDIDO, EVENTO_REACTIVADO, INSCRIPCION_CONFIRMADA, EVENTO_CANCELADO, RECORDATORIO_10MIN, INVITACION_VALORAR, AGREGADO_A_EQUIPO.
- **Two code paths set `FINALIZADO`** (`FinalizacionEventoService` scheduler and `FinalizacionEventosService`, which also runs when the organizer lists their events); both call `AvisoInscriptosService.invitarAValorar`, and the ledger prevents a double send.
- Reminder lead time: `enexia.notificaciones.recordatorio.segundos-antes` (600 default; use 30 + `intervalo-ms=5000` to test). Clock uses `enexia.zona-horaria` (Ushuaia), not the JVM zone.
- **Known gap (accepted until deploy, 2026-10-17):** `FinalizacionEventoScheduler` still uses `LocalDateTime.now()` (JVM zone) — on a UTC host it finalizes events 3 h early. Fix before the first deploy.
- **Level-2 DFDs** `N.1` to `N.5` are now all in `docs/diagrams/notificaciones/` (N.2 moderation and N.4 team added 2026-10-17). Moderation/team notices use `despachar` (no ledger); only the clock-driven ones use `despacharUnico`.
- **Recovery screens (2026-10-17):** `pages/auth/password-reset.html` (request the link + "email sent" state, 2-min client cooldown mirroring the server) and `pages/auth/recuperar.html?token=` (new password; same policy as registration: 8-72 chars, lower, upper, digit). The token is removed from the address bar on load; success does **not** log in (the endpoint returns no JWT), it links to the login. Built on `tokens.css` (light palette, square corners) with shared `pages/css/recuperacion.css`.

---

## Sprint 1: Backend Authentication MVP (completed)

### Scope & Security Measures

**Sprint 1 Goal**: Implement registration and login endpoints with core security measures.

### Registration Flow: Persona Física vs Persona Jurídica

**Persona Física (PF):**
- User registers with: nickname, email, password, name, surname, DNI, birth date, address
- Account **activated immediately** upon registration
- Estado: `ACTIVO` (Usuario_Estado)
- Can immediately browse events and register as "PARTICIPANTE"

**Persona Jurídica (PJ) — implemented in Sprint 2, via TWO entry points:**

The documentation describes two flows and neither invalidates the other, so **both are implemented on top of the same service method** (`PersonaJuridicaService.crearOrganizacion`), so no two business rules can drift apart:

| Entry point | Source | Who can call it |
|---|---|---|
| `POST /api/auth/registro/organizacion` | DFD 7.1/7.2 (the Física/Jurídica split **inside** the registration form) | Public — creates personal account + organization in one transaction |
| `POST /api/organizador/organizaciones` | RF-7.2 — an additional organization on an account that already exists | Authenticated `ORGANIZADOR` |

> **"Separate flow" meant a separate *entity*, not a separate *moment*.** The old RF-7.2 wording ("not part of the initial registration") read as a ban on the first entry point and contradicted half the implementation. RF-7.2 was rewritten on 2026-09-09: the organization's alta is its own process with its own validations, regardless of *when* the person runs it. Both entry points are legitimate.

- Organization data: razon_social, nombre_fantasia (optional), CUIT, corporate email, phone, fiscal address
- **The account created is always the human person's.** There is no "company login".
- User is linked to PJ via `Miembros_Organizacion` with `rol_en_empresa` = "ADMINISTRADOR", **atomically with the organization itself** — an organization with no members is unadministrable and holds its CUIT forever.
- These state fields belong to `Persona_Juridica` itself (`Persona_Juridica_Estado_Sistema` / `Persona_Juridica_Estado`), **not** to the founder's `Usuario_Estado` — the founder's login account is `ACTIVO` throughout.

##### Alta resolution — REWRITTEN 2026-09-09 (ADR-14): no review state

The alta resolves **immediately, in the same request**:
- CUIT passes RF-7.3 mod-11 → `APROBADO` + `ACTIVO` on the spot, cleared to publish events.
- CUIT fails → the whole request is rejected, nothing is persisted, the CUIT stays free to retry.

There is **no `REVISION_PENDIENTE` on alta and no deferred approval.** Verifying real existence against the AFIP/ARCA padrón needs a fiscal key and a digital certificate — there is no free, stable public service. Holding a review state that no process could ever close left every organization permanently unable to publish, and made RF-7.4's corporate signature unreachable. Resolving with the only check actually available beats a gate with no key.

> **Accepted limitation:** mod-11 is **arithmetic, not probative**. It proves the CUIT is well-formed, not that an entity exists behind it. An invented CUIT whose check digit closes will be accepted.

> `REVISION_PENDIENTE` and `RECHAZADO` **stay in the catalog** and in the history table (the MER declares them). Unused on alta, they are needed for admin suspension in Módulo 6.

| Feature | Sprint 1 | Sprint 2+ |
|---------|----------|----------|
| User Registration (Persona Física) | ✅ | - |
| User Registration (Persona Jurídica) | ✅ | - |
| PJ Moderation (Manual review + status tracking) | ✅ | - |
| JWT Authentication | ✅ | - |
| ~~Rate Limiting (IP-based)~~ | ❌ **REMOVED 2026-09-08** | see below |
| Account Locking (silent, single 3-failure step) | ✅ **code aligned 2026-09-19** | - |
| ~~Cooldown / CAPTCHA ladder (3/6/9)~~ | ❌ **REMOVED 2026-09-19** (code now matches spec) | - |
| ~~Account unlock via `/unlock-account?token=`~~ | ❌ **DISCARDED 2026-10-19** (admin-only unlock) | - |
| Password Reset by email (RF-1.5) — does **not** unlock | ✅ **Sprint 2** | - |
| Text Moderation (better-profanity) | ✅ | - |
| 2FA (Email verification) | ❌ | Sprint 2+ |
| CAPTCHA | ❌ | Sprint 2+ |
| Password Reset Flow | ✅ **done in Sprint 2** | - |

### External Integrations (Sprint 1)

| Need | Solution | Notes |
|------|----------|-------|
| **Text Moderation** | `better-profanity` (Java library) | Free, lightweight, offline |
| **Email Service** | Mailtrap (Free tier: 10k/month) or Gmail App Password | For future: password reset |
| **CUIT Validation** | Format validation only (11 digits + check digit) | Real AFIP validation for later |
| **Image Storage** | Not in Sprint 1 | Planned for Module 2 (Events) |

### Database Setup (Sprint 1)

```bash
# 1. Create database
mysql -u root -p
CREATE DATABASE enexia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;

# 2. Spring Boot creates the tables from @Entity (ddl-auto=update)

# 3. Apply pending migrations — NOT OPTIONAL, see the warning below
mysql -u root -p enexia < docs/diseño_bd/migraciones/2026-09-08_sprint2.sql
```

> ### ✅ `ddl-auto=update` column bug — RESOLVED 2026-09-09 (upgrade to MariaDB 11.8.9)
>
> **Root cause (discovered 2026-09-08):** Hibernate 7 (Spring Boot 4.1) dropped support for MariaDB below **10.6** and still emits `ALTER TABLE IF EXISTS <t> ADD COLUMN ...`. The MariaDB shipped with every current XAMPP bundle (PHP 8.0/8.1/8.2) is **10.4.32**, which rejects `IF EXISTS` in `ALTER TABLE` with **error 1064, syntax error**. Hibernate logs the failure but **does not abort startup**, so the app comes up normally and the column simply isn't there — the symptom shows up later at runtime as `Unknown column '...' in 'field list'`. This had been silently broken since Sprint 1: four `persona_juridica` columns declared in the entity since 2026-07-26 never existed in the database, unnoticed until the public catalog queried that table.
>
> **Fix applied:** installed **MariaDB 11.8 LTS as a standalone Windows service** (outside XAMPP, same port 3306, `application.properties` unchanged), restored the data, and confirmed `ddl-auto=update` now adds/recreates columns with zero `1064` errors and zero `HHH000511` warnings. XAMPP-Lite's bundled 11.4.10 would also clear the 10.6 floor, but changes Apache/PHP too — the standalone service was simpler. XAMPP itself is left intact but **must not have its MySQL service running** (port conflict on 3306). Full writeup: [docs/log/sprint_2/2026-09-09_upgrade_mariadb.md](./docs/log/sprint_2/2026-09-09_upgrade_mariadb.md).
>
> **Dev environment requirement:** MariaDB **10.6+** (project runs 11.8.9 LTS). If you're on a fresh machine with only XAMPP's bundled MariaDB, this bug will reappear — install a standalone 10.6+ instance instead.
>
> **Side effect worth knowing:** upgrading past 10.4 exposed two real concurrency bugs that the old version's conservative locking had been masking — a lost-update on `Evento` (fixed with `@DynamicUpdate`) and a snapshot-isolation abort on concurrent writes (fixed with pessimistic locking + `READ_COMMITTED` on those transactions). See the writeup for details if you hit `Record has changed since last read` anywhere.
>
> **Rule for the team, unchanged:** every column added to an `@Entity` still goes into a migration script under `docs/diseño_bd/migraciones/` — `ddl-auto=update` working again doesn't replace migrations, especially since production will run `ddl-auto=validate` (Flyway/Liquibase) regardless of dev's MariaDB version.

### Key Database Fields (Sprint 1 - Usuario table)

```java
@Entity
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String email;
    private String password;          // BCrypt hashed
    private String nickname;
    
    @Enumerated(EnumType.STRING)
    private EstadoUsuario estado;     // ACTIVO, BLOQUEADO, SUSPENDIDO, DE_BAJA
    
    private Integer intentos_fallidos;    // Reset to 0 on success; BLOQUEADO at 3 (ADR-13)
    
    private LocalDateTime fecha_baja;     // Soft delete field
    private LocalDateTime fecha_registro;
}
```

> Note: in the real entity, states are **related tables** (`usuario_estado`), not enums — see ADR-02. The enum above is the CLAUDE.md draft, kept for reference.

### Important Implementation Notes

- **Content Moderation** (Sprint 1): Use `better-profanity` for registration names/nicknames
- **Content Moderation** (Sprint 2+): Backend must validate titles/descriptions of events (Module 2, RF-2.2)
- **State Tracking**: Users have `estado_usuario` (ACTIVO, BLOQUEADO, etc.) — check in login
- **Account Locking**: silent. See the login failure policy above — never expose the block through the HTTP response; notify by email instead.
- **Async pipeline**: background work is dispatched via `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`, **never** by calling the async service directly from a `@Transactional` method. A direct call starts the worker thread while the row is still uncommitted and invisible to it — this actually happened and left events stuck in `RECHAZADO_SISTEMA/ERROR_PIPELINE`.
- **`@Transactional` and self-invocation**: `REQUIRES_NEW` only applies when the call crosses the Spring proxy, i.e. comes from *another* bean. Writing helpers that need their own transaction inside the same class silently disables the annotation. This is why `IntentosLoginService` and `VisitaService` are separate beans.
- **Soft Deletes**: Use `fecha_baja` field; never hard-delete users or events
- **RBAC (Backend)**: Always validate roles in service layer via `@PreAuthorize` or manual checks; frontend can render UI conditionally, but backend enforces
- **Cloudinary Integration**: Planned for Sprint 2 (Module 2 - Event Images); for now skip image uploads
- **Analytics** (Future): Aggregate visits (unique per user) and avg ratings from `Valoracion` table for event dashboards


---

## Design Rules (read before any frontend/CSS change)

- **Square corners, everywhere (2026-10-16).** Borders must stay squarish, especially in `pages/admin/*`. Use ONLY the tokens from `pages/css/tokens.css` section 8: `--radio-campo` (4px), `--radio-tarjeta` (6px), `--radio-sm` (6px). Never hardcode `border-radius` values (12/14/16/20/24px) and never use `999px` pills or override `--radio-tarjeta` per page. `50%` is allowed only for avatars, status dots and round close/icon buttons.
- **Escape user text (2026-10-19).** Any text written by a person (event name/description, ticket type, address, review comment, organization name) that goes into `innerHTML` or an HTML attribute **must pass through `UI.esc(...)`** (`js/utils.js`) or be assigned with `textContent`. Public pages were fixed (stored XSS); not every screen was audited (`docs/SUGERENCIAS_PENDIENTES.md`, X1).
- No "+" icon or "+" text on any "Crear Evento" button/link.
- Never show internal DB table names (`Tabla: ...`) in user-facing forms.
- Admin accounts never see participant actions (inscription button or its notice) on the event detail page.


---

## References

- **Programming manual (learning path, A–D)**: [docs/manual/](./docs/manual/README.md)
- **Pending DFDs and decisions**: [docs/tempDFD/](./docs/tempDFD/README.md) · **Open questions**: [docs/SUGERENCIAS_PENDIENTES.md](./docs/SUGERENCIAS_PENDIENTES.md)
- **Functional Requirements**: [docs/requisitos/requisitos_funcionales/](./docs/requisitos/requisitos_funcionales/)
- **Database Model**: [docs/diseño_bd/MER.md](./docs/diseño_bd/MER.md) and [DER.md](./docs/diseño_bd/DER.md)
- **Workflow Diagrams**: [docs/diagrams/](./docs/diagrams/)
- **Non-Functional Requirements**: [docs/requisitos/requisitos_no_funcionales/](./docs/requisitos/requisitos_no_funcionales/)

---

## Contact & Support

For architectural questions or clarifications on the specification, refer to the documentation in the `docs/` folder. The project is currently in the specification phase; implementation will follow once the backend and frontend skeletons are created.
