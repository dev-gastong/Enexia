# CLAUDE_es-ES.md

Este archivo proporciona una guía en español sobre el proyecto Enexia. La versión original en inglés es `CLAUDE.md` (para Claude Code).

---

## 📝 Nota Importante

⚠️ **ESTA ES LA VERSIÓN EN ESPAÑOL** — para que la entiendas.

📄 **Versión en inglés:** `CLAUDE.md` (referencia para Claude Code)

**Instrucción de sincronización:** Cuando se actualiza `CLAUDE.md`, también se actualiza `CLAUDE_es-ES.md` para mantenerlos en sincronía. La estructura de contenido es idéntica; solo cambia el idioma.

---

## Descripción General del Proyecto

**Enexia** es una plataforma web para la gestión centralizada y difusión de eventos culturales, educativos, sociales, deportivos y de gaming en Tierra del Fuego. Es un proyecto MVP full-stack enfocado en experiencia de usuario, seguridad y escalabilidad.

### Arquitectura de Alto Nivel

```
┌────────────────────────────────────────────────────────┐
│       Frontend (HTML, CSS, JavaScript Vanilla)         │
│  Múltiples páginas HTML (separación por roles)         │
│  - pages/auth/* (login, registro, recuperar contraseña)│
│  - pages/participant/* (catálogo, inscripciones)       │
│  - pages/organizer/* (gestión de eventos)              │
│  - pages/admin/* (gestión de usuarios/eventos)         │
└────────────────────┬─────────────────────────────────┘
                     │ REST API / JSON (Fetch)
                     ↓
┌────────────────────────────────────────────────────────┐
│            Backend (Spring Boot 3.x+)                  │
│  ┌──────────────────────────────────────────┐          │
│  │  Capa de Controladores                   │          │
│  │  (endpoints REST, validación JWT, RBAC)  │          │
│  └──────────────────────────────────────────┘          │
│  ┌──────────────────────────────────────────┐          │
│  │  Capa de Servicios                       │          │
│  │  (lógica de negocio, moderación)         │          │
│  └──────────────────────────────────────────┘          │
│  ┌──────────────────────────────────────────┐          │
│  │  Capa de Repositorio                     │          │
│  │  (acceso a datos, JPA/Hibernate ORM)     │          │
│  └──────────────────────────────────────────┘          │
└────────────────────┬─────────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        ↓            ↓            ↓
      MySQL    Cloudinary    Servicio de Email
       (BD)     (Imágenes)    (Notificaciones)
```

---

## Stack Tecnológico

| Capa | Tecnología | Versión |
|------|-----------|---------|
| **Backend** | Spring Boot | 3.x+ |
| **Lenguaje (Backend)** | Java | 17+ (OpenJDK Temurin) |
| **Herramienta de Compilación** | Gradle | Latest |
| **Frontend** | HTML5, CSS3, JavaScript Vanilla | ES6+ |
| **Base de Datos** | MySQL | Por definir |
| **Autenticación** | JWT (JSON Web Tokens) | - |
| **Hash de Contraseñas** | BCrypt | - |
| **Almacenamiento de Imágenes** | Cloudinary | Integración externa |

---

## Entidades Principales y Modelo de Dominio

El sistema se centra en estas entidades clave (ver `docs/diseño_bd/MER.md` para el ERD completo):

- **Persona**: Entidad base (Física = Individual, Jurídica = Organización)
- **Usuario**: Cuenta de usuario con roles (Participante, Organizador, Administrador)
- **Evento**: Evento creado por organizadores con seguimiento de estado
- **Evento_Cronograma**: Múltiples fechas/horarios para un mismo evento
- **Cronograma_Ticket**: Tipos de tickets, precios y gestión de cupos por fecha
- **Inscripcion**: Registros de participantes con control de cupos
- **Valoracion**: Calificaciones y reseñas de participantes
- **Visita**: Seguimiento de visualizaciones para analítica
- **Ubicacion**: Datos de ubicación geográfica
- **Categoria**: Categorías de eventos

---

## Módulos Funcionales (8 Módulos)

Consulta `docs/requisitos/requisitos_funcionales/` para especificaciones detalladas:

1. **Módulo 1**: Gestión de Usuarios y Autenticación (JWT, roles, estados de cuenta)
2. **Módulo 2**: Gestión de Eventos (CRUD, multimedia, agendamiento, cupos)
3. **Módulo 3**: Dashboard y Catálogo de Participantes
4. **Módulo 4**: Sistema de Tickets e Inscripciones
5. **Módulo 5**: Calificaciones y Reseñas
6. **Módulo 6**: Panel de Administración
7. **Módulo 7**: Notificaciones y Email
8. **Módulo 8**: Analítica y Reportes

---

## Requisitos No Funcionales

