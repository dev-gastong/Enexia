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

- **Persona**: Identidad de una persona HUMANA, nada mas. Supertipo de `Persona_Fisica` y unica identidad que puede tener `Usuario` (login).
  - **Cambio de Sprint 2 (2026-09-08):** se **elimino** la columna `tipo_persona`. Suponia una jerarquia `Persona -> (Fisica | Juridica)` que nunca existio: el MER solo declara `Persona ||--|| Persona_Fisica`, y `Persona_Juridica` no tiene FK a `Persona`. La columna valia `"FISICA"` en el 100% de las filas, y admitir `"JURIDICA"` habria permitido crear una persona juridica sin persona fisica asociada, rompiendo el invariante de que todo `Usuario` es un ser humano.
- **Persona_Juridica**: contenedor administrativo, **no es identidad de acceso**. Se vincula a las personas por `Miembros_Organizacion` (RF-7.2).
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
- **Rate limiting por IP**: ❌ **eliminado** (ADR-09). El conteo es por cuenta, nunca por IP.
- **Bloqueo de Cuenta**: silencioso, a los 3 intentos fallidos → `estado_usuario` = `BLOQUEADO`. Ver la política de fallos de login más abajo.
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

## Sprint 2: Organizaciones, Eventos y Catálogo Público (Actual)

**Estado:** backend completo; frontend pendiente (a la espera de los diseños de Figma).

| Módulo | Alcance entregado |
|---|---|
| **M1** — Autenticación | Bloqueo silencioso de cuenta, eliminación del rate limiting por IP, recuperación de contraseña y desbloqueo por email (RF-1.5) |
| **M7** — Organizaciones | Alta de `Persona_Juridica` por los dos caminos, validación de CUIT módulo 11 (RF-7.3), `Miembros_Organizacion`. *Control por revisión eliminado el 2026-09-09 (ADR-14) — la especificación aprueba en el acto; el código todavía escribe `REVISION_PENDIENTE`.* |
| **M2** — Eventos | Creación con pipeline asíncrono de moderación (RF-2.1 a RF-2.6), integración Cloudinary (RF-2.3), dashboard del organizador (RF-2.8), baja lógica (RF-2.9), estadísticas (RF-2.10) |
| **M4** — Interfaz pública | Catálogo paginado, búsqueda por texto, filtros por categoría/fecha/ubicación, ficha técnica, registro pasivo de visitas (RF-4.1 a RF-4.5) |
| **M5** — Moderación | Fase de texto + fase de imágenes, secuencial y asíncrona (RF-5.1 a RF-5.3) |

### ⚠️ Cambio de política de fallos de login (2026-09-08, decisión del usuario)

**1. Se ELIMINÓ el rate limiting por IP.**
Detrás de un CGNAT o del wifi de una institución, cientos de dispositivos legítimos comparten una única IP pública. Bloquear esa IP dejaba sin servicio a toda una zona por culpa de un solo atacante: una denegación de servicio que el propio atacante podía provocar a voluntad.

> **Riesgo abierto y asumido:** sin control por IP, el *password spraying* (una contraseña común contra miles de emails) ya no tiene freno propio, porque ninguna cuenta acumula fallos. **Resuelto el 2026-09-09 (ADR-13):** el bloqueo al 3° fallo por cuenta, junto con un rechazo indistinguible en cuerpo, cabeceras y tiempo — y un endpoint de recuperación igual de opaco — le quita al atacante tanto el margen de intentos como la señal de enumeración que necesita. Alertar por volumen anómalo en `historial_interacciones`, **sin** rechazar peticiones, queda como mejora deseable.

**2. El bloqueo de cuenta pasó a ser SILENCIOSO.**
Todos los rechazos de login responden idéntico: `401`, código `CREDENCIALES_INVALIDAS`, mismo mensaje, sin cabeceras extra y con el mismo costo en tiempo. El atacante no puede distinguir "el email no existe" de "la contraseña está mal" ni de "la cuenta está bloqueada": ni por el cuerpo, ni por el status, ni por las cabeceras, ni por el reloj.

