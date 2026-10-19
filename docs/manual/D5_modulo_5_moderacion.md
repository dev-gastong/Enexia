# D5 — Módulo 5: Moderación de contenido

> **Parte D · Los módulos.** Enexia publica **lo que escriben y suben las personas**. Este módulo es la **barrera automática**: revisa **textos** (nombres, descripciones, comentarios) y **imágenes** antes de que lleguen al público. Es un módulo **transversal**: no tiene pantallas propias; lo usan el registro (D1), la creación y edición de eventos (D2), las valoraciones (D3) y el panel de administración (D6).

**Requisitos que cubre** (`requisitos_funcionales/modulo_5.md`)

| RF | Resumen |
|---|---|
| RF-5.1 | Moderación del **texto** (lenguaje ofensivo). |
| RF-5.2 / 5.3 | Moderación de **imágenes**: formato, peso y contenido sensible. |
| RF-5.4 | Re-moderación al **editar** un evento. |

DFD: [`modulo_5_moderacion/`](../diagrams/modulo_5_moderacion/nivel1_moderacion.md).

---

## 1. Principio rector: **moderar ANTES de persistir**

Es "la regla arquitectónica del proyecto" (la repiten los javadocs de `EventoService`, `EventoCrearRequest`, `PublicacionEventoService` y la suite de tests):

> Si un título o una imagen **inapropiados** se guardan "primero y se revisan después", **ya están en la base**: pueden filtrarse por un listado, un backup, un panel mal filtrado o una URL directa.

Consecuencias de diseño en todo el sistema:

| Dónde | Qué se hace |
|---|---|
| **Registro** | `moderarCamposDePerfil` corre **antes** del primer `INSERT`: un nickname ofensivo nunca llega a la base. |
| **Crear evento** | Se guarda un **esqueleto** sin contenido; el contenido entra **solo tras aprobarse** (D2 §1.3). |
| **Evento rechazado** | **No se persiste nada** del contenido rechazado: queda el esqueleto con su estado y **motivo**, que es lo que el panel de administración necesita (`PublicacionEventoService.rechazar`). |
| **Imagen rechazada** | Se **borra de Cloudinary** para que no quede accesible por URL directa. |
| **Valoración** | El comentario se modera **síncronamente** antes del `INSERT` (D3 §6.2). |
| **Registro del rechazo** | Se audita el **campo**, **no el valor**: *"guardar en el log el texto ofensivo lo replica dentro del sistema sin ninguna utilidad"*. |

---

## 2. Moderación de texto (RF-5.1): `ModeracionTextoService`

### 2.1 Por qué no es "better-profanity"

El `CLAUDE.md` mencionaba la librería `better-profanity`. **Es un paquete de Python y no tiene equivalente para Java**; el propio código lo aclara y **implementa el filtro localmente**, cumpliendo los criterios pedidos: *gratuito, liviano y offline* (sin red: no agrega latencia ni un tercero del que depender). **Reemplazarlo por una API externa (Perspective, OpenAI) es cambiar la implementación de esa clase; ningún otro archivo cambia** (principio de abstracción: A02 §2).

### 2.2 Cómo evita las evasiones más comunes

Comparar el texto tal cual llega es inútil: `PUTO`, `puto` y `pu7o` pasarían todos. El servicio **normaliza** antes de comparar:

```
"P.U.T.0"  →  minúsculas   →  "p.u.t.0"
           →  sin acentos  →  "p.u.t.0"         (Normalizer NFD + regex de marcas diacríticas)
           →  sin l33t     →  "p.u.t.o"         (0→o, 1→i, 3→e, 4→a, 5→s, 7→t, @→a, $→s, !→i, |→i)
           →  solo letras  →  "puto"   ⇒ coincide
```

Se **normaliza también la lista**: *"si el archivo y el texto de entrada no pasan por el mismo proceso, nunca coincidirían"*.

### 2.3 Dos pasadas

1. **Palabras completas:** `texto.toLowerCase().split("[^\\p{L}\\p{N}]+")` separa por todo lo que no sea letra o dígito; cada palabra normalizada se busca en el `Set`. *"Sos un boludo"* se detecta **sin marcar "boludismo"**.
2. **Cadena compacta** (solo si el texto tiene **≤ 40 caracteres**): cubre nicknames sin separadores (`xXpu7oXx`). Es la parte que puede dar **falsos positivos** (el *problema Scunthorpe*: un apellido legítimo que contiene un término de la lista), por eso se aplica **después** y **solo a textos cortos**.

### 2.4 Detalles de implementación que enseñan

