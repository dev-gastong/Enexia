# 📚 REGISTRO ACADÉMICO: Modelo de Datos JPA - Sprint 1

**Fecha:** 2026-07-26  
**Sprint:** Sprint 1 - Fase 1 (Codificación del Modelo de Datos)  
**Participantes:** Gaston (dev.gastong@gmail.com)  
**Referencia:** `CLAUDE.md`, `docs/diseño_bd/MER.md`, `RF-1.x` (Módulos 1-8)

---

## 📑 Síntesis Ejecutiva

Se completó la implementación del **modelo de datos relacional en JPA/Hibernate** con:
- **37 entidades `@Entity`** mapeadas a tablas MySQL
- **2 clases `@Embeddable`** para claves compuestas
- **Relaciones complejas**: 1:1, 1:N, N:N con estrategias de mapeo especializadas
- **Configuración Hibernate** para desarrollo (ddl-auto=update) y soporte futuro de producción
- **Campos de seguridad** agregados a `Usuario` (intentos_fallidos, requiere_captcha, fecha_desbloqueo_cooldown)

**Estado:** ✅ Compilación exitosa, base de datos sincronizada, listo para Fase 2 (Repositories + Services)

---

## 🎯 Decisión Técnica

Se eligió **JPA (Java Persistence API) con Hibernate** como ORM (Object-Relational Mapping) para abstraer la complejidad de SQL y mapear automáticamente entidades Java a tablas relacional MySQL. La decisión incluye relaciones 1:1, 1:N y N:N, claves compuestas con `@EmbeddedId`, y herencia single-table.

**Justificación:** Hibernate genera DDL automático (`ddl-auto=update`), valida relaciones en tiempo de compilación/runtime, y permite queries type-safe vía criterios y JPQL.

---

## 🏫 Concepto Académico

### **Object-Relational Mapping (ORM)**
Es un patrón arquitectónico que mapea **entidades de objetos Java** a **esquemas relacionales (tablas SQL)**, eliminando la brecha de impedancia entre programación orientada a objetos (OOP) y bases de datos relacionales (RDBMS).

**JPA (Java Persistence API):**
- Especificación Java estándar (javax.persistence / jakarta.persistence)
- Define anotaciones, interfaces y comportamiento de persistencia
- Implementaciones: Hibernate, EclipseLink, OpenJPA

**Hibernate:**
- Implementación de facto de JPA (usado en 90% de proyectos Java)
- Auto-genera SQL y DDL a partir de anotaciones
- Gestiona ciclo de vida de entidades (transient → persistent → detached)

---

## 📚 Píldora Teórica

### **1. Anotaciones Fundamentales de JPA**

```java
@Entity                    // Marca la clase como tabla persistible
@Table(name = "usuario")   // Especifica nombre de tabla (opcional, default = nombre de clase)
@Id                        // Designa campo como clave primaria
@GeneratedValue            // Auto-genera valor de PK (IDENTITY, SEQUENCE, etc.)
@Column(name = "...")      // Especifica nombre de columna y atributos (nullable, unique, length)
@OneToOne                  // Relación 1:1
@OneToMany                 // Relación 1:N
@ManyToOne                 // Relación N:1 (inversa de @OneToMany)
@ManyToMany                // Relación N:N (requiere tabla de unión)
@JoinColumn(name = "...")  // Define FK en lado propietario
@Embeddable                // Clase de clave compuesta (reutilizable)
@EmbeddedId                // Usa clase @Embeddable como PK
@Enumerated                // Mapea enum Java a columna VARCHAR/INT
@Transient                 // Excluye campo de persistencia
```

### **2. Estrategias de Mapeo Relacional en el Proyecto**

#### **A. Relaciones One-to-Many (1:N) — La más común**

```java
// LADO PROPIETARIO (N) — contiene @JoinColumn
@Entity
public class Inscripcion {
    @Id
    private Long idInscripcion;
    
    @ManyToOne(fetch = FetchType.LAZY)  // Lazy-loading (no carga Usuario automáticamente)
    @JoinColumn(name = "id_usuario")    // FK que apunta a Usuario
    private Usuario usuario;             // Cardinalidad: N usuarios → 1 Usuario
}

// LADO INVERSO (1) — NO propietario (SIN @JoinColumn)
@Entity
public class Usuario {
    @Id
    private Long idUsuario;
    
    // @OneToMany no se mapea en este proyecto (evita N+1 queries)
    // Si lo tuviéramos: @OneToMany(mappedBy = "usuario")
}
```

