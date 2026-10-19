# A03 — Java funcional y colecciones

> **Parte A · Fundamentos de Java.** Hasta ahora trabajaste con **un** objeto a la vez. Los programas reales manejan **grupos** de objetos (los eventos de una página, los roles de un usuario, los tickets de un cronograma). Este capítulo cubre cómo se guardan los grupos (**colecciones**) y la forma moderna de recorrerlos y transformarlos (**programación funcional**: lambdas, *streams*, `Optional`).

**Qué vas a aprender**
- `List`, `Set` y `Map`: qué son, en qué se diferencian y cuándo usar cada una.
- Colecciones mutables e inmutables (`List.of`, `new ArrayList<>()`).
- Qué es una **función como valor**: lambdas, interfaces funcionales y referencias a métodos (`Usuario::getIdUsuario`).
- *Streams*: `map`, `filter`, `collect`, `groupingBy`, `toMap`.
- `Optional`: la forma de decir "puede no haber resultado" sin `null`.
- Bloques de texto (`"""`), formateo de `String` y `StringBuilder`.

---

## 1. Colecciones: guardar grupos de cosas

Una **colección** es un objeto que contiene otros objetos. Todas viven en `java.util` y son **genéricas** (A02 §11): `List<Evento>` es "una lista de eventos".

| Colección | Qué es | Permite repetidos | Mantiene orden | Se accede por |
|---|---|---|---|---|
| `List<T>` | Secuencia ordenada | Sí | Sí (el de inserción) | posición (`get(0)`) |
| `Set<T>` | Conjunto de elementos únicos | **No** | Depende de la implementación | pertenencia (`contains`) |
| `Map<K, V>` | Pares clave → valor | Valores sí, **claves no** | Depende | clave (`get(clave)`) |

`List`, `Set` y `Map` son **interfaces** (A02 §4). Las clases concretas más usadas:

- `ArrayList` (lista basada en arreglo), `HashSet` (conjunto por *hash*), `HashMap` (mapa por *hash*).

Se declara con la interfaz y se crea con la clase:

```java
List<Long> ids = new ArrayList<>();
Set<Long> idsTicketEnviados = new HashSet<>();      // PublicacionEventoService
Map<Long, Long> mapa = new HashMap<>();             // AdminCategoriaService
```

### 1.1 Operaciones básicas

```java
List<String> roles = new ArrayList<>();
roles.add("PARTICIPANTE");             // agregar al final
roles.add("ORGANIZADOR");
roles.size();                          // 2
roles.contains("ORGANIZADOR");         // true
roles.get(0);                          // "PARTICIPANTE"
roles.isEmpty();                       // false
roles.remove("PARTICIPANTE");

Set<Long> ids = new HashSet<>();
ids.add(3L);
ids.add(3L);                           // no hace nada: ya estaba
ids.size();                            // 1

Map<Long, String> nombres = new HashMap<>();
nombres.put(1L, "Ana");
nombres.get(1L);                       // "Ana"
nombres.get(99L);                      // null: no hay clave 99
nombres.getOrDefault(99L, "—");        // "—": el valor por defecto
nombres.putIfAbsent(1L, "Otra");       // no pisa: ya existe la clave 1
nombres.keySet();                      // Set con las claves
nombres.values();                      // Collection con los valores
```

### 1.2 Cuándo usar cada una (con casos del proyecto)

- **`List`** cuando importa el **orden** o puede haber repetidos: los cronogramas de un evento ordenados por fecha, los resultados de una página.
- **`Set`** para preguntar rápido **"¿está?"** y evitar duplicados. En `EventoMapper.EstadoParticipante`, `Set<Long> idsInscriptoActivo` responde "¿esta persona está inscripta en el cronograma 12?" con `contains`, en tiempo constante. Una `List` tendría que recorrerse entera.
- **`Map`** para **buscar por clave**. En `AdminUsuarioService.listar` se arma un `Map<Long, Long> eventosPorUsuario` (id de usuario → cantidad de eventos) para ir **a la tabla de resultados sin hacer una consulta por usuario** (evita el problema N+1, B03).

El método `getOrDefault` aparece una y otra vez para resolver "si no hay dato, es 0":

```java
publicados.getOrDefault(c.getIdCategoria(), 0L)     // AdminCategoriaService
```

### 1.3 Colecciones inmutables: `List.of`, `Set.of`, `Map.of`

```java
List<String> vacia = List.of();                       // lista vacía inmutable
List<String> uno = List.of("ADMINISTRADOR");
Set<Long> nada = Set.of();
Map<Long, String> sinDatos = Map.of();
```

