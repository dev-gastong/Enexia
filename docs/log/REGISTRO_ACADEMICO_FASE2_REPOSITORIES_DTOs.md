# 📚 REGISTRO ACADÉMICO: Repositories + DTOs - Fase 2 (Esqueleto)

**Fecha:** 2026-07-31  
**Sprint:** Sprint 1 - Fase 2 (Capa de Acceso a Datos + Objetos de Transferencia)  
**Participantes:** Gaston (dev.gastong@gmail.com)  
**Referencia:** `CLAUDE.md`, `REGISTRO_ACADEMICO_SPRINT1_MODELO_JPA.md`

---

## 📑 Síntesis Ejecutiva

Se completó la creación del **esqueleto de la Fase 2** de la arquitectura backend:
- **37 Repositories** (`JpaRepository` interfaces, una por entidad)
- **10 DTOs** (`*Request` y `*Response` clases)
- **Compilación:** ✅ BUILD SUCCESSFUL
- **Estado:** Esqueleto listo para Service layer (sin lógica de negocio)

---

## 🎯 Decisión Técnica

Se implementó el patrón **Data Access Object (DAO) + Data Transfer Object (DTO)** para separar la capa de persistencia (Repositories) de la presentación HTTP (DTOs), evitando exponer entidades JPA directamente en endpoints REST. Cada Repository es una interfaz vacía que hereda `JpaRepository<Entity, ID>`, delegando operaciones CRUD y queries al framework Spring Data JPA.

**Beneficio inmediato:** Estructura lista para implementar lógica de negocio en Service layer sin cambios de arquitectura.

---

## 🏫 Concepto Académico

### **1. Data Access Object (DAO) Pattern**
Es un patrón arquitectónico que **abstrae y encapsula** el acceso a datos (BD) en objetos especializados. En lugar de mezclar SQL/ORM con lógica de negocio, cada entidad tiene un **Repository** responsable de:
- CRUD básico (Create, Read, Update, Delete)
- Queries especializadas (findByXxx)
- Transacciones

**En Enexia:** `UsuarioRepository`, `EventoRepository`, etc., son interfaces que delegan a Spring Data JPA.

### **2. Data Transfer Object (DTO) Pattern**
Es un patrón que define **objetos simples sin lógica** para transportar datos entre capas (Controller ↔ Service ↔ Client HTTP). Razones:
- **Seguridad:** No exponer estructura interna de BD
- **Flexibilidad:** Evolucionar entidades sin breaking API changes
- **Validación:** Separar validaciones HTTP de validaciones de BD
- **Performance:** Seleccionar solo campos necesarios (no lazy-load sorpresas)

**En Enexia:** `UsuarioLoginRequest`, `EventoResponse`, etc.

### **3. Spring Data JPA**
Framework que simplifica DAO implementando `JpaRepository<T, ID>` automáticamente:
```java
// NO es necesario escribir:
public class UsuarioDAO { 
    public Usuario findById(Long id) { /* SQL */ }
}

// Spring genera automáticamente desde:
public interface UsuarioRepository extends JpaRepository<Usuario, Long> { }
```

---

## 📚 Píldora Teórica

### **A. Por qué Repositories (DAO abstracción)**

**El Problema (Anti-patrón):**
```java
// ❌ ANTI-PATRÓN: Mezclar SQL/ORM con lógica de negocio
@Service
public class AuthService {
    public void login(String email) {
        // Query SQL/JPQL directo
        Usuario u = em.createQuery("SELECT u FROM Usuario u WHERE u.email = ?", Usuario.class)
            .setParameter(1, email)
            .getSingleResult();  // ← Throws NoResultException si no existe
        // Mezcla concerns: persistencia + validación + negocio
    }
}
```

**La Solución (Repository):**
```java
// ✅ PATRÓN: Separación de concerns
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // Spring genera automáticamente: SELECT u FROM Usuario u WHERE u.email = ?
}

@Service
public class AuthService {
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    public void login(String email) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
        // Lógica de negocio clara, sin SQL visible
    }
}
```