- **Tiempos de Respuesta**: Máx 2s para lecturas (catálogo), 4s para escrituras (crear evento)
- **Concurrencia**: Soportar múltiples usuarios simultáneamente
- **Seguridad**: Hash BCrypt de contraseñas, JWT auth, RBAC, moderación de contenido
- **Disponibilidad**: Manejo de errores con mensajes amigables + logging del servidor
- **Usabilidad**: "Regla de 3 clicks" para acciones principales, divulgación progresiva
- **Arquitectura**: Patrón en capas (Controller → Service → Repository) para mantenibilidad
- **Navegadores**: Chrome, Firefox, Safari, Edge (diseño responsive para desktop y móvil)

---

## Configuración y Comandos

### Backend (Spring Boot + Gradle)

```bash
# Navegar al directorio del backend (cuando esté creado)
cd backend/

# Compilar el proyecto (Gradle)
gradle build

# Ejecutar pruebas
gradle test

# Ejecutar una prueba específica
gradle test --tests ClassName

# Ejecutar la aplicación (desarrollo)
gradle bootRun

# Verificar dependencias
gradle dependencies
```

### Frontend (HTML, CSS, JavaScript Vanilla)

```bash
# Navegar al directorio del frontend (cuando esté creado)
cd frontend/

# Servidor de desarrollo (servidor HTTP simple)
# Opción 1: Python 3
python -m http.server 8000

# Opción 2: Node.js (paquete http-server, opcional)
npm install -g http-server
http-server . -p 8000

# No se requiere build step - JavaScript vanilla se ejecuta directamente en el navegador
# Para producción, simplemente servir los archivos mediante un servidor web (nginx, Apache, etc.)
```

### Base de Datos

```bash
# Cuando MySQL esté configurado, importar el esquema desde DER
# Los scripts del esquema se generarán desde docs/diseño_bd/DER.md
mysql -u root -p enexia < schema.sql
```

---

## Estructura de Directorios (Por Crear)

```
enexia/
├── backend/
│   ├── src/
│   │   ├── main/java/com/enexia/
│   │   │   ├── controller/          # Endpoints REST
│   │   │   ├── service/             # Lógica de negocio
│   │   │   ├── repository/          # Acceso a datos (JPA/Hibernate)
│   │   │   ├── model/               # Clases de entidades
│   │   │   ├── dto/                 # Objetos de Transferencia de Datos
│   │   │   ├── security/            # JWT, filtros, lógica de autenticación
│   │   │   ├── exception/           # Excepciones personalizadas
│   │   │   └── utils/               # Utilidades (BCrypt, validadores)
│   │   └── resources/
│   │       ├── application.yml      # Configuración
│   │       └── application-dev.yml  # Perfil de desarrollo
│   └── build.gradle
├── frontend/
│   ├── index.html                   # Página de inicio
│   ├── pages/
│   │   ├── auth/
│   │   │   ├── login.html
│   │   │   ├── register.html
│   │   │   └── password-reset.html
│   │   ├── participant/
│   │   │   ├── dashboard.html       # Inicio participante
│   │   │   ├── event-catalog.html   # Explorar eventos
│   │   │   ├── event-details.html   # Ver evento individual
│   │   │   ├── my-registrations.html # Mis inscripciones
│   │   │   └── profile.html
│   │   ├── organizer/
│   │   │   ├── dashboard.html       # Inicio organizador
│   │   │   ├── my-events.html       # Listar eventos propios
│   │   │   ├── create-event.html
│   │   │   ├── edit-event.html
│   │   │   └── event-stats.html
│   │   └── admin/
│   │       ├── dashboard.html       # Inicio admin
│   │       ├── manage-users.html
│   │       ├── manage-events.html
│   │       └── manage-categories.html
│   ├── css/
│   │   ├── styles.css               # Estilos globales
│   │   ├── responsive.css           # Media queries mobile-first
│   │   └── components.css           # Estilos de componentes reutilizables
│   ├── js/
│   │   ├── api.js                   # Cliente API (envolvedor fetch)
│   │   ├── auth.js                  # JWT token, login/logout, verificación de rol
│   │   ├── utils.js                 # Funciones auxiliares (formatDate, validar, etc.)
│   │   └── modules/
│   │       ├── events.js            # Lógica catálogo, búsqueda, filtros
│   │       ├── organizer.js         # Crear/editar eventos
│   │       ├── participant.js       # Inscripciones, calificaciones
│   │       └── admin.js             # Operaciones específicas de admin
│   └── assets/                      # Imágenes, iconos, logos
├── docs/                            # Documentación existente
│   ├── requisitos/
│   ├── diseño_bd/
│   └── diagrams/
└── CLAUDE_es-ES.md                  # Este archivo
```

---

## Guías Clave de Desarrollo

### Backend (Java/Spring Boot)

- **Arquitectura en Capas**: Controllers → Services → Repositories (separación estricta)
- **Patrón DTO**: Usar DTOs para solicitudes/respuestas de API, no entidades directamente
- **Manejo de Excepciones**: Clases de excepción personalizadas con códigos HTTP adecuados
- **Seguridad**: 
  - Filtro JWT en endpoints protegidos
  - Verificaciones RBAC en `@PreAuthorize` o capa de servicio
  - Validación de entrada (email, fuerza de contraseña, etc.)