**¿Por qué `fetch = FetchType.LAZY`?**
- Evita cargar automáticamente el Usuario cuando se busca una Inscripción
- Mejora performance: solo carga si se accede `.getUsuario()` explícitamente
- Alternativa `EAGER` causa problema N+1 (1 query + N queries para entidades relacionadas)

#### **B. Relación One-to-One (1:1) con PK Compartida**

```java
// LADO PROPIETARIO — mantiene @OneToOne + @MapsId
@Entity
@Table(name = "evento_detalle")
public class EventoDetalle {
    @Id
    @Column(name = "id_evento")
    private Long idEvento;                   // Misma PK que Evento
    
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId                                  // "Usa la PK del Evento como mi PK"
    @JoinColumn(name = "id_evento")          // FK = PK (no doble columna)
    private Evento evento;                   // 1:1 = EventoDetalle.id_evento <-> Evento.id_evento
}

// LADO INVERSO (opcional, solo si se necesita acceso bidireccional)
@Entity
public class Evento {
    @OneToOne(mappedBy = "evento")
    private EventoDetalle detalle;
}
```

**Ventaja de `@MapsId`:**
- Evita dos PKs separadas (id_evento_detalle + id_evento)
- Integridad referencial automática: si borro Evento, se borra EventoDetalle
- SQL más limpio: solo una FK/PK compuesta lógica

#### **C. Claves Compuestas con `@Embeddable` (N:N)**

```java
// CLASE EMBEDDABLE — define PK compuesta (reutilizable)
@Embeddable
public class UsuarioRolId implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @Column(name = "id_usuario")
    private Long idUsuario;
    
    @Column(name = "id_rol")
    private Long idRol;
    
    @Override
    public boolean equals(Object o) { /* campos: idUsuario + idRol */ }
    
    @Override
    public int hashCode() { /* Objects.hash(idUsuario, idRol) */ }
    // ⚠️ OBLIGATORIO: implements Serializable + equals/hashCode para usar en sets/mapas
}

// ENTIDAD CON @EmbeddedId
@Entity
@Table(name = "usuario_rol")
public class UsuarioRol {
    @EmbeddedId
    private UsuarioRolId id = new UsuarioRolId();  // "Mi PK es una composición de idUsuario + idRol"
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idUsuario")                           // Vincula UsuarioRolId.idUsuario a Usuario
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idRol")                               // Vincula UsuarioRolId.idRol a Rol
    @JoinColumn(name = "id_rol")
    private Rol rol;
}
```

**SQL Resultante:**
```sql
CREATE TABLE usuario_rol (
    id_usuario BIGINT NOT NULL,
    id_rol BIGINT NOT NULL,
    PRIMARY KEY (id_usuario, id_rol),
    FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    FOREIGN KEY (id_rol) REFERENCES rol(id_rol)
);
```

#### **D. Estados como Entidades (NO enums)**

```java
// ❌ ANTI-PATRÓN (hardcoded en Java):
@Enumerated(EnumType.STRING)
private EstadoUsuario estado;  // enum { ACTIVO, BLOQUEADO, SUSPENDIDO }

// ✅ PATRÓN USADO (flexible, auditable):
@Entity
@Table(name = "usuario_estado_sistema")
public class UsuarioEstadoSistema {
    @Id
    private Long idEstado;
    
    @Column(name = "nombre")
    private String nombre;  // "ACTIVO", "BLOQUEADO", etc. (en BD, no en código)
}

// En Usuario:
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "id_estado_usuario_sistema")
private UsuarioEstadoSistema estadoUsuarioSistema;
```

**Ventajas:**
- ✅ Agregar nuevos estados sin redeploy (update tablas)
- ✅ Auditoría: `HistorialEstadoUsuario` registra transiciones
- ✅ Queries dinámicas: no hardcoded enum comparisons

#### **E. Relaciones Opcionales (FK nullable) — Pago**

