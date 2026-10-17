# Sprint 2 · 2026-10-16 y 2026-10-17 — Módulo de correos, pantallas de recuperación y estado real de suscripciones

**Rama:** `main` · **Remoto:** `origin` (`dev-gastong/Enexia`)
**Estado al cierre:** el subsistema de notificaciones por correo está completo en código y documentación; la recuperación de contraseña tiene sus dos pantallas; los 3 tests de unidad que estaban desactualizados pasan. Falta probar el flujo de correo de punta a punta con las claves de Brevo.

---

## 🎯 Objetivos del período
1. Traer a esta máquina el trabajo de correos hecho desde la notebook (`b914fab`) y comprobar qué faltaba para dejarlo coherente (código ↔ DFD ↔ documentación).
2. Construir el frontend de la recuperación de contraseña (RF-1.5), que tenía backend pero ninguna pantalla.
3. Dejar el repositorio al día: tests, DFD, `CLAUDE.md`/`CLAUDE_es-ES.md` y log.
4. Aclarar qué hay realmente construido sobre suscripciones (RF-6.4 vs Módulo 8).

## ✅ Entregables

| Fecha | Entregable | Detalle |
|---|---|---|
| 10-16 | **Módulo de correos (notebook)** | 12 tipos de aviso, `EmailService`, `BrevoClient` (API HTTPS de Brevo, sin SMTP), libro `notificacion_enviada` (idempotencia + límite por destinatario), `RecordatorioEventoScheduler`, `AvisoInscriptosService`, `CorreoHtml`. Migración `2026-10-16_notificacion_enviada.sql`. DFD Nivel 1, N.1, N.3 y N.5. |
| 10-17 | **DFD N.2 y N.4** | `docs/diagrams/notificaciones/N.2_moderacion_de_eventos.md` y `N.4_equipo_de_organizacion.md`, armados leyendo `AdminEventoService`, `ModeracionEventoService` y `MiembrosOrganizacionService`. Con esto el nivel 2 de notificaciones queda completo (N.1 a N.5). |
| 10-17 | **Pantallas de recuperación** | `pages/auth/password-reset.html` (pedir el enlace + "correo enviado") y `pages/auth/recuperar.html` (contraseña nueva), más `pages/css/recuperacion.css`. `api.js`: `solicitarRecuperacion` y `confirmarRecuperacion`. |
| 10-17 | **Tests de unidad corregidos** | `EventoServiceTest`, `InscripcionServiceTest$Historial`, `RegistroInscripcionServiceTest`: ver "Lecciones". |
| 10-17 | **Documentación** | `CLAUDE.md` y `CLAUDE_es-ES.md` (M6 completo, M8 parcial, endpoints admin, pantallas de recuperación), este log y el histórico. |

## 💡 Decisiones de arquitectura (ADR)

- **ADR-28 — Los avisos de una acción puntual no usan el libro de idempotencia.** `despachar` (moderación, equipo, bloqueo, recuperación) frente a `despacharUnico` (recordatorio, invitación a valorar, inscripción confirmada, cancelación): solo los que los dispara un reloj o dos caminos distintos necesitan la restricción única de `notificacion_enviada`. Documentado en N.2 y N.4.
- **ADR-29 — Aprobar un rechazo del sistema no es "reactivar".** Si el admin aprueba un `RECHAZADO_SISTEMA` se avisa con `MODERACION_RESULTADO` ("ya está publicado"); `EVENTO_REACTIVADO` queda para revertir una suspensión (`RECHAZADO_MANUAL`). Un evento rechazado por el pipeline nunca estuvo al aire. Ya era así en `AdminEventoService.avisarAlOrganizador`; ahora está escrito.
- **ADR-30 — La recuperación termina en el login, no inicia sesión.** El diseño de Figma decía "Actualizar y Acceder", pero `POST /api/auth/recuperacion/confirmar` no devuelve JWT (a propósito: autenticar con un enlace de correo ampliaría la superficie). La pantalla muestra "Contraseña actualizada" y enlaza al login.
- **ADR-31 — El token del enlace sale de la barra de direcciones apenas carga la página** (`history.replaceState`), para que no quede en el historial ni se copie al compartir la URL. Sin `?token=` la pantalla muestra "Enlace no válido" y no pide la contraseña.
- **ADR-32 — Las pantallas nuevas siguen el sistema de diseño vigente, no el Figma literal.** Los frames de recuperación eran oscuros (celeste); el proyecto usa `tokens.css` en paleta clara con esquinas cuadradas (`--radio-campo`, `--radio-tarjeta`). Se conservó el layout y los textos y se descartó el botón "Pantalla de cambio" (un atajo de maquetado: a la pantalla 3 solo se llega por el enlace del correo).

## 🔎 Suscripciones: qué existe y qué no (para definir)

**Existe (RF-6.4, lado administrador):** `suscripciones.html`, `GET /api/admin/suscripciones[/resumen]` y `PATCH /api/admin/suscripciones/{id}` con tres acciones. Reglas en `AdminSuscripcionService`:

| Acción | Se puede sobre | Resultado |
|---|---|---|
| `REVOCAR` | `ACTIVA` o `VENCIDA` | pasa a `CANCELADA`; el organizador queda con privilegios Free |
| `CAMBIAR_PLAN` | solo `ACTIVA` | conmuta `tipo_plan` FREE ↔ PRO (exige indicar el plan) |
| `RENOVAR` | solo `VENCIDA` PRO | vuelve a `ACTIVA`, cobertura nueva desde hoy de igual duración; **no genera `Pago`** |

