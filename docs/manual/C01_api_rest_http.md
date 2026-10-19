# C01 — API REST y HTTP: cómo se hablan el navegador y el servidor

> **Parte C · Del servidor al navegador.** Hasta ahora estudiamos el backend por dentro. Este capítulo es el **puente**: el **protocolo** (HTTP), el **estilo de diseño** (REST) y el **contrato concreto** de la API de Enexia, con todos sus endpoints. Después (C02) veremos cómo lo consume JavaScript.

**Qué vas a aprender**
- Qué es HTTP: anatomía de una petición y de una respuesta.
- Métodos, códigos de estado, cabeceras y cuerpo.
- JSON: sintaxis y cómo se corresponde con los objetos Java.
- REST: recursos, verbos, idempotencia, buenas prácticas.
- Autenticación por `Authorization: Bearer`, CORS, multipart.
- Probar la API con `curl` y con el navegador.
- **El contrato completo de Enexia**, módulo por módulo.

---

## 1. HTTP en una página

**HTTP** (*HyperText Transfer Protocol*) es el idioma con el que un **cliente** (el navegador, `curl`, Playwright) le pide cosas a un **servidor** (Spring Boot en el puerto 8080). Es un protocolo de **pregunta y respuesta**: el cliente envía una **petición**, el servidor devuelve **una respuesta**, y después "se olvida" del cliente (por eso es *sin estado*, y por eso hace falta el token en **cada** pedido: B04).

### 1.1 Anatomía de una petición

```
POST /api/auth/login HTTP/1.1                      ← línea inicial: MÉTODO  RUTA  VERSIÓN
Host: localhost:8080                               ┐
Content-Type: application/json                     │ CABECERAS (metadatos)
Accept: application/json                           ┘
                                                   ← línea en blanco
{"nickname":"gaston_test","password":"Clave123"}   ← CUERPO (opcional)
```

Partes de la **URL** `http://localhost:8080/api/publico/eventos?texto=rock&pagina=1#resultados`:

| Parte | Ejemplo | Qué es |
|---|---|---|
| Esquema | `http` | Protocolo (`https` en producción: cifrado). |
| Host y puerto | `localhost:8080` | Dónde está el servidor. |
| **Ruta** (*path*) | `/api/publico/eventos` | Qué recurso se pide. |
| **Consulta** (*query string*) | `?texto=rock&pagina=1` | Parámetros `clave=valor` separados por `&`. |
| Fragmento | `#resultados` | Solo lo usa el navegador; **no se envía** al servidor. |

### 1.2 Anatomía de una respuesta

```
HTTP/1.1 200 OK                                    ← VERSIÓN  CÓDIGO  TEXTO
Content-Type: application/json                     ← cabeceras
Content-Length: 187

{"idUsuario":3,"email":"ana@enexia.test","token":"eyJ...","tipoToken":"Bearer","roles":["PARTICIPANTE"]}
```

### 1.3 Métodos (verbos)

El método dice **qué querés hacer** con el recurso.

| Método | Intención | ¿Tiene cuerpo? | ¿Seguro?¹ | ¿Idempotente?² | Uso en Enexia |
|---|---|---|---|---|---|
| `GET` | **Leer** | No | Sí | Sí | Catálogo, listados, perfil |
| `POST` | **Crear** o ejecutar una acción | Sí | No | **No** | Registro, login, inscribirse, valorar |
| `PUT` | **Reemplazar** completo | Sí | No | Sí | Editar un evento, actualizar el perfil |
| `PATCH` | Modificar **parcialmente** | Sí | No | Depende | Decisiones del administrador |
| `DELETE` | **Quitar** | No | No | Sí | Baja lógica, cancelar inscripción |

¹ *Seguro*: no cambia nada en el servidor. ² *Idempotente*: repetirlo da el mismo resultado que hacerlo una vez (borrar dos veces el mismo recurso lo deja borrado igual).

Por qué importa: un navegador o un *proxy* pueden **repetir** un `GET` sin riesgo, pero **nunca** deben repetir un `POST` automáticamente (se inscribiría dos veces). Y **un secreto nunca va en un `GET`**: la URL queda en historiales y logs; por eso el login es `POST` (B04 §5.4).

### 1.4 Códigos de estado

Siempre un número de tres cifras; la **primera cifra** da la familia:

| Familia | Significa | Ejemplos de Enexia |
|---|---|---|
| **2xx** éxito | Salió bien. | `200 OK`, `201 Created` (registro), `202 Accepted` (crear evento; pedir recuperación) |
| **3xx** redirección | Andá a otro lado. | (poco usado: el frontend redirige con JS) |
| **4xx** error **del cliente** | Pediste mal. | `400` validación, `401` sin sesión, `403` sin rol, `404` no existe, `409` conflicto, `422` moderación, `402` pago |
| **5xx** error **del servidor** | Falló el servidor. | `500` imprevisto (mensaje genérico, nunca el detalle) |

Regla: **4xx = "corregí tu pedido"**, **5xx = "no es tu culpa, reintentá/avisá"**. La tabla completa de códigos y sus excepciones está en A04 §1.7 y B02 §3.4.

### 1.5 Cabeceras importantes

| Cabecera | Dirección | Para qué |
|---|---|---|
| `Content-Type` | ambas | Formato del cuerpo: `application/json`, `multipart/form-data`. |
| `Accept` | pedido | Qué formato espera el cliente. |
| **`Authorization: Bearer <JWT>`** | pedido | La credencial (B04). `Bearer` = "portador": quien lo presenta es quien dice ser. |
| `Origin` | pedido | Desde qué sitio se hace el pedido (lo usa CORS). |
| `Access-Control-Allow-*` | respuesta | El servidor autoriza a otro origen (CORS). |
| `Content-Length` | ambas | Tamaño del cuerpo. |

Decisión de seguridad: la API **no agrega cabeceras de diagnóstico** a las respuestas de login (antes existía `X-Reintentar-Despues`; se eliminó porque delataba que la cuenta existía).

---

## 2. JSON

**JSON** (*JavaScript Object Notation*) es el formato de datos de la API. Es texto, legible, y todos los lenguajes lo entienden.

```json
{
  "idUsuario": 3,
  "email": "ana@enexia.test",
  "activo": true,
  "apellido": null,
  "roles": ["ORGANIZADOR", "PARTICIPANTE"],
  "ubicacion": { "calle": "San Martín", "numero": "123" }
}
```

Reglas de sintaxis (muy estrictas):
- Un **objeto** va entre llaves `{ }` con pares `"clave": valor`.
- Un **arreglo** va entre corchetes `[ ]`.
- Las **claves** siempre entre **comillas dobles**; los textos también (nunca simples).
- Valores posibles: texto, número, `true`/`false`, `null`, objeto, arreglo. **No hay fechas**: se mandan como texto ISO (`"2026-10-19"`, `"2026-10-19T20:30:00"`).
- **No admite comentarios ni comas finales.**

### Correspondencia con Java

| JSON | Java | Quién convierte |
|---|---|---|
| objeto `{...}` | clase DTO | **Jackson** |
| arreglo `[...]` | `List<T>` | Jackson |
| texto `"..."` | `String`, `LocalDate`, `enum` | Jackson |
| número | `int`, `long`, `BigDecimal` | Jackson |
| `true`/`false` | `boolean` | Jackson |
| `null` | `null` | Jackson |

Al recibir (`@RequestBody`), Jackson usa el **constructor vacío + setters**; al responder, usa los **getters**. Un campo desconocido en el JSON se ignora o falla según la configuración; un **tipo equivocado** (`"edad": "abc"` donde se espera número) produce un `400`.

---

## 3. REST: un estilo para diseñar APIs

**REST** no es un protocolo: es un **conjunto de convenciones** para que una API sea predecible.

1. **Todo es un recurso, identificado por una URL con sustantivos**, no verbos: `/api/organizador/eventos/12`, no `/api/obtenerEvento?id=12`.
2. **El verbo HTTP dice la acción**: `GET /eventos` lista, `POST /eventos` crea, `PUT /eventos/12` edita, `DELETE /eventos/12` da de baja.
3. **Las respuestas hablan por su código de estado** y su cuerpo (JSON).
4. **Sin estado:** cada pedido trae todo lo necesario (el token).
5. **Colecciones y elementos:** `/eventos` (la colección) y `/eventos/{id}` (uno).
6. **Los recursos anidados reflejan la relación:** `/eventos/{id}/valoraciones`, `/organizaciones/{id}/miembros`.

### 3.1 Dónde Enexia es fiel a REST... y dónde se aparta (a propósito)