- Todos los fallos heredan de `AutenticacionFallidaException`, que lleva un código **interno** (`EMAIL_INEXISTENTE`, `PASSWORD_INCORRECTA`, `CUENTA_BLOQUEADA`, `CUENTA_EN_COOLDOWN`, `CUENTA_SUSPENDIDA`). Ese código va al log y a `historial_interacciones`, **nunca** a la respuesta HTTP.
- `GlobalExceptionHandler` tiene **un solo** handler para toda la familia. **No agregar un `@ExceptionHandler` más específico** para ninguna subclase: Spring elegiría el más específico y la respuesta volvería a delatar el estado de la cuenta.
- Cada rama de rechazo paga una comparación BCrypt (hash señuelo) para que el tiempo de respuesta sea constante.
- Se quitó la cabecera `X-Reintentar-Despues`: leída desde las DevTools, confirmaba que la cuenta existe y está penalizada.
- **Al titular legítimo se le avisa por email**, con enlace de recuperación de un solo uso (`RecuperacionCuentaService`): el único canal que el atacante no controla.

**3. Umbral de bloqueo — DECISIÓN FINAL 2026-09-09: bloqueo al 3° fallo.**
La antigua escalera de penalización (3 → captcha + cooldown de 5 min, 6 → cooldown de 30 min, 9 → bloqueo) **desaparece de la especificación**. La regla es ahora un único escalón, contado **por cuenta** (`usuario.intentos_fallidos`), nunca por IP:

- 3 fallos consecutivos → `estado_usuario = BLOQUEADO` + email de seguridad al titular
- Cualquier login exitoso → contador a 0

El correo incluye un **enlace de desbloqueo directo** — `/unlock-account?token=XYZ`, token de un solo uso y con vencimiento acotado. Al consumirlo, se pone `estado_usuario = ACTIVO`, `intentos_fallidos = 0`, se limpia `fecha_desbloqueo_cooldown` y se invalida el token. El titular vuelve a entrar con sus **credenciales habituales**, sin cambiar la contraseña.

Con esto queda cerrada la divergencia de RF-1.4: `docs/requisitos/requisitos_funcionales/modulo_1.md` y `docs/diagrams/login_registro/login.md` se reescribieron el 2026-09-09 y ahora coinciden. CAPTCHA y 2FA quedan fuera de alcance y se quitaron del DFD de login.

> ### ⚠️ ACÁ LA DOCUMENTACIÓN VA POR DELANTE DEL CÓDIGO
> Lo de arriba (escalón único al 3° fallo + endpoint de desbloqueo) es el **objetivo**. Lo que hoy ejecutan `AuthService` / `IntentosLoginService` sigue siendo la escalera 3/6/9, y `/unlock-account` **todavía no existe** — `RecuperacionCuentaService` solo emite el enlace de restablecimiento de contraseña. Alinear el código es trabajo pendiente; hasta entonces, el código refleja el comportamiento viejo y esta sección refleja el requisito.

### Registro de Persona Jurídica: los dos caminos

La documentación describe dos flujos y ninguno invalida al otro, así que **ambos se implementaron sobre el mismo método de servicio** (`PersonaJuridicaService.crearOrganizacion`), de modo que no puede haber dos reglas de negocio que diverjan:

| Punto de entrada | De dónde sale | Quién puede llamarlo |
|---|---|---|
| `POST /api/auth/registro/organizacion` | DFD 7.1/7.2 (la bifurcación Física/Jurídica **dentro** del formulario de registro) | Público — crea cuenta personal + organización en una transacción |
| `POST /api/organizador/organizaciones` | RF-7.2 — una organización adicional sobre una cuenta que ya existe | `ORGANIZADOR` autenticado |

> **"Flujo separado" significaba entidad separada, no momento separado.** La redacción vieja de RF-7.2 ("no como parte del registro inicial") se leía como una prohibición del primer punto de entrada y contradecía la mitad de la implementación. RF-7.2 se reescribió el 2026-09-09: el alta de la organización es un proceso propio, con sus propias validaciones, independientemente de *cuándo* lo ejecute la persona. Los dos caminos son legítimos.