Toda intervención deja historial con motivo y auditoría. El plan vigente es el de la última suscripción; sin suscripciones, Gratuito (ADR-26).

**No existe (Módulo 8):**
- **RF-8.1** flujo de upgrade del organizador (suscripción `PENDIENTE` → pago simulado → `ACTIVA` + `Pago`). No hay pantalla ni endpoint del lado del organizador.
- **RF-8.2** tope de eventos del plan Free al crear. El tope vive en **dos lugares sin conexión**: `enexia.planes.free.eventos-permitidos` (=2, solo lo lee el panel admin) y `enexia.eventos.limite-plan-gratuito` (=20, lo lee `EventoService.verificarLimiteDePlan`, que **no consulta la suscripción**: se aplica a todos los organizadores, Pro incluido, contando los eventos no dados de baja). Hoy el panel admin dice "Free = 2, Pro ilimitado" pero en la práctica todos topean en 20.
- **RF-8.3** estadísticas avanzadas solo Pro (403 para Free). Hoy `GET .../estadisticas` responde a cualquier organizador.

**Decisiones que faltan tomar (propuestas):**
1. ¿Cuántos eventos permite Free? (hoy 2 o 20). Se propone unificar en `enexia.planes.free.eventos-permitidos`.
2. ¿Qué cuenta contra el tope: eventos `PUBLICADO` + aprobados, o también los pendientes de moderación? ¿`FINALIZADO` libera cupo?
3. ¿Qué pasa con los eventos ya publicados cuando un Pro se revoca y queda sobre el tope? Se propone **no tocarlos** y solo bloquear los nuevos.
4. ¿Qué es "avanzado" en métricas (tendencias, visitas, exportar asistencia)? Definir qué queda abierto a Free (p. ej. el conteo de inscriptos).
5. ¿El upgrade es mensual y/o anual, y el precio sale de `enexia.planes.pro.precio-mensual`? El modelo no guarda el ciclo de cobro (ADR-26).
6. ¿Una organización (Persona Jurídica) tiene plan propio o cuenta el del organizador que crea el evento?

## ⚠️ Bloqueos / Lecciones aprendidas

- **Tres tests de unidad rotos por refactors anteriores, no por el módulo de correos** *(10-17)*: (1) `EventoServiceTest` mockeaba `categoriaRepository.existsById`, pero el servicio pasó a `existsByIdCategoriaAndActivaTrue` con el ABM de categorías (RF-6.3); (2) `InscripcionServiceTest` no tenía `@Mock ValoracionRepository`, que el historial usa desde la invitación a valorar (`@InjectMocks` lo dejó en `null`); (3) `RegistroInscripcionServiceTest`: el servicio compara contra `hora_inicio` (corte de inscripción) y el fixture solo fijaba `hora_fin` → `NullPointerException: time`. Lección: cuando un servicio cambia de qué dato depende, el fixture del test tiene que acompañarlo; los `@Mock` ausentes no fallan al compilar.
- **Los tests de Playwright y de API (`LoginTest`, `RegistroTest`, `EventoApiTest`, `CrearEventoTest`) no son parte de la suite de unidad:** necesitan el backend levantado y parámetros (`-Dnickname=...`). Con `gradle test` sin eso fallan por entorno, no por código.
- **Ruido de fin de línea en `CLAUDE.md`:** el commit `b914fab` mostraba ~1200 líneas cambiadas por CRLF/LF entre las dos PC, cuando el cambio real eran ~13. Conviene fijar `.gitattributes` o `core.autocrlf`.
- **Archivos de diseño con errores de tipeo:** el Figma de la pantalla 3 traía "Contraseñaa". Se corrigió al construirla.
- **Deuda aceptada hasta el despliegue:** `FinalizacionEventoScheduler` sigue con `LocalDateTime.now()` (zona de la JVM); en UTC finaliza los eventos 3 h antes. No se toca hasta preparar el despliegue.

## 📐 Estructura agregada (referencia)

```
docs/diagrams/notificaciones/  N.2_moderacion_de_eventos.md · N.4_equipo_de_organizacion.md
enexia/src/main/resources/static/
├── pages/auth/   password-reset.html · recuperar.html
├── pages/css/    recuperacion.css
└── js/api.js     solicitarRecuperacion · confirmarRecuperacion
enexia/src/test/java/com/enexia/rg/service/
└── EventoServiceTest · InscripcionServiceTest · RegistroInscripcionServiceTest   (fixtures corregidos)
```

## ⏭️ Próximos pasos
1. Probar el flujo de correo real (recuperación, inscripción, suspensión de evento) con `BREVO_API_KEY` y `EMAIL_REMITENTE`, y confirmar las filas de `notificacion_enviada`.
2. Definir con el equipo las 6 decisiones de suscripciones y construir el Módulo 8 (RF-8.1 a RF-8.3).
3. Endpoint de desbloqueo de cuenta sin cambio de contraseña (RF-1.5, aún abierto).
4. Otorgar el rol `ADMINISTRADOR` desde la aplicación.
5. Antes de desplegar: zona horaria del scheduler de finalización.
