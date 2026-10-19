# D9 — Subsistema N: Notificaciones por correo

> **Parte D · Los módulos (cierre).** Cada módulo anterior **emite avisos** (un bloqueo, una suspensión, una inscripción, un recordatorio...). El subsistema **N** es la **única salida** hacia el correo: compone cada mensaje, lo despacha por una API externa y garantiza que **nunca tumbe** a quien lo originó ni se mande **dos veces**. Es el mejor ejemplo del proyecto de **integración con un tercero** bien hecha. (El prefijo **N** es porque *"Módulo 7"* del requisito es *Organizaciones*.)

DFD: [`diagrams/notificaciones/`](../diagrams/notificaciones/nivel1_notificaciones.md) — Nivel 1 y N.1 a N.5. Construido el 2026-10-16/17.

---

## 1. Arquitectura en una vista

```
 Orígenes (con su transacción)                      Despachador                 Proveedor
┌─────────────────────────────┐   emailService.enviarX(...)    ┌────────────────────────┐   ┌─────────┐
│ AuthService / Recuperación  │──────────────────────────────►│ EmailService           │   │         │
│ AdminUsuario / AdminEvento  │   (dentro de su transacción)  │  · compone el texto    │   │  Brevo  │
│ ModeracionEventoService     │                                │  · programar(): afterCommit │ │  API    │
│ InscripcionService          │                                │  · pool "ejecutorCorreo"│──►│ HTTPS   │
│ MiembrosOrganizacionService │                                │  · NotificacionService │   │ /v3/smtp│
│ Finalización / Recordatorio │                                │    (libro: idempotencia)│   │ /email  │
└─────────────────────────────┘                                └──────────┬─────────────┘   └────▲────┘
                                                                           └────── BrevoClient ────┘
```

| Clase | Responsabilidad única |
|---|---|
| **`EmailService`** | **Compone** cada aviso (asunto, texto, enlace) y lo **despacha** (cuándo y cómo). 12 métodos públicos, uno por tipo de aviso. |
| **`BrevoClient`** | Es **la única salida** al proveedor (`POST https://api.brevo.com/v3/smtp/email`). Nunca lanza: devuelve `true`/`false`. |
| **`NotificacionService`** | El **libro de correos**: reserva el derecho a mandar, registra el resultado. |
| **`CorreoHtml`** (`util/`) | Arma el HTML y **escapa** todo dato de usuario. |
| **`RecordatorioEventoScheduler/Service`** | El reloj del recordatorio. |
| **`AvisoInscriptosService`** | "Quiénes son los inscriptos de un evento": usado por baja y finalización. |

Cada una es **una razón para cambiar** (SRP, A02 §13): si cambia el proveedor, se toca `BrevoClient`; si cambia un texto, `EmailService`; si cambia la regla de repetición, `NotificacionService`.

---

## 2. El catálogo (12 tipos)

| Código | Destinatario | Origen | Despacho |
|---|---|---|---|
| `RECUPERACION` | Titular | Pide restablecer la contraseña | `despachar` + límite por destinatario |
| `BLOQUEO` | Titular | 3.er intento fallido | `despachar` |
| `PASSWORD_CAMBIADA` | Titular | Al consumir el token | `despachar` |
| `CUENTA_SUSPENDIDA` | Titular | Un administrador suspende | `despachar` |
| `MODERACION_RESULTADO` | Organizador | Pipeline automático / aprobar un rechazo | `despachar` |
| `EVENTO_SUSPENDIDO` | Organizador | Administrador (motivo + observación) | `despachar` |
| `EVENTO_REACTIVADO` | Organizador | Administrador revierte una suspensión | `despachar` |
| `INSCRIPCION_CONFIRMADA` | Participante | Se confirma la inscripción | **`despacharUnico`** |
| `EVENTO_CANCELADO` | Inscriptos | Baja del evento | **`despacharMasivo`** (con libro) |
| `RECORDATORIO_10MIN` | Inscriptos | **Reloj** (cada minuto) | **`despacharUnico`** |
| `INVITACION_VALORAR` | Inscriptos | Evento → `FINALIZADO` | **`despacharMasivo`** (con libro) |
| `AGREGADO_A_EQUIPO` | Miembro agregado | "Mi Equipo" | `despachar` |

