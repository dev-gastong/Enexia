# Sprint 2 · 2026-10-19 — Cierre del proyecto

**Rama:** `main` · **Remoto:** `origin` (`dev-gastong/Enexia`)
**Estado al cierre:** módulos M1 a M7 y el subsistema de correos construidos y documentados; **M8 (membresías) especificado en `docs/tempDFD/` sin programar**; manual de programación completo (`docs/manual/`); desbloqueo de cuentas exclusivo del panel de administración; sin tope de eventos; XSS almacenado corregido en las páginas públicas.

---

## 🎯 Objetivos del período
1. Dejar en `tempDFD` **los DFD de lo que falta programar**, con la suscripción **básica**, **sin límite de eventos** y las estadísticas **por cronograma**.
2. **Desbloqueo de cuenta solo desde el panel de administración.**
3. Validar que **las valoraciones sean por cronograma**.
4. **Documentar todo** el proyecto, "desde crear una clase hasta aplicar POO, Spring Boot, MVC, la API, JavaScript vanilla, HTML y CSS", en orden y módulo por módulo.

## ✅ Entregables

| Entregable | Detalle |
|---|---|
| **Desbloqueo solo por administrador** | `RecuperacionCuentaService.confirmarRestablecimiento` ya **no reactiva** cuentas `BLOQUEADAS` (se quitó `reactivarSiEstabaBloqueada` y sus dependencias). El correo de bloqueo y `recuperar.html` lo aclaran. RF-1.4, RF-1.5, RF-6.2, DFD 1.5, N.1, 6.2 y `CLAUDE.md` actualizados. Test `noDesbloqueaCuentaBloqueada`. |
| **Sin tope de eventos** | Eliminados `EventoService.verificarLimiteDePlan`, `EventoRepository.contarVigentesDeOrganizador` y las propiedades `enexia.eventos.limite-plan-gratuito` y `enexia.planes.free.eventos-permitidos`. El panel de suscripciones muestra "Eventos ilimitados". Test `sinLimiteDePlan` (25 seguidos). |
| **Valoraciones por cronograma: validado** | Tabla (`id_cronograma`, `UNIQUE(id_usuario, id_cronograma)`), entidad, servicio y API. Detalle en `docs/tempDFD/modulo_2_estadisticas/2.10_estadisticas_por_cronograma.md` y en el manual (D3 §6). |
| **`docs/tempDFD/` reordenado** | Quedan **solo los pendientes**: `README` (decisiones D1–D7 + orden), M8 (`nivel1`, 8.1, 8.2, 8.3), `2.10` (estadísticas por cronograma) y `6.5` (otorgar el rol ADMINISTRADOR). Los DFD **ya implementados** (6.3, 6.4 —**reescrito para coincidir con el código**—, `nivel1_admin` y todo el Módulo 7) pasaron a `docs/diagrams/`; también se alinearon 6.2 y `consultar_estadisticas`. `docs/diagrams/README.md` indexa todo. |
| **Requisitos** | RF-8.1 a 8.3 reescritos (suscripción básica, plan vigente, estadísticas avanzadas solo Pro), RF-2.10 (por cronograma), historia de usuario HU 06. |
| **Manual de programación** | `docs/manual/`: 4 capítulos de Java (A), 6 de Spring Boot (B), 3 de API/JavaScript/HTML-CSS (C), 9 de módulos (D) y glosario. Ver el `README` del manual. |
| **Corrección de XSS** | `UI.esc` en `js/utils.js` aplicado a los textos de usuario en `index.html`, `evento-detalle.html` (incluidos comentarios y toasts), `dashboard.html`, `mis-eventos.html`, `mis-inscripciones.html` y `perfil.html`. Verificado en el navegador con una respuesta de la API **simulada** que traía `<img src=x onerror=...>`. |
| **Sugerencias y preguntas** | `docs/SUGERENCIAS_PENDIENTES.md`: lo que habría sido una pregunta se dejó como sugerencia, con el valor por defecto elegido y la alternativa. |