**La cuenta que se crea es siempre la de la persona humana.** No existe un login "de empresa".

##### Resolución del alta — REESCRITO 2026-09-09 (ADR-14): sin estado de revisión

El alta se resuelve **de forma inmediata, en la misma petición**:
- CUIT válido según el módulo 11 de RF-7.3 → `APROBADO` + `ACTIVO` en el acto, habilitada para publicar.
- CUIT inválido → se rechaza la petición completa, no se persiste nada, el CUIT queda libre para reintentar.

**No hay `REVISION_PENDIENTE` en el alta ni aprobación diferida.** Verificar la existencia real contra el padrón de AFIP/ARCA exige clave fiscal y certificado digital: no hay servicio público, gratuito y estable. Sostener un estado de revisión que ningún proceso podía cerrar dejaba a toda organización permanentemente inhabilitada para publicar, y volvía inalcanzable la firma corporativa de RF-7.4. Resolver con el único control realmente disponible es mejor que una puerta sin llave.

> **Limitación asumida:** el módulo 11 es **aritmético, no probatorio**. Acredita que el CUIT está bien formado, no que exista una entidad detrás. Un CUIT inventado cuyo dígito verificador cierre será aceptado.

> `REVISION_PENDIENTE` y `RECHAZADO` **se conservan** en el catálogo y en el historial (el MER los declara). No se usan en el alta, pero hacen falta para la suspensión por parte de un administrador en el Módulo 6.

### ⚠️ `ddl-auto=update` NO PUEDE AGREGAR COLUMNAS EN ESTE ENTORNO

Detectado el 2026-09-08. Hibernate genera `ALTER TABLE IF EXISTS <t> ADD COLUMN ...`, y la MariaDB que trae XAMPP (**10.4.32**) no soporta `IF EXISTS` en un `ALTER TABLE`: responde **error 1064, de sintaxis**. Hibernate registra el fallo pero **no detiene el arranque**, así que la aplicación levanta con normalidad y la columna simplemente no existe. El síntoma aparece después, en tiempo de ejecución, como `Unknown column '...' in 'field list'`.

Venía roto en silencio desde Sprint 1: cuatro columnas de `persona_juridica` declaradas en la entidad desde el 2026-07-26 nunca existieron en la base. Nadie lo notó porque ninguna consulta tocaba esa tabla hasta que lo hizo el catálogo público.

**Regla para el equipo:** toda columna que se agregue a una `@Entity` de ahora en más tiene que ir también a un script de migración en `docs/diseño_bd/migraciones/`. El arreglo de fondo es actualizar MariaDB a **10.6+** (el mínimo que soporta Hibernate 7) o pasar a Flyway/Liquibase con `ddl-auto=validate`, que es lo que corresponde antes de producción.

```bash
# Aplicar la migración pendiente (NO es opcional)
mysql -u root -p enexia < docs/diseño_bd/migraciones/2026-09-08_sprint2.sql
```

### Endpoints principales

```
POST   /api/auth/registro                    público   alta de Persona Física
POST   /api/auth/registro/organizacion       público   alta PF + organización (DFD 7.1/7.2)
POST   /api/auth/login                       público
POST   /api/auth/recuperacion                público   pide enlace (RF-1.5)
POST   /api/auth/recuperacion/confirmar      público   consume el enlace y desbloquea

POST   /api/organizador/organizaciones       ORGANIZADOR   alta de organización (RF-7.2)
GET    /api/organizador/organizaciones       ORGANIZADOR
POST   /api/organizador/eventos              ORGANIZADOR   multipart: datos (JSON) + imagenes
GET    /api/organizador/eventos              ORGANIZADOR   dashboard paginado (RF-2.8)
DELETE /api/organizador/eventos/{id}         ORGANIZADOR   baja lógica (RF-2.9)
GET    /api/organizador/eventos/{id}/estadisticas         métricas (RF-2.10)

GET    /api/publico/eventos                  anónimo   catálogo + búsqueda + filtros
GET    /api/publico/eventos/{id}             anónimo   ficha técnica + registra visita
GET    /api/publico/categorias               anónimo
GET    /api/publico/provincias               anónimo
GET    /api/publico/provincias/{id}/ciudades anónimo
```