Son **de solo lectura**: `add`, `remove`, `put` lanzan `UnsupportedOperationException`. Además **no admiten `null`**. Sirven cuando devolvés "un valor por defecto" que nadie debe modificar:

```java
// Usuario.getRoles()
if (usuarioRoles == null || usuarioRoles.isEmpty()) {
    return List.of();
}
```

Y en `InscripcionService.historial`, una expresión que **evita una consulta innecesaria**:

```java
Map<Long, List<HistorialEstadoItem>> historialPorInscripcion = ids.isEmpty()
        ? Map.of()
        : historialRepository.buscarPorInscripciones(ids).stream()
                .collect(/* ... */);
```

Si la página no tiene inscripciones, se usa un mapa vacío y **no se toca la base**.

> **Trampa clásica:** `Stream.toList()` también devuelve una lista **inmutable**; `Collectors.toList()` devuelve una mutable (aunque no se garantiza). Si vas a agregar elementos después, creá `new ArrayList<>(...)`.

### 1.4 Recorrer colecciones

```java
for (CronogramaRequest peticion : cronogramasNuevos) {   // for-each
    // ...
}
```

El `for-each` funciona con cualquier colección. No modifiques la colección que estás recorriendo (excepto con un `Iterator`): provoca `ConcurrentModificationException`.

---

## 2. Funciones como valores

Hasta Java 7, para pasar "un comportamiento" a un método había que escribir una clase entera. Desde Java 8, **una función puede tratarse como un valor**: guardarse en una variable, pasarse como argumento, devolverse. Eso es la base de la **programación funcional**.

### 2.1 Interfaces funcionales

Una **interfaz funcional** es una interfaz con **un solo método abstracto**. Java ya trae las más comunes:

| Interfaz | Método | Qué representa | Ejemplo en el proyecto |
|---|---|---|---|
| `Runnable` | `void run()` | una tarea sin parámetros ni resultado | `Executor.execute(runnable)`; `Runnable::run` en los tests |
| `Supplier<T>` | `T get()` | "dame un valor" | `orElseThrow(() -> new ...)` |
| `Function<T, R>` | `R apply(T)` | transforma un `T` en un `R` | `.map(Usuario::getIdUsuario)` |
| `Predicate<T>` | `boolean test(T)` | condición | `.filter(Objects::nonNull)` |
| `Consumer<T>` | `void accept(T)` | hace algo con un `T` | `.ifPresent(usuario -> {...})` |

### 2.2 Lambdas

Una **lambda** es una función anónima con la forma `(parámetros) -> expresión`.

```java
// Supplier: no recibe nada, devuelve una excepción
() -> new RecursoNoEncontradoException("No se encontro la cuenta")

// Function: recibe u, devuelve u
u -> u

// Predicate sobre un cronograma
c -> c.getFecha().isAfter(LocalDate.now())

// Con varias líneas, se usan llaves y return
usuario -> {
    String token = emitirToken(usuario);
    emailService.enviarAvisoBloqueo(usuario.getEmail(), token);
}
```

Reglas:
- Los tipos de los parámetros se **deducen** del contexto.
- Con un solo parámetro se pueden omitir los paréntesis: `u -> u`.
- Si el cuerpo es una **expresión**, el resultado es su valor; con llaves hace falta `return` (si devuelve algo).
- Una lambda **no puede modificar variables locales** de afuera: solo leerlas, y éstas deben ser *efectivamente finales* (no reasignadas). Por eso en `CorreosTest` aparece `final int n = intento;` antes de usar el contador dentro de la lambda.

### 2.3 Referencias a métodos

Cuando la lambda solo **llama a un método existente**, se puede abreviar con `::`:

| Forma larga | Referencia a método |
|---|---|
| `u -> u.getIdUsuario()` | `Usuario::getIdUsuario` |
| `i -> i.getIdInscripcion()` | `Inscripcion::getIdInscripcion` |
| `x -> Objects.nonNull(x)` | `Objects::nonNull` |
| `r -> r.run()` | `Runnable::run` |
| `h -> this.mapearHistorialItem(h)` | `this::mapearHistorialItem` |

Ejemplo real (`InscripcionService.historial`):

```java
List<Long> ids = pagina.getContent().stream().map(Inscripcion::getIdInscripcion).toList();
```

Se lee: *"del contenido de la página, convertí cada inscripción en su id y juntalos en una lista"*.

---

