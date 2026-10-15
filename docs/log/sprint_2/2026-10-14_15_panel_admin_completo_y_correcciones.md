# Sprint 2 · 2026-10-14 y 2026-10-15 — Panel de administración completo + correcciones críticas

**Rama:** `main` · **Remoto:** `origin` (`dev-gastong/Enexia`)
**Estado al cierre:** módulo 6 (panel admin) construido en sus cuatro pantallas; cuatro correcciones del testing manual; suite de generación de datos. Todo verificado contra la base de dev real (MariaDB 11.8.9), sin tests automáticos nuevos para lo de seguridad (ver deuda).

---

## 🎯 Objetivos del período
1. Cerrar el módulo 6: gestión de cuentas (RF-6.2), ABM de categorías (RF-6.3) y control de suscripciones (RF-6.4), sobre el panel que ya tenía moderación de eventos (RF-6.1).
2. Corregir cuatro hallazgos del testing manual del usuario, el primero **crítico**: un usuario suspendido o bloqueado podía seguir navegando e inscribiéndose.
3. Dejar una forma repetible de generar datos de prueba (30 eventos con cronogramas y tickets aleatorios).

## ✅ Entregables

| Fecha | Entregable | Detalle |
|---|---|---|
| 10-14 | **Gestión de cuentas (RF-6.2)** | `GET/PATCH /api/admin/usuarios`, `/resumen`. Suspender, reactivar; `usuarios.html`. DFD 6.2 aprobado y movido a `diagrams/modulo_6_admin/`. |
| 10-14 | **Fix crítico: sesión de suspendidos** | `JwtAuthenticationFilter` consulta el estado vigente en cada request; `auth.js` verifica la sesión al abrir cada página. |
| 10-14 | **Eventos FINALIZADO automáticos** | `FinalizacionEventosService` + `FinalizacionEventosJob` (barrido cada 5 min) y pasada inmediata en el listado del organizador. |
| 10-14 | **Relacionados solo a futuro / home en una fila** | `evento-detalle.html` pide `desde=hoy`; `index.html` con buscador en una fila, contenedor 1440 px y 5 columnas. |
| 10-14 | **Cuenta admin de dev** | `admin_enexia` (id 956) creada por SQL respetando las relaciones. Ver [2026-10-14_cuenta_admin_dev.md](./2026-10-14_cuenta_admin_dev.md). |
| 10-14 | **Suite masiva** | `EventosMasivosApiTest`: 30 eventos, 1-3 fechas, 1-2 tickets por fecha. Corrida real: ids 311–340, 30/30 aprobados. |
| 10-15 | **ABM de categorías (RF-6.3)** | `GET/POST/PUT/PATCH /api/admin/categorias`; `categorias.html` con modales Desactivar, Activar, Gestionar y confirmación. Migración `2026-10-15_categoria_activa.sql`. |
| 10-15 | **Control de suscripciones (RF-6.4)** | Lectura (`GET /api/admin/suscripciones`, `/resumen`) + intervenciones (`PATCH /{id}`: REVOCAR, CAMBIAR_PLAN, RENOVAR); `suscripciones.html` con sus tres modales. Estados de suscripción sembrados. |

## 💡 Decisiones de arquitectura (ADR)