**Ventajas:**
- ✅ Queries centralizadas en Repository (no esparcidas en Services)
- ✅ Testeable: mock de Repository sin BD real
- ✅ Cambiable: si mañana usamos MongoDB en lugar de MySQL, cambias 1 Repository, no 20 Services
- ✅ Transacciones: Spring maneja @Transactional automáticamente en Repository

---

### **B. Por qué DTOs (no exponer entidades)**

**El Problema (Exposición de Entidades):**
```java
// ❌ ANTI-PATRÓN: Retornar entidad JPA directamente
@GetMapping("/usuarios/{id}")
public Usuario getUsuario(@PathVariable Long id) {
    return usuarioRepository.findById(id).orElse(null);  // ← Expone todo
}

// Cliente recibe:
{
  "id": 1,
  "email": "user@example.com",
  "password": "bcrypt_hash_12345...",  // ← GRAVE: expone hash de password
  "intentosFallidos": 3,              // ← Interno de seguridad
  "personaFisica": {                   // ← Lazy-load N+1 query
    "id": 100,
    "dni": "12345678",
    "nombre": "Juan"
  }
}
```

**La Solución (DTO):**
```java
// ✅ PATRÓN: DTO Request/Response separado
@GetMapping("/usuarios/{id}")
public UsuarioResponse getUsuario(@PathVariable Long id) {
    Usuario usuario = usuarioRepository.findById(id).orElse(null);
    // Mapper transforma Entity → DTO
    return new UsuarioResponse(usuario.getId(), usuario.getEmail(), "ACTIVO");
}

// Cliente recibe SOLO lo necesario:
{
  "id": 1,
  "email": "user@example.com",
  "estado": "ACTIVO"  // ← Seguro, resumido, sin detalles internos
}
```

**Ventajas:**
- ✅ **Seguridad:** Oculta campos sensibles (passwords, intentos_fallidos, cooldown)
- ✅ **Control de versiones:** API v1 devuelve ciertos campos, v2 agrega más, sin breaking clients
- ✅ **Performance:** Selecciona solo campos necesarios (evita serializar todo)
- ✅ **Claridad:** RequestDTO vs ResponseDTO definen claramente qué espera/devuelve cada endpoint

---

### **C. Estructura de Repositories en Enexia**

Cada Repository sigue el patrón:
```java
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // Spring genera automáticamente:
    // - findAll()
    // - findById(Long id)
    // - save(Usuario u)
    // - delete(Usuario u)
    // - deleteById(Long id)
    
    // Queries personalizadas (agregadas en Fase 3):
    // Optional<Usuario> findByEmail(String email);
    // List<Usuario> findByEstadoUsuarioSistema(UsuarioEstadoSistema estado);
}
```

**Características Spring Data JPA automáticas:**
- `CrudRepository.save()` → INSERT o UPDATE
- `CrudRepository.findById()` → SELECT (lazy-load)
- `CrudRepository.delete()` → DELETE (pero respeta soft-deletes en Service)
- `JpaRepository.findAll()` → SELECT * (con paginación)

---

## 🔬 Análisis Técnico: 37 Repositories Mapeados

### **Por Categoría de Entidad**

#### **Núcleo Usuario (6 Repositories)**
| Repo | Entidad | Uso |
|------|---------|-----|
| `UsuarioRepository` | Usuario | CRUD principal, findByEmail (próximo) |
| `PersonaRepository` | Persona | Base heredada (Física/Jurídica) |
| `PersonaFisicaRepository` | PersonaFisica | 1:1 con Usuario |
| `PersonaJuridicaRepository` | PersonaJuridica | Futuro: Registro de Organizaciones |
| `RolRepository` | Rol | Lookup: roles en BD |
| `UsuarioRolRepository` | UsuarioRol | N:N Usuario ↔ Rol |