| Decisión | Detalle |
|---|---|
| **Fiel:** URLs con sustantivos y verbos HTTP correctos. | `GET /api/organizador/eventos`. |
| **Se aparta:** acciones de estado con **`PATCH` + un cuerpo con la acción**. | `PATCH /api/admin/usuarios/{id}/estado` con `{ "accion": "SUSPENDER" }`. El cliente pide una **acción**, no un estado destino (ADR-23): el servidor valida la transición según el estado real. |
| **Se aparta:** `DELETE` que **no borra**. | "DELETE por semántica HTTP, pero NO borra: muta el estado a DADO_DE_BAJA." (borrado lógico). |
| **Se aparta:** el prefijo por **rol** en la ruta. | `/api/admin/**`, `/api/organizador/**`, `/api/participante/**`: así `SecurityConfig` protege **por prefijo** con una sola regla. |
| **Se aparta:** `POST` para el login. | Es una **acción** (autenticar), no un recurso; y evita que las credenciales viajen en la URL. |

### 3.2 Convenciones transversales

- **Paginación:** parámetros `pagina` (desde 0) y `tamano`; la respuesta es una `Page` con `content`, `totalElements`, `totalPages`, `number`, `size`.
- **Filtros opcionales** como parámetros de consulta, todos combinables (`?texto=rock&idCategoria=3&idCiudad=2&desde=2026-11-01&orden=recientes`).
- **Errores uniformes:** siempre `ErrorResponse` (B02 §4.4).
- **Fechas:** ISO 8601 (`yyyy-MM-dd`, `HH:mm:ss`).
- **Idiomas:** claves en `camelCase`, mensajes de error en español, códigos de error estables en `MAYUSCULAS_CON_GUION_BAJO` para que el frontend haga `switch` sobre ellos.

---

## 4. Autenticación en la API

1. El cliente envía `POST /api/auth/login` con nickname y contraseña.
2. Recibe `{ ..., "token": "eyJ...", "tipoToken": "Bearer", "roles": [...] }`.
3. **En cada pedido protegido** agrega la cabecera:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

4. Si falta o es inválida → **401**. Si es válida pero falta el rol → **403**.

El token dura **24 horas**. No hay *refresh token*: al vencer, se vuelve a iniciar sesión.

---

## 5. CORS en una frase

El navegador impide que una página de **un origen** llame a la API de **otro** (origen = esquema + host + puerto) salvo que el servidor lo autorice (B04 §7.4). Como Enexia sirve frontend y API **desde `localhost:8080`**, son **el mismo origen** y no hay problema; cualquier otro origen (un servidor estático en el 8000, un archivo `file://`) provoca errores que **parecen** "el backend está caído". Por eso el `CLAUDE.md` prohíbe levantar un servidor aparte.

---

## 6. Multipart: datos y archivos juntos

Para crear o editar un evento se manda un `multipart/form-data`: varias **partes**, cada una con su propio tipo.

```
POST /api/organizador/eventos
Authorization: Bearer eyJ...
Content-Type: multipart/form-data; boundary=----X123

------X123
Content-Disposition: form-data; name="datos"; filename="blob"
Content-Type: application/json

{"nombre":"Festival de Invierno","descripcion":"...","idCategoria":1, ...}
------X123
Content-Disposition: form-data; name="imagenes"; filename="portada.png"
Content-Type: image/png

<bytes de la imagen>
------X123--
```

- El `boundary` (separador) lo **calcula el navegador**. Por eso en JavaScript **no se debe fijar a mano** `Content-Type` en un `fetch` con `FormData` (C02 §7).
- La parte `datos` es un **JSON dentro del multipart** (`@RequestPart("datos") EventoCrearRequest`).
- Las partes `imagenes` son archivos (`List<MultipartFile>`).

---

## 7. Probar la API a mano

### 7.1 Con `curl`

`curl` es un cliente HTTP de línea de comandos. Con la app corriendo (`./gradlew bootRun`):

```bash
# 1) Catálogo público: sin token
curl "http://localhost:8080/api/publico/eventos?pagina=0&tamano=3"

# 2) Login: guarda el token en una variable
curl -s -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"nickname":"gaston_test","password":"..."}'

# 3) Endpoint protegido: con la cabecera Authorization
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer <TOKEN>"

# 4) Ver status y cabeceras (-i)
curl -i http://localhost:8080/api/auth/me                       # sin token -> 401

# 5) Inscribirse (POST con JSON)
curl -X POST http://localhost:8080/api/participante/inscripciones \
     -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" \
     -d '{"idCronogramaTicket": 5, "metodoPago": "tarjeta"}'
```

