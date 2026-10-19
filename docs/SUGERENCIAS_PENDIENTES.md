# Sugerencias y preguntas abiertas (cierre del proyecto, 2026-10-19)

> **Qué es este archivo:** durante el cierre no se hicieron preguntas al titular del proyecto; todo lo que quedó sin resolver se anota acá. Cada punto trae **qué se hizo por defecto** y **la alternativa**, para que quien continúe pueda cambiarlo sin tener que adivinar.

## 1. Suscripciones y estadísticas

| # | Pregunta | Qué se hizo por defecto | Alternativa |
|---|---|---|---|
| S1 | ¿Cancelar el Plan Pro es inmediato o rige hasta `fecha_fin`? | **Inmediato** (vuelve a Gratuito en el acto). Es lo más simple y coincide con `REVOCAR` del panel. | Mantenerlo hasta `fecha_fin` y no renovarlo: exige un campo nuevo (p. ej. `renovacion_cancelada`) porque el estado `ACTIVA` no distingue "activa" de "activa pero cancelada". |
| S2 | ¿Pro mensual y también anual? | **Solo mensual.** El panel de administración ya *deduce* "anual" si la cobertura dura 300 días o más (ADR-26), pero el organizador no puede contratarlo. | Agregar un campo de ciclo de cobro en `Suscripcion` y un segundo precio (`enexia.planes.pro.precio-anual`). |
| S3 | ¿El plan es del usuario o de la organización (Persona Jurídica)? | **Del usuario organizador** que creó el evento (D6). | Un plan por organización exigiría una relación `Suscripcion` ↔ `Persona_Juridica` y decidir qué pasa con los miembros que crean eventos "bajo" ella. |
| S4 | ¿Qué es exactamente "avanzado"? | Distribución de puntajes, cancelaciones, ingresos, series diarias, conversión y exportación de asistentes (ver DFD 8.3). | Ajustar la lista sin cambiar el mecanismo: es una decisión de producto. |
| S5 | ¿Un Gratuito debería ver el candado de lo avanzado con el botón "Pasar a Pro"? | Sí: el 403 lleva el código `PLAN_PRO_REQUERIDO` para que la pantalla lo detecte. | Ocultar directamente la sección. |
| S6 | ¿Qué pasa con las estadísticas históricas si un Pro vence? | Dejan de verse las avanzadas; los datos **no se borran**. | Mostrar un resumen congelado del último mes Pro. |
| S7 | ¿Avisar por correo cuando se activa, se cancela o vence una suscripción? | **No** (el catálogo de notificaciones no tiene esos tipos). | Agregar `SUSCRIPCION_ACTIVADA`, `SUSCRIPCION_VENCIDA` (con aviso 3 días antes) siguiendo el patrón de `despacharUnico`. |
| S8 | `Pago.id_suscripcion` es `@OneToOne`: ¿cómo se reintenta un pago rechazado? | Sin decidir (ver nota en el DFD 8.1). | Cambiar a `@ManyToOne` (una suscripción, varios intentos de pago) o crear una suscripción `PENDIENTE` nueva por intento. |

## 2. Cuentas y seguridad

| # | Pregunta | Qué se hizo por defecto | Alternativa |
|---|---|---|---|
| C1 | ¿Avisar por correo al titular cuando un administrador **reactiva** su cuenta? | **No.** Hoy solo se avisa la suspensión (`CUENTA_SUSPENDIDA`). Al reactivar, el titular se entera al volver a entrar. | Nuevo tipo `CUENTA_REACTIVADA` con `despachar`, llamado desde `AdminUsuarioService.cambiarEstado` cuando `!suspender`. |
| C2 | ¿Cómo se contacta un titular bloqueado con la administración? | El correo de bloqueo dice "comunicate con la administración de Enexia", pero **no hay un canal definido** (ni formulario ni dirección). | Una dirección de soporte configurable (`enexia.email.soporte`) que el correo incluya; o un formulario público de "solicitar desbloqueo" que cree un aviso en el panel. |
| C3 | ¿Los administradores pueden desbloquear a otros administradores? | **No** (`esGestionable` protege a los demás administradores y a uno mismo). Un administrador bloqueado solo se recupera por base de datos. | Una acción especial entre administradores con doble confirmación, o un script de rescate documentado. |
| C4 | ¿Se otorga el rol `ADMINISTRADOR` desde la app? | Propuesta en el DFD 6.5: solo si la cuenta no tiene eventos vigentes ni inscripciones futuras. | Permitir la promoción siempre y dejar la actividad previa "congelada" (visible pero no operable). |
| C5 | Los JWT emitidos siguen siendo válidos hasta vencer aunque se cambie la contraseña. | Limitación conocida (documentada en N.1). | Un campo `token_version` en `Usuario` que se incremente al cambiar la contraseña o suspender, y que el filtro compare. |
| C6 | La contraseña de la cuenta admin de desarrollo quedó en un archivo versionado (`docs/log/sprint_2/2026-10-14_cuenta_admin_dev.md`). | Sin cambios. | **Cambiarla** y quitar el valor del archivo antes de publicar el repositorio. |
| C7 | El correo del "password spraying": alertar por volumen anómalo en `Historial_Interacciones`. | Diferido (RF-1.4). | Una consulta programada que cuente `PASSWORD_INCORRECTA` por hora y avise si supera un umbral. |

## 3. Eventos y operación

| # | Pregunta | Qué se hizo por defecto | Alternativa |
|---|---|---|---|
| E1 | `FinalizacionEventoScheduler` usa `LocalDateTime.now()` (zona de la JVM). En un servidor UTC finaliza los eventos 3 h antes. | **Sin tocar** (el usuario pidió ignorarlo hasta desplegar). | Usar `Clock` con la zona `enexia.zona-horaria`, como ya hace el recordatorio. **Hacerlo antes del primer despliegue.** |
| E2 | Los tests de Playwright y de API (`LoginTest`, `RegistroTest`, `EventoApiTest`, `CrearEventoTest`, `EventosMasivosApiTest`) necesitan el backend, la base y parámetros; fallan en `gradle test` sin ellos. | Sin tocar. | Moverlos a un *source set* aparte (`integrationTest`) para que `gradle test` corra solo los de unidad. |
| E3 | Ruido de fin de línea (CRLF/LF) entre las dos PC. | Sin tocar. | Un `.gitattributes` con `* text=auto eol=lf`. |
| E4 | Los DFD de los módulos 3, 4 y 5 no se revisaron contra el código en este cierre. | Se asumen alineados. | Pasar `check-rules` por cada módulo. |
| E5 | `pages/organizador/mis-eventos.html` no tiene acceso a las estadísticas del evento. | Pendiente (ver 2.10). | Botón "Estadísticas" por evento. |
| E6 | Las pantallas de recuperación se probaron con un enlace real solo hasta la llegada del correo. | El tramo "abrir el enlace del correo → cambiar la contraseña" no se probó de punta a punta con un token real. | Abrir a mano el enlace del correo de bloqueo en `gaston_test` y completar el cambio. |

## 4. Documentación

| # | Pregunta | Qué se hizo por defecto |
|---|---|---|
| D1 | ¿Dónde está el manual de programación? | En `docs/manual/` (ver el índice). Es un recorrido de **aprendizaje**, no de referencia: sigue el orden en que se construyó el proyecto. |
| D2 | `CLAUDE.md` sigue en inglés y `CLAUDE_es-ES.md` en español. | Se mantienen sincronizados. |
