# CLAUDE_es-ES.md

Este archivo proporciona una guía en español sobre el proyecto Enexia. La versión original en inglés es `CLAUDE.md` (para Claude Code).

---

## 📝 Nota Importante

⚠️ **ESTA ES LA VERSIÓN EN ESPAÑOL**.

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

### Estrategia de Asignación de Roles y Modelo de Participación

**Sprint 1: Solo Personas Físicas**
- **PARTICIPANTE** (Persona Física): Recibe **solo el rol PARTICIPANTE** — puede explorar, registrarse y participar en eventos.
- **ORGANIZADOR** (Persona Física): Recibe **los roles ORGANIZADOR + PARTICIPANTE** — puede crear/gestionar eventos Y participar en eventos de otros.
  - **Justificación:** Evita forzar a los organizadores a crear cuentas separadas para participar. Mantiene el sistema liviano para el MVP.

**Sprint 2: Personas Jurídicas como Contenedores Administrativos**
- **Persona Jurídica** (Organización/Empresa): Creada por una Persona Física ORGANIZADOR; no es una entidad de login.
  - **Principio clave:** Las empresas no participan en eventos. La participación siempre es de Personas Físicas (personas).
  - Miembros: Se registran vía la tabla `Miembros_Organizacion`, vinculando Personas Físicas como miembros/administradores de la organización.
  - Autoría de eventos: Los eventos creados "bajo" una organización se atribuyen al nombre de la empresa, pero son organizados/gestionados por miembros Persona Física.
  - **Justificación:** Garantiza coherencia conceptual — todos los participantes reales de un evento son personas individuales (Personas Físicas), nunca entidades abstractas. Esto modela el comportamiento del mundo real: una empresa no asiste a un concierto, sus empleados sí.

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
- **Usuario**: Cuenta de usuario con roles (Participante, Organizador, Administrador). **Nota de diseño:** los Organizadores reciben ambos roles ORGANIZADOR + PARTICIPANTE para crear eventos Y participar en los de otros; solo las Personas Físicas pueden registrarse directamente. Las Personas Jurídicas gestionan participantes a través de Miembros_Organizacion (Sprint 2).
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

## Arquitectura del Backend (Spring Boot - Java)

### Estructura de Directorios
```
backend/src/main/java/com/enexia/
├── config/              # @Configuration, filtros JWT, CORS, beans de seguridad
├── controller/          # @RestController endpoints (API REST)
├── dto/                 # DTOs Request/Response (sin exponer entidades)
├── service/             # @Service lógica de negocio
├── repository/          # @Repository JPA (extends JpaRepository)
├── model/               # @Entity clases JPA (mapeadas a BD)
├── security/            # Utilidades JWT, BCrypt, lógica RBAC
├── exception/           # Excepciones personalizadas (BadCredentialsEx, etc.)
├── util/                # Validadores, formateadores, helpers (lógica no empresarial)
└── logger/              # Logging SLF4J vía anotación @Slf4j
```

### Guías Clave de Desarrollo

#### Arquitectura en Capas (Separación Estricta)
- **Controller** (@RestController): Endpoints HTTP, validación de entrada, mapeo de respuestas
- **Service** (@Service): Lógica de negocio, transacciones, orquestación, verificaciones de seguridad
- **DTO**: Objetos separados `*Request` y `*Response` (nunca exponer entidades directamente)
- **Repository** (extends JpaRepository): CRUD + consultas personalizadas solamente
- **Model** (@Entity): Mapeos JPA solamente (sin lógica de negocio)

#### Patrón DTO
- Crear DTOs `*Request` y `*Response` separados para cada endpoint
- Usar `ModelMapper` o mapeo manual para convertir Entity ↔ DTO
- Nunca exponer entidades en respuestas de API

#### Manejo de Excepciones
- Crear excepciones personalizadas extendiendo `RuntimeException` (ej: `BadCredentialsException`, `AccountBlockedException`)
- Usar `@ControllerAdvice` con `@ExceptionHandler` para mapear excepciones a códigos HTTP
- Retornar JSON de error consistente: `{ "error": "...", "timestamp": "...", "status": 400 }`

#### Seguridad (Sprint 1 MVP)
- **Autenticación**: JWT (JSON Web Tokens) emitido en login exitoso
- **Autorización**: Extraer roles del JWT; validar en `@PreAuthorize` en métodos de servicio o controller
- **Contraseña**: Hashing BCrypt (nunca en texto plano)
- **Rate Limiting**: Rastrear intentos por email/IP; bloquear en 3 fallos por 5 minutos
- **Bloqueo de Cuenta**: Después de 3 intentos fallidos, establecer `estado_usuario` = "BLOQUEADO"
- **Cooldown**: Timestamp `fecha_desbloqueo_cooldown` previene reintentos inmediatos
- **Moderación de Texto**: Librería `better-profanity` para filtrado de contenido (Registro + Login)
- **Validación de Entrada**: Usar `@Valid` + `@NotNull`, `@Email`, `@Pattern` en DTOs

#### Logging
- Usar **SLF4J** vía `@Slf4j` (Lombok) en clases @Service/@Controller
- Registrar eventos de seguridad: intentos de login, bloqueos de cuenta, tokens inválidos
- Persistir auditoría en tabla `Historial_Interacciones` (user_id, acción, endpoint, IP, timestamp)

#### Base de Datos
- **JPA/Hibernate** para ORM (Spring Boot auto-crea tablas vía `@Entity`)
- Usar `@ManyToOne`, `@OneToMany`, `@OneToOne` cuidadosamente (lazy-load preferido)
- Evitar queries N+1; usar `@Query` con `JOIN FETCH` cuando sea necesario
- Borrados lógicos: usar campos `fecha_baja` o `estado`; nunca hard-delete

