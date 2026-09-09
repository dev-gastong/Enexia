# Sprint 2 — Backend completo: seguridad, organizaciones, eventos y catálogo público

**Fecha:** 2026-09-08
**Módulos:** 1 (Autenticación) · 2 (Eventos) · 4 (Interfaz Pública) · 5 (Moderación) · 7 (Organizaciones)
**Estado:** ✅ Backend operativo de extremo a extremo · ⏸️ Frontend diferido (a la espera de Figma)
**Suite:** 165 pruebas JUnit/Mockito/Playwright + 245 aserciones Postman · **0 fallos**

---

## 0. Encargo y orden de ejecución

El usuario pidió, en este orden:

1. Quitar `tipo_persona` de `Persona`.
2. Eliminar el bloqueo por IP y reemplazarlo por **bloqueo de cuenta silencioso** con aviso por email.
3. Registro de Persona Jurídica, verificado contra los DFD y los RF.
4. Módulo de organizador: creación de eventos, Cloudinary, moderación según DFD.
5. Página principal pública para visualizar.
6. Todo sin frontend.

Se hizo en ese orden y está entregado completo. Lo que quedó fuera se enumera en la sección 9.

---

## 1. Decisión: se elimina `tipo_persona`

### El problema que había

La columna suponía una jerarquía `Persona → (Física | Jurídica)` que el modelo **nunca tuvo**:

- El MER solo declara `Persona ||--|| Persona_Fisica`.
- `Persona_Juridica` no tiene FK a `Persona`.
- El DER ya modelaba `P → PF → U` y `PJ` como entidad separada.

En la práctica la columna valía `"FISICA"` en el 100% de las 32 filas existentes: no discriminaba nada. Y admitir `"JURIDICA"` habría abierto la puerta a crear una persona jurídica sin persona física asociada, rompiendo el invariante de que **todo `Usuario` es un ser humano**.

### El principio que la reemplaza

> Una Persona Jurídica **no es una identidad de acceso**: es un **contenedor administrativo**. No tiene usuario, no tiene contraseña, no se inscribe a eventos. Se vincula a personas humanas por `Miembros_Organizacion`.

Esto resuelve el bloqueante que Sprint 1 había dejado anotado como *"Inconsistencia de modelo — hay que definir esto antes de escribir código"*.

**Dato relevante:** el DER ya estaba bien (línea 154: `PJ ---|"0..1"| R_PJ_Ev -->|"N"| E`). El que estaba desactualizado era el MER. La implementación siguió el DER.

**Archivos:** [`Persona.java`](../../../enexia/src/main/java/com/enexia/rg/model/Persona.java), [`MER.md`](../../diseño_bd/MER.md), [`2026-09-08_sprint2.sql`](../../diseño_bd/migraciones/2026-09-08_sprint2.sql)

---

## 2. Decisión: bloqueo silencioso, sin rate limiting por IP

### 2.1 Por qué se retiró el control por IP

Razón del usuario, textual: *"Se puede bloquear una zona con muchos dispositivos"*.

Es correcto y es un problema grave. Detrás de un CGNAT o del wifi de una institución, cientos de dispositivos legítimos comparten una única IP pública. Bloquear esa IP dejaba sin servicio a toda una zona por culpa de un solo atacante — **una denegación de servicio que el propio atacante podía provocar a voluntad**, y sin costo: le bastaba con fallar el login 20 veces desde la red que quisiera dejar afuera.

Se eliminaron `RateLimitService` y `RateLimitExcedidoException`.

### 2.2 Qué lo reemplaza

La solución que pidió el usuario: **el login sigue respondiendo "credenciales inválidas" siempre, con el mismo código de error; internamente se opera con el código real, y al titular se le avisa por email con opción de recuperar la cuenta.**

Implementado en cuatro capas, porque uniformar el mensaje solo no alcanza:

| Capa | Qué garantiza | Cómo |
|---|---|---|
| **Tipo** | Las cinco ramas de rechazo comparten raíz | `AutenticacionFallidaException` con subclases |
| **Respuesta** | Mismo status, código, mensaje y cabeceras | **Un solo** `@ExceptionHandler` para toda la familia |
| **Tiempo** | Mismo costo en el reloj | BCrypt señuelo en **toda** rama de rechazo |
| **Canal alterno** | El titular sí se entera | Email con enlace de recuperación de un solo uso |

**La capa de tiempo era la más fácil de dejar abierta.** El rechazo por estado de cuenta ocurre *antes* de comparar la contraseña, así que sin el señuelo salía en ~1ms contra los ~250ms de un rechazo normal. Midiendo desde las DevTools, esa diferencia delata exactamente lo que el mensaje uniforme intenta ocultar. Hay tres pruebas dedicadas solo a esto.

También se quitó la cabecera `X-Reintentar-Despues`, que publicaba el momento exacto en que vencía el cooldown: era un oráculo perfecto, legible desde la consola del navegador.

**Verificado en la corrida real** (log del servidor, misma respuesta HTTP en los cinco casos):

```
WARN ... GlobalExceptionHandler : Fallo de autenticacion. Motivo interno: PASSWORD_INCORRECTA
WARN ... GlobalExceptionHandler : Fallo de autenticacion. Motivo interno: CUENTA_EN_COOLDOWN
WARN ... GlobalExceptionHandler : Fallo de autenticacion. Motivo interno: EMAIL_INEXISTENTE
```

```json
{"error":"CREDENCIALES_INVALIDAS","mensaje":"Email o contrasena incorrectos","status":401}
```

### 2.3 Riesgo abierto que hay que conocer

> **Sin control por IP, el *password spraying* (una contraseña común probada contra miles de emails distintos) ya no tiene freno propio**, porque ninguna cuenta llega a acumular fallos.
>
> Es la contracara honesta de la decisión, y conviene tenerla anotada. La mitigación natural para Sprint 3 **no** es volver a bloquear por IP, sino **alertar por volumen anómalo** en `historial_interacciones` sin rechazar peticiones: se detecta el ataque sin dejar a nadie afuera.

### 2.4 Recuperación de cuenta (RF-1.5, completado de paso)

El bloqueo silencioso obligaba a implementarlo: si el login ya no avisa, el email tiene que hacerlo, y el aviso sin enlace de recuperación dejaría al usuario sin salida.

- Token de **256 bits** aleatorios, Base64 URL-safe.
- En la base se guarda el **SHA-256**, no el token. Durante su vigencia el token equivale a la contraseña: guardarlo en claro haría que un dump o un backup filtrado entregue acceso inmediato a toda cuenta con recuperación pendiente.
- **Un solo uso**: se borra al consumirse. Emitir uno nuevo invalida el anterior.
- Vence a los 30 minutos (configurable).
- **Solo reactiva cuentas `BLOQUEADO`.** Nunca `SUSPENDIDO` ni `DE_BAJA`: si cambiar la contraseña levantara una suspensión, cualquier sancionado la esquivaría pidiendo un restablecimiento.

**Archivos:** [`AutenticacionFallidaException.java`](../../../enexia/src/main/java/com/enexia/rg/exception/AutenticacionFallidaException.java), [`AuthService.java`](../../../enexia/src/main/java/com/enexia/rg/service/AuthService.java), [`RecuperacionCuentaService.java`](../../../enexia/src/main/java/com/enexia/rg/service/RecuperacionCuentaService.java), [`EmailService.java`](../../../enexia/src/main/java/com/enexia/rg/service/EmailService.java)

---

## 3. Registro de Persona Jurídica (RF-7.2, RF-7.3)

### 3.1 Contradicción en la documentación, y cómo se resolvió

La documentación describe **dos flujos distintos** para lo mismo:

| Fuente | Qué dice |
|---|---|
| **RF-7.2** | "a través de un flujo separado (**no como parte del registro inicial**)" |
| **DFD 7.1/7.2** | Bifurcación Física/Jurídica **dentro del formulario de registro**, terminando en "Continuar a Credenciales de Usuario" |
| **CLAUDE.md** | Se contradice a sí mismo: dice "no es entidad de login" en una sección y "el usuario se registra con razón social..." en otra |

