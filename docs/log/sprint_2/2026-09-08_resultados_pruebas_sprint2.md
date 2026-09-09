# Resultados de pruebas — Sprint 2

**Fecha de ejecución:** 2026-09-08 (UTC-3)
**Cambios evaluados:** [`2026-09-08_sprint2_backend.md`](./2026-09-08_sprint2_backend.md)

## Resultado global

| Suite | Herramienta | Casos | ✅ | ❌ |
|---|---|---:|---:|---:|
| Unitarias | JUnit 5 + Mockito | 106 | 106 | 0 |
| API de extremo a extremo | Playwright (`APIRequestContext`) | 44 | 44 | 0 |
| E2E navegador (regresión Sprint 1) | Playwright Chromium | 14 | 14 | 0 |
| Carga de contexto Spring | JUnit 5 | 1 | 1 | 0 |
| API — Sprint 2 | Postman / Newman | 28 peticiones · **150 aserciones** | 150 | 0 |
| API — Sprint 1 (regresión) | Postman / Newman | 17 peticiones · **95 aserciones** | 95 | 0 |
| **TOTAL** | | **165 tests + 245 aserciones** | **todos** | **0** |

```
BUILD SUCCESSFUL in 1m 3s
165 tests, 0 fallos, 59.6 s
```

**Entorno:** Windows 10 · OpenJDK Temurin 17.0.19 · Gradle · Spring Boot 4.1.0 ·
MariaDB 10.4.32 (XAMPP) puerto 3306 · Playwright Java 1.61.0 · Newman 6.x ·
backend en `localhost:8080` · Cloudinary en **modo simulado** (sin credenciales).

---

## 1. Unitarias — JUnit 5 + Mockito (106/106)

Sin Spring, sin base de datos, sin navegador. Todas las dependencias son `@Mock`,
así que lo único bajo examen es la lógica del método. Corren en **~5 segundos**.

### 1.1 `ValidadorCuitTest` — 18 casos

| Bloque | Casos | Verifica |
|---|---:|---|
| CUIT válidos | 6 | Cinco números reales verificables contra el padrón, más el formato con guiones |
| CUIT inválidos | 8 | Verificador equivocado, largos incorrectos, `null`, letras mezcladas |
| Normalización y formato | 3 | `30-71659554-0` ↔ `30716595540`, y que `formatear()` no rompa con entrada inválida |
| **Discrepancia con el DFD** | 1 | **Fija que con resto 0 el verificador es 0, no 9** |

> Se usan CUIT **reales** y no inventados a propósito: un valor inventado que "da bien"
> con mi implementación probaría únicamente que la implementación es consistente
> consigo misma, no que el algoritmo sea el correcto.
>
> El último caso es el más importante del bloque: si alguien "corrige" el código para
> que coincida con el rótulo del nodo 7.3.6B del DFD, la suite falla y queda claro
> cuál de los dos está mal.

### 1.2 `AuthServiceLoginTest` — 12 casos

Lo que se prueba no es "que el login funcione" (eso es un solo test), sino que
**no filtre información**. Son tres propiedades independientes y cada una necesita
su prueba:

| Bloque | Casos | Verifica |
|---|---:|---|
| Camino feliz | 1 | JWT con roles + reseteo de contadores |
| **Respuesta uniforme** | 6 | Las cinco ramas de rechazo dan el mismo mensaje público, y todas comparten la raíz que el handler traduce a un único 401 |
| **Tiempo constante** | 3 | Email inexistente, cuenta bloqueada y cooldown pagan **una** comparación BCrypt cada una |
| Aviso por email | 2 | Al bloquear se emite el enlace; un fallo que **no** bloquea no manda nada |

> El bloque de tiempo constante es el que más fácil se rompe sin darse cuenta al tocar
> el código: uniformar cuerpo y status no sirve de nada si el reloj delata igual.
> Se verifica contando invocaciones a `passwordEncoder.matches()`.
>
> El caso `todasCompartenLaRaiz` es preventivo: si mañana alguien agrega una excepción
> de login que no herede de `AutenticacionFallidaException`, el handler global no la
> atraparía, caería en el catch-all como 500 y **esa diferencia bastaría** para
> distinguir la rama desde afuera.

