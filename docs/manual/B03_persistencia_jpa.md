# B03 — Persistencia: bases de datos, JPA e Hibernate

> **Parte B · Spring Boot.** Un programa que olvida todo al apagarse no sirve. Este capítulo explica **cómo Enexia guarda y lee sus datos**: primero la base de datos relacional (tablas, claves, relaciones), después **JPA/Hibernate** (que convierte clases Java en tablas), los **repositorios** de Spring Data y, al final, el tema más delicado del backend: **las transacciones y la concurrencia**.

**Qué vas a aprender**
- Conceptos de bases relacionales: tabla, fila, clave primaria, clave foránea, `UNIQUE`, índice, normalización.
- Cómo leer el **MER/DER** del proyecto.
- Mapeo objeto-relacional: `@Entity`, `@Table`, `@Id`, `@Column`, relaciones `@ManyToOne` / `@OneToMany` / `@OneToOne`, claves compuestas.
- Carga **perezosa** (`LAZY`) y el problema **N+1**; `JOIN FETCH` y `@EntityGraph`.
- Repositorios: métodos derivados, `@Query` (JPQL), parámetros con nombre, bloqueo pesimista.
- **Transacciones**: `@Transactional`, aislamiento, propagación, `readOnly`, la trampa de la autoinvocación.
- Concurrencia: condiciones de carrera y cómo las enfrenta el proyecto.
- Migraciones y `ddl-auto`; borrado lógico.

---

## 1. Fundamentos de bases de datos relacionales

### 1.1 Tablas, filas y columnas

Una **base de datos relacional** organiza la información en **tablas**. Cada tabla tiene **columnas** (los campos) y **filas** (los registros). Enexia usa **MariaDB** (compatible con MySQL).

Tabla `valoracion` (la real, vista con `SHOW CREATE TABLE`):

| id_valoracion | id_cronograma | id_usuario | valor | comentario | fecha |
|---|---|---|---|---|---|
| 1 | 12 | 7 | 5 | "Excelente" | 2026-10-10 |
| 2 | 12 | 9 | 4 | "Muy bueno" | 2026-10-10 |

### 1.2 Claves

- **Clave primaria (PK):** la columna (o combinación) que **identifica de forma única** cada fila. Nunca se repite ni es nula. Suele ser un número autoincremental (`id_valoracion`).
- **Clave foránea (FK):** una columna que **apunta a la PK de otra tabla** y así expresa una **relación**. `valoracion.id_cronograma` apunta a `evento_cronograma.id_cronograma`: "esta valoración es de ese cronograma". La base **impide** crear una valoración de un cronograma que no existe.
- **Restricción `UNIQUE`:** ninguna otra fila puede tener ese valor (o esa combinación). En `valoracion`: `UNIQUE (id_usuario, id_cronograma)` — un usuario no puede valorar dos veces el mismo cronograma.
- **Índice:** estructura auxiliar que hace **rápidas las búsquedas** por una columna. Las PK y los `UNIQUE` crean índices solos.

### 1.3 Relaciones

| Tipo | Significado | Ejemplo en Enexia |
|---|---|---|
| **Uno a muchos** (1:N) | Una fila de A se relaciona con muchas de B. | Un evento tiene muchos cronogramas; un cronograma, muchas valoraciones. |
| **Uno a uno** (1:1) | Una fila de A con **una sola** de B. | Un `Usuario` ↔ su `Persona_Fisica`. |
| **Muchos a muchos** (N:M) | Se resuelve con **una tabla intermedia**. | `Usuario` ↔ `Rol` mediante `usuario_rol`; `Usuario` ↔ `Persona_Juridica` mediante `miembros_organizacion`. |

### 1.4 Normalización en una frase

**No repetir datos:** cada dato vive en **un solo lugar** y se referencia por clave. Por eso el nombre de una categoría está en `categoria` y el evento guarda solo `id_categoria`: renombrar la categoría la cambia para todos. (Un ejemplo de decisión opuesta, argumentada en el código de `Evento`: la **firma** del organizador no se copia como texto en el evento, *"porque un cambio de razón social dejaría los eventos viejos con el nombre anterior"*.)

### 1.5 Leer el MER del proyecto

El modelo de datos está en `docs/diseño_bd/MER.md` en formato Mermaid (`erDiagram`). Una entidad se ve así:

```
Valoracion {
    int id_valoracion PK
    int id_cronograma FK
    int id_usuario FK
    int valor
    varchar comentario
    date fecha
}
```

Y las relaciones, con la notación *crow's foot*: `Usuario ||--o{ Historial_Estado_Usuario` se lee "un usuario **tiene cero o muchos** registros de historial". Leelo antes de tocar el modelo: **primero se actualiza el MER y después el código** (criterio del Sprint 1).

---

## 2. JPA e Hibernate: objetos ↔ tablas

### 2.1 El problema (la *impedancia objeto-relacional*)

En Java pensás en **objetos con referencias** (`valoracion.getCronograma().getEvento()`). En la base hay **filas con claves foráneas**. Traducir una cosa en la otra a mano (escribir `SELECT`, armar objetos desde `ResultSet`) es repetitivo y propenso a errores.

- **JPA** (*Jakarta Persistence API*) es la **especificación**: define anotaciones y reglas para mapear clases a tablas.
- **Hibernate** es la **implementación** que Spring Boot usa por debajo. Genera el SQL por vos.
- **Spring Data JPA** agrega los *repositories* (§6).

### 2.2 Una entidad

`model/Valoracion.java`:

```java
@Entity
@Table(name = "valoracion", uniqueConstraints =
        @UniqueConstraint(name = "uk_valoracion_usuario_cronograma",
                          columnNames = {"id_usuario", "id_cronograma"}))
@Getter
@Setter
@NoArgsConstructor
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_valoracion")
    private Long idvaloracion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cronograma")
    private EventoCronograma cronograma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "valor")
    private Integer valor;

    @Column(name = "comentario", length = 1000)
    private String comentario;

    @Column(name = "fecha")
    private LocalDate fecha;
}
```

| Anotación | Significado |
|---|---|
| `@Entity` | Esta clase se corresponde con una tabla. |
| `@Table(name = "valoracion", uniqueConstraints = ...)` | Nombre de la tabla y restricciones. **El nombre del `UNIQUE` es explícito a propósito**: así `GlobalExceptionHandler` puede reconocerlo en el mensaje de error y dar "Ya valoraste este cronograma". |
| `@Id` | La clave primaria. |
| `@GeneratedValue(strategy = IDENTITY)` | La base la **autoincrementa**; Hibernate lee el valor generado tras el `INSERT`. |
| `@Column(name = "...", length = 1000)` | Nombre de columna y propiedades. `length` debe **coincidir con la validación** del DTO: el javadoc cuenta que sin él Hibernate asumía 255 y un comentario de 300 caracteres pasaba la validación pero **explotaba en el `INSERT`**. |
| `@ManyToOne` + `@JoinColumn` | Muchas valoraciones → un cronograma. `@JoinColumn` nombra la **columna FK**. |

Las convenciones que el proyecto sigue: atributos `camelCase` ↔ columnas `snake_case` (declaradas con `@Column`/`@JoinColumn`); sin lógica de negocio en la entidad; `@Getter/@Setter/@NoArgsConstructor` de Lombok (JPA **exige** constructor vacío).

### 2.3 Relaciones

| Anotación | Relación | Ejemplo |
|---|---|---|
| `@ManyToOne` | N:1. El lado "muchos" guarda la FK. | `Valoracion.cronograma`, `Evento.categoria` |
| `@OneToMany(mappedBy = "...")` | 1:N. El lado "uno" ve la colección. **No tiene columna**: `mappedBy` dice "mirá el atributo `usuario` del otro lado". | `Usuario.usuarioRoles` |
| `@OneToOne` | 1:1. | `Usuario.personaFisica` |

```java
@OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
private Set<UsuarioRol> usuarioRoles;
```

### 2.4 Carga `LAZY` y `EAGER`

- **`EAGER`**: al cargar la entidad, **se cargan también** sus relaciones.
- **`LAZY`**: la relación se carga **recién cuando la usás** (`getCronograma()`), con otra consulta.

En Enexia **casi todo es `LAZY`** (regla del `CLAUDE.md`: *"lazy-load preferred"*). `EAGER` solo donde siempre hace falta (los roles del usuario).

### 2.5 Dos problemas clásicos de `LAZY`