| Detalle | Concepto |
|---|---|
| Los términos viven en `resources/moderacion/terminos-bloqueados.txt` (un término por línea, `#` = comentario). **Ampliar la lista no requiere tocar código ni recompilar**. | Configuración como dato. |
| Se cargan **una vez** al arrancar con `@PostConstruct` a un `Set<String>`. | B01 §3, A04 §1.5.1 |
| Es un **`HashSet`**, no una `List`: la búsqueda pasa de O(n) a **O(1)**, y corre en cada registro. | A03 §1.2 |
| `try-with-resources` para leer el archivo; si falla, **la app arranca igual** pero con `log.error` visible: *"fallar el arranque sería peor que arrancar sin filtro, pero dejarlo pasar en silencio significaría creer que se está moderando cuando no"*. | A04 §1.5 |
| **Dos `char[]` alineados** (`LEET_ORIGEN` y `LEET_DESTINO`) con un comentario que exige mantenerlos alineados. | A01 §8.5 |
| `contieneLenguajeOfensivo` (devuelve `boolean`) y `validar` (lanza `ContenidoInapropiadoException`). | Dos estilos de API para dos usos. |

### 2.5 Limitación honesta

*"La lista es intencionalmente breve y conservadora. Su objetivo es demostrar el mecanismo de moderación, no ser exhaustiva."* El filtro detecta **términos**, no **intención ni contexto**: el discurso de odio escrito con vocabulario neutro se le escapa. Esa es la razón de que exista la **segunda instancia humana** (D6).

---

## 3. Moderación de imágenes (RF-5.2 / 5.3): `CloudinaryService`

### 3.1 Qué valida

1. **Formato** (`formatoPermitido`: extensión `.jpg`, `.jpeg` o `.png`) y **peso** (`pesoPermitido`: `enexia.multimedia.max-bytes`, 2 MB) **antes de subir**. La extensión **no es prueba de nada** (renombrar un `.exe` a `.jpg` es trivial): el código lo reconoce y la trata como *"un filtro barato de primera línea, no la garantía final"*, porque Cloudinary **rechaza lo que no sea una imagen** al procesarla.
2. **Subida a Cloudinary** y, si está configurado, **un complemento de moderación** (`enexia.cloudinary.moderacion`).
3. Interpreta el **veredicto** (`approved` / `rejected` / `pending`).

### 3.2 Decisiones de seguridad y diseño

| Decisión | Razón (del código) |
|---|---|
| `"overwrite", false` y **Cloudinary elige el `public_id`**. | *"Dejar que lo elija el cliente permitiría sobrescribir la imagen de otro evento mandando un `public_id` ajeno."* |
| Una imagen **rechazada se borra** (`borrarSilencioso`). | No dejar contenido inapropiado accesible por URL directa. |
| **`pending` = aprobada provisional.** | *"Bloquear el evento esperando un veredicto que puede tardar minutos anularía el sentido del pipeline."* El repaso de pendientes corresponde al panel de moderación manual. |
| **`catch (Exception)`** al subir → `ResultadoImagen.rechazada("error técnico")`. | El SDK envuelve fallos de red y de parseo en tipos propios sin raíz común (A04 §1.3). **Nunca lanza.** |
| **`record ResultadoImagen`** con fábricas `aprobada(...)` / `rechazada(...)`. | A02 §8: no se devuelve un `boolean` porque *"un rechazo sin motivo no se puede explicar"*. |

### 3.3 Degradación controlada: modo simulado

Sin `CLOUDINARY_CLOUD_NAME` el servicio **no sube nada, no sale a la red y aprueba**, **salvo** que el nombre del archivo contenga `rechazar`: es el **gancho de prueba** que permite ejercitar la rama de rechazo **sin credenciales** (*"sin esto la rama 'todas las imágenes rechazadas → RECHAZADO_SISTEMA' quedaría sin probar en toda la suite, que es justo la rama donde se esconden los errores"*). Es el mismo patrón que `BrevoClient` y la pasarela de pago simulada (B01 §5.3).

---

## 4. El pipeline completo (RF-2.2 + RF-5.x): `ModeracionEventoService`

Ya lo recorrimos en D2 §2.4 y B05 §4. Acá lo vemos **como moderación**:

```
procesar(idEvento, datos, imagenes, emailOrganizador)         (corre en el pool "ejecutorEnexia")
  try {
    FASE 1 · TEXTO ── título o descripción ofensivos ──► rechazar(MODERACION_TEXTO)   ← sin subir NINGUNA imagen
    FASE 2 · IMÁGENES ── por cada una: subirYModerar ──► aprobadas[] / rechazadas++
    VEREDICTO ── aprobadas.isEmpty() ?
                   rechazadas > 0 → rechazar(MODERACION_IMAGEN)
                   si no          → rechazar(SIN_IMAGENES_VALIDAS)
                 ≥ 1 aprobada     → aprobarYPersistir()   → APROBADO_SISTEMA   + correo "ya está publicado"
  } catch (RuntimeException) {
    intentarRechazoPorError() → RECHAZADO_SISTEMA / ERROR_PIPELINE + correo "revisará un administrador"
  }
```