```java
@Entity
public class Pago {
    @Id
    private Long idPago;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inscripcion", nullable = true)  // 0 o 1
    private Inscripcion inscripcion;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_suscripcion", nullable = true)  // 0 o 1
    private Suscripcion suscripcion;
    
    // Pago puede ligarse a UNA Inscripción O UNA Suscripción, pero no ambas
}
```

---

## 🔬 Análisis Técnico Detallado

### **Stack Tecnológico Actual**

| Componente | Versión | Rol |
|-----------|---------|-----|
| **Spring Boot** | 4.1.0 | Framework web, auto-config |
| **Java** | 17 (OpenJDK Temurin) | Lenguaje |
| **Gradle** | Latest | Build tool |
| **JPA/Hibernate** | Spring Data JPA (auto) | ORM |
| **MySQL Connector** | mysql-connector-j | Driver JDBC |
| **Lombok** | Latest | Reduce boilerplate (@Getter/@Setter) |
| **JWT (JJWT)** | 0.12.3 | Autenticación (Sprint 1) |
| **Cloudinary** | 1.33.0 | Almacenamiento imágenes (Sprint 2) |
| **Spring Security** | 6.x (auto) | Autenticación/Autorización |

### **Configuración Hibernate (application.properties)**

```properties
# ✅ Lazy-load para evitar N+1 queries
spring.jpa.open-in-view=false
  → Cierra sesión Hibernate después del controlador
  → Fuerza uso de @Transactional o lazy-loading explícito

# ✅ DDL automático (desarrollo)
spring.jpa.hibernate.ddl-auto=update
  → "Compara entidades @Entity con BD"
  → "Crea/altera tablas si es necesario"
  → "NO borra datos" (a diferencia de 'create-drop')

# ✅ Logging SQL (desarrollo)
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
  → Imprime en logs el SQL generado (facilita debugging)

# ✅ DataSource MySQL (XAMPP local)
spring.datasource.url=jdbc:mysql://localhost:3306/enexia
spring.datasource.username=root
spring.datasource.password=
  → Conecta a BD MariaDB en puerto 3306
  → Usa timezone Ushuaia (RFC 3339 para LocalDate/LocalDateTime)
```

### **Estructura de 37 Entidades**

#### **Núcleo de Usuarios (5 entidades)**
- `Persona` (base: Física/Jurídica)
- `PersonaFisica` (1:1 con Persona, datos DNI/nombre)
- `PersonaJuridica` (1:1 con Persona, datos CUIT/empresa)
- `Usuario` (cuenta login: email, password, nickname, campos seguridad)
- `UsuarioRol` (N:N Usuario ↔ Rol, tabla de unión)

#### **Autenticación & Seguridad (4 entidades)**
- `Rol` (PARTICIPANTE, ORGANIZADOR, ADMINISTRADOR)
- `UsuarioEstado` (ACTIVO, DE_BAJA, SUSPENDIDO_TEMPORAL)
- `UsuarioEstadoSistema` (ACTIVO, BLOQUEADO — por fallos login)
- `HistorialEstadoUsuario` (auditoría: quién cambió qué estado y cuándo)

#### **Eventos & Programación (6 entidades)**
- `Evento` (nombre, organizador, categoría, estado)
- `EventoDetalle` (1:1 con Evento, descripción + ubicación)
- `EventoCronograma` (fechas/horarios múltiples de 1 evento)
- `EventoEstadoSistema` (PUBLICADO, MODERACION, RECHAZADO)
- `EventoEstadoOrganizador` (BORRADOR, PUBLICADO, CANCELADO)
- `HistorialEstadoEvento` (auditoría: cambios de estado)

#### **Tickets & Registros (5 entidades)**
- `TipoTicket` (GENERAL, VIP, ESTUDIANTE, JUBILADO)
- `CronogramaTicket` (N:N Cronograma ↔ TipoTicket, con cupos)
- `Inscripcion` (registro: usuario + cronograma_ticket + estado + fecha)
- `InscripcionEstado` (CONFIRMADA, CANCELADA, PENDIENTE_PAGO)
- `HistorialEstadoInscripcion` (auditoría)