**(a) `LazyInitializationException`.** Con `spring.jpa.open-in-view=false` (así está configurado) la "sesión" de Hibernate se cierra al terminar el método del servicio. Si **después** leés una relación `LAZY`, ya no hay sesión para ir a buscarla y explota. Por eso se **mapea a DTO dentro de la transacción** y por eso `ValoracionService.listarDeEvento` tiene `@Transactional(readOnly = true)`: su javadoc cuenta que *"ya pasó una vez con `cronograma.getEvento().getNombre()` en `mapear()`"*.

**(b) El problema N+1.** Tenés 20 inscripciones y para cada una leés `getCronogramaTicket().getCronograma()`: Hibernate hace **1 consulta para las 20 + 20 consultas más** (una por cada relación `LAZY`) = 21. Con 1000 filas son 1001 consultas. Soluciones del proyecto:

1. **`JOIN FETCH`** en la consulta: trae las relaciones en el **mismo** `SELECT`.

```java
@Query("""
        SELECT u FROM Usuario u
        LEFT JOIN FETCH u.usuarioRoles ur
        LEFT JOIN FETCH ur.rol
        LEFT JOIN FETCH u.estadoUsuario
        LEFT JOIN FETCH u.personaFisica pf
        LEFT JOIN FETCH pf.persona
        WHERE u.email = :email AND u.fechaBaja IS NULL
        """)
Optional<Usuario> buscarActivoPorEmailConRoles(@Param("email") String email);
```

Una sola consulta trae el usuario, sus roles, su estado y su persona: justo lo que necesita cada petición autenticada.

2. **`@EntityGraph`**: declarativo, para métodos derivados.

```java
@EntityGraph(attributePaths = {"usuario", "cronograma", "cronograma.evento"})
Page<Valoracion> findByCronogramaEventoIdEventoOrderByFechaDesc(Long idEvento, Pageable pageable);
```

3. **Consultas en lote + `Map`** (A03 §1.2): una sola consulta para "los ids ya valorados de estos 12 cronogramas" en vez de preguntar cronograma por cronograma.

### 2.5.1 Claves compuestas: `@EmbeddedId`

Algunas tablas intermedias tienen **PK de dos columnas**. `MiembrosOrganizacion` usa `@EmbeddedId` y `@MapsId`:

```java
@Entity
@Table(name = "miembros_organizacion")
public class MiembrosOrganizacion {

    @EmbeddedId
    private MiembrosOrganizacionId id = new MiembrosOrganizacionId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idUsuario")                  // la parte "idUsuario" de la PK viene de esta relación
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idPersonaJuridica")
    @JoinColumn(name = "id_persona_juridica")
    private PersonaJuridica personaJuridica;

    @Column(name = "rol_en_empresa")
    private String rolEnEmpresa;
}
```

La clase de la clave (`@Embeddable`, con `equals` y `hashCode`, A02 §9) agrupa las dos columnas. `@MapsId` evita declarar dos veces la misma columna.

### 2.6 Estados como tablas, no como `enum` persistido

Los estados de un usuario, de un evento o de una suscripción viven en **tablas de catálogo** (`usuario_estado`: `1 ACTIVO, 2 BLOQUEADO, 3 SUSPENDIDO, 4 DE_BAJA`). El `Usuario` apunta a su estado con una FK. Los `enum` de Java (`EstadoUsuarioNombre`) son solo el **vocabulario** para no escribir cadenas a mano (A02 §7.1). Como esas tablas arrancan vacías, `DatosInicialesConfig` las precarga (B01 §7).

### 2.7 Historiales de estado

Cada cambio de estado importante inserta una fila en una tabla `historial_estado_*` (usuario, evento, inscripción, suscripción...). Así se puede responder **quién cambió qué y cuándo**. Se escribe **en la misma transacción** que el cambio (ver `AdminUsuarioService.cambiarEstado`), para que **o ambos o ninguno** queden guardados.

---

## 3. Borrado lógico (*soft delete*)

**Regla del proyecto:** *nunca* se hace `DELETE` de usuarios, eventos ni inscripciones. Se **marca** como dado de baja:

- `Usuario.fechaBaja` (no nulo = cuenta dada de baja).
- `estado_organizador = DADO_DE_BAJA` en el evento.
- `categoria.activa = false`.

Cada consulta filtra lo dado de baja (`WHERE u.fechaBaja IS NULL`). **Por qué:** conserva el historial, permite auditar, evita *huérfanos* (filas que apuntan a algo borrado) y hace **reversible** un error. Tiene un costo: **un email o un DNI de una cuenta dada de baja no se puede reutilizar** (el `UNIQUE` sigue vigente) — por eso las pruebas de API generan identidades nuevas en cada corrida.