Las **cuatro razones de rechazo** (`MotivoModeracionEvento`) son filas del catálogo `evento_estado_sistema` (**una fila por motivo**): `MODERACION_TEXTO`, `MODERACION_IMAGEN`, `SIN_IMAGENES_VALIDAS`, `ERROR_PIPELINE`. El **orden de las fases** es una decisión de costo: lo barato y local primero; lo caro y de red después.

### 4.1 Versión síncrona para editar (RF-5.4)

`moderarSincrono(datos, imagenes)` repite las dos fases **sin tocar la base**: si rechaza, **lanza `ReglaNegocioException`** y el evento vigente **ni se entera** (D2 §4). Devuelve las URLs aprobadas para `reemplazarContenido`.

### 4.2 Por qué las escrituras están en otro bean

`PublicacionEventoService` (con `REQUIRES_NEW`) hace las escrituras finales para que **mientras se habla con Cloudinary no haya ninguna transacción abierta** (D2 §2.4). Un orquestador `@Async` **sin** `@Transactional` y escritores transaccionales **aparte** es el patrón para cualquier proceso largo con llamadas externas.

---

## 5. Segunda instancia: el administrador (vista previa)

La moderación automática **se equivoca** (falsos positivos y negativos). Por eso existe la **cola de moderación** del administrador (RF-6.1, D6): puede **aprobar** un `RECHAZADO_SISTEMA` (→ `APROBADO_MANUAL`) o **suspender** un evento ya publicado (→ `RECHAZADO_MANUAL`, con **motivo y observación**), y el organizador recibe un correo en cada caso (D9). Los estados `*_MANUAL` registran que **hubo un humano**.

---

## 6. Pruebas

| Clase | Qué protege |
|---|---|
| `ModeracionEventoServiceTest` | Fase de texto (no sube imágenes), veredicto permisivo (≥ 1 imagen), cada motivo de rechazo, **`ERROR_PIPELINE` como red de contención**, `moderarSincrono`. |
| `ValoracionServiceTest` | Comentario ofensivo → 422 con auditoría **aparte**. |
| `AuthServiceRegistroTest` | Nickname/nombre ofensivo → rechazo **antes de persistir** y auditoría del rechazo. |
| API (`EventoApiTest`) | **Awaitility** esperando el veredicto asíncrono; una imagen con `rechazar` en el nombre fuerza el rechazo sin credenciales. |

---

## 7. Límites y pendientes

1. **El filtro de texto es por lista**: no entiende contexto, ironía ni vocabulario neutro (§2.5). Un servicio externo mejoraría la detección a costa de latencia y dependencia.
2. **Moderación solo de nombre y descripción**: la **dirección**, el nombre de los **tickets** y la **observación** no pasan por el filtro.
3. **Sin reintento** del pipeline: si el proceso se reinicia con tareas pendientes, esos eventos quedan `EN_PROCESO` (cola en memoria: B05).
4. **Cloudinary sin complemento de moderación** (`enexia.cloudinary.moderacion` vacío) **sube y aprueba** todo: el log de arranque lo advierte (*"Cloudinary activo pero SIN complemento de moderación"*); configurarlo es requisito de producción.
5. Los `pending` quedan aprobados provisoriamente hasta que alguien los revise.

---

## 8. Conceptos aplicados

| Concepto | Dónde |
|---|---|
| Moderar antes de persistir (invariante de arquitectura) | §1, D2 |
| `Normalizer`, regex Unicode, `Set` para búsqueda O(1) | A04 §2.4, A03 §1 |
| `@PostConstruct`, recursos en el classpath, `try-with-resources` | B01, A04 |
| `record` con fábricas estáticas como resultado | A02 §8 |
| Modo simulado / degradación controlada | B01 §5.3 |
| Orquestador `@Async` + escritores transaccionales aparte | B05, D2 §2.4 |
| Contención de fallos | B05 §8 |

---

## 9. Para practicar

1. Agregá un término al archivo de la lista y comprobá, **sin recompilar**, que un registro con ese nickname se rechaza. ¿Qué cambios hacen falta si querés que `"x.p.u.t.o"` también se detecte? (Pista: ya se detecta; explicá por qué.)
2. Pensá un **falso positivo** (una palabra legítima que contenga un término bloqueado) y decidí qué pasada del algoritmo lo causaría y cómo se mitigaría.
3. Un evento con 3 imágenes: 1 aprobada, 2 rechazadas. ¿Qué estado termina? ¿Se avisa de las rechazadas? (Mirá el log del pipeline.)
4. ¿Por qué `moderarSincrono` no necesita `@Transactional`? ¿Qué garantiza que "si rechaza, el evento vigente no cambia"?
5. Diseñá la **interfaz** `ModeradorDeTexto` con un método y dos implementaciones (la local y una externa). ¿Qué cambia en `ModeracionEventoService`? (Respuesta esperada: nada.)

**Siguiente capítulo:** [D6 — Módulo 6: Panel de administración](./D6_modulo_6_administracion.md)