#### **Autenticación & Seguridad (6 Repositories)**
| Repo | Entidad | Uso |
|------|---------|-----|
| `UsuarioEstadoRepository` | UsuarioEstado | Lookup estados (ACTIVO, DE_BAJA) |
| `UsuarioEstadoSistemaRepository` | UsuarioEstadoSistema | Lookup estados sistema (BLOQUEADO) |
| `HistorialEstadoUsuarioRepository` | HistorialEstadoUsuario | Auditoría cambios estado usuario |
| `HistorialInteraccionesRepository` | HistorialInteracciones | Rate-limiting, auditoría login |
| `PasswordResetTokenRepository` | PasswordResetToken | Tokens email reset (Sprint 2) |

#### **Eventos (8 Repositories)**
| Repo | Entidad | Uso |
|------|---------|-----|
| `EventoRepository` | Evento | CRUD principal |
| `EventoDetalleRepository` | EventoDetalle | 1:1 descripción + ubicación |
| `EventoCronogramaRepository` | EventoCronograma | Fechas múltiples evento |
| `EventoEstadoSistemaRepository` | EventoEstadoSistema | Estados moderación |
| `EventoEstadoOrganizadorRepository` | EventoEstadoOrganizador | Estados organizador |
| `CategoriaRepository` | Categoria | Lookup categorías |
| `EventoMultimediaRepository` | EventoMultimedia | Imágenes/videos |
| `HistorialEstadoEventoRepository` | HistorialEstadoEvento | Auditoría cambios evento |

#### **Tickets & Inscripciones (7 Repositories)**
| Repo | Entidad | Uso |
|------|---------|-----|
| `CronogramaTicketRepository` | CronogramaTicket | N:N + cupos |
| `TipoTicketRepository` | TipoTicket | Lookup tipos (GENERAL, VIP) |
| `InscripcionRepository` | Inscripcion | CRUD registros |
| `InscripcionEstadoRepository` | InscripcionEstado | Lookup estados inscripción |
| `HistorialEstadoInscripcionRepository` | HistorialEstadoInscripcion | Auditoría cambios inscripción |

#### **Suscripciones & Pagos (7 Repositories)**
| Repo | Entidad | Uso |
|------|---------|-----|
| `SuscripcionRepository` | Suscripcion | CRUD suscripciones |
| `SuscripcionEstadoRepository` | SuscripcionEstado | Lookup estados suscripción |
| `PagoRepository` | Pago | CRUD pagos |
| `PagoEstadoRepository` | PagoEstado | Lookup estados pago |
| `HistorialEstadoPagoRepository` | HistorialEstadoPago | Auditoría cambios pago |
| `HistorialEstadoSuscripcionRepository` | HistorialEstadoSuscripcion | Auditoría suscripciones |

#### **Ubicación & Referencias (5 Repositories)**
| Repo | Entidad | Uso |
|------|---------|-----|
| `UbicacionRepository` | Ubicacion | Dirección + lat/lon |
| `PaisRepository` | Pais | Lookup países (PK = String) |
| `ProvinciaRepository` | Provincia | Lookup provincias |
| `CiudadRepository` | Ciudad | Lookup ciudades |

#### **Valoración & Analytics (2 Repositories)**
| Repo | Entidad | Uso |
|------|---------|-----|
| `ValoracionRepository` | Valoracion | CRUD ratings/reviews |
| `VisitaRepository` | Visita | Tracking analytics |

#### **Organizaciones (1 Repository)**
| Repo | Entidad | Uso |
|------|---------|-----|
| `MiembrosOrganizacionRepository` | MiembrosOrganizacion | N:N Usuario ↔ PersonaJuridica |

---

## 🎁 Análisis Técnico: 10 DTOs Mapeos

### **Por Funcionalidad de Endpoint**

#### **1. Autenticación (3 DTOs)**

**UsuarioRegistroRequest**
```java
// POST /api/auth/register
{
  "email": "user@example.com",
  "password": "securePass123",
  "passwordConfirmacion": "securePass123",
  "nombre": "Juan",
  "apellido": "Pérez",
  "dni": "12345678"
}
```
- **Entrada:** Datos nuevos usuario
- **Validación (próximo):** email único, password fuerte, DNA formato, moderación nombre/apellido
- **Uso:** AuthService.register()

