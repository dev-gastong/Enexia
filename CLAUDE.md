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
- **Usuario**: User account with roles (Participante, Organizador, Administrador)
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

## Key Development Guidelines

### Backend (Java/Spring Boot)

- **Layered Architecture**: Controllers → Services → Repositories (strict separation)
- **DTO Pattern**: Use DTOs for API requests/responses, not entities directly
- **Exception Handling**: Custom exception classes with proper HTTP status codes
- **Security**: 
  - JWT filter on protected endpoints
  - RBAC checks in `@PreAuthorize` or service layer
  - Input validation (email, password strength, etc.)
- **Logging**: Use SLF4J for server-side error logging (required by non-functional requirements)
- **Database**: JPA/Hibernate for ORM; lazy-load relationships carefully

### Frontend (HTML, CSS, JavaScript Vanilla)

- **ES6+ JavaScript**: Use modern JS (arrow functions, const/let, fetch API, async/await)
- **DOM Manipulation**: Use vanilla DOM methods (`querySelector`, `addEventListener`, `innerHTML`, etc.)
- **API Integration**: 
  - Create `js/api.js` with reusable fetch wrappers for HTTP requests
  - Always include JWT token in `Authorization: Bearer <token>` header
- **Authentication**: 
  - Store JWT in `sessionStorage` or `localStorage` (consider security implications)
  - Implement token validation and refresh logic in `js/auth.js`
- **Role-Based UI (RBAC)**:
  - Separate HTML pages per user role (e.g., `pages/organizer-dashboard.html`, `pages/participant-dashboard.html`, `pages/admin-dashboard.html`)
  - Backend validates roles; frontend serves role-specific pages
  - On login/redirect, JavaScript checks user role from JWT and redirects to the appropriate dashboard
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

## Important Implementation Notes

- **Content Moderation**: Backend must validate titles/descriptions of events against offensive content before persisting (Module 2, RF-2.2)
- **Cloudinary Integration**: Validate image formats (JPG/PNG), max 2MB, check for sensitive content
- **State Tracking**: Events and users have multiple state fields (`estado_sistema`, `estado_organizador`, `estado_usuario`) — query the correct one per use case
- **Quota Management**: `cupo_actual` must be incremented atomically on successful registration; prevent double-booking
- **Account Locking**: After 3 failed login attempts, set user state to "BLOQUEADO"
- **Soft Deletes**: Use `fecha_baja` field for users and `DADO_DE_BAJA` status for events (don't delete, just mark)
- **Analytics**: Aggregate visits (unique per user) and average ratings from `Valoracion` table for event dashboards

---

## References

- **Functional Requirements**: [docs/requisitos/requisitos_funcionales/](./docs/requisitos/requisitos_funcionales/)
- **Database Model**: [docs/diseño_bd/MER.md](./docs/diseño_bd/MER.md) and [DER.md](./docs/diseño_bd/DER.md)
- **Workflow Diagrams**: [docs/diagrams/](./docs/diagrams/)
- **Non-Functional Requirements**: [docs/requisitos/requisitos_no_funcionales/](./docs/requisitos/requisitos_no_funcionales/)

---

## Contact & Support

For architectural questions or clarifications on the specification, refer to the documentation in the `docs/` folder. The project is currently in the specification phase; implementation will follow once the backend and frontend skeletons are created.