- **ADR-20 — El JWT no alcanza para saber si una cuenta puede operar: el estado se consulta en cada request autenticado.** El token es stateless y seguía firmado y vigente después de una suspensión (RF-6.2) o un bloqueo (RF-1.4), de modo que la sanción solo impedía el *próximo login*. `JwtAuthenticationFilter` ahora hace un `COUNT` por email (índice único) y, si la cuenta no está `ACTIVO` o tiene `fecha_baja`, trata la petición como anónima → 401 en lo protegido. **Falla cerrado:** ante un error de base de datos la petición queda anónima. Costo: una consulta liviana por request autenticado; aceptado frente a mantener una lista de revocación. El cliente cierra el círculo (`auth.js`: una sonda a `/api/usuario/perfil` al cargar cada página; 401 → limpia sesión y manda al login).
- **ADR-21 — "FINALIZADO" es un estado persistido, no derivado.** Se evaluó derivarlo en el mapper (menos código), pero rompía los filtros del dashboard (`estado=FINALIZADO` / `PUBLICADO` se resuelven en SQL), la guarda de edición (`verificarEditable` ya lo contemplaba) y el catálogo público. Se persiste con historial (`usuario = null`: lo decide el sistema). Doble disparo: barrido periódico (limpia el catálogo solo) + pasada sincrónica sobre los eventos del organizador **desde el controller y antes de abrir la transacción de lectura**, porque con REPEATABLE READ el snapshot se fija en la primera lectura y un cambio confirmado después no se vería dentro de esa misma transacción.
- **ADR-22 — La baja de una categoría es lógica y reversible (`categoria.activa`).** RF-6.3 pide "evitar huérfanos"; el DFD original decía "409 si tiene eventos", lo que volvía imposible retirar casi cualquier categoría. El diseño del usuario ("desactivar con 19 eventos activos") lo confirmó. Una categoría inactiva no se ofrece para eventos nuevos ni en los filtros; los eventos existentes la conservan y siguen editables sin reclasificarse. **Invariante:** no se puede desactivar la última activa. DFD 6.3 reescrito en consecuencia. La columna lleva `DEFAULT 1` por una razón concreta: sin él, al agregarla a una tabla con filas MariaDB las deja en 0 y el catálogo queda sin categorías.
- **ADR-23 — Las intervenciones de admin piden una ACCIÓN, no un estado destino** (`SUSPENDER`/`REACTIVAR`, `DESACTIVAR`/`ACTIVAR`, `REVOCAR`/`CAMBIAR_PLAN`/`RENOVAR`). El service resuelve origen y destino con lo que hay en la base, valida la transición (409 si ya está en ese estado) y registra historial + auditoría. Mismo criterio que ADR-19 para RF-6.1.
- **ADR-24 — Cada escritura del panel responde `{mensaje, …}` y el modal de confirmación solo existe con ese mensaje.** El texto de la confirmación lo decide el backend, de modo que la pantalla nunca confirma algo que el servidor no aplicó.
- **ADR-25 — Reglas de cuentas protegidas:** no se suspende a otro `ADMINISTRADOR` ni a uno mismo (una suspensión por error del único admin dejaría el panel inaccesible) y `DE_BAJA` (decisión del titular) no se toca. El campo `gestionable` de cada fila es la única fuente de verdad; la pantalla no replica la regla.
- **ADR-26 — El plan vigente es el de la última suscripción; sin suscripciones, es Gratuito.** Facturación (mensual/anual) y monto se **deducen** (duración ≥ 300 días → anual; precio Pro mensual de configuración) porque el modelo no guarda el ciclo de cobro. *Renovar* omite la pasarela a propósito (excepción administrativa) y por lo tanto **no genera `Pago`**: no cuenta como ingreso.
- **ADR-27 — El plan Pro es ilimitado (RF-8.2).** El diseño de la tarjeta decía "10 eventos"; el modal de Cambiar Plan y el requisito dicen ilimitado. Se siguió el requisito: `eventosPermitidos = null` → "Ilimitados"; Free = 2 (configurable). **Ningún tope se aplica todavía al crear eventos** (RF-8.2 sin construir): hoy es solo informativo.

## ⚠️ Bloqueos / Lecciones aprendidas

- **Autoinvocación que salta el proxy transaccional, otra vez** *(10-14)*: `FinalizacionEventosService.barrido()` llamaba a su propio método `@Transactional`; el bloqueo de fila falló con `TransactionRequiredException`. Es la trampa ya documentada en `CLAUDE.md` (`IntentosLoginService`, `VisitaService`). Se resolvió separando el disparador (`FinalizacionEventosJob`) y haciendo transaccionales ambos puntos de entrada con un método privado común. **Lección:** un `@Scheduled` en la misma clase que el `@Transactional` que invoca es el mismo error con otra cara.
- **`DatosInicialesConfig` sembraba categorías "por nombre faltante" en cada arranque** *(10-15)*: el comentario prometía no pisar renombres, pero el código no lo cumplía; con un ABM, renombrar "Musica" la habría resucitado en el próximo reinicio. Ahora solo siembra si la tabla está vacía.
- **Un puerto 8080 ocupado por la instancia del propio usuario** *(10-14)*: el `bootRun` de verificación fallaba sin explicación aparente. Las verificaciones corren en el 8081 (`--args='--server.port=8081'`), contra la **misma base**, y se apagan al terminar. Como `API.BASE` está fijo en `localhost:8080`, la verificación en navegador requiere reescribirlo en caliente.
- **La tarjeta de `evento-detalle` mostraba eventos terminados como "relacionados"** por no filtrar fecha; además el catálogo nunca se limpiaba porque nada marcaba `FINALIZADO`. Ambos eran la misma causa raíz (ADR-21).
- **Herramienta:** los *heredocs* largos con comillas fallaban en el shell del entorno; los scripts de edición masiva se guardan como archivo y se ejecutan (`python script.py`).
- **Higiene de datos de prueba:** las verificaciones crean filas reales (eventos, cuentas `*@enexia.test`, suscripciones). Las que son de uso único (suscripciones, categoría de prueba) se borran al terminar; las de la suite masiva (ids 311–340, etiqueta `S10D-…`) se dejan porque son el producto pedido.
- **Contraseña de la cuenta admin de dev en un archivo versionado** *(10-15)*: `2026-10-14_cuenta_admin_dev.md` quedó commiteado y empujado. Es una cuenta solo de la base local, pero conviene **cambiarle la contraseña** y no reutilizar ese patrón.