### 1.3 `PersonaJuridicaServiceTest` — 16 casos

| Bloque | Casos | Verifica |
|---|---:|---|
| Alta correcta | 5 | Nace `REVISION_PENDIENTE`+`INACTIVO`, CUIT normalizado al guardar y formateado al devolver, textos recortados, fundador `ADMINISTRADOR`, historial y email |
| Rechazos | 6 | Rol, CUIT inválido, CUIT duplicado, moderación, ciudad inexistente, catálogo sin sembrar |
| Autoría de eventos | 5 | Sin organización → `null`; no ser miembro → 404; en revisión → rechaza; aprobada pero inactiva → rechaza; aprobada+activa → habilita |

> El test de moderación verifica **el orden**: se comprueba con `verify(..., never())`
> que ni `personaJuridicaRepository` ni `ubicacionRepository` recibieron una escritura.
> Si la moderación corriera después, el texto rechazado quedaría en la base aunque el
> alta se revierta.

### 1.4 `EventoServiceTest` — 13 casos

| Bloque | Casos | Verifica |
|---|---:|---|
| Skeleton | 3 | Nace `EN_PROCESO`+`PUBLICADO` con fecha; **no persiste contenido**; el pipeline se dispara publicando un evento |
| Validaciones | 7 | Horarios incoherentes, duración cero, fechas duplicadas, sin imágenes, más de 3, categoría inexistente, límite de plan |
| Baja lógica | 3 | Muta el estado sin borrar la fila; evento ajeno → 404; baja repetida → 409 |

> `noPersisteContenido` es el guardián de la regla arquitectónica del proyecto. Si un
> cambio futuro empezara a guardar el título "porque es cómodo para el dashboard", esta
> prueba lo detecta.
>
> `disparaPorEvento` fija la corrección de la condición de carrera: verifica que se
> publique un `EventoCreadoEvent` y no que se llame al service asíncrono.

### 1.5 `ModeracionEventoServiceTest` — 12 casos

| Bloque | Casos | Verifica |
|---|---:|---|
| Fase 1 (texto) | 4 | Título y descripción ofensivos; **las imágenes se descartan sin procesar**; se notifica el rechazo |
| Fase 2 (imágenes) | 5 | Todas rechazadas → `MODERACION_IMAGEN`; ninguna → `SIN_IMAGENES_VALIDAS`; una de tres alcanza para publicar; se procesan todas; aviso de aprobación |
| Contención | 3 | Fallo de publicación → `ERROR_PIPELINE`; nunca propaga excepciones; un fallo doble no explota |

> `noSubeImagenesSiElTextoFalla` usa `verifyNoInteractions(cloudinaryService)`: no basta
> con que rechace, hay que probar que **ni siquiera intentó subir**. Es una optimización
> que RF-2.2 pide explícitamente.
>
> El bloque de contención existe porque en un método asíncrono `void` una excepción no
> llega a nadie: el hilo del pool la registra y sigue, y el evento quedaría `EN_PROCESO`
> para siempre sin que nadie se entere.

### 1.6 `RecuperacionCuentaServiceTest` — 10 casos

| Bloque | Casos | Verifica |
|---|---:|---|
| Emisión | 5 | Emite y envía; **guarda el hash, no el token**; invalida los previos; vigencia de 30 min; email inexistente no emite nada |
| Consumo | 5 | Restablece y limpia contadores; desbloquea `BLOQUEADO`; **no levanta `SUSPENDIDO`**; mismo mensaje para token inexistente y vencido; contraseñas distintas cortan antes de consultar |

> `guardaHashNoToken` compara lo guardado contra lo enviado y exige que **difieran**,
> más los 64 caracteres del SHA-256 en hexadecimal.
>
> `noLevantaSuspension` protege una regla fácil de romper: si cambiar la contraseña
> levantara una suspensión, cualquier sancionado la esquivaría pidiendo un restablecimiento.

### 1.7 `EventoMapperTest` — 10 casos