---

## 4. `ddl-auto`, esquema y migraciones

```properties
spring.jpa.hibernate.ddl-auto=update
```

| Valor | Qué hace |
|---|---|
| `none` | No toca el esquema. |
| `validate` | Solo **comprueba** que las entidades coincidan con las tablas; falla si no. (Producción.) |
| `update` | **Crea o altera** tablas para que coincidan con las entidades, sin borrar datos. (Desarrollo.) |
| `create` / `create-drop` | Borra y recrea todo. ⚠️ Nunca con datos reales. |

`update` es cómodo pero **no reemplaza a las migraciones**. Una **migración** es un **script SQL versionado** que describe un cambio de esquema (`docs/diseño_bd/migraciones/`):

```sql
-- 2026-10-15_categoria_activa.sql
ALTER TABLE categoria
    ADD COLUMN IF NOT EXISTS activa BIT(1) NOT NULL DEFAULT 1;
```

Cada columna nueva en una entidad **debe** tener su script, porque en producción (`validate`) Hibernate no creará nada. Detalles que enseñan: el `DEFAULT 1` es obligatorio (sin él, las filas existentes quedarían en `0` = "inactivas" y el catálogo se vaciaría); `IF NOT EXISTS` hace el script **idempotente**.

> **Lección real (log de 2026-09-09):** Hibernate 7 emite `ALTER TABLE IF EXISTS t ADD COLUMN ...`, que **MariaDB 10.4 (el de XAMPP) rechaza**. Hibernate lo registraba **pero no abortaba el arranque**, así que durante semanas faltaron columnas sin que nadie lo notara. Se resolvió actualizando a MariaDB 11.8. Moraleja: leé siempre el log de arranque.

---

## 5. Repositorios con Spring Data JPA

### 5.1 La magia: una interfaz y nada más

```java
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // ...
}
```

Con solo extender `JpaRepository<Entidad, TipoDeId>` ya tenés, **sin escribir código**:

| Método | Hace |
|---|---|
| `save(entidad)` | `INSERT` si es nueva (sin id), `UPDATE` si ya existe. |
| `findById(id)` | Devuelve un `Optional<Usuario>`. |
| `findAll()`, `findAll(Pageable)` | Todas / una página. |
| `existsById(id)`, `count()` | ¿existe? / cuántas. |
| `delete(entidad)` | Borra (recordá: Enexia casi no lo usa). |

Spring genera la implementación **al arrancar** (usando proxies, B01 §9).

### 5.2 Métodos derivados del nombre

Spring Data **interpreta el nombre del método** y arma la consulta:

```java
boolean existsByUsuarioIdUsuarioAndCronogramaIdCronograma(Long idUsuario, Long idCronograma);
long countByCronogramaEventoIdEvento(Long idEvento);
Page<Valoracion> findByCronogramaEventoIdEventoOrderByFechaDesc(Long idEvento, Pageable pageable);
```

Se lee como una frase: *"existe una valoración **por** `usuario.idUsuario` **y** `cronograma.idCronograma`"*. Las palabras clave: `findBy`, `existsBy`, `countBy`, `And`, `Or`, `OrderBy...Desc`, `IgnoreCase`, `IsNull`. Navega por **relaciones** con la notación de camello (`CronogramaEventoIdEvento` = `cronograma.evento.idEvento`).

Sirve para consultas simples. Cuando el nombre se vuelve inmanejable, se pasa a `@Query`.

### 5.3 `@Query` con JPQL

**JPQL** es como SQL pero sobre **entidades y atributos**, no tablas y columnas.

```java
@Query("SELECT AVG(v.valor) FROM Valoracion v WHERE v.cronograma.evento.idEvento = :idEvento")
Double promedioDeEvento(@Param("idEvento") Long idEvento);
```

- `Valoracion v` es un *alias* de la entidad; `v.cronograma.evento.idEvento` **navega** por las relaciones (Hibernate arma los `JOIN`).
- `:idEvento` es un **parámetro con nombre**; `@Param("idEvento")` lo enlaza al argumento.
- Los **bloques de texto** `"""` (A03 §5.3) permiten escribir la consulta en varias líneas.

### 5.4 Seguridad: nunca concatenar SQL