## 📐 Estructura agregada (referencia)

```
enexia/src/main/java/com/enexia/rg/
├── controller/  UsuarioAdminController · CategoriaAdminController · SuscripcionAdminController
├── service/     AdminUsuarioService · AdminCategoriaService · AdminSuscripcionService
│                FinalizacionEventosService · FinalizacionEventosJob
├── dto/         Admin{Usuario,Categoria,Suscripcion,Suscriptor}*Request/Response
├── model/       SuscripcionEstadoNombre (enum) · Categoria.activa
└── config/      SchedulingConfig (@EnableScheduling)
enexia/src/main/resources/static/pages/admin/
└── moderacion-eventos · usuarios · categorias · suscripciones   (.html)
docs/diseño_bd/migraciones/2026-10-15_categoria_activa.sql
```

**Endpoints nuevos** (todos `hasRole('ADMINISTRADOR')`):
```
GET    /api/admin/usuarios                     directorio paginado (texto, estado)
GET    /api/admin/usuarios/resumen
PATCH  /api/admin/usuarios/{id}/estado         { accion: SUSPENDER | REACTIVAR }
GET    /api/admin/categorias                   listado + totales
POST   /api/admin/categorias                   { nombre }
PUT    /api/admin/categorias/{id}              { nombre }
PATCH  /api/admin/categorias/{id}/estado       { accion: DESACTIVAR | ACTIVAR }
GET    /api/admin/suscripciones                organizadores con su plan vigente
GET    /api/admin/suscripciones/resumen
PATCH  /api/admin/suscripciones/{id}           { accion: REVOCAR | CAMBIAR_PLAN | RENOVAR, tipoPlan? }
```

**Configuración nueva** (`application.properties`): `enexia.planes.free.eventos-permitidos` (2), `enexia.planes.pro.precio-mensual` (9.99).

## ✅ Verificación (condiciones)
- Compila (`./gradlew compileJava`); JS de cada página parseado con `node` y todos los `id` referenciados presentes.
- **API probada en vivo** contra MariaDB 11.8.9 con la cuenta `admin_enexia`: casos felices y rechazos (401/403/404/409/400) de los tres módulos; el token de una cuenta suspendida da 401 y vuelve a 200 al reactivarla.
- **No probado:** el flujo de clic real en navegador para los modales de Activar categoría y Suscripciones (el pane de verificación es angosto y captura a mitad de animación); la lógica nueva **no tiene tests automáticos** (ver pendientes).

## ⏭️ Próximos pasos
1. **Tests** del filtro JWT (el caso del suspendido), de `FinalizacionEventosService`, `AdminUsuarioService`, `AdminCategoriaService` y `AdminSuscripcionService`.
2. **Modales pendientes de RF-6.4:** *Actualizar* (Gratuito → Pro) y *Reactivar*; diseño de confirmaciones propias.
3. **Módulo 8** (RF-8.1 upgrade con pago simulado, RF-8.2 tope de eventos, RF-8.3 métricas Pro): hoy el panel administra suscripciones que ningún flujo del organizador crea. Definiciones abiertas: qué cuenta como "activo" para la cuota, upgrade con una suscripción `PENDIENTE` previa, y qué pasa con los eventos de un Pro que vuelve a Free.
4. **Otorgar `ADMINISTRADOR`** desde la app (hoy es un `INSERT`).
5. Mover a `diagrams/` los DFD 6.3, 6.4 y `nivel1_admin` (6.3 ya está reescrito; falta el visto bueno), y los de los módulos 7 y 8.
6. Email al suspendido/reactivado (lo menciona el DFD 6.2); mensaje al usuario suspendido en el login en vez de redirigir sin explicación.
7. JSON malformado responde 500 en vez de 400 (anterior a este período).
8. Cambiar la contraseña de `admin_enexia` y sacar el patrón de credenciales de `docs/`.