### Máquina de estados del evento

Dos ejes independientes; el catálogo público exige que **ambos** habiliten:

```
estado_sistema (moderación)          estado_organizador (dueño)
  EN_PROCESO                           PUBLICADO
    ├─→ APROBADO_SISTEMA   ← visible   CANCELADO
    ├─→ RECHAZADO_SISTEMA              DADO_DE_BAJA
    └─→ APROBADO_MANUAL    ← visible   FINALIZADO
        RECHAZADO_MANUAL
```

`motivo_codigo` vive en el catálogo `evento_estado_sistema` (según el MER), así que `RECHAZADO_SISTEMA` tiene **una fila por motivo**: `MODERACION_TEXTO`, `MODERACION_IMAGEN`, `SIN_IMAGENES_VALIDAS`, `ERROR_PIPELINE`.

### Dos trampas de Spring que ya costaron un error real

1. **Trabajo asíncrono y commit.** El pipeline se dispara publicando un evento de aplicación (`ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`), **nunca** llamando al servicio asíncrono directo desde un método `@Transactional`. La llamada directa arranca el hilo con la fila todavía sin confirmar e invisible para él: los eventos terminaban en `RECHAZADO_SISTEMA/ERROR_PIPELINE`.

2. **`@Transactional` y auto-invocación.** `REQUIRES_NEW` solo se aplica cuando la llamada cruza el proxy de Spring, o sea cuando viene de **otro** bean. Un método auxiliar que necesita su propia transacción dentro de la misma clase deja la anotación sin efecto, en silencio. Por eso `IntentosLoginService` y `VisitaService` son beans aparte.

### Cloudinary

Sin `CLOUDINARY_CLOUD_NAME` el servicio corre en **modo simulado**: valida formato y peso, no sube nada y aprueba por defecto. Un nombre de archivo que contenga `rechazar` se rechaza, y así se prueba la rama de rechazo sin credenciales.

---

## Sprint 1: Backend Autenticación MVP (completado)

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
- La cuenta personal queda `ACTIVO`, y la organización **aprobada y activa de inmediato** si el CUIT valida (ver la resolución del alta más arriba). Si no valida, se rechaza todo y no se persiste nada.
- Estos campos de estado pertenecen a la propia `Persona_Juridica` (`Persona_Juridica_Estado_Sistema` / `Persona_Juridica_Estado`), **no** al `Usuario_Estado` del fundador.
- El usuario queda vinculado a la PJ vía `Miembros_Organizacion` con `rol_en_empresa` = "ADMINISTRADOR", **de forma atómica con la organización misma** — una organización sin miembros es inadministrable y retiene su CUIT para siempre.

| Característica | Sprint 1 | Sprint 2+ |
|---|---|---|
| Registro de Usuarios (Persona Física) | ✅ | - |
| Registro de Usuarios (Persona Jurídica) | ✅ | - |
| Moderación de PJ (Revisión manual + seguimiento de estado) | ✅ | - |
| Autenticación con JWT | ✅ | - |
| ~~Rate Limiting (por IP)~~ | ❌ **ELIMINADO 2026-09-08** | ver ADR-09 |
| Bloqueo de cuenta silencioso en 3 intentos fallidos | ✅ *(en código sigue la escalera 3/6/9; la especificación quedó en un solo escalón el 2026-09-09)* | alinear código |
| Cooldown (penalización 5 / 30 min) | ✅ *(retirado de la especificación el 2026-09-09)* | quitar |
| Desbloqueo por enlace `/unlock-account?token=` | ❌ especificado, sin construir | siguiente |
| Moderación de texto (better-profanity) | ✅ | - |
| 2FA (Verificación por email) | ❌ fuera de alcance | - |
| CAPTCHA | ❌ fuera de alcance | - |
| Restablecimiento de contraseña por email (RF-1.5) | ✅ **hecho en Sprint 2** | - |

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