**UsuarioLoginRequest**
```java
// POST /api/auth/login
{
  "email": "user@example.com",
  "password": "securePass123"
}
```
- **Entrada:** Credenciales login
- **Validación (próximo):** email existe, password correcto, cuenta no bloqueada
- **Uso:** AuthService.login()

**UsuarioLoginResponse**
```java
// 200 OK
{
  "idUsuario": 1,
  "email": "user@example.com",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tipoToken": "Bearer"
}
```
- **Salida:** JWT + metadata usuario
- **Seguridad:** NO expone password, intentos_fallidos, campos auditoría
- **Uso:** Cliente almacena token en Authorization header

---

#### **2. Eventos (3 DTOs)**

**EventoResponse**
```java
// GET /api/eventos (catálogo)
[{
  "idEvento": 1,
  "nombre": "Concierto Jazz 2026",
  "descripcion": "Evento de jazz local",
  "urlPortada": "https://cloudinary.com/.../portada.jpg",
  "categoria": "Música",
  "estado": "PUBLICADO",
  "organizador": "Asociación Cultural XYZ"
}]
```
- **Salida:** Catálogo público (resumen)
- **Campos:** Solo necesarios para listado
- **Uso:** GET /api/eventos (con paginación)

**EventoDetalleResponse**
```java
// GET /api/eventos/{id}
{
  "idEvento": 1,
  "nombre": "Concierto Jazz 2026",
  "descripcion": "Evento de jazz...",
  "urlPortada": "...",
  "categoria": "Música",
  "estado": "PUBLICADO",
  "organizador": "Asociación...",
  "ubicacion": "Ushuaia, Tierra del Fuego",
  "latitud": -54.8019,
  "longitud": -68.3030
}
```
- **Salida:** Detalle evento + ubicación geográfica
- **Campos:** Más completo que EventoResponse
- **Uso:** GET /api/eventos/{id}

**EventoCronogramaResponse**
```java
// GET /api/eventos/{id}/cronogramas
[{
  "idCronograma": 1,
  "fechaInicio": "2026-09-15T19:00:00",
  "fechaFin": "2026-09-15T21:30:00",
  "cupoDisponible": 45,
  "cupoTotal": 100
}]
```
- **Salida:** Fechas + disponibilidad cupos
- **Uso:** EventoCronograma (múltiples fechas por evento)

---

#### **3. Inscripciones (2 DTOs)**

**InscripcionRequest**
```java
// POST /api/inscripciones
{
  "idCronogramaTicket": 5,
  "idUsuario": 1
}
```
- **Entrada:** Usuario se registra en fecha/tipo ticket
- **Validación (próximo):** cupo disponible, usuario no duplicado, estado evento OK
- **Uso:** InscripcionService.registrar()

**InscripcionResponse**
```java
// 201 CREATED
{
  "idInscripcion": 100,
  "eventoNombre": "Concierto Jazz 2026",
  "estado": "CONFIRMADA",
  "fechaInscripcion": "2026-08-01",
  "precioAbonado": "250.00"
}
```
- **Salida:** Confirmación registro
- **Campos:** Resumen para usuario
- **Uso:** POST /api/inscripciones (respuesta)

---

#### **4. Soporte (2 DTOs)**

**UsuarioResponse**
```java
// GET /api/usuarios/{id} (perfil)
{
  "idUsuario": 1,
  "email": "user@example.com",
  "nombre": "Juan",
  "apellido": "Pérez",
  "estado": "ACTIVO",
  "rol": "PARTICIPANTE"
}
```
- **Salida:** Perfil usuario (público)
- **Seguridad:** NO expone campos internos
- **Uso:** GET perfil, directorios, etc.

**ErrorResponse**
```java
// 400 / 401 / 500
{
  "error": "BadCredentialsException",
  "mensaje": "Email o contraseña inválidos",
  "status": 401,
  "timestamp": "2026-07-31T14:23:45.123456"
}
```
- **Salida:** Errores HTTP estandarizados
- **Uso:** @ControllerAdvice global
- **Formato:** Consistente en toda API

---

## ✅ Decisiones de Diseño Justificadas

