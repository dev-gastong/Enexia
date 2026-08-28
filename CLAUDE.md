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

### Role Assignment Strategy (Sprint 1)

- **PARTICIPANTE** (Persona Física): Receives **PARTICIPANTE role only** — can browse, register, and participate in events.
- **ORGANIZADOR** (Persona Física): Receives **ORGANIZADOR + PARTICIPANTE roles** — can create/manage events AND participate in others' events.
- **ORGANIZADOR** (Persona Jurídica, Sprint 2): Via `Miembros_Organizacion`, individual members are Personas Físicas with PARTICIPANTE role; the organization itself does not have a direct role. This ensures all actual participants are rooted in Persona Física.

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

- **Persona**: Base entity (Física = Individual, Jurídica = Organization)
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

```bash
# Navigate to frontend directory (when created)
cd frontend/

# Development server (simple HTTP server for testing)
# Option 1: Python 3
python -m http.server 8000

# Option 2: Node.js (http-server package, optional)
npm install -g http-server
http-server . -p 8000

# No build step required - vanilla JS runs directly in browser
# For production, simply serve the files via a web server (nginx, Apache, etc.)
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

#### Security (Sprint 1 MVP)
- **Authentication**: JWT (JSON Web Tokens) issued on successful login with `roles[]` array (multi-role support)
- **Authorization**: Extract roles[] array from JWT; validate in `@PreAuthorize("hasAnyRole(...)")` on service methods or controller
- **Password**: BCrypt hashing (never plain text)
- **Rate Limiting**: Track login attempts by email/IP; block at 3 failures for 5 minutes
- **Account Blocking**: After 3 failed attempts, set `estado_usuario` = "BLOQUEADO"
- **Cooldown**: `fecha_desbloqueo_cooldown` timestamp prevents immediate retry
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

## Sprint 1: Backend Authentication MVP (Current)

### Scope & Security Measures

**Sprint 1 Goal**: Implement registration and login endpoints with core security measures.

| Feature | Sprint 1 | Sprint 2+ |
|---------|----------|----------|
| User Registration (Persona Física) | ✅ | - |
| JWT Authentication | ✅ | - |
| Rate Limiting (IP-based) | ✅ | - |
| Account Locking (3 failed attempts) | ✅ | - |
| Cooldown (5 min penalty) | ✅ | - |
| Text Moderation (better-profanity) | ✅ | - |
| 2FA (Email verification) | ❌ | Sprint 2+ |
| CAPTCHA | ❌ | Sprint 2+ |
| Password Reset Flow | ❌ | Sprint 2+ |
| Persona Jurídica Registration | ❌ | Sprint 2 |

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

# 2. Spring Boot auto-creates tables via @Entity + application.yml
# Set: spring.jpa.hibernate.ddl-auto=create-drop (dev) or validate (prod)
```

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
    
    private Integer intentos_fallidos;    // Reset to 0 on success, increment on failure
    private LocalDateTime fecha_desbloqueo_cooldown; // Null = no penalty
    private Boolean requiere_captcha;     // False initially, True after 3 attempts
    
    private LocalDateTime fecha_baja;     // Soft delete field
    private LocalDateTime fecha_registro;
}
```

### Important Implementation Notes

- **Content Moderation** (Sprint 1): Use `better-profanity` for registration names/nicknames
- **Content Moderation** (Sprint 2+): Backend must validate titles/descriptions of events (Module 2, RF-2.2)
- **State Tracking**: Users have `estado_usuario` (ACTIVO, BLOQUEADO, etc.) — check in login
- **Rate Limiting**: Track failed attempts in `Historial_Interacciones` table by email/IP
- **Account Locking**: After exactly 3 failed attempts, set estado = "BLOQUEADO" + send security email (Sprint 2)
- **Soft Deletes**: Use `fecha_baja` field; never hard-delete users or events
- **RBAC (Backend)**: Always validate roles in service layer via `@PreAuthorize` or manual checks; frontend can render UI conditionally, but backend enforces
- **Cloudinary Integration**: Planned for Sprint 2 (Module 2 - Event Images); for now skip image uploads
- **Analytics** (Future): Aggregate visits (unique per user) and avg ratings from `Valoracion` table for event dashboards

---

## References

- **Functional Requirements**: [docs/requisitos/requisitos_funcionales/](./docs/requisitos/requisitos_funcionales/)
- **Database Model**: [docs/diseño_bd/MER.md](./docs/diseño_bd/MER.md) and [DER.md](./docs/diseño_bd/DER.md)
- **Workflow Diagrams**: [docs/diagrams/](./docs/diagrams/)
- **Non-Functional Requirements**: [docs/requisitos/requisitos_no_funcionales/](./docs/requisitos/requisitos_no_funcionales/)

---

## Contact & Support

For architectural questions or clarifications on the specification, refer to the documentation in the `docs/` folder. The project is currently in the specification phase; implementation will follow once the backend and frontend skeletons are created.