El `enum NotificacionTipo` se persiste como texto en `notificacion_enviada.tipo`; su javadoc advierte: *"renombrar un código equivale a perder el historial de idempotencia del anterior: se agregan valores nuevos y no se renombran los existentes"* (principio abierto/cerrado: A02 §13).

---

## 3. Los tres principios de `EmailService` (los explica su javadoc)

### 3.1 Siempre **después del commit**

Un correo no puede salir por una operación que luego se revierte (*"tu evento fue suspendido"* sobre una suspensión que no ocurrió). `programar(Runnable)`:

```java
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { encolar(contenida); }
    });
} else {
    encolar(contenida);        // sin transacción: se encola ya
}
```

Detalle en B05 §5. Para los servicios que llaman a `emailService.enviarX(...)` **dentro** de su transacción esto es **transparente**: el envío real queda diferido solo.

### 3.2 Siempre **asíncrono**, en un pool propio

Si fuera síncrono, **el intento de login que dispara el bloqueo tardaría más** que los anteriores, y *esa diferencia de tiempo sería el oráculo* que la respuesta uniforme intenta cerrar (B04 §5.2). El pool `ejecutorCorreo` (2–3 hilos, cola de 200) está **separado del de moderación** para que una ráfaga de pedidos públicos de recuperación no deje sin moderar los eventos (B05 §2.3). Cola llena → se **descarta con un log** (*"mejor perder un correo que bloquear el hilo de una petición o tirar un 500"*).

### 3.3 **Nunca propaga** un fallo

Toda tarea va envuelta en `try/catch (RuntimeException)` con `log.error`. Que el proveedor esté caído **no puede** tumbar el login, una inscripción ni la creación de un evento.

---

## 4. Idempotencia: `NotificacionService` y el libro

Se explicó en B05 §6; el resumen operativo:

- `despachar`: aviso de **una acción puntual de una persona** → **sin libro** (no hay un reloj que lo repita).
- `despacharUnico`: aviso que **un reintento o un reloj podrían repetir** → **reserva** `(tipo, usuario, referencia)` en `notificacion_enviada` (UNIQUE) **antes** de mandar; el segundo intento se descarta. Registra después `ENVIADO` / `FALLIDO`.
- `despacharMasivo`: lo mismo para **muchos destinatarios**, **de a uno** y con una **pausa** entre correos (`enexia.email.pausa-masivo-ms = 200`) para *cuidar la cuota diaria del proveedor y evitar ráfagas*.
- `reservarConLimite`: el **límite por destinatario** de la recuperación (3 en 24 h, separados 2 min), contando filas recientes — **nunca por IP**.

```java
// El hilo que envía masivo respeta la interrupción (buena práctica en cualquier sleep)
private boolean pausar() {
    if (pausaMasivoMs <= 0) return true;
    try { Thread.sleep(pausaMasivoMs); return true; }
    catch (InterruptedException ex) {
        Thread.currentThread().interrupt();     // restaurar la marca de interrupción
        return false;                           // y cortar el envío
    }
}
```

`Thread.sleep` lanza `InterruptedException` cuando alguien le pide al hilo que se detenga (por ejemplo, al **apagar la aplicación**). Capturarla y **no** volver a marcar la interrupción (`Thread.currentThread().interrupt()`) es un error clásico: el apagado ordenado dejaría de funcionar.

---

## 5. `BrevoClient`: integrar con un tercero con criterio

La API de Brevo se llama por **HTTPS** (`POST /v3/smtp/email`) y **no por SMTP**: *los puertos SMTP están bloqueados en hosting gratuito como Render*. Se usa `RestClient` de Spring (sin el starter de JavaMail: `build.gradle` lo explica).

```java
private static RestClient construirCliente() {
    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXION).build());
    fabrica.setReadTimeout(TIMEOUT_LECTURA);
    return RestClient.builder().baseUrl(URL_BASE).requestFactory(fabrica).build();
}
```