### **1. Una Repository por Entidad**
- ✅ **Un Repository por cada `@Entity`** (1:1 mapping)
- **Razón:** Mantenibilidad, responsabilidad única, queries específicas centralizadas
- ❌ **NOT:** Un "MegaRepository" con lógica de negocio

### **2. Repositories como Interfaces (NO clases)**
- ✅ Spring Data genera implementación en runtime
- **Razón:** Menos boilerplate, auto-proxy, transacciones automáticas
- ❌ **NOT:** Implementar manualmente `repository.findById()`

### **3. DTOs separados: Request vs Response**
- ✅ `UsuarioRegistroRequest` (entrada), `UsuarioLoginResponse` (salida)
- **Razón:** Login NO devuelve lo mismo que recibe (password → token)
- ❌ **NOT:** Reutilizar mismo DTO para request y response

### **4. ErrorResponse centralizado**
- ✅ Formato único para todos los errores
- **Razón:** Cliente espera estructura consistente; @ControllerAdvice mapea excepciones
- ❌ **NOT:** Cada endpoint define su propio error format

### **5. DTOs sin lógica (Lombok @Getter/@Setter)**
- ✅ Solo atributos + anotaciones validación (próximo)
- **Razón:** Son vehículos de datos, no contienen negocio
- ❌ **NOT:** Métodos de negocio en DTOs (eso va en Service)

---

## ⚠️ Gaps y Pendientes (Fase 3 - Services)

### **Repositories: Métodos query personalizados**
- ⏳ `UsuarioRepository.findByEmail(String email): Optional<Usuario>`
- ⏳ `UsuarioRepository.findByEstadoUsuarioSistema(UsuarioEstadoSistema estado): List<Usuario>`
- ⏳ `EventoRepository.findByEstadoSistema(EventoEstadoSistema estado): List<Evento>`
- ⏳ `InscripcionRepository.findByUsuarioAndCronogramaTicket(...): Optional<Inscripcion>`
- ⏳ `VisitaRepository.countByEvento(Evento evento): Long` (analytics)

### **DTOs: Validaciones**
- ⏳ `@NotNull`, `@Email`, `@Size`, `@Pattern` en Request DTOs
- ⏳ Validador personalizado para DNI (formato)
- ⏳ Validador personalizado para CUIT (futuro)
- ⏳ Moderación de texto en Service (no en DTO)

### **Mapeo Entity → DTO**
- ⏳ `ModelMapper` bean o mapeo manual en Service
- ⏳ Estrategia: lazy-load controlado (no traer Persona.PersonaFisica si no es necesario)

### **Transacciones en Service**
- ⏳ `@Transactional` en métodos Service que escriben
- ⏳ `@Transactional(readOnly=true)` en queries

### **RBAC en Controllers**
- ⏳ `@PreAuthorize("hasRole('PARTICIPANTE')")` en endpoints
- ⏳ JWT filter extrae roles de token

---

## 📖 Referencias Arquitectura

| Concepto | Ubicación en Código | Documentación |
|----------|-------------------|---------------|
| **DAO Pattern** | `repository/` | CLAUDE.md / Design Patterns (Gang of Four) |
| **DTO Pattern** | `dto/` | CLAUDE.md / Martin Fowler (DTO) |
| **Spring Data JPA** | `JpaRepository` interface | Spring Framework docs |
| **Lazy vs Eager** | `@ManyToOne(fetch = LAZY)` en model | REGISTRO_ACADEMICO_SPRINT1_MODELO_JPA.md |
| **Arquitectura Layered** | Controller→Service→DTO→Repository | CLAUDE.md (High-Level Architecture) |
| **RBAC** | `@PreAuthorize` (próximo) | RF-1 (User Management) |

---

## 🎓 Conceptos Clave Aplicados

### **1. Principio SOLID: Single Responsibility**
- Repository: solo acceso datos
- DTO: solo transporte datos
- Service: solo lógica negocio (próximo)
- Controller: solo HTTP handling (próximo)