Opciones útiles: `-X MÉTODO`, `-H "Cabecera: valor"`, `-d 'cuerpo'`, `-i` (mostrar cabeceras), `-s` (silencioso).

### 7.2 Con las DevTools del navegador

Pestaña **Network**: cada pedido muestra método, URL, status, cabeceras, cuerpo y **tiempo**. Es la herramienta principal para depurar el frontend (¿se mandó el pedido? ¿qué respondió? ¿con qué código?).

### 7.3 Con el cliente de Playwright en los tests

`BaseApiTest` usa `APIRequestContext` (B06 §5): el mismo pedido, pero automatizado y con aserciones.

---

## 8. El contrato de la API de Enexia

> Fuente: los controllers (`enexia/src/main/java/com/enexia/rg/controller/`). Rol "público" = sin token. Un **organizador** también tiene el rol *participante* (puede inscribirse a eventos ajenos). Un **administrador** es solo administrador.

### 8.1 Módulo 1 — Autenticación y cuenta (`/api/auth`, `/api/usuario`)

| Método y ruta | Rol | Qué hace | Respuestas |
|---|---|---|---|
| `POST /api/auth/registro` | público | Alta de Persona Física (RF-1.1). | 201 · 400 · 409 · 422 |
| `POST /api/auth/registro/organizacion` | público | Alta de persona + organización en un paso (DFD 7.1/7.2). | 201 · 400 · 409 |
| `POST /api/auth/login` | público | Inicio de sesión; devuelve el JWT. | 200 · 400 · 401 |
| `GET /api/auth/me` | autenticado | Datos del usuario del token. | 200 · 401 |
| `POST /api/auth/recuperacion` | público | Pide el enlace de restablecimiento. **Siempre 202**. | 202 · 400 |
| `POST /api/auth/recuperacion/confirmar` | público | Fija la contraseña nueva con el token del correo. **No** desbloquea la cuenta. | 200 · 400 |
| `GET /api/usuario/perfil` | autenticado | "Mi perfil". | 200 · 401 |
| `PUT /api/usuario/perfil` | autenticado | Actualiza el perfil. | 200 · 400 |

### 8.2 Módulo 2 — Eventos del organizador (`/api/organizador/eventos`)

| Método y ruta | Qué hace | Respuestas |
|---|---|---|
| `POST /` (multipart) | Crea un evento: **202**, moderación asíncrona (RF-2.1 a 2.6). | 202 · 400 · 403 |
| `GET /` | Dashboard paginado con filtros (RF-2.8). | 200 |
| `GET /{id}` | Detalle propio para precargar el formulario (RF-2.7). | 200 · 404 |
| `PUT /{id}` (multipart) | Edita un evento aprobado; moderación **sincrónica** (RF-2.7). | 200 · 400 · 404 · 409 |
| `DELETE /{id}` | Baja lógica (RF-2.9). | 200 · 409 |
| `GET /{id}/moderacion` | Por qué está rechazado o suspendido. | 200 · 404 |
| `GET /{id}/estadisticas` | Visitas únicas/totales, cronogramas, cupos (RF-2.10). | 200 · 404 |

### 8.3 Módulo 3 — Participación (`/api/participante`)

| Método y ruta | Qué hace | Respuestas |
|---|---|---|
| `POST /inscripciones` | Inscribirse a un ticket de un cronograma (RF-3.1/3.2). | **201** · 402 pago rechazado · 404 · 409 |
| `DELETE /inscripciones/{id}` | Cancelar (RF-3.3). | 200 · 404 · 409 |
| `GET /inscripciones` | Historial paginado (RF-3.6). | 200 |
| `POST /valoraciones` | Valorar **un cronograma** finalizado (RF-3.4/3.5). | **201** · 404 · 409 · 422 |

### 8.4 Módulo 4 — Catálogo público (`/api/publico`, GET, sin token)

| Ruta | Qué devuelve |
|---|---|
| `GET /eventos?texto&idCategoria&idCiudad&desde&orden&pagina&tamano` | Catálogo paginado con búsqueda y filtros (RF-4.1 a 4.3). |
| `GET /eventos/{id}` | Ficha técnica completa y **registra una visita** (RF-4.4/4.5). |
| `GET /eventos/{id}/valoraciones?pagina&tamano` | Reseñas del evento, cada una con su cronograma. |
| `GET /categorias`, `/provincias`, `/provincias/{id}/ciudades` | Catálogos para los filtros. |