| Práctica | Detalle |
|---|---|
| **Timeouts explícitos** (5 s conexión, 10 s lectura) | Sin ellos, un proveedor lento deja **colgados** los hilos del pool (B05 §9). |
| **Sin clave = modo simulado** | `estaConfigurado()` es `false` si no hay `BREVO_API_KEY`: *escribe el correo (con el enlace) en el log y no sale a la red*. En desarrollo es la única forma de completar un flujo de recuperación sin una cuenta. |
| **Nunca lanza** | `enviar(...)` devuelve `boolean`: `RestClientResponseException` → `false` + **solo el código HTTP** al log; otra `RuntimeException` → `false` + **solo el tipo**. |
| **No loguear el cuerpo de un correo real** | Algunos llevan un **token de recuperación**: en un log sería una credencial en claro. Del error del proveedor tampoco se registra el cuerpo (*puede eco-ar el contenido de la petición*). |
| **Remitente verificado** | `EMAIL_REMITENTE` debe ser un remitente verificado en Brevo, o el proveedor rechaza. Si falta, `enviar` registra el error y devuelve `false`. |
| **Constructor para pruebas** | Un segundo constructor *package-private* recibe un `RestClient` atado a un servidor simulado (`BrevoClientTest`). |
| **`@Autowired` en el constructor público** | Con dos constructores, Spring necesita saber **cuál** usar. |

---

## 6. Texto y HTML seguros: `CorreoHtml`

El proveedor exige `htmlContent`. Los textos incluyen **datos que escribe un usuario** (el nombre de un evento, el motivo de una suspensión): sin escapar, un nombre como `<a href="http://malo">Entrada gratis</a>` llegaría a la casilla de **cientos de inscriptos como un enlace clickeable dentro de un correo legítimo de Enexia**.

- `escapar(valor)`: `& < > " '` → entidades (un `switch` con flechas sobre `char`: A01 §8.3).
- **Las URLs del texto nunca se convierten en enlaces**: el **único** enlace del HTML es el que arma el sistema (`enlace`), jamás uno dentro de texto de un usuario.
- `aHtml`: párrafos por línea en blanco (`split("\\R\\s*\\R")`), `<br>` por saltos simples, y un botón con el enlace del sistema + la dirección en texto (*por si el botón no funciona*).
- `sanearAsunto`: quita saltos y tabulaciones y **recorta a 150 caracteres** (evita la **inyección de cabeceras** y asuntos kilométricos).
- Los textos van **sin tildes ni ñ** *"para no depender de la codificación de los archivos fuente"*.
- **El enlace base viene de configuración** (`enexia.email.url-base-frontend`), **no del header `Host`** de la petición: un *Host header poisoning* podría apuntar el enlace de recuperación al dominio de un atacante.

---

## 7. El recordatorio: un reloj robusto

(B05 §7, y el DFD N.3.) Lo específico:

- **Ventana, no instante:** `ahora < inicio <= ahora + 10 min`. Un tick perdido se recupera en el siguiente.
- **Por función (cronograma)**, no por evento: cada fecha avisa antes de empezar.
- **Solo lo vigente:** inscripciones `CONFIRMADAS` de eventos `PUBLICADOS` y aprobados; si el evento se suspende en la ventana, el aviso **ya no sale** (la consulta lo excluye).
- **El reloj es un parámetro** (`avisarProximos(ahora)`): se prueba la ventana **sin esperar** (B06). El scheduler le pasa `LocalDateTime.now(zona)` con la zona de Ushuaia.
- La ventana puede **cruzar la medianoche**: se piden **ambas fechas** a la consulta.
- Para **probar a mano**: `enexia.notificaciones.recordatorio.segundos-antes=30` e `intervalo-ms=5000` (**el intervalo debe ser menor que el adelanto**).

---

## 8. Quién llama a quién (mapa de orígenes)

| Aviso | Lo dispara |
|---|---|
| `BLOQUEO` | `RecuperacionCuentaService.notificarBloqueoConEnlaceDeRecuperacion` (desde `AuthService.procesarIntentoFallido`) |
| `RECUPERACION`, `PASSWORD_CAMBIADA` | `RecuperacionCuentaService` |
| `CUENTA_SUSPENDIDA` | `AdminUsuarioService.cambiarEstado` (solo al suspender) |
| `MODERACION_RESULTADO` | `ModeracionEventoService` (aprobado / rechazado / error) y `AdminEventoService` (aprobar un rechazo del sistema) |
| `EVENTO_SUSPENDIDO` / `EVENTO_REACTIVADO` | `AdminEventoService.avisarAlOrganizador` |
| `INSCRIPCION_CONFIRMADA` | `InscripcionService.inscribir` |
| `EVENTO_CANCELADO` | `EventoService.darDeBaja` → `AvisoInscriptosService` |
| `INVITACION_VALORAR` | **Los dos caminos de `FINALIZADO`** → `AvisoInscriptosService` (el libro evita el doble envío) |
| `RECORDATORIO_10MIN` | `RecordatorioEventoScheduler` → `RecordatorioEventoService` |
| `AGREGADO_A_EQUIPO` | `MiembrosOrganizacionService.agregar` |