#### Testing
- **JUnit 5** para pruebas unitarias
- **Mockito** para mock de dependencias
- Estructura: `@DisplayName`, `@Test`, patrón arrange-act-assert
- Probar casos de éxito Y casos de error (excepciones)
- Para este sprint: probar AuthService, endpoint de login, lógica de rate limiting

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

## Sprint 1: Backend Autenticación MVP (Actual)

### Alcance y Medidas de Seguridad

**Objetivo Sprint 1**: Implementar endpoints de registro y login con medidas de seguridad core.

### Flujo de Registro: Persona Física vs Persona Jurídica

**Persona Física (PF):**
- El usuario se registra con: nickname, email, password, nombre, apellido, DNI, fecha de nacimiento, domicilio
- La cuenta se **activa inmediatamente** al registrarse
- Estado: `ACTIVO` (Usuario_Estado)
- Puede explorar eventos y registrarse como "PARTICIPANTE" de inmediato

**Persona Jurídica (PJ):**
- El usuario se registra con: nickname, email, password, razon_social, nombre_fantasia (opcional), CUIT, teléfono, domicilio
- La cuenta se crea pero **entra en estado de revisión**
- Estos dos campos de estado pertenecen a la propia `Persona_Juridica` (`Persona_Juridica_Estado_Sistema` / `Persona_Juridica_Estado`), **no** al `Usuario_Estado` del fundador — la cuenta de login del fundador se mantiene `ACTIVO` en todo momento, solo la organización queda condicionada:
  - `estado_persona_juridica_sistema`: `REVISION_PENDIENTE` (moderador/admin revisa CUIT + razón social)
  - `estado_persona_juridica`: Inicialmente `INACTIVO` hasta la aprobación
  - Tras la aprobación: `estado_persona_juridica_sistema` → `APROBADO`, `estado_persona_juridica` → `ACTIVO`
- El usuario queda vinculado a la PJ vía la tabla `Miembros_Organizacion` con `rol_en_empresa` = "ADMINISTRADOR"

| Característica | Sprint 1 | Sprint 2+ |
|---|---|---|
| Registro de Usuarios (Persona Física) | ✅ | - |
| Registro de Usuarios (Persona Jurídica) | ✅ | - |
| Moderación de PJ (Revisión manual + seguimiento de estado) | ✅ | - |
| Autenticación con JWT | ✅ | - |
| Rate Limiting (por IP) | ✅ | - |
| Bloqueo en 3 intentos fallidos | ✅ | - |
| Cooldown (penalización 5 min) | ✅ | - |
| Moderación de texto (better-profanity) | ✅ | - |
| 2FA (Verificación por email) | ❌ | Sprint 2+ |
| CAPTCHA | ❌ | Sprint 2+ |
| Recuperación de Contraseña | ❌ | Sprint 2+ |

### Integraciones Externas (Sprint 1)

| Necesidad | Solución | Notas |
|---|---|---|
| **Moderación de Texto** | Librería `better-profanity` (Java) | Gratuita, offline, lightweight |
| **Servicio de Email** | Mailtrap (Free: 10k/mes) o Gmail App Password | Para futuro: recuperación de contraseña |
| **Validación CUIT** | Solo validación de formato (11 dígitos + verificador) | Validación real con AFIP para después |
| **Almacenamiento de Imágenes** | No necesario en Sprint 1 | Planeado para Módulo 2 (Eventos) |

### Configuración de Base de Datos (Sprint 1)

```bash
# 1. Crear base de datos
mysql -u root -p
CREATE DATABASE enexia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;

# 2. Spring Boot auto-crea tablas vía @Entity + application.yml
# Configurar: spring.jpa.hibernate.ddl-auto=create-drop (dev) o validate (prod)
```

### Campos Clave de BD (Sprint 1 - Tabla Usuario)

```java
@Entity
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String email;
    private String password;          // Hasheado con BCrypt
    private String nickname;
    
    @Enumerated(EnumType.STRING)
    private EstadoUsuario estado;     // ACTIVO, BLOQUEADO, SUSPENDIDO, DE_BAJA
    
    private Integer intentos_fallidos;    // Reset a 0 en éxito, incrementar en fallo
    private LocalDateTime fecha_desbloqueo_cooldown; // Null = sin penalización
    private Boolean requiere_captcha;     // False inicialmente, True después 3 intentos
    
    private LocalDateTime fecha_baja;     // Campo de borrado lógico
    private LocalDateTime fecha_registro;
}
```

### Notas Importantes de Implementación

- **Moderación de Contenido** (Sprint 1): Usar `better-profanity` para nombres/nicknames en registro
- **Moderación de Contenido** (Sprint 2+): Backend debe validar títulos/descripciones de eventos (Módulo 2, RF-2.2)
- **Seguimiento de Estado**: Usuarios tienen `estado_usuario` (ACTIVO, BLOQUEADO, etc.) — verificar en login
- **Rate Limiting**: Rastrear intentos fallidos en tabla `Historial_Interacciones` por email/IP
- **Bloqueo de Cuenta**: Después de exactamente 3 intentos fallidos, establecer estado = "BLOQUEADO" + enviar email de seguridad (Sprint 2)
- **Eliminación Lógica**: Usar campo `fecha_baja`; nunca hard-delete usuarios o eventos
- **RBAC (Backend)**: Siempre validar roles en capa de servicio vía `@PreAuthorize` o verificaciones manuales; frontend puede renderizar UI condicionalmente, pero backend es la autoridad final
- **Integración Cloudinary**: Planeada para Sprint 2 (Módulo 2 - Imágenes de Eventos); por ahora saltar cargas de imagen
- **Analítica** (Futuro): Agregar visualizaciones (únicas por usuario) y calificación promedio de tabla `Valoracion` para dashboards de eventos

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
