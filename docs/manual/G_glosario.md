# Glosario

> Términos del manual, en orden alfabético, con una definición corta y **dónde se explica**. Si una palabra no está acá, buscala en el capítulo indicado por el tema (A = Java, B = Spring Boot, C = cliente, D = módulos).

## A

- **ACID** — Atomicidad, Consistencia, Aislamiento, Durabilidad: las propiedades de una transacción. → B03 §6.1
- **ADR** — *Architecture Decision Record*: una decisión de diseño registrada con su motivo (ADR-13, 14, 20...). Están en `docs/log/` y en el `CLAUDE.md`.
- **API REST** — Una interfaz HTTP organizada por recursos y verbos. → C01
- **Arrow function (función flecha)** — `x => x * 2` en JavaScript. → C02 §3
- **Asíncrono** — Que se ejecuta en otro hilo o más tarde, sin hacer esperar a quien lo pidió. → B05
- **Atomicidad** — "Todo o nada" dentro de una transacción. → B03 §6
- **Auditoría** — El registro de **quién hizo qué, cuándo y desde dónde** (`historial_interacciones`). → B03, D6
- **Autenticación / Autorización** — Probar quién sos / decidir qué podés hacer. → B04 §1

## B

- **Bean** — Un objeto creado y administrado por Spring. → B01 §3
- **BCrypt** — Algoritmo de hash de contraseñas, con sal y costo configurable. → B04 §2
- **BEM** — *Block–Element–Modifier*: convención de nombres de clases CSS. → C03 §9
- **Bloqueo pesimista** — `SELECT ... FOR UPDATE`: bloquear la fila antes de leerla. → B03 §7.2
- **Bytecode** — El código intermedio que genera `javac` y ejecuta la JVM. → A01 §1

## C

- **Cascada (CSS)** — Las reglas para decidir qué estilo gana cuando varios aplican. → C03 §5.2
- **Checked / unchecked** — Excepciones que el compilador obliga a tratar / que no. → A04 §1.6
- **Clase / objeto** — El molde / una pieza fabricada con él. → A01 §2
- **Claim (JWT)** — Un dato dentro del payload del token (`sub`, `roles`, `exp`...). → B04 §3.2
- **Commit / rollback** — Confirmar / deshacer una transacción. → B03 §6
- **Composición** — Una clase que *tiene* a otra (frente a heredar). → A02 §12
- **Condición de carrera** — Un resultado que depende del orden exacto de dos operaciones simultáneas. → B03 §7.1, B05 §4.1
- **CORS** — Mecanismo del navegador para permitir pedidos entre orígenes distintos. → B04 §7.4, C01 §5
- **Contraste (WCAG)** — La diferencia de luminancia entre texto y fondo (≥ 4,5:1). → C03 §10
- **Controller** — La capa que recibe HTTP y delega. → B02 §3
- **CSRF** — Ataque que aprovecha la cookie de sesión del navegador; Enexia no usa cookies de sesión. → B04 §7.3
- **CUIT** — Clave Única de Identificación Tributaria (Argentina); se valida con módulo 11. → D7 §2

## D

- **Deny-by-default** — Lo no declarado explícitamente está prohibido (`anyRequest().authenticated()`). → B04 §4.2
- **Delegación de eventos** — Un solo *listener* en un ancestro estable. → C02 §7.2
- **Dependency Injection (DI)** — Pasarle a un objeto lo que necesita en vez de que lo cree él. → B01 §2
- **DFD** — Diagrama de flujo de datos. → `docs/diagrams`, `docs/tempDFD`
- **DOM** — El árbol de objetos que el navegador arma a partir del HTML. → C02 §6
- **DTO** — *Data Transfer Object*: el objeto que viaja por la API (`*Request` / `*Response`). → B02 §4

## E

- **EAGER / LAZY** — Cargar las relaciones ya / cuando se usan. → B03 §2.4
- **Encapsulamiento** — Ocultar el estado y exponer métodos. → A02 §1
- **Endpoint** — Una ruta + un verbo de la API. → C01 §8
- **Entidad** — Una clase `@Entity` mapeada a una tabla. → B03 §2.2
- **Enum** — Un tipo con un conjunto cerrado de valores. → A02 §7
- **Escape de HTML** — Convertir `< > & " '` en entidades. → C02 §12, D9 §6
- **Excepción** — Un objeto que representa "algo anormal pasó". → A04 §1

## F

- **Fetch** — La API del navegador para hacer pedidos HTTP. → C02 §8.3
- **Filtro (servlet)** — Una pieza que corre antes del controller. → B04 §4.1
- **Flexbox / Grid** — Los dos sistemas de maquetado de CSS. → C03 §5.5–5.6
- **FK / PK** — Clave foránea / primaria. → B03 §1.2