El javadoc de `UsuarioRepository` lo dice: *"todas las consultas usan parámetros con nombre o derivación de método. En ambos casos Spring Data genera un `PreparedStatement` y el valor viaja como parámetro enlazado, nunca concatenado al SQL"*. Es la defensa contra la **inyección SQL**:

```java
// ❌ VULNERABLE: si email vale  ' OR '1'='1   la consulta devuelve todos los usuarios
"SELECT u FROM Usuario u WHERE u.email = '" + email + "'"

// ✅ SEGURO: el valor viaja separado, nunca se interpreta como SQL
@Query("... WHERE u.email = :email")  +  @Param("email")
```

### 5.5 Consultas que modifican: `@Modifying`

```java
@Modifying
@Query("DELETE FROM PasswordResetToken t WHERE t.usuario.idUsuario = :idUsuario")
void borrarPorUsuario(@Param("idUsuario") Long idUsuario);
```

`@Modifying` avisa que la consulta **escribe** (`UPDATE`/`DELETE`). Se usa con moderación.

### 5.6 Agregaciones y resultados "crudos"

Cuando una consulta devuelve columnas sueltas (id y conteo), el resultado es `List<Object[]>`:

```java
// AdminCategoriaService.contar
for (Object[] fila : eventoRepository.contarPorCategoria(/* filtros */)) {
    mapa.put((Long) fila[0], (Long) fila[1]);        // fila[0] = id de categoría, fila[1] = cantidad
}
```

Es feo pero eficiente: **una consulta agrupada** (`GROUP BY`) reemplaza N consultas.

---

## 6. Transacciones

### 6.1 Qué es una transacción

Una **transacción** agrupa varias operaciones de base de datos en **una unidad indivisible**: **o se aplican todas, o ninguna**. Propiedades **ACID**:

| | Significa |
|---|---|
| **A**tomicidad | Todo o nada. |
| **C**onsistencia | Se respetan las restricciones (`UNIQUE`, FK). |
| **A**islamiento | Lo que hace una transacción no interfiere con otras en curso. |
| **D**urabilidad | Lo confirmado (*commit*) sobrevive a una caída. |

Ejemplo: inscribirse con pago. Hay que (1) crear la inscripción, (2) registrar el pago, (3) sumar un cupo, (4) escribir el historial. **Si falla el paso 3, los pasos 1 y 2 deben deshacerse** (*rollback*), o el cupo y los pagos quedan inconsistentes.

### 6.2 `@Transactional`

```java
@Transactional
public ValoracionResponse crear(String email, ValoracionRequest datos, HttpServletRequest request) {
    // todo lo que acá se escriba es UNA transacción
}
```

Spring abre la transacción **antes** del método y, al terminar:
- **sin excepción → `commit`** (todo se confirma);
- **con una excepción `RuntimeException` → `rollback`** (se deshace todo).

> **Atención:** por defecto el *rollback* ocurre con excepciones **unchecked**; con una *checked* hace commit. Es otra razón de que todas las excepciones propias del proyecto extiendan `RuntimeException`.

Se pone en la capa **de servicio**, que es donde está la unidad de trabajo de negocio.

### 6.3 `readOnly = true`

```java
@Transactional(readOnly = true)
public Page<InscripcionResponse> historial(String email, Pageable paginado) { ... }
```

Declara que el método **solo lee**: Hibernate evita trabajo innecesario (no vigila cambios) y mantiene una **vista consistente** para varias consultas.

### 6.4 Niveles de aislamiento

Cuando dos transacciones corren a la vez, el **nivel de aislamiento** decide cuánto "ven" una de la otra. MariaDB usa por defecto `REPEATABLE READ` (una instantánea fija desde la primera lectura). Enexia usa explícitamente:

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public MensajeResponse cambiarEstado(...) { ... }
```

`READ_COMMITTED` ve lo **último confirmado** en cada lectura. Combinado con bloqueo de fila (§7), hace que la segunda transacción **espere su turno y lea el dato ya actualizado** en vez de abortar por conflicto de instantánea. Es el patrón de **todas** las intervenciones del administrador.

### 6.5 Propagación: `REQUIRES_NEW`

¿Qué pasa si un método transaccional **llama a otro** método transaccional? Por defecto el segundo **se une** a la transacción existente. `REQUIRES_NEW` hace lo contrario: **suspende la actual y abre una nueva e independiente**, que confirma o revierte **por su cuenta**.

El caso de manual del proyecto, `IntentosLoginService.registrarFallo`:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean registrarFallo(Long idUsuario) { /* incrementa el contador de intentos */ }
```