**Decisión: se implementaron los dos, sobre el mismo método de servicio.** No es duplicación — es un solo `PersonaJuridicaService.crearOrganizacion()` con dos puntos de entrada. Cambia de dónde sale el usuario, no la lógica, así que no puede haber dos reglas de negocio que diverjan.

```
POST /api/auth/registro/organizacion    → público, DFD 7.1/7.2 (cuenta + organización, una transacción)
POST /api/organizador/organizaciones    → autenticado, RF-7.2 literal
```

**Invariante respetado en ambos:** la cuenta que se crea es **siempre la de la persona humana**. No existe login "de empresa".

### 3.2 Validación de CUIT y un error del DFD

El DFD 7.3 tiene **dos nodos mal rotulados**, y ninguno se puede leer al pie de la letra:

| Nodo del DFD | Dice | Problema |
|---|---|---|
| 7.3.6A | "Resto = 11 → dígito 0" | Un resto módulo 11 nunca puede valer 11: rama muerta |
| 7.3.6B | "Resto = 0 → dígito 9" | **Falso.** Con resto 0 el verificador es 0 |

Implementarlo literal **rechazaría todo CUIT terminado en 0** (por ejemplo `30-71659554-0`, que es válido y real) y aceptaría inválidos terminados en 9.

Lo que el diagrama describe en realidad es la variable intermedia `11 - resto`, no el resto. Se implementó esa lectura, que es el algoritmo estándar, y **hay un test dedicado que fija el criterio** para que, si alguien "corrige" el código para que coincida con el rótulo, la suite falle y quede claro cuál de los dos está mal.

👉 **Acción pendiente: corregir el rótulo de los nodos 7.3.6A y 7.3.6B del DFD.**

### 3.3 Por qué nace en revisión

El CUIT se valida por aritmética, **no contra el padrón de ARCA**: un número puede ser matemáticamente válido y no pertenecer a quien lo carga. Dejar la organización operativa de inmediato permitiría publicar eventos a nombre de una empresa ajena.

Nace `REVISION_PENDIENTE` (sistema) + `INACTIVO` (propio). Los **dos** ejes deben habilitar para poder firmar un evento. La cuenta personal, en cambio, queda `ACTIVO` y usable desde el primer momento — la distinción importa: si el estado de revisión recayera sobre el usuario, el fundador no podría ni iniciar sesión.

**Archivos:** [`ValidadorCuit.java`](../../../enexia/src/main/java/com/enexia/rg/util/ValidadorCuit.java), [`PersonaJuridicaService.java`](../../../enexia/src/main/java/com/enexia/rg/service/PersonaJuridicaService.java), [`OrganizacionController.java`](../../../enexia/src/main/java/com/enexia/rg/controller/OrganizacionController.java)

---

## 4. Módulo 2 — Eventos y moderación asíncrona

### 4.1 Fase síncrona: el "skeleton"

RF-2.2 es taxativo: al crear **no se persiste contenido**. Solo `id`, `id_organizador`, `estado_sistema` y `fecha_creacion`.

Se agregaron dos campos que **no son contenido moderable sino atribución**, y por eso sí van en el skeleton:

- `id_persona_juridica` — bajo qué organización se publica (RF-2.1). Se valida antes: si el usuario no es miembro, no hay evento que crear.
- `id_estado_organizador = PUBLICADO` — la intención del organizador, necesaria para que el dashboard pueda filtrar (RF-2.8) desde el primer momento.

El título, la descripción, la ubicación, la agenda, los tickets y las imágenes **no tocan la base** hasta que la moderación apruebe. Es la regla arquitectónica del proyecto: **moderar antes de persistir contenido**. Hay un test (`noPersisteContenido`) que lo fija explícitamente.

Responde **202 Accepted**, no 201: el recurso quedó aceptado pero todavía no publicado. Un 201 le diría al cliente que ya está completo, y no lo está.

### 4.2 Fase asíncrona: pipeline de dos fases