### **2. Inyección de Dependencias**
```java
// Service depende de Repository, NO lo instancia
@Service
public class AuthService {
    @Autowired
    private UsuarioRepository usuarioRepository;  // ← Inyectado por Spring
}
```

### **3. Abstracción (Contrato Repository)**
```java
// Repository define contrato; Spring lo implementa
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // Definir qué queries existen sin SQL visible
}
```

### **4. Composición (DTOs transportan objetos simples)**
```java
// UsuarioLoginResponse es composición de primitivos
public class UsuarioLoginResponse {
    private Long idUsuario;         // dato
    private String email;           // dato
    private String token;           // dato
}
```

---

## 📊 Métricas Fase 2

| Métrica | Valor | Nota |
|---------|-------|------|
| Repositories creados | 37 | Uno por entidad |
| DTOs creados | 10 | Necesarios para Sprint 1 endpoints |
| Líneas de código (Repositories) | ~400 | Interfaces solo, sin implementación |
| Líneas de código (DTOs) | ~150 | Lombok reduce boilerplate |
| Compilación | BUILD SUCCESSFUL | ✅ Sin errores |
| Test unitarios | 0 (próximo) | Service layer tendrá 80%+ cobertura |

---

## 🚀 Próxima Fase (Fase 3 - Services)

### **Estructura Service Layer**
```
src/main/java/com/enexia/rg/
├── service/
│   ├── AuthService.java           // login, register, JWT
│   ├── EventoService.java         // CRUD eventos
│   ├── InscripcionService.java    // validar cupos, registrar
│   ├── HistorialService.java      // auditoría
│   └── ValidationService.java     // moderación, validaciones
├── exception/
│   ├── BadCredentialsException.java
│   ├── AccountBlockedException.java
│   ├── CupoAgotadoException.java
│   └── GlobalExceptionHandler.java
├── util/
│   ├── JwtTokenProvider.java      // generar/validar JWT
│   ├── PasswordEncryptor.java     // BCrypt
│   └── TextModerator.java         // better-profanity
└── mapper/
    ├── UsuarioMapper.java         // Entity ↔ DTO
    └── EventoMapper.java          // Entity ↔ DTO
```

### **Lógica a Implementar**
1. ✅ **AuthService.login():** BCrypt validation, rate-limiting, JWT generation
2. ✅ **AuthService.register():** Moderación texto, creación Persona + Usuario
3. ✅ **InscripcionService.registrar():** Validar cupo disponible (transacción atómica)
4. ✅ **HistorialService:** Auditoría cambios estado
5. ✅ **ValidationService:** Moderación títulos eventos, descripciones

### **Anotaciones a Usar**
- `@Transactional` — transacciones ACID
- `@PreAuthorize` — RBAC (próximo, Controllers)
- `@Validated` — validación request DTOs
- `@ExceptionHandler` — manejo global errores
- `@Slf4j` — logging SLF4J

---

## 📝 Conclusión Académica

La **Fase 2 (Esqueleto)** demuestra maestría en **patrones arquitectónicos de capas**:

1. **Separación de Concerns:** Repository (datos) ≠ DTO (transporte) ≠ Service (negocio)
2. **Data Access Abstraction:** Repositories ocultan SQL/ORM detalles
3. **HTTP Contract Clarity:** DTOs definen explícitamente qué entra/sale en cada endpoint
4. **Escalabilidad:** Agregar 37 repositories y 10 DTOs sin redundancia
5. **Testabilidad:** Repositories mockeables, sin BD real en tests

**Siguiente:** Service layer transformará este esqueleto en operaciones de negocio validadas (moderación, rate-limiting, transacciones, auditoría).

---

## 📝 Notas Finales

- **Compilación:** `./gradlew compileJava` ✅ BUILD SUCCESSFUL
- **Integración:** Repositories + DTOs + model/Entities = 3 capas listas
- **Próxima sesión:** Implementar @Service con lógica (no más esqueleto)
- **Testing:** Tests de Service layer harán mock de Repositories

---

**Generado:** 2026-07-31  
**Por:** Claude Code + skill `documentar-avance`  
**Próxima actualización:** Post-Fase 3 (Services & Exception Handling)