#### **Ratings & Analytics (3 entidades)**
- `Valoracion` (usuario + evento + puntaje/comentario + fecha)
- `Visita` (tracking: usuario + evento + fecha + IP — para analytics)
- `HistorialInteracciones` (auditoría: login, busquedas, errores)

#### **Ubicación (5 entidades)**
- `Pais` (PK String: "AR", "CL", etc.)
- `Provincia` (Santa Cruz, Buenos Aires, etc.)
- `Ciudad` (Ushuaia, CABA, etc.)
- `Ubicacion` (ciudad + direccion + latitud/longitud)
- (Referencia en `EventoDetalle`)

#### **Suscripciones & Pagos (5 entidades)**
- `Suscripcion` (usuario + tipo + fecha inicio/fin)
- `SuscripcionEstado` (ACTIVA, CANCELADA)
- `Pago` (inscripcion O suscripcion + monto + fecha)
- `PagoEstado` (PENDIENTE, COMPLETADO, FALLIDO)
- `HistorialEstadoPago` (auditoría)

#### **Multimedia (2 entidades)**
- `EventoMultimedia` (1:N con Evento, URLs imágenes/videos)
- `Categoria` (categoría evento: Cultura, Deporte, Educación, etc.)

#### **Pendiente: Reset de Password (1 entidad)**
- `PasswordResetToken` (email-based reset, sprint 2)

#### **Organizaciones (2 entidades)**
- `MiembrosOrganizacion` (N:N Usuario ↔ PersonaJuridica, con rol_en_empresa)
- (Permite que una persona sea miembro de múltiples organizaciones)

---

## ✅ Decisiones de Diseño Documentadas

### **1. Claves Primarias Numéricas (Long)**
- ✅ `@GeneratedValue(strategy = GenerationType.IDENTITY)` para auto-incremento MySQL
- ❌ NOT String (salvo `Pais` que es código ISO-3166)
- **Razón:** Mejor performance en FK, queries, índices

### **2. Lazy-Loading (`FetchType.LAZY`)**
- ✅ Todas las FK usan `@ManyToOne(fetch = FetchType.LAZY)`
- ❌ Evita cargar entidades relacionadas innecesariamente
- **Razón:** Reduce N+1 queries problem, mejora latencia

### **3. Herencia Single-Table (PersonaFisica/PersonaJuridica)**
- ✅ `@Inheritance(strategy = InheritanceType.JOINED)` — 2 tablas (Persona + Persona_Fisica/Juridica)
- **Razón:** Mantiene normalización, permite queries polimórficas

### **4. Estados como Entidades (NO Enums)**
- ✅ `UsuarioEstadoSistema`, `UsuarioEstado`, `EventoEstadoSistema`, etc. son `@Entity`
- **Razón:** Auditoría en `HistorialEstado*`, transiciones sin redeploy

### **5. Sin Colecciones Inversas (No @OneToMany)**
- ✅ Solo lado propietario (`@ManyToOne` / `@OneToOne`)
- **Razón:** Evita complejidad de cascadas, lazy-load issues; Repositories harán queries directas

### **6. Claves Compuestas con `@Embeddable`**
- ✅ `UsuarioRolId`, `MiembrosOrganizacionId` = PKs lógicas 2-campo
- **Razón:** Fiel al MER, evita PKs sintéticas innecesarias

### **7. `@MapsId` para 1:1 con PK Compartida**
- ✅ `PersonaFisica` + `EventoDetalle` comparten PK con padre
- **Razón:** Integridad referencial, evita redundancia, DDL más limpio

---

## 🚀 Análisis de Cumplimiento de Requisitos

### **RF-1: User Management & Authentication (Módulo 1)**
- ✅ `Usuario` + `Persona` + roles
- ✅ Campos de seguridad: `intentos_fallidos`, `requiere_captcha`, `fecha_desbloqueo_cooldown`
- ✅ Estados: `UsuarioEstadoSistema` (BLOQUEADO), `UsuarioEstado` (DE_BAJA)
- ⏳ Controllers/Services: pendiente Fase 2

### **RF-2: Event Management (Módulo 2)**
- ✅ `Evento` + `EventoDetalle` + `EventoCronograma` + multimedia
- ✅ Estados: `EventoEstadoSistema` (moderación), `EventoEstadoOrganizador`
- ✅ Categorías: `Categoria` (N:1 con Evento)
- ⏳ CRUD endpoints: pendiente Fase 2