```
Fase 1 — Texto (título + descripción)
   ├─ rechazado → RECHAZADO_SISTEMA / MODERACION_TEXTO
   │              las imágenes se DESCARTAN SIN PROCESAR   ← RF-2.2 lo pide explícito
   └─ aprobado  → Fase 2

Fase 2 — Imágenes (Cloudinary)
   ├─ ninguna aprobada → RECHAZADO_SISTEMA / MODERACION_IMAGEN
   │                     (o SIN_IMAGENES_VALIDAS si no había ninguna)
   └─ al menos una     → persiste TODO + APROBADO_SISTEMA + visible en catálogo
```

No subir las imágenes cuando el texto ya fue rechazado ahorra hasta 6MB de tráfico, cuota de Cloudinary y segundos de un hilo del pool en un resultado que no se va a usar.

El criterio de "alcanza con una imagen aprobada" es deliberadamente permisivo (RF-2.3): castigar el evento entero porque dos de tres imágenes fallaron penalizaría al organizador por un error menor y fácil de corregir.

### 4.3 `motivo_codigo`: se respetó el MER

El DFD 2.5C dice "guardar `motivo_codigo` de infracción → `Evento`", pero el MER lo pone en el catálogo `evento_estado_sistema`. **Se siguió el MER**: `RECHAZADO_SISTEMA` tiene una fila por motivo, y el evento apunta a la que corresponde. Así queda registrado el porqué del rechazo sin agregar columnas fuera del modelo.

```
7   RECHAZADO_SISTEMA   MODERACION_TEXTO
8   RECHAZADO_SISTEMA   MODERACION_IMAGEN
9   RECHAZADO_SISTEMA   SIN_IMAGENES_VALIDAS
10  RECHAZADO_SISTEMA   ERROR_PIPELINE
```

### 4.4 Cloudinary

- Validación local **antes** de tocar la red: extensión JPG/PNG y peso ≤ 2MB (RF-5.2). Un archivo que no pasa esto no se sube: sería gastar ancho de banda para que el servidor remoto lo rechace igual.
- **Modo simulado** sin credenciales: valida formato y peso, no llama a la red y aprueba por defecto. Permite desarrollar y correr toda la suite sin cuenta ni conexión.
- Gancho de prueba: un nombre de archivo que contenga `rechazar` se rechaza en modo simulado. Sin él, la rama "todas las imágenes rechazadas" quedaría sin probar en toda la suite — que es justo donde se esconden los errores.
- **Límite honesto:** Cloudinary no analiza contenido por sí solo; hace falta un complemento (`aws_rek_moderation`, etc.) habilitado en la cuenta. Si no está configurado, la imagen se sube **sin análisis** y se aprueba. Eso **no** pasa en silencio: se avisa por log al arrancar, porque una moderación que no modera y nadie sabe que no modera es peor que no tenerla.

**Archivos:** [`EventoService.java`](../../../enexia/src/main/java/com/enexia/rg/service/EventoService.java), [`ModeracionEventoService.java`](../../../enexia/src/main/java/com/enexia/rg/service/ModeracionEventoService.java), [`PublicacionEventoService.java`](../../../enexia/src/main/java/com/enexia/rg/service/PublicacionEventoService.java), [`CloudinaryService.java`](../../../enexia/src/main/java/com/enexia/rg/service/CloudinaryService.java)

---

## 5. Módulo 4 — Catálogo público

Único grupo de endpoints sin token, porque RF-4.1 pide un catálogo *"indexable y accesible de forma anónima"*: si exigiera autenticación, ningún buscador podría indexarlo y nadie vería un evento antes de registrarse — al revés de lo que necesita una plataforma de difusión.

- **Sesión opcional**: el JWT, si viene, se aprovecha para atribuir la visita (RF-4.5); si no viene, la consulta funciona igual.
- **Doble condición de visibilidad**: aprobado por el sistema **Y** publicado por su organizador. Comprobar una sola dejaría entrar eventos cancelados o aún en revisión. La regla vive en las consultas del repositorio, no en el service, así que **no se puede saltear pidiendo un id directo**.
- **404 uniforme** para "no existe" y para "existe pero no es público": distinguirlos permitiría descubrir qué eventos fueron rechazados por moderación probando ids.
- **Tres consultas por página, no tres por fila.** `@EntityGraph` resuelve las asociaciones *-a-uno; ciudad y próxima fecha se resuelven en bloque para toda la página. Con 20 tarjetas, la alternativa ingenua serían 41 consultas.
- **Techo de paginación (50).** Sin él, `?tamano=1000000` traería la tabla entera a memoria en el endpoint más expuesto del sistema.