## H

- **Hash** — Una huella irreversible de un dato. → B04 §1
- **Hibernate** — La implementación de JPA que usa Spring Boot. → B03 §2.1
- **HTTP** — El protocolo web de pregunta y respuesta. → C01 §1

## I

- **Idempotencia** — Hacerlo una o muchas veces da el mismo resultado. → B05 §6, C01 §1.3
- **IoC (inversión de control)** — El framework crea y conecta los objetos. → B01 §2
- **Interfaz** — Un contrato de métodos. → A02 §4
- **Isolation (aislamiento)** — Cuánto "ve" una transacción de otra. → B03 §6.4

## J

- **JPA / JPQL** — La especificación de persistencia / su lenguaje de consultas sobre entidades. → B03
- **JSON** — El formato de datos de la API. → C01 §2
- **JVM / JDK** — La máquina virtual / el kit de desarrollo de Java. → A01 §1
- **JWT** — *JSON Web Token*: un token firmado con la identidad y los roles. → B04 §3

## L

- **Lambda** — Una función anónima `(x) -> ...`. → A03 §2
- **Libro de correos** — La tabla `notificacion_enviada` (idempotencia y límite por destinatario). → B05 §6, D9 §4
- **Lombok** — Genera getters, setters, constructores, builders y `log`. → A04 §4.1
- **Lost update** — Una escritura que pisa a otra concurrente. → B03 §7.2

## M

- **Mass assignment** — Que el cliente pueda fijar campos que no debería. → B02 §4.1, B04 §7.6
- **Migración** — Un script SQL versionado de un cambio de esquema. → B03 §4
- **Mock / spy** — Dobles de prueba. → B06 §4
- **MVC** — *Model–View–Controller*. → B02 §1

## N

- **N+1** — Una consulta por cada elemento de una lista. → B03 §2.5
- **N:M** — Relación muchos a muchos (tabla intermedia). → B03 §1.3

## O

- **Optional** — Una caja que contiene un valor o nada. → A03 §4
- **ORM** — Mapeo objeto-relacional. → B03 §2

## P

- **Paginación** — Devolver una porción de una lista (`Page`, `Pageable`). → B02 §7
- **Pasarela de pago simulada** — `PasarelaPagoSimuladaService`. → D3 §3.5
- **Pipeline (moderación)** — El proceso asíncrono de crear un evento. → D2 §2.4, B05 §4
- **Polimorfismo** — Una misma interfaz, muchas formas. → A02 §6
- **Pool de hilos** — Un grupo reutilizable de hilos con una cola. → B05 §2
- **Promesa / `async`–`await`** — Un valor futuro / su sintaxis. → C02 §8
- **Proxy** — El objeto que Spring pone alrededor de un bean para agregar comportamiento. → B01 §9, B03 §6.6

## R

- **RBAC** — Control de acceso por rol. → B04 §4
- **Record** — Una clase inmutable de datos. → A02 §8
- **Regex** — Expresión regular. → A04 §2
- **Repository** — La capa de acceso a datos. → B03 §5
- **REQUIRES_NEW** — Una propagación que abre una transacción independiente. → B03 §6.5
- **Responsive** — Diseño que se adapta al tamaño de pantalla. → C03 §8

## S

- **Sal** — Un valor aleatorio que se mezcla con la contraseña antes de hashear. → B04 §2.2
- **Scheduler (`@Scheduled`)** — Tareas periódicas. → B01 §8, B05 §7
- **Service** — La capa de lógica de negocio. → B02 §1.1
- **Soft delete (borrado lógico)** — Marcar como dado de baja en vez de borrar. → B03 §3
- **SOLID** — Cinco principios de diseño orientado a objetos. → A02 §13
- **Stateless** — Sin estado en el servidor entre pedidos. → B04 §3.1
- **Stream** — Una tubería de operaciones sobre una colección. → A03 §3

## T

- **Token (CSS)** — Una variable de diseño con nombre (`--acento`). → C03 §6
- **Transacción** — Un conjunto de operaciones atómicas. → B03 §6
- **Trace (Playwright)** — La grabación paso a paso de un test de interfaz. → B06 §6.1

## U

- **UNIQUE** — Restricción de unicidad de la base; la defensa real contra duplicados concurrentes. → B03 §7.2

## V

- **Validación (Bean Validation)** — Anotaciones como `@NotBlank`, `@Size`, `@Pattern`. → B02 §5

## X

- **XSS** — *Cross-Site Scripting*: inyectar código en una página que otros ven. → B04 §7.2, C02 §12