## 💡 Decisiones (ADR)

- **ADR-33 — Una sola puerta para desbloquear una cuenta: el panel de administración.** El enlace del correo solo cambia la contraseña. Una puerta es una puerta que auditar; y cambiar la contraseña no debe levantar una sanción ni un bloqueo.
- **ADR-34 — Ningún plan limita la cantidad de eventos.** El plan Pro se diferencia por las **estadísticas avanzadas por cronograma**. Se eliminó el tope del código y de la configuración (estaba duplicado: 2 en el panel, 20 en el servicio).
- **ADR-35 — Suscripción básica:** dos planes (`GRATUITO` por defecto, sin fila; `PRO` mensual con pago simulado); el plan pertenece al **usuario organizador**, no a la organización; sin renovación automática; cancelar es inmediato (S1).
- **ADR-36 — Las estadísticas se desglosan por cronograma; las visitas quedan en el evento.** La ficha se visita antes de elegir una función, así que una visita no se puede atribuir a un cronograma. Las valoraciones **sí** (ya eran por cronograma).
- **ADR-37 — Todo texto de usuario que entre a `innerHTML` pasa por `UI.esc`** (o se asigna con `textContent`).

## ⚠️ Hallazgos y lecciones

- **XSS almacenado vigente** *(10-19)*: aparecía como pendiente en el histórico del 10-11 ("Corregir el XSS almacenado en el nombre del evento") y **seguía abierto** en las páginas públicas (nombre, descripción, dirección, nombre de ticket, comentarios de valoraciones). Se corrigió lo público; **no se auditaron todas las pantallas** (X1). Lección: una deuda de seguridad anotada en un "próximos pasos" **hay que cerrarla o escribirla en el backlog con dueño**.
- **Textos del panel que contradecían una decisión**: la modal "Cambiar plan" decía *"modificará sus límites de publicaciones"*; se corrigió. Al cambiar una regla de producto hay que **buscar su texto en la interfaz**.
- **Comentarios desactualizados** (X4): `AuthController` y `ValidadorCuit` hablan de `REVISION_PENDIENTE`, etapa eliminada en ADR-14. El código manda.
- **Brechas de frontend que el backend ya cubre** (X2, X3): el registro de organizaciones y "publicar a nombre de una organización" **no están conectados** al backend existente. Hoy el flujo de organizaciones solo se puede usar por API.
- **El modal de estadísticas ya existe** (por evento), contra lo que decía una versión previa de las sugerencias (E5). Verificar antes de afirmar que "no hay pantalla".
- **Antes de documentar, verificar contra el código:** varias afirmaciones intuitivas del manual se corrigieron al comprobarlas (la previsualización de imágenes usa `FileReader` y no `URL.createObjectURL`; el formulario de evento **no** tiene el selector de organización; la moderación de imágenes es **por extensión**, no por contenido).

## 🧪 Verificación

- Tests de servicio de lo tocado (`RecuperacionCuentaServiceTest`, `EventoServiceTest`, `EmailServiceTest`, `AdminEventoServiceTest`, `InscripcionServiceTest`, `RegistroInscripcionServiceTest`): **verdes**.
- Frontend: `index.html`, `evento-detalle.html` y `dashboard.html` cargan sin errores de consola; el escape se verificó con una respuesta simulada.
- **No se corrió** la suite completa de API/UI en este cierre (requiere base, backend y credenciales): quedó como estaba el 10-17.

## ⏭️ Próximos pasos (para quien continúe)

1. Programar M8 siguiendo la **lista de tareas de D8 §6** y `docs/tempDFD/README.md`.
2. Auditar el resto de las pantallas por XSS y agregar un test (X1).
3. Conectar el registro de organizaciones y el selector "publicar como" (X2, X3).
4. Corregir la zona horaria de `FinalizacionEventoScheduler` **antes de desplegar** (E1).
5. Otorgar el rol ADMINISTRADOR desde la aplicación (DFD 6.5) y avisar por correo al reactivar (C1).