**Archivos:** [`CatalogoPublicoService.java`](../../../enexia/src/main/java/com/enexia/rg/service/CatalogoPublicoService.java), [`CatalogoPublicoController.java`](../../../enexia/src/main/java/com/enexia/rg/controller/CatalogoPublicoController.java), [`EventoRepository.java`](../../../enexia/src/main/java/com/enexia/rg/repository/EventoRepository.java)

---

## 6. Dos errores encontrados y corregidos durante la implementación

Ambos aparecieron ejecutando el sistema de verdad, no leyendo el código. Quedan documentados porque son trampas clásicas de Spring y van a volver a aparecer.

### 6.1 Carrera entre el commit y el hilo asíncrono

**Síntoma observado:**

```
RecursoNoEncontradoException: El evento 1 ya no existe
→ el evento terminaba en RECHAZADO_SISTEMA / ERROR_PIPELINE
```

**Causa:** `EventoService.crear()` es `@Transactional` y llamaba directo al servicio asíncrono. La tarea arrancaba en otro hilo **mientras el INSERT seguía sin confirmar**; ese hilo abre su propia transacción, busca el evento por id y no lo encuentra, porque una fila sin confirmar es invisible fuera de su transacción.

Y no fallaba siempre: dependía de quién ganara la carrera. **La peor clase de error posible** — funciona en las pruebas y falla en producción, o al revés.

**Corrección:** se publica un evento de aplicación y el pipeline escucha con `@TransactionalEventListener(phase = AFTER_COMMIT)`. Spring garantiza que la tarea no arranca hasta que la transacción se confirmó. Efecto útil adicional: si algo fallara y la transacción se revirtiera, el evento **no se entrega**, así que no queda un pipeline trabajando sobre un evento que nunca existió.

### 6.2 `@Transactional` y auto-invocación

**Síntoma observado:**

```
UnexpectedRollbackException: Transaction silently rolled back
because it has been marked as rollback-only
→ la ficha técnica respondía 500 por culpa de una métrica
```

**Causa:** `registrarVisita()` estaba dentro de `CatalogoPublicoService` y se invocaba como `this.registrarVisita(...)` desde `verFicha()`, que es `@Transactional(readOnly = true)`. Al no cruzar el proxy de Spring, el `REQUIRES_NEW` **quedaba sin efecto** y el INSERT terminaba dentro de la transacción de solo lectura.

Lo peor: el `try/catch` que envolvía el guardado **no alcanzaba a atraparlo**, porque el fallo no ocurre en el `save()` sino al confirmar, ya fuera del método.

**Corrección:** se extrajo a `VisitaService`. Es exactamente la misma lección que ya estaba documentada en `IntentosLoginService` desde Sprint 1 — y aun así se volvió a cometer, lo cual dice bastante sobre lo fácil que es caer.

---

## 7. 🚨 Hallazgo grave: `ddl-auto=update` no puede agregar columnas

**Este es el punto más importante del sprint para el equipo.**

Hibernate genera `ALTER TABLE IF EXISTS <t> ADD COLUMN ...`. La MariaDB que trae XAMPP (**10.4.32**) **no soporta `IF EXISTS` en un `ALTER TABLE`**:

```
ERROR 1064 (42000): You have an error in your SQL syntax ...
near 'IF EXISTS evento ADD COLUMN ...'
```

Hibernate **registra el fallo pero no detiene el arranque**. La aplicación levanta con total normalidad y la columna simplemente no existe. El síntoma aparece mucho después, en tiempo de ejecución:

```
Unknown column 'e1_0.fecha_creacion' in 'field list'
Unknown column 'pj1_0.id_estado_persona_juridica' in 'field list'
```