| Bloque | Casos | Verifica |
|---|---:|---|
| Firma del organizador (RF-7.4) | 6 | Fantasía > razón social > nombre y apellido; fantasía en blanco cuenta como ausente; dos fallbacks que no explotan |
| Cupos y precios | 4 | Cupo disponible, agotado, nunca negativo, `0.00` es gratuito, nulos tolerados |

> La firma tiene tres niveles de precedencia y un fallback: es exactamente el tipo de
> cadena de condiciones que se rompe al tocarla, y donde el error no se nota hasta que
> un evento corporativo aparece firmado con el nombre y apellido de un empleado — una
> **filtración de dato personal**.
>
> `gratuitoConEscala` cubre una trampa real de `BigDecimal`: `equals` compara también
> la escala, así que `0.00` no sería "igual" a `0` y una entrada gratuita se mostraría
> como paga. Por eso el código usa `compareTo`.

### 1.8 `AuthServiceRegistroTest` — 15 casos (regresión de Sprint 1)

Sin cambios funcionales. Se ajustaron los mocks del constructor (`RateLimitService`
salió, entraron `RecuperacionCuentaService` y `PersonaJuridicaService`).

> **Fallo real que detectó esta suite:** el cambio para exponer los roles en memoria
> (`usuario.setUsuarioRoles(...)`) inicialmente usaba el retorno de
> `usuarioRolRepository.save(...)`, que en un mock devuelve `null`. Ocho tests fallaron
> con `NullPointerException`. Se corrigió agregando la instancia local en vez del
> retorno del repositorio — mejor código además de test verde.

---

## 2. API de extremo a extremo — Playwright (44/44)

Las pantallas de Sprint 2 todavía no existen, pero los endpoints sí. Playwright, además
del navegador, ofrece `APIRequestContext`: un cliente HTTP con la misma API de
aserciones, que habla directo con el backend.

Eso permite probar **hoy** el recorrido completo `HTTP → Spring → MariaDB`, y cuando las
pantallas existan estas pruebas siguen valiendo como capa de contrato de la API.

| Clase | Casos | Cobertura |
|---|---:|---|
| `AuthApiTest` | 14 | Registro (4), respuesta uniforme del login (4), recuperación (3), RBAC (3) |
| `OrganizacionApiTest` | 11 | Camino público (7), camino autenticado (4) |
| `EventoApiTest` | 19 | Creación y pipeline (7), catálogo público (7), dashboard y baja (5) |

### Qué aportan sobre las unitarias

Las unitarias verifican que cada rama lance la excepción correcta. Lo que **no** pueden
verificar es cómo sale eso por HTTP. Tres ejemplos concretos:

- `mismaRespuestaParaAmbosCasos` compara status, código y mensaje entre "contraseña
  incorrecta" y "email inexistente". Un `@ExceptionHandler` más específico agregado por
  descuido pasaría todas las unitarias y reabriría la fuga; esta lo detecta.
- `elCooldownNoSeNota` hace cuatro intentos fallidos seguidos y compara los cuerpos
  **byte a byte** (descartando el `timestamp`). Antes de la política nueva, el tercero
  devolvía 403 con cabecera; ahora los cuatro son idénticos.
- `transaccionalidad` (organizaciones) reintenta un alta que falló por CUIT inválido, con
  el CUIT corregido. Si la cuenta hubiera quedado creada a medias, el reintento chocaría
  con "ese email ya está registrado" y el usuario quedaría trabado sin entender por qué.

### Espera activa, nunca `Thread.sleep` fijo

El pipeline de moderación tarda lo que tarde. Un `sleep` de 2 segundos sería a la vez
lento (casi siempre termina antes) y frágil (a veces no alcanza). Se usa **Awaitility**:
consulta cada 200ms hasta que el estado cambia o vence el límite de 20s. La prueba corre
rápido cuando el sistema responde rápido y solo falla si de verdad se colgó.

### El gancho de rechazo simulado

Sin credenciales de Cloudinary, toda imagen se aprueba y la rama "todas las imágenes
rechazadas" quedaría **sin probar en toda la suite** — justo donde se esconden los
errores. Por eso `CloudinaryService` rechaza en modo simulado cualquier archivo cuyo
nombre contenga `rechazar`, y las pruebas suben `foto-a-rechazar.png`.