(El 2026-10-19 el texto de `BLOQUEO` cambió: ahora explica que **solo un administrador desbloquea** y que **cambiar la contraseña no desbloquea**.)

---

## 9. Prueba real: `CorreosTest`

Manda correos **de verdad** a la bandeja del equipo (B06 §8): login, recuperación y bloqueo con aviso. Verifica con un **spy** sobre `BrevoClient` (`@MockitoSpyBean`) que Brevo **aceptó** cada correo; "aceptó" significa respuesta 2xx, **no** que haya llegado a la bandeja (revisar spam si el remitente es nuevo). Requiere `BREVO_API_KEY` y `EMAIL_REMITENTE` **en el entorno de quien lanza Gradle** y **falla a propósito** si no están (*"no tiene sentido que 'pase' sin haber mandado nada"*). Usa la cuenta `gaston_test` con el alias `+gaston_test` del mismo Gmail.

Otras pruebas: `EmailServiceTest` (textos, escape de HTML de usuario, idempotencia, que **nunca propaga**), `BrevoClientTest` (contra un servidor simulado: 2xx, 4xx, timeout, modo simulado), `RecordatorioEventoServiceTest` (la ventana, la medianoche, filtros), `CorreoHtmlTest` (escape y saneo).

---

## 10. Límites y sugerencias

1. **No hay aviso de reactivación de cuenta** ni de **suscripción** (S7, C1 en `SUGERENCIAS_PENDIENTES.md`).
2. **El libro `notificacion_enviada` crece** sin límite: conviene purgar filas viejas (por fecha) con un job.
3. **Sin reintentos**: un correo `FALLIDO` no se reintenta solo (un job podría reintentar los `FALLIDO` recientes).
4. **No hay plantillas** ni internacionalización: los textos están en Java. Para más de un par de avisos conviene archivos de plantilla.
5. **No hay preferencias de notificación** del usuario (darse de baja de recordatorios).
6. **Cuota del proveedor** (plan gratuito): el envío masivo con pausa la cuida, pero un evento con miles de inscriptos agotaría el día.
7. **Pool y cola en memoria**: un reinicio pierde los correos encolados (los *reservados* quedan `PENDIENTE` en el libro).

---

## 11. Conceptos aplicados

| Concepto | Dónde |
|---|---|
| Después del commit, asíncrono, que nunca propaga | B05 §5, §8 |
| Idempotencia con `UNIQUE` + libro + `REQUIRES_NEW` | B05 §6, B03 §7.2 |
| Pools aislados, rechazo de cola | B05 §2 |
| `RestClient`, timeouts, degradación controlada | §5, B01 §5.3 |
| Escape de HTML, inyección de cabeceras, Host header | §6, B04 §7 |
| `record` privado, `switch` de flechas, `StringBuilder`, text blocks | A01–A03 |
| `Thread.sleep` + `InterruptedException` | §4 |
| Reloj como parámetro para probar | B06, §7 |

---

## 12. Para practicar

1. Agregá (en papel) el aviso `CUENTA_REACTIVADA`: ¿qué agregás al enum, a `EmailService`, a `AdminUsuarioService`, al test y al DFD? ¿Con `despachar` o `despacharUnico`? ¿Por qué?
2. ¿Qué pasaría si `EmailService.programar` encolara el envío **sin** esperar el `afterCommit`? Dibujá un caso concreto con una suspensión que se revierte.
3. Probá `BrevoClient` en **modo simulado** (sin `BREVO_API_KEY`): ejecutá un pedido de recuperación y encontrá el correo (con su enlace) en el log.
4. Un evento con 5.000 inscriptos se da de baja. Calculá cuánto tarda el envío masivo con `pausa = 200 ms` y proponé una mejora.
5. ¿Por qué el enlace del correo usa `enexia.email.url-base-frontend` y no la URL de la petición? Explicalo como si se lo contaras a alguien que no conoce el ataque.

---

**Fin de la Parte D.** → [Glosario](./G_glosario.md) · [Índice general](./README.md)