### Venía roto desde Sprint 1

`persona_juridica` tenía **cuatro columnas declaradas en la entidad desde el 2026-07-26 que nunca existieron en la base**: `nombre_fantasia`, `fecha_registro`, `id_estado_persona_juridica`, `id_estado_persona_juridica_sistema`.

Nadie lo notó porque ninguna consulta tocaba esa tabla. La primera que lo hizo — el catálogo público, que la incluye para resolver la firma del organizador (RF-7.4) — falló de inmediato.

### Qué hay que hacer

1. **Ya:** aplicar la migración. Es obligatoria, no opcional.
   ```bash
   mysql -u root -p enexia < docs/diseño_bd/migraciones/2026-09-08_sprint2.sql
   ```
2. **De ahora en más:** toda columna que se agregue a una `@Entity` va también a un script de migración en `docs/diseño_bd/migraciones/`.
3. **Antes de producción:** actualizar MariaDB a **10.6+** (el mínimo que soporta Hibernate 7, confirmado por el aviso HHH000511) **o** pasar a Flyway/Liquibase con `ddl-auto=validate`. Es lo que corresponde de todos modos.

---

## 8. Cambios en el modelo de datos

| Tabla | Cambio | Motivo |
|---|---|---|
| `persona` | ➖ `tipo_persona` | No discriminaba nada y habilitaba un estado inválido (§1) |
| `persona_juridica` | ➕ UNIQUE en `cuit` | Defensa real contra duplicados bajo concurrencia |
| `persona_juridica` | 🔧 4 columnas que faltaban | Reparación del defecto de §7 |
| `evento` | ➕ `fecha_creacion` | RF-2.2 la exige literal en el skeleton |
| `evento` | ➕ `id_persona_juridica` (nullable, FK) | RF-2.1 Sprint 2 + RF-7.4. Ya estaba en el DER |
| `evento`, `visita` | ➕ índices de apoyo | Sostener el requisito no funcional de 2s en lecturas |

Catálogos sembrados por `DatosInicialesConfig` (**53 filas nuevas** en la primera corrida): roles, estados de usuario, estados de organización, 10 estados de evento (con sus motivos), 4 estados de organizador, 8 categorías, 5 tipos de ticket y geografía (Argentina → 5 provincias → 15 ciudades, con Tierra del Fuego completa).

---

## 9. Lo que quedó fuera, y por qué

| Tema | Estado | Motivo |
|---|---|---|
| **Frontend** | ⏸️ | Pedido explícito del usuario: se retoma con los diseños de Figma |
| **RF-2.7 Modificación de eventos** | ❌ | Dispara re-moderación (`EN_REVISION` + borrador). El estado ya existe en el catálogo; falta el flujo |
| **Devolución de pagos en la baja (RF-2.9)** | ❌ | Depende del Módulo 3 (inscripciones y pagos). Una devolución a medias es peor que ninguna |
| **Panel de administración (RF-6.1)** | ❌ | Módulo 6. Hoy una organización no se puede aprobar por API — hay que hacerlo por SQL |
| **Suscripciones reales (Módulo 8)** | ❌ | El paso 2.1 del DFD existe con un límite configurable (`enexia.eventos.limite-plan-gratuito=20`). Cuando llegue el M8, solo cambia de dónde sale el número |
| **CAPTCHA / 2FA** | ❌ | Fuera de alcance desde Sprint 1. El flag `requiere_captcha` sí se marca |
| **Moderación por API externa (Perspective/OpenAI)** | ❌ | Se usa el filtro local. Detecta términos de una lista, no intención ni contexto |
| **RF-1.4 vs escalera 3/6/9** | ⚠️ | Divergencia **sin resolver**: el requisito dice bloquear al 3° intento, el DFD y el código usan la escalera. Hay que alinear uno de los dos |
| **DFD 7.3 nodos 7.3.6A/B** | ⚠️ | Rótulos incorrectos (§3.2). Hay que corregir el diagrama |
| **Password spraying** | ⚠️ | Riesgo abierto y asumido tras retirar el control por IP (§2.3) |