---

## 3. Postman / Newman — Sprint 2 (150/150 aserciones)

```
┌─────────────────────┬──────────┬────────┐
│                     │ executed │ failed │
├─────────────────────┼──────────┼────────┤
│            requests │       28 │      0 │
│        test-scripts │       56 │      0 │
│  prerequest-scripts │       29 │      0 │
│          assertions │      150 │      0 │
└─────────────────────┴──────────┴────────┘
total run duration: 7.7s
average response time: 89ms [min: 4ms, max: 417ms]
```

### Estructura

| # | Bloque | Peticiones |
|---|---|---|
| 00 | Sonda | Backend arriba y catálogos sembrados |
| 01–04 | Organizaciones | Alta correcta, CUIT inválido, CUIT mal formado, CUIT duplicado con otro formato |
| 05–08 | Bloqueo silencioso | Login, fallo, email inexistente idéntico, tercer fallo sin notarse |
| 09–11 | Recuperación | Email existente / inexistente idénticos, token inventado |
| 12–14 | RBAC y autoría | Sin token, listado propio, evento bajo organización no aprobada |
| 15–17 | Eventos | Creación multipart, espera, verificación de `APROBADO_SISTEMA` |
| 18–23 | Catálogo público | Búsqueda, ficha técnica, filtros, rango inválido, tope de paginación, estadísticas |
| 24–27 | Baja lógica | Baja, baja repetida, desaparición del catálogo, 404 en la ficha |

### Tres aserciones transversales, en **todas** las peticiones

```javascript
pm.test('Responde JSON', ...)
pm.test('Sin cabeceras que delaten el estado de la cuenta', ...)   // X-Reintentar-Despues
pm.test('Ningun 500: los errores previstos se traducen, no explotan', ...)
```

> La tercera es la que más valor tiene a largo plazo: un 500 significa que una excepción
> escapó del `GlobalExceptionHandler`, y con ella el stack trace hacia el cliente.

### Identidades irrepetibles

El sistema usa borrado lógico y **nunca borra filas**, así que reutilizar un email, un
DNI o un CUIT haría que la suite pase una sola vez y después devuelva 409 en todo. El
script de pre-request de la colección genera un sufijo por corrida y, con él, **calcula
un CUIT válido al vuelo** aplicando el módulo 11 en JavaScript.

### Limitación conocida

La petición **16** es una pausa fija de 3 segundos: Newman no tiene espera activa. Es
inferior a Awaitility, y por eso el mismo escenario está cubierto además en
`EventoApiTest`, que sí espera de forma inteligente.

---

## 4. Regresión — Sprint 1 (95/95 aserciones + 14 E2E navegador)

Ninguna regresión pese a haber tocado `AuthService`, `GlobalExceptionHandler`,
`SecurityConfig` y el modelo `Persona`.

```
requests 17 · assertions 95 · failed 0 · 2.2s
```

Las 14 pruebas de navegador (`RegistroTest`, `LoginTest`) siguen verdes: el formulario
de registro real sigue funcionando contra el backend modificado.

---

## 5. Qué NO cubren estas pruebas

Se enumera explícitamente para que nadie confunda "suite verde" con "sistema probado".

| Área | Motivo |
|---|---|
| **Cloudinary real** | Corre en modo simulado. La rama de subida real, el análisis de contenido y los timeouts de red no se ejercitan |
| **Envío SMTP real** | `EmailService` cae a modo log. Se verifica que se **invoque**, no que el correo llegue |
| **Flujo completo de recuperación** | El token viaja por email; sin SMTP no se puede leer para consumirlo en una prueba automática. Se cubre la emisión y el consumo por separado (unitarias), no el ciclo entero |
| **Concurrencia** | Los UNIQUE de la base son la defensa real contra altas simultáneas, pero no hay prueba que lance peticiones en paralelo |
| **Carga y tiempos de respuesta** | El requisito no funcional (2s lecturas / 4s escrituras) no está medido bajo carga |
| **Aprobación de organizaciones** | Requiere el panel de administración (Módulo 6). Hoy se hace por SQL |