### 8.5 Módulo 6 — Administración (`/api/admin`, solo `ADMINISTRADOR`)

| Método y ruta | Qué hace |
|---|---|
| `GET /eventos`, `GET /eventos/{id}`, `PATCH /eventos/{id}` | Cola y decisión de moderación (RF-6.1). |
| `GET /usuarios`, `GET /usuarios/resumen`, `PATCH /usuarios/{id}/estado` | Cuentas; `SUSPENDER`/`REACTIVAR`. **Reactivar es la única vía de desbloqueo** (RF-6.2). |
| `GET /categorias`, `POST /categorias`, `PUT /categorias/{id}`, `PATCH /categorias/{id}/estado` | ABM de categorías con baja lógica (RF-6.3). |
| `GET /suscripciones`, `GET /suscripciones/resumen`, `PATCH /suscripciones/{id}` | Planes; `REVOCAR`/`CAMBIAR_PLAN`/`RENOVAR` (RF-6.4). |

### 8.6 Módulo 7 — Organizaciones (`/api/organizador/organizaciones`)

| Método y ruta | Qué hace |
|---|---|
| `POST /` (201) · `GET /` (200) | Alta y listado de organizaciones propias (RF-7.2). |
| `GET /{id}/miembros` | "Mi Equipo": listar (cualquier miembro). |
| `POST /{id}/miembros` | Agregar un miembro existente (solo administrador de la organización). **201**. |
| `DELETE /{id}/miembros/{idUsuario}` | Quitar (solo administrador; nunca al último). **204** sin cuerpo. |

### 8.7 Módulo 8 — Membresías

**Del lado del organizador aún no hay endpoints** (ver `docs/tempDFD/modulo_8_membresias/`). Los que existen son los del panel de administración (§8.5).

---

## 9. Diseñar un endpoint nuevo: checklist

1. **Recurso y verbo:** ¿qué sustantivo y qué método? ¿es idempotente?
2. **Quién puede:** ¿qué rol? ¿va bajo `/api/<rol>/...`? ¿es público?
3. **Entrada:** `*Request` con **solo** los campos permitidos y sus validaciones.
4. **Salida:** `*Response` con **solo** lo que la pantalla necesita (nada de entidades).
5. **Códigos:** éxito (200/201/202), y cada error con su excepción y su código.
6. **Reglas de negocio:** en el **servicio**, con su transacción y su historial/auditoría.
7. **Seguridad:** ¿evita enumerar recursos ajenos (404 en vez de 403)? ¿no filtra información en errores ni en tiempos?
8. **Pruebas:** un test de servicio (reglas) y un test de API (contrato).
9. **Documentación:** el DFD, el RF y este contrato.

---

## 10. Errores típicos

| Error | Síntoma | Remedio |
|---|---|---|
| Usar `GET` para acciones que cambian datos. | Se repiten solos (prefetch, caché). | `POST`/`PUT`/`PATCH`/`DELETE`. |
| Secretos en la URL. | Quedan en logs e historial. | Cuerpo o cabecera. |
| Devolver `200` con un error adentro. | El cliente no puede distinguir. | El código HTTP correcto. |
| Fijar `Content-Type` a mano en un multipart. | Falta el `boundary` y el servidor no entiende nada. | Dejar que el navegador lo calcule. |
| Páginas sin tope de tamaño. | Un cliente pide un millón de filas. | `Math.min(tamano, MAXIMO)`. |
| Cambiar el nombre de un campo JSON sin avisar. | Se rompe el frontend. | Contrato estable; versionar o ampliar en vez de renombrar. |

---

## 11. Para practicar

1. Con el backend corriendo, ejecutá `curl -i "http://localhost:8080/api/publico/categorias"` y leé status, cabeceras y cuerpo.
2. Hacé login con `curl`, guardá el token y consultá `GET /api/auth/me`. Después modificá una letra del token y repetí: ¿qué código obtenés?
3. Intentá `GET /api/admin/usuarios` con el token de un participante. ¿401 o 403? Explicá por qué.
4. Pedí `GET /api/publico/eventos?tamano=100000`. ¿Qué tamaño de página devuelve realmente? ¿Dónde está el tope?
5. Diseñá (en papel) el endpoint `POST /api/organizador/suscripcion/upgrade` del DFD 8.1 usando el checklist del §9.

**Siguiente capítulo:** [C02 — JavaScript vanilla: consumir la API desde el navegador](./C02_javascript_vanilla.md)