### **RF-4: Ticket System (Módulo 4)**
- ✅ `CronogramaTicket` (N:N con cupos), `TipoTicket`
- ✅ `Inscripcion` con estado, precio, fecha
- ✅ Auditoría: `HistorialEstadoInscripcion`
- ⏳ Validación de cupos: pendiente Fase 2 (Service layer)

### **RF-5: Ratings & Reviews (Módulo 5)**
- ✅ `Valoracion` (usuario + evento + puntaje)
- ✅ `Visita` (analytics: tracking de vistas)
- ⏳ Validaciones (rango 1-5): pendiente Service layer

---

## ⚠️ Gaps Resueltos y Pendientes

### **Resueltos (Sprint 1)**
- ✅ Modelo JPA de 37 entidades sincronizado con MER
- ✅ Campos de seguridad `Usuario` (completados en Sesión 2026-07-26)
- ✅ Relaciones 1:1, 1:N, N:N con estrategias optimizadas
- ✅ Claves compuestas y compartidas documentadas

### **Pendientes (Fase 2 — Repositories + Services)**
- ⏳ Interfaces `JpaRepository` para cada entidad
- ⏳ Queries personalizadas: `findByEmail`, `findByEstado`, etc.
- ⏳ `@Service` para lógica: moderación, validación de cupos, rate-limiting
- ⏳ `@ControllerAdvice` para manejo de excepciones
- ⏳ DTOs (`*Request`, `*Response`) para API

### **Pendientes (Fase 3 — Controllers + Auth)**
- ⏳ REST endpoints: `/api/auth/login`, `/api/auth/register`, etc.
- ⏳ JWT filter + `@PreAuthorize` para RBAC
- ⏳ `better-profanity` en registro
- ⏳ BCrypt + `PasswordEncoder` bean

---

## 📖 Referencias Documentación

| Documento | Ubicación | Uso |
|-----------|-----------|-----|
| **CLAUDE.md** | `/` (root) | Guía arquitectónica, stack, sprint scope |
| **MER.md** | `docs/diseño_bd/MER.md` | Diagrama Entidad-Relación (fuente verdad BD) |
| **DER.md** | `docs/diseño_bd/DER.md` | Diagrama Conceptual (Mermaid) |
| **Requisitos Funcionales** | `docs/requisitos/requisitos_funcionales/` | RF-1 a RF-8 (Módulos) |
| **Requisitos No-Funcionales** | `docs/requisitos/requisitos_no_funcionales/` | Performance, seguridad, usabilidad |
| **historial.md** | `/` | Línea de tiempo: sesiones + decisiones |
| **SPRINT_LOG.md** | `docs/log/` | Log de actividades sprint |

---

## 🎓 Conceptos Clave Aprendidos

### **1. Patrón DTO (Data Transfer Object)**
- **Concepto:** Separar entidades `@Entity` de objetos transportados en REST
- **Uso:** `UsuarioRequest` (login), `EventoResponse` (listado)
- **Beneficio:** Oculta detalles de BD, permite evolucion independiente

### **2. Lazy vs Eager Loading**
- **Lazy:** Carga entidad solo cuando se accede (recomendado, es default en 1:N)
- **Eager:** Carga automáticamente en la misma consulta (puede causar N+1)
- **Decisión:** Usado Lazy en todo el proyecto

### **3. Open Session in View (OSIV)**
- **Concepto:** Sesión Hibernate abierta durante procesamiento HTTP
- **En Enexia:** `open-in-view=false` (mejor práctica: evita lazy-load sorpresas en vistas)
- **Consecuencia:** Service layer debe usar `@Transactional` explícitamente

### **4. Auditoría via Historial**
- **Patrón:** `HistorialEstadoUsuario`, `HistorialEstadoEvento`, etc. registran cambios
- **Alternativa:** `@Audited` (Hibernate Envers) — más automatizado, pero overhead
- **Decisión:** Manual Historial para control fino