- **Logging**: Usar SLF4J para logging de errores del servidor (requerido por requisitos no funcionales)
- **Base de Datos**: JPA/Hibernate para ORM; cargar perezosamente las relaciones con cuidado

### Frontend (HTML, CSS, JavaScript Vanilla)

- **JavaScript ES6+**: Usar JS moderno (arrow functions, const/let, fetch API, async/await)
- **Manipulación del DOM**: Usar métodos vanilla del DOM (`querySelector`, `addEventListener`, `innerHTML`, etc.)
- **Integración de API**: 
  - Crear `js/api.js` con wrappers fetch reutilizables para solicitudes HTTP
  - Siempre incluir token JWT en header `Authorization: Bearer <token>`
- **Autenticación**: 
  - Almacenar JWT en `sessionStorage` o `localStorage` (considerar implicaciones de seguridad)
  - Implementar lógica de validación y renovación de token en `js/auth.js`
- **UI basada en Roles (RBAC)**:
  - Páginas HTML separadas por rol de usuario (ej: `pages/organizer-dashboard.html`, `pages/participant-dashboard.html`, `pages/admin-dashboard.html`)
  - Backend valida roles; frontend sirve páginas específicas de rol
  - En login/redirección, JavaScript verifica rol del usuario desde JWT y redirige al dashboard apropiado
  - Ejemplo: Participante hace clic para ver eventos → va a `pages/event-catalog.html`; Organizador va a `pages/organizer-dashboard.html`
- **Arquitectura Multi-página** (No SPA):
  - Cada página tiene su propio archivo `.html` (ej: `pages/login.html`, `pages/event-details.html`)
  - Recargas de página completa al navegar entre páginas (sin enrutamiento del lado del cliente)
  - Navegación mediante etiquetas `<a>` estándar o `window.location`
- **Diseño Responsive**: CSS mobile-first con media queries (sin framework CSS requerido)
- **Divulgación Progresiva**: Usar secciones colapsibles, detalles expandibles y diálogos modales
- **Manejo de Errores**: Mostrar notificaciones toast amigables o diálogos de alerta para errores de API
- **Gestión de Estado**: Usar objetos/clases vanilla de JS o sessionStorage para estado simple; ninguna librería externa

---

## Notas Importantes de Implementación

- **Moderación de Contenido**: Backend debe validar títulos/descripciones de eventos contra contenido ofensivo antes de persistir (Módulo 2, RF-2.2)
- **Integración Cloudinary**: Validar formatos de imagen (JPG/PNG), máx 2MB, verificar contenido sensible
- **Seguimiento de Estado**: Eventos y usuarios tienen múltiples campos de estado (`estado_sistema`, `estado_organizador`, `estado_usuario`) — usar el correcto según caso de uso
- **Gestión de Cupos**: `cupo_actual` debe incrementarse atómicamente en inscripción exitosa; prevenir doble reserva
- **Bloqueo de Cuenta**: Después de 3 intentos fallidos de login, establecer estado del usuario a "BLOQUEADO"
- **Eliminación Lógica**: Usar campo `fecha_baja` para usuarios y estado `DADO_DE_BAJA` para eventos (no eliminar, solo marcar)
- **Analítica**: Agregar visualizaciones (únicas por usuario) y calificaciones promedio de tabla `Valoracion` para dashboards de eventos

---

## Referencias

- **Requisitos Funcionales**: [docs/requisitos/requisitos_funcionales/](./docs/requisitos/requisitos_funcionales/)
- **Modelo de Base de Datos**: [docs/diseño_bd/MER.md](./docs/diseño_bd/MER.md) y [DER.md](./docs/diseño_bd/DER.md)
- **Diagramas de Flujo**: [docs/diagrams/](./docs/diagrams/)
- **Requisitos No Funcionales**: [docs/requisitos/requisitos_no_funcionales/](./docs/requisitos/requisitos_no_funcionales/)

---

## Contacto y Soporte

Para preguntas arquitectónicas o aclaraciones sobre la especificación, consulta la documentación en la carpeta `docs/`. El proyecto actualmente está en fase de especificación; la implementación comenzará una vez que los esqueletos del backend y frontend estén creados.

---

## 📝 Convención de Documentación para Logs Históricos

**Todo "log histórico escrito"** (análisis, resúmenes, documentación de decisiones, registros de cambios) **debe estar redactado en español**. 

Esto incluye:
- Archivos `.md` de análisis y comparativas
- Documentos de memoria del proyecto
- Reportes y summaries de investigación
- Notas de investigación técnica
- `HISTORIAL.md` del proyecto

El código fuente (Java, JavaScript, HTML, etc.) y comentarios de código pueden estar en inglés o español según preferencia del equipo.