## 3. Streams: procesar colecciones como una tubería

Un **stream** es una secuencia de elementos por la que se pasa una **cadena de operaciones** (una *pipeline*). No es una colección (no guarda datos): es una forma de describir **qué hacer** con ellos.

```
colección ──► .stream() ──► operaciones intermedias ──► operación terminal
              (fuente)      filter / map / distinct       collect / toList / count
```

- **Operaciones intermedias** (`filter`, `map`, `distinct`, `sorted`...): devuelven otro stream y son **perezosas**: no hacen nada hasta que hay una terminal.
- **Operación terminal** (`toList`, `collect`, `count`, `anyMatch`, `forEach`): dispara el recorrido y produce un resultado.

### 3.1 `map` y `toList`

`map` **transforma** cada elemento (uno a uno).

```java
List<Long> ids = pagina.getContent().stream()
        .map(Inscripcion::getIdInscripcion)     // Inscripcion -> Long
        .toList();
```

### 3.2 `filter`

`filter` **se queda** con los que cumplen una condición (un `Predicate`).

```java
List<Long> idsCronograma = pagina.getContent().stream()
        .map(i -> i.getCronogramaTicket() == null || i.getCronogramaTicket().getCronograma() == null
                ? null : i.getCronogramaTicket().getCronograma().getIdCronograma())
        .filter(Objects::nonNull)      // descarta los null
        .distinct()                    // y los repetidos
        .toList();
```

Pipeline completa: *"de cada inscripción sacá el id de su cronograma (o `null` si falta algún eslabón), tirá los nulos, tirá los repetidos y devolvé la lista"*. `distinct()` elimina duplicados con `equals`/`hashCode` (A02 §9).

### 3.3 `collect` y los *collectors*

`collect` junta el resultado en una estructura. Los **collectors** de `java.util.stream.Collectors` describen cuál.

**`Collectors.toMap(claveFn, valorFn)`** — arma un `Map`. Es el caso más usado del proyecto: **indexar por id** para buscar rápido.

```java
// AdminUsuarioService.listar
Map<Long, Usuario> porId = usuarioRepository.cargarParaAdmin(ids.getContent()).stream()
        .collect(Collectors.toMap(Usuario::getIdUsuario, u -> u));
```

Se lee: *"por cada usuario, la clave es su id y el valor es el propio usuario"*. Después se hace `porId.get(id)` sin recorrer nada. ⚠️ Si dos elementos producen **la misma clave**, `toMap` lanza `IllegalStateException`; sirve acá porque los ids son únicos.

**`Collectors.groupingBy(claveFn)`** — **agrupa** en un `Map<Clave, List<Valor>>`:

```java
// EventoMapper.aFicha
Map<Long, List<CronogramaTicket>> ticketsPorCronograma = tickets.stream()
        .collect(Collectors.groupingBy(t -> t.getCronograma().getIdCronograma()));
```

Resultado: para cada id de cronograma, la lista de sus tickets. Después, `ticketsPorCronograma.getOrDefault(id, List.of())` entrega los de un cronograma (o vacío).

**`groupingBy` con un colector "downstream"** (`InscripcionService.historial`):

```java
.collect(Collectors.groupingBy(
        h -> h.getInscripcion().getIdInscripcion(),
        Collectors.mapping(this::mapearHistorialItem, Collectors.toList())));
```

Agrupa los renglones de historial por inscripción **y**, ya dentro de cada grupo, los transforma con `mapearHistorialItem`. El segundo argumento le dice qué hacer con cada grupo.

### 3.4 Pipeline completa del proyecto, desglosada

```java
// AdminUsuarioService.listar
List<AdminUsuarioResponse> contenido = ids.getContent().stream()
        .map(porId::get)                       // 1) de cada id, el usuario (mapa)
        .filter(u -> u != null)                // 2) por si alguno ya no está
        .map(u -> aRespuesta(u, administrador,
                eventosPorUsuario.getOrDefault(u.getIdUsuario(), 0L)))   // 3) a DTO
        .toList();                             // 4) a lista
```

1. `porId::get` es la **referencia a un método de un objeto concreto** (`porId.get(x)`), usada como `Function`.
2. El filtro es defensivo: si otro hilo borró el usuario entre las dos consultas, no rompe.
3. La lambda calcula la respuesta; `getOrDefault(..., 0L)` evita `null` cuando el usuario no tiene eventos.

Y una pipeline pequeña dentro de `Usuario.getRoles()`:

```java
List<String> roles = usuarioRoles.stream()
    .map(ur -> ur.getRol().getNombreRol())     // UsuarioRol -> nombre del rol
    .collect(Collectors.toList());
```

### 3.5 Otras operaciones útiles (no todas están en el proyecto, pero las vas a necesitar)

| Operación | Para qué | Ejemplo |
|---|---|---|
| `anyMatch(pred)` | ¿alguno cumple? | `roles.stream().anyMatch(r -> r.equals("ADMINISTRADOR"))` |
| `allMatch(pred)` | ¿todos cumplen? | |
| `count()` | cuántos hay | `eventos.stream().filter(e -> e.activo()).count()` |
| `sorted(comparator)` | ordenar | `.sorted(Comparator.comparing(Evento::getFecha))` |
| `limit(n)` / `skip(n)` | recortar | |
| `flatMap` | aplanar listas de listas | |
| `Collectors.joining(", ")` | unir textos | `nombres.stream().collect(Collectors.joining(", "))` |

### 3.6 ¿Cuándo un `for` es mejor que un stream?

Los streams brillan para **transformar y filtrar**. Cuando hay **estado que cambia** durante el recorrido o varias decisiones interdependientes, un `for` explícito se lee mejor. `ValidadorCuit.esValido` usa un `for` con acumulador (`suma += ...`) y es lo correcto: acumular con efectos laterales dentro de un stream sería ilegible.

---

## 4. `Optional`: "puede que no haya resultado"

**El problema:** un método de búsqueda puede no encontrar nada. Devolver `null` obliga a todos a acordarse de controlarlo, y el que se olvida provoca un `NullPointerException`.

**La solución:** `Optional<T>` es una **caja** que contiene **un valor o nada**. El tipo de retorno **avisa** que puede estar vacío; el compilador no te deja olvidarlo.

Los *repositories* de Spring Data devuelven `Optional`:

```java
Optional<Usuario> buscarActivoPorEmailConRoles(String email);
```

Cómo se lo consume (todos de `AdminUsuarioService` y similares):

```java
// 1) orElseThrow: si no hay valor, lanzá esta excepción. EL MÁS USADO.
Usuario administrador = usuarioRepository.buscarActivoPorEmailConRoles(emailAdmin)
        .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

// 2) orElse: si no hay, usá este valor por defecto
EventoDetalle detalle = detalleRepository.buscarConUbicacion(idEvento).orElse(null);

// 3) ifPresent: si hay, hacé algo con él
usuarioRepository.findById(idUsuario).ifPresent(usuario -> {
    String tokenPlano = emitirToken(usuario);
    emailService.enviarAvisoBloqueo(usuario.getEmail(), tokenPlano);
});
```

Otros métodos: `isPresent()`, `isEmpty()`, `map(...)` (transforma el contenido si existe), `get()` (¡evitalo!: lanza excepción si está vacío).

> **El `orElseThrow` recibe un `Supplier`** (`() -> new ...`), no la excepción ya creada. Es **perezoso a propósito**: la excepción (que captura la traza de la pila, algo costoso) solo se fabrica si realmente hace falta.

**Cuándo usar `Optional`:** como **tipo de retorno** de métodos que pueden no encontrar algo. **No** como tipo de atributo ni de parámetro.

---

## 5. Texto: `String`, bloques de texto, `StringBuilder`

### 5.1 Métodos de `String` frecuentes

```java
String s = "  Hola, Enexia  ";
s.strip();                  // "Hola, Enexia"   (quita espacios; mejor que trim() con Unicode)
s.isBlank();                // false            (true si está vacío o son solo espacios)
s.toLowerCase();
s.contains("Enexia");       // true
s.startsWith("  H");
s.substring(2, 6);          // "Hola"           (desde 2, hasta 6 sin incluir)
s.replace("Hola", "Chau");
s.split(",");               // arreglo ["  Hola", " Enexia  "]
String.valueOf(10);         // "10"
```

### 5.2 Formatear: `formatted` y `String.format`

```java
"Pasarela simulada APROBO una operacion de %s (token %s)".formatted(monto, token);
String.format("%02x", b);       // de RecuperacionCuentaService.hashear: byte a hexadecimal de 2 dígitos
```

`%s` inserta un texto, `%d` un entero, `%02x` un byte en hexadecimal con dos dígitos.

### 5.3 Bloques de texto (`"""`)

Un **bloque de texto** permite escribir texto **de varias líneas** sin escapes ni concatenaciones. Es el formato de todas las consultas JPQL y de los cuerpos de los correos del proyecto:

```java
@Query("""
        SELECT COUNT(e) FROM Evento e
        WHERE e.organizador.idUsuario = :idOrganizador
          AND e.estadoOrganizador.estadoOrganizador <> :estadoBaja
        """)
```

```java
despachar(destinatario, new Correo(
        "Enexia - Tu evento volvio a estar publicado",
        """
        Un administrador de Enexia reviso la suspension de tu evento "%s" y la revirtio.

        El evento volvio a ser visible en el catalogo publico."""
                .formatted(nombreEvento),
        null));
```

- Se abre con `"""` + salto de línea y se cierra con `"""`.
- Las comillas simples dobles `"` van **sin escapar**.
- La indentación común se descarta.
- Combina con `.formatted(...)` para insertar datos.

### 5.4 `StringBuilder`: armar texto sin crear mil `String`

Como los `String` son inmutables, concatenar en un bucle crea un objeto nuevo en cada vuelta. `StringBuilder` es un buffer **mutable**:

```java
// CorreoHtml.escapar
StringBuilder salida = new StringBuilder(valor.length() + 16);
for (int i = 0; i < valor.length(); i++) {
    char c = valor.charAt(i);
    switch (c) {
        case '&' -> salida.append("&amp;");
        // ...
        default -> salida.append(c);
    }
}
return salida.toString();
```

Para uniones simples (`"a" + b + "c"`) la concatenación con `+` está bien; `StringBuilder` conviene dentro de bucles.

---

## 6. Números y utilidades que aparecen todo el tiempo

- **Sufijo `L`** para literales `long`: `0L`, `10_000L`. Se mezcla con `Long`/`long` en `getOrDefault(id, 0L)`.
- **`Objects`**: `Objects.nonNull(x)`, `Objects.equals(a, b)`, `Objects.hash(...)`.
- **`Math`**: `Math.max(0, organizadores - proConCuentaActiva)` evita un conteo negativo en el panel de suscripciones.
- **`UUID.randomUUID().toString()`**: identificador aleatorio único (el token de operación de la pasarela de pago simulada).
- **`SecureRandom`**: aleatorio **criptográficamente seguro**, el que se usa para el token de recuperación (B04); `Random` **no** sirve para eso.
- **`Base64`**: codifica bytes como texto (`Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)`).

---

## 7. Errores típicos

| Error | Síntoma | Remedio |
|---|---|---|
| Modificar una lista de `List.of()` / `toList()`. | `UnsupportedOperationException`. | Copiá: `new ArrayList<>(lista)`. |
| `toMap` con claves repetidas. | `IllegalStateException: Duplicate key`. | Garantizar unicidad, o pasar una función de fusión como tercer argumento. |
| Llamar `Optional.get()` sin controlar. | Excepción en producción. | `orElseThrow`, `orElse`, `ifPresent`. |
| `map(...)` que devuelve `null` y luego `filter` ausente. | `NullPointerException` más adelante. | `filter(Objects::nonNull)` donde pueda aparecer. |
| Una consulta a la base **por cada elemento** del bucle (N+1). | Lentitud. | Una consulta en lote y un `Map` (§1.2, B03). |
| Usar un stream con efectos laterales (`forEach` que muta). | Resultados difíciles de razonar. | `for` explícito, o separar la transformación del efecto. |

---

## 8. Para practicar

1. Dada una `List<Inscripcion>`, escribí una pipeline que devuelva la **cantidad** de las que están `CONFIRMADA`. (Pista: `filter` + `count`.)
2. Dada una `List<CronogramaTicket>`, calculá el **cupo total** (suma de `cupoMaximo`). (Pista: `mapToInt(...).sum()`.)
3. Reescribí `Usuario.getRoles()` usando solo `for` y compará la legibilidad.
4. En `AdminCategoriaService`, ubicá los dos `Map<Long, Long>` (`publicados` y `totales`) y explicá qué problema de rendimiento evitan.
5. ¿Por qué `EstadoParticipante` usa `Set<Long>` y no `List<Long>`? ¿Qué cambia en el costo de `contains`?
6. Convertí este bucle en una pipeline y viceversa; decidí cuál preferís y por qué:
   ```java
   List<String> nombres = new ArrayList<>();
   for (Categoria c : categorias) {
       if (c.isActiva()) {
           nombres.add(c.getNombreCategoria());
       }
   }
   ```

**Siguiente capítulo:** [A04 — Excepciones, expresiones regulares y librerías](./A04_excepciones_regex_y_librerias.md)