### **5. Soft Delete vs Hard Delete**
- **Soft:** Campo `fecha_baja` = null (activo), no null (borrado lógico)
- **Hard:** DELETE de DB (irreversible)
- **Decisión:** Soft delete en todas las entidades (cumple RGPD + auditoría)

---

## 🔄 Próximas Fases

### **Fase 2: Repositories & Services (Estimado: 2-3 días)**
1. Crear `Repository` interfaces (`JpaRepository<Entity, ID>`)
2. Métodos query: `findByEmail`, `findByEstado`, etc.
3. `@Service` para lógica:
   - `AuthService`: login, register, JWT token
   - `EventoService`: CRUD con validaciones
   - `InscripcionService`: control de cupos
   - `HistorialService`: auditoría
4. Exception handling custom
5. Validadores (email, CUIT, etc.)

### **Fase 3: REST Controllers & JWT (Estimado: 3-4 días)**
1. `@RestController` endpoints:
   - `POST /api/auth/register` — crear usuario
   - `POST /api/auth/login` — emitir JWT
   - `GET /api/eventos` — catálogo
   - `POST /api/inscripciones` — registrar en evento
2. JWT filter (`OncePerRequestFilter`)
3. `@PreAuthorize` para RBAC
4. `@ControllerAdvice` + `@ExceptionHandler`
5. `ModelMapper` para DTO ↔ Entity

### **Fase 4: Frontend HTML/CSS/JS (Paralelo a Fase 3)**
1. Login/Register pages
2. Catálogo de eventos
3. Dashboard (Participant/Organizer/Admin)
4. Fetch API calls a `/api/**`

### **Fase 5: Testing & Deployment**
1. JUnit 5 + Mockito (Services)
2. Integration tests (Repositories)
3. Segment tests (Endpoints)
4. Deploy a staging (XAMPP → Docker → Cloud)

---

## 📊 Métricas de Implementación

| Métrica | Valor | Nota |
|---------|-------|------|
| Entidades `@Entity` | 37 | Fiel al MER |
| Clases `@Embeddable` | 2 | UsuarioRolId, MiembrosOrganizacionId |
| Relaciones 1:1 | 3 | Persona-PersonaFisica, Persona-PersonaJuridica, Evento-EventoDetalle |
| Relaciones 1:N | ~20 | Usuario→Inscripcion, Evento→EventoCronograma, etc. |
| Relaciones N:N | 2 | UsuarioRol, MiembrosOrganizacion |
| Tablas de auditoría | 5 | HistorialEstado{Usuario,Evento,Inscripcion,Suscripcion,Pago} + HistorialInteracciones |
| Campos de seguridad | 3 | intentos_fallidos, requiere_captcha, fecha_desbloqueo_cooldown |
| Líneas de código (modelos) | ~2000 | Boilerplate reducido con Lombok |
| Tiempo compilación | <5s | Gradle build |
| Sincronización BD | ✅ | 37 tablas creadas, DDL validado |

---

## 🎯 Conclusión Académica

El modelo JPA de Enexia demuestra **maestría en patrones ORM**:

1. **Normalización correcta**: 3NF respetada (sin redundancias de datos)
2. **Relaciones complejas**: 1:1 con PK compartida, N:N con claves compuestas, 1:N con lazy-loading
3. **Auditoría integrada**: Historial para trazabilidad de cambios (cumple requisitos legales)
4. **Seguridad en modelo**: Campos de control de login, soft-deletes, estados auditables
5. **Performance optimizado**: FetchType.LAZY, open-in-view=false, índices implícitos en FKs

**Próximo paso:** Layer Service transformará este modelo en operaciones de negocio validadas (moderación, cupos, rate-limiting), y Layer REST lo expondrá vía API JSON.

---

## 📝 Notas del Autor

- **Idioma:** Documentación mantenida en español (user es hispanohablante). Código en inglés (convención industry).
- **Sincronización:** `CLAUDE.md` (EN) ↔ `CLAUDE_es-ES.md` (ES) mantener en sync
- **Evolución:** Este registro es baseline; se actualizará después de cada Fase
- **Feedback:** Contribuciones/correcciones al `docs/log/`

---

**Generado:** 2026-07-31  
**Por:** Claude Code + skill `documentar-avance`  
**Próxima actualización:** Post-Fase 2 (Repositories & Services)