Su comentario lo explica: quien lo llama **lanza `CredencialesInvalidasException` inmediatamente después**. Si compartieran transacción, esa excepción haría *rollback* **del incremento del contador**: *"el resultado sería un contador que nunca avanza y una cuenta que jamás se bloquea"*. Con `REQUIRES_NEW` el contador queda **confirmado aunque el login falle**. Lo mismo hace `AuditoriaService.registrarAparte` para que el **intento de infracción** quede registrado aunque la operación se revierta (con una advertencia en su javadoc: solo debe usarse cuando la acción **no** referencia filas sin confirmar de la transacción en curso, porque provoca un interbloqueo por clave foránea).

### 6.6 La trampa de la autoinvocación

`@Transactional` funciona por **proxy** (B01 §9): el proxy envuelve al bean y abre la transacción cuando **alguien de afuera** llama al método. Si un método de la clase llama a **otro método de la misma clase**, la llamada va **directo al objeto** y se salta el proxy:

```java
@Service
public class MiServicio {
    public void a() {
        b();                         // ← llamada interna: NO pasa por el proxy
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void b() { ... }          // la anotación se IGNORA en silencio
}
```

**No hay error ni aviso**: simplemente no abre la transacción. Por eso en el proyecto:
- `IntentosLoginService` y `VisitaService` son **beans separados** y no métodos de `AuthService`.
- `FinalizacionEventoScheduler` es un bean aparte de `FinalizacionEventoService`: *"para que cada `finalizar(id)` cruce el proxy y corra en su propia transacción: un evento que falla no deshace a los que ya se finalizaron"*.
- Esta fue la causa de un bug real (log de 2026-10-14): `FinalizacionEventosService.barrido()` llamaba a su propio método `@Transactional` y el bloqueo de fila falló con `TransactionRequiredException`.

---

## 7. Concurrencia: dos usuarios a la vez

### 7.1 La condición de carrera

Un servidor atiende **muchas peticiones al mismo tiempo**. Una **condición de carrera** ocurre cuando el resultado depende del **orden exacto** en que se intercalan dos operaciones. El patrón clásico es **leer-modificar-escribir**:

```
Contador de intentos = 2
Petición A lee 2          Petición B lee 2
A escribe 3               B escribe 3        ← debería ser 4: se perdió un incremento
```

### 7.2 Tres defensas que usa el proyecto

**(1) Restricciones `UNIQUE` como red de seguridad real.** Un chequeo en el servicio (`existsByEmail`) **no alcanza**: dos altas simultáneas pasan ambas el chequeo (ninguna ve la fila aún no confirmada de la otra) y las dos intentan insertar. Solo el **índice `UNIQUE`**, que es atómico, frena a la segunda. El servicio mantiene el chequeo previo **solo para dar un mensaje amable en el caso común**; el `UNIQUE` es la defensa verdadera. El `GlobalExceptionHandler` traduce la violación a `409` (A04 §1.8).