---

## 9-bis. Auditoría `check-rules`

Ejecutada sobre los servicios y controllers nuevos, contra las cinco reglas del proyecto.

| # | Regla | Resultado |
|---|---|---|
| 1 | **Uso de DTOs** — no exponer entidades JPA | ⚠️ **1 hallazgo → corregido** |
| 2 | **Concurrencia** — `cupo_actual` con transacción | ✅ (con salvedad, ver abajo) |
| 3 | **Borrado lógico** — nada de `repository.delete()` | ✅ (con una excepción justificada) |
| 4 | **Seguridad RBAC** — `@PreAuthorize` en endpoints privados | ✅ |
| 5 | **Moderación antes de persistir** | ✅ |

### Hallazgo 1 — entidad JPA expuesta (corregido)

`GET /api/publico/categorias` devolvía `List<Categoria>`, es decir **la entidad JPA
directamente**. Es el único caso de todo el sprint y se corrigió en el acto.

Por qué importa, más allá de la regla: una entidad en la respuesta acopla el contrato
público de la API al esquema de la base — renombrar una columna rompe a los clientes —,
expone cualquier campo que se agregue después sin haberlo decidido, y puede disparar
carga perezosa en plena serialización.

Se reemplazó por el record `OpcionCatalogo(id, nombre)`, que ahora comparten los tres
selectores del panel de filtros (categorías, provincias y ciudades): los tres necesitan
exactamente lo mismo, y tres DTOs idénticos con nombres distintos serían ruido.

Verificado tras la corrección: `./gradlew test` (165/165) y Newman (150/150) siguen en verde.

### Regla 2 — concurrencia de cupos: salvedad

`cupo_actual` se **inicializa** en 0 dentro de una transacción, pero **todavía no se
muta**: quien lo mueve son las inscripciones, que son el Módulo 3 y no están
implementadas.

> Cuando llegue ese módulo, el decremento **tiene** que hacerse con bloqueo pesimista o
> con un `UPDATE ... SET cupo_actual = cupo_actual + 1 WHERE cupo_actual < cupo_maximo`
> atómico. Un leer-modificar-escribir sin protección permite vender más entradas que el
> cupo bajo concurrencia — exactamente el patrón que `IntentosLoginService` ya resuelve
> con `PESSIMISTIC_WRITE` para el contador de intentos fallidos, y que conviene copiar.

### Regla 3 — los dos `delete()` que sí corresponden

Los únicos borrados físicos del código están en `RecuperacionCuentaService`, sobre
`PasswordResetToken`:

- Al consumir el token (**un solo uso**: si el enlace queda en el historial del navegador
  o se reenvía, ya no sirve).
- Al emitir uno nuevo (invalida los anteriores).

No son una violación de la regla: un token de un solo uso **no es** una entidad de
negocio con historial. Conservarlo sería conservar una credencial vencida, que es lo
contrario de lo que se busca. Todo lo demás — usuarios, eventos, organizaciones — usa
estado o `fecha_baja`, nunca `delete()`.

### Regla 4 — RBAC

| Controller | `@PreAuthorize` | Nota |
|---|---:|---|
| `EventoOrganizadorController` | 5 | Todos los métodos |
| `OrganizacionController` | 3 | Todos los métodos |
| `AuthController` | 0 | Correcto: son endpoints públicos por definición |
| `CatalogoPublicoController` | 0 | Correcto: RF-4.1 exige acceso anónimo |

Las anotaciones son **redundantes** con el `hasRole("ORGANIZADOR")` que `SecurityConfig`
ya aplica al prefijo `/api/organizador/**`. La redundancia es deliberada: si mañana
alguien reorganiza las rutas y un endpoint deja de caer bajo ese prefijo, la anotación
lo sigue protegiendo. Defensa en profundidad sobre endpoints que crean entidades legales
y contenido público.

### Regla 5 — moderación antes de persistir

Verificado en los dos flujos, y **con pruebas que fijan el orden**, no solo el resultado:

- `PersonaJuridicaService`: `moderarNombres()` corre antes de la primera escritura. El
  test comprueba con `verify(..., never())` que ni `personaJuridicaRepository` ni
  `ubicacionRepository` recibieron nada.
- `ModeracionEventoService`: la Fase 1 (texto) corre antes que la Fase 2 (imágenes), y el
  contenido del evento no se persiste hasta que ambas aprueban. El test
  `noSubeImagenesSiElTextoFalla` usa `verifyNoInteractions(cloudinaryService)`.

---

## 10. Resultados de las pruebas

Detalle completo en [`2026-09-08_resultados_pruebas_sprint2.md`](./2026-09-08_resultados_pruebas_sprint2.md).

| Suite | Herramienta | Casos | ✅ | ❌ |
|---|---|---:|---:|---:|
| Unitarias | JUnit 5 + Mockito | 106 | 106 | 0 |
| API E2E | Playwright (APIRequestContext) | 44 | 44 | 0 |
| E2E navegador (regresión Sprint 1) | Playwright Chromium | 14 | 14 | 0 |
| Carga de contexto Spring | JUnit 5 | 1 | 1 | 0 |
| API — Sprint 2 | Postman / Newman | 28 peticiones · **150 aserciones** | 150 | 0 |
| API — Sprint 1 (regresión) | Postman / Newman | 17 peticiones · **95 aserciones** | 95 | 0 |
| **TOTAL** | | **165 tests + 245 aserciones** | **todos** | **0** |

---

## 11. Cómo correr todo

Requiere MariaDB (XAMPP) con la base `enexia` creada **y la migración aplicada**.

```bash
# 1. Migración (obligatoria, ver §7)
mysql -u root -p enexia < docs/diseño_bd/migraciones/2026-09-08_sprint2.sql

# 2. Suite completa de JUnit + Playwright (levanta Spring sola en el 8080)
cd enexia && ./gradlew test
```

> El puerto 8080 tiene que estar **libre**: si hay un `bootRun` abierto, cerrarlo antes.

```bash
# 3. Suites de API, con el backend ya levantado (./gradlew bootRun)
cd enexia/pruebas/postman
npx newman run Enexia-Sprint2.postman_collection.json -e Enexia-Local.postman_environment.json
npx newman run Enexia-Registro.postman_collection.json -e Enexia-Local.postman_environment.json
```

> Newman se ejecuta **desde esa carpeta**: la creación de evento sube `fixtures/portada.png` por ruta relativa.

---

## 12. Archivos

**Nuevos (49)** — servicios (`PersonaJuridicaService`, `EventoService`, `ModeracionEventoService`, `PublicacionEventoService`, `CatalogoPublicoService`, `CloudinaryService`, `EmailService`, `RecuperacionCuentaService`, `VisitaService`, `EventoMapper`), 3 controllers, 13 DTOs, 4 enums, `ValidadorCuit`, `AsyncConfig`, `EventoCreadoEvent`, 3 excepciones, 7 clases de test, colección Postman y migración SQL.

**Modificados (40)** — `Persona`, `Evento`, `PersonaJuridica`, `AuthService`, `AuditoriaService`, `GlobalExceptionHandler`, `SecurityConfig`, `DatosInicialesConfig`, `AuthController`, 16 repositorios, `application.properties`, `build.gradle`, `MER.md`, `CLAUDE.md`, `CLAUDE_es-ES.md`.

**Eliminados (2)** — `RateLimitService.java`, `RateLimitExcedidoException.java`.

---

## 13. Próximos pasos sugeridos

1. **Aplicar la migración** en cualquier entorno donde ya exista la base (§7).
2. **Decidir** la divergencia RF-1.4 ↔ escalera 3/6/9 y actualizar el documento que corresponda.
3. **Corregir** los rótulos de los nodos 7.3.6A/B del DFD 7.3.
4. Frontend de Sprint 2 cuando lleguen los diseños de Figma.
5. Módulo 6 (panel de administración): sin él, aprobar una organización requiere SQL manual.
6. RF-2.7 (modificación con re-moderación) — el estado `EN_REVISION` ya está sembrado.