**(2) Bloqueo pesimista (`SELECT ... FOR UPDATE`).** "Antes de leer esta fila, **bloqueala**: nadie más puede modificarla hasta que yo termine".

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT e FROM Evento e WHERE e.idEvento = :idEvento")
Optional<Evento> bloquearParaActualizar(@Param("idEvento") Long idEvento);
```

Lo usan: el contador de intentos del login, cada decisión del administrador (evento, cuenta, suscripción), la reserva de cupos de un ticket. Con esto la segunda transacción **espera** y después lee el estado **ya actualizado**. Su javadoc agrega algo importante: *"ambas rutas toman ESTE bloqueo antes que ningún otro, así que el orden de adquisición es siempre el mismo y no puede haber interbloqueo (deadlock)"*. Si dos transacciones bloquean filas en **órdenes distintos**, pueden esperarse mutuamente para siempre.

**(3) `@DynamicUpdate` para no pisar columnas ajenas.** Por defecto Hibernate hace `UPDATE` de **todas** las columnas. En `Evento`, dos procesos escriben **campos distintos** en paralelo (el pipeline de moderación el estado del sistema; el organizador el estado propio). Sin `@DynamicUpdate`, el pipeline **reescribía** `estado_organizador` con el valor viejo y **se perdía la baja** del organizador (*lost update*). Con `@DynamicUpdate` el `UPDATE` solo incluye las columnas que esa transacción **realmente modificó**. El javadoc de `Evento` cuenta el incidente completo: es una lectura muy recomendable.

> Existe también el **bloqueo optimista** (una columna `@Version`: si la fila cambió desde que la leíste, falla). El proyecto prefirió el pesimista porque los conflictos son probables en esas filas y se quiere **esperar** en vez de reintentar.

---

## 8. Lectura de un servicio con todo junto

`AdminUsuarioService.cambiarEstado` combina casi todo este capítulo:

```java
@Transactional(isolation = Isolation.READ_COMMITTED)                       // §6.4
public MensajeResponse cambiarEstado(String emailAdmin, Long idUsuario,
                                     AdminCambioEstadoUsuarioRequest datos,
                                     HttpServletRequest request) {

    Usuario administrador = usuarioRepository.buscarActivoPorEmailConRoles(emailAdmin)   // §2.5 JOIN FETCH
            .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta")); // A03 Optional

    Usuario objetivo = usuarioRepository.bloquearParaActualizarSeguridad(idUsuario)      // §7.2 bloqueo pesimista
            .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el usuario"));

    if (!esGestionable(objetivo, administrador)) {
        throw new OperacionNoPermitidaException("No se puede modificar el estado de esta cuenta"); // → 409
    }
    // ...
    objetivo.setEstadoUsuario(filaDestino);
    usuarioRepository.save(objetivo);                                                    // UPDATE

    HistorialEstadoUsuario historial = new HistorialEstadoUsuario();                     // §2.7 historial
    // ... se completa y se guarda en la MISMA transacción
    historialRepository.save(historial);

    auditoriaService.registrar(administrador, ACCION_..., "...", request);               // auditoría
    // el correo se programa "después del commit" (B05)
    return new MensajeResponse(...);
}
```

Todo lo que está entre la apertura y el cierre es **atómico**: el cambio de estado, el historial y la auditoría se confirman **juntos** o no se confirma nada.

---

## 9. Errores típicos

| Error | Síntoma | Remedio |
|---|---|---|
| Leer una relación `LAZY` fuera de la transacción. | `LazyInitializationException`. | Mapear a DTO dentro del servicio; `JOIN FETCH`/`@EntityGraph`. |
| Un bucle que consulta por cada fila. | Lentitud (N+1). | Consulta en lote + `Map`. |
| Confiar solo en `existsBy...` para unicidad. | Duplicados bajo concurrencia. | `UNIQUE` en la base + traducir la violación a 409. |
| Llamar a un método `@Transactional` de la misma clase. | La transacción no se abre, sin avisos. | Pasar la lógica a otro bean. |
| `@Transactional` en el controller. | Mezcla de capas; transacciones demasiado largas. | En el servicio. |
| Columna nueva sin migración. | En producción (`validate`) la app no arranca o falla en ejecución. | Un script SQL por cambio de esquema. |
| Excepción *checked* dentro de `@Transactional`. | Commit en vez de rollback. | Excepciones `RuntimeException`. |
| Concatenar valores en una consulta. | Inyección SQL. | Parámetros con nombre. |

---

## 10. Para practicar

1. Abrí la base y ejecutá `SHOW CREATE TABLE evento_cronograma;` y `SHOW CREATE TABLE cronograma_ticket;`. Dibujá las relaciones entre `evento`, `evento_cronograma`, `cronograma_ticket` e `inscripcion`.
2. Escribí el método de repositorio derivado que cuente las inscripciones de un usuario en un estado dado. (`countBy...`)
3. En la consola de MariaDB reproducí el *lost update*: abrí dos sesiones, `START TRANSACTION; SELECT ... ;` en ambas, `UPDATE` en una y confirmá; luego `UPDATE` en la otra. Probalo después con `SELECT ... FOR UPDATE` y observá cómo **espera**.
4. Escribí una migración SQL para agregar la columna `telefono VARCHAR(20) NULL` a `persona_fisica`, idempotente.
5. ¿Qué pasaría si `AuditoriaService.registrarAparte` **no** usara `REQUIRES_NEW` y la moderación de un comentario lanzara la excepción? Explicá qué se perdería.

**Siguiente capítulo:** [B04 — Seguridad: contraseñas, JWT y control de acceso](./B04_seguridad.md)
