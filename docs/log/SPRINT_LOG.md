# 📖 Registro Maestro de Sprints — Enexia

Este archivo indexa el progreso por Sprints. El detalle diario vive en subcarpetas `docs/log/sprint_N/`.

---

## Sprint 2 — Backend: Organizaciones, Eventos y Catálogo Público

**Estado:** ✅ Backend completo · ⏸️ Frontend diferido (a la espera de Figma)
**Fecha:** 2026-09-08

### 🎯 Objetivos del Sprint
- Quitar `tipo_persona` de `Persona` y cerrar la inconsistencia de modelo que dejó Sprint 1. ✅
- Reemplazar el bloqueo por IP por **bloqueo de cuenta silencioso** con aviso por email. ✅
- Registro de Persona Jurídica con validación de CUIT (RF-7.2, RF-7.3). ✅
- Módulo de organizador: creación de eventos, Cloudinary y moderación asíncrona (M2, M5). ✅
- Catálogo público con búsqueda, filtros y ficha técnica (M4). ✅

### ✅ Entregables Completados
| Fecha | Entregable | Detalle |
|-------|-----------|---------|
| 2026-09-08 | **Backend completo de Sprint 2** | 5 módulos, 49 archivos nuevos y 40 modificados. Ver [sprint_2/2026-09-08_sprint2_backend.md](./sprint_2/2026-09-08_sprint2_backend.md) |
| 2026-09-08 | **Suite de pruebas** | 165 tests (JUnit/Mockito/Playwright) + 245 aserciones (Postman/Newman), 0 fallos. Ver [sprint_2/2026-09-08_resultados_pruebas_sprint2.md](./sprint_2/2026-09-08_resultados_pruebas_sprint2.md) |
| 2026-09-08 | **Migración de esquema** | [migraciones/2026-09-08_sprint2.sql](../diseño_bd/migraciones/2026-09-08_sprint2.sql) — **obligatoria**, ver ADR-11 |
| 2026-09-09 | **Upgrade a MariaDB 11.8.9 LTS** | Destapó 2 bugs de concurrencia reales que la 10.4 ocultaba. 166 tests en verde. Ver [sprint_2/2026-09-09_upgrade_mariadb.md](./sprint_2/2026-09-09_upgrade_mariadb.md) |
| 2026-09-09 | **Cierre de la divergencia RF-1.4** | Decisión final: bloqueo al 3° fallo + desbloqueo por enlace. RF y DFD reescritos. Ver ADR-13 |

### 💡 Decisiones de Arquitectura (ADR)
- **ADR-08 — `Persona` es solo persona humana**: se elimina `tipo_persona`. La columna suponía una jerarquía `Persona → (Física | Jurídica)` que el MER nunca declaró y que el DER ya contradecía. Una Persona Jurídica es un **contenedor administrativo**, no una identidad de acceso: se vincula por `Miembros_Organizacion`.
- **ADR-09 — Bloqueo silencioso en lugar de rate limiting por IP**: todos los rechazos de login responden idéntico (401, mismo código, mismo mensaje, sin cabeceras, mismo tiempo). El motivo real solo va al log y a la auditoría. Al titular se le avisa por email con enlace de recuperación. **Riesgo asumido:** el password spraying queda sin freno propio.
- **ADR-10 — Dos puntos de entrada para el alta de organización, un solo servicio**: RF-7.2 y el DFD 7.1/7.2 describen flujos distintos; se implementaron los dos sobre `PersonaJuridicaService.crearOrganizacion()` para que no puedan divergir.
- **ADR-11 — Las migraciones de esquema pasan a ser scripts versionados**: `ddl-auto=update` NO puede agregar columnas contra MariaDB 10.4 (genera `ALTER TABLE IF EXISTS`, sintaxis que 10.4 rechaza) y **falla en silencio**. Toda columna nueva va también a `docs/diseño_bd/migraciones/`.
- **ADR-14 — El alta de organización se resuelve en el acto; se elimina el estado de revisión** *(2026-09-09, decisión del usuario)*: una PJ deja de nacer en `REVISION_PENDIENTE`/`INACTIVO`. Si el CUIT pasa el módulo 11 de RF-7.3, queda `APROBADO`/`ACTIVO` y habilitada para publicar en la misma petición; si no lo pasa, se rechaza la petición completa y no se persiste nada, quedando el CUIT libre para reintentar. **Motivo:** verificar la existencia real de la entidad contra el padrón de AFIP/ARCA exige clave fiscal y certificado digital, y no hay servicio público gratuito y estable. El estado de revisión no tenía ningún proceso que lo cerrara — ver el bloqueo de abajo. **Limitación asumida y documentada en RF-7.2/7.3:** el módulo 11 es aritmético, no probatorio; un CUIT inventado cuyo dígito verificador cierre será aceptado. Los estados `REVISION_PENDIENTE` y `RECHAZADO` se conservan en el catálogo y el historial para la suspensión administrativa del Módulo 6.
- **ADR-10 bis — "Flujo separado" significaba entidad separada, no momento separado** *(2026-09-09)*: la redacción de RF-7.2 ("no como parte del registro inicial") se leía como una prohibición del alta durante el registro, y contradecía frontalmente el endpoint público `POST /api/auth/registro/organizacion` que ADR-10 había decidido implementar. Se reescribió RF-7.2: el alta de la organización es un proceso propio con sus propias validaciones, independientemente de cuándo lo ejecute la persona. Los dos puntos de entrada quedan explícitamente legitimados, y se elevó a requisito la atomicidad organización + primera membresía.
- **ADR-13 — Bloqueo al 3° fallo, silencioso, con desbloqueo por enlace de correo** *(2026-09-09, decisión del usuario)*: se descarta la escalera 3/6/9 y el cooldown escalonado. La regla queda en **un solo escalón**: 3 fallos consecutivos por cuenta → `estado_usuario = BLOQUEADO`. El bloqueo **no se informa por ningún canal visible para un atacante** (respuesta, cabeceras, tiempo, frontend, consola de depuración): hacia afuera siempre "credenciales incorrectas"; hacia adentro se opera con el código real y se audita. El aviso va **solo al email del titular**, con un enlace `/unlock-account?token=XYZ` de un solo uso que reactiva la cuenta y permite volver a entrar con las credenciales habituales. Esto cierra a la vez la divergencia RF-1.4 y el hueco de aviso al usuario legítimo. **CAPTCHA y 2FA quedan fuera de alcance** y se eliminaron del DFD de login.
- **ADR-12 — El trabajo asíncrono se dispara después del commit**: vía `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`, nunca por llamada directa desde un método `@Transactional`.

### ⚠️ Bloqueos / Lecciones Aprendidas
- **🚨 `ddl-auto=update` roto desde Sprint 1**: cuatro columnas de `persona_juridica` declaradas en la entidad desde el 2026-07-26 nunca existieron en la base. Nadie lo notó porque ninguna consulta tocaba esa tabla. Corregido por migración; ver ADR-11.
- **Carrera con el commit**: el pipeline asíncrono arrancaba antes de confirmar el INSERT y dejaba los eventos en `RECHAZADO_SISTEMA/ERROR_PIPELINE`. Corregido con ADR-12.
- **`@Transactional` y auto-invocación**: `registrarVisita()` dentro del mismo bean dejaba el `REQUIRES_NEW` sin efecto y la ficha técnica respondía 500. Se extrajo a `VisitaService`. Es la **misma** lección ya documentada en `IntentosLoginService` — y aun así se volvió a cometer.
- **🚨 El Módulo 7 estaba construido pero inerte**: `crearOrganizacion()` dejaba toda PJ en `REVISION_PENDIENTE`/`INACTIVO`, y `resolverOrganizacionHabilitada()` exige `APROBADO`/`ACTIVO` para publicar bajo una organización — pero **ningún código escribía nunca `APROBADO`**. La única vía era el Módulo 6, descartado. Consecuencia en cadena: ninguna organización podía publicar, y la firma corporativa de **RF-7.4 quedaba inalcanzable** (`EventoMapper.firmaOrganizador()` caía siempre en nombre + apellido). Detectado el 2026-09-09 al revisar el código contra los RF; resuelto a nivel de especificación por ADR-14. **Lección:** una máquina de estados sin transición de salida es un requisito a medio implementar, no un requisito cumplido — el gating se escribió sin que ningún RF lo pidiera, y nadie verificó que existiera quien lo levantara.
- **El estado de revisión no tenía respaldo documental**: el gating `REVISION_PENDIENTE`/`INACTIVO` se inventó en `CLAUDE.md` y se implementó en código, pero RF-7.2 **nunca lo mencionó**. Es el espejo exacto de la divergencia RF-1.4: allá el documento pedía algo que el código no hacía; acá el código hacía algo que el documento nunca pidió.
- **El DFD 7.3 tiene dos nodos mal rotulados** (7.3.6A y 7.3.6B): implementarlos literal rechazaría todo CUIT terminado en 0. Se implementó el algoritmo estándar y hay un test que fija el criterio. **Falta corregir el diagrama.**

### ⏭️ Próximos Pasos (Backlog)
1. Aplicar la migración en todo entorno con base existente.
2. ✅ ~~Resolver la divergencia RF-1.4~~ — **cerrada el 2026-09-09** por ADR-13. `modulo_1.md` y `docs/diagrams/login_registro/login.md` reescritos. **Queda pendiente alinear el código**: `AuthService`/`IntentosLoginService` siguen ejecutando la escalera 3/6/9 y `/unlock-account` no existe todavía.
3. ⏳ Corregir los rótulos de los nodos 7.3.6A/B del DFD 7.3 — *en revisión por el usuario.*
4. Frontend de Sprint 2 con los diseños de Figma — *diferido; el registro (paso 1 embebido en paso 2) ya está hecho, quedan correcciones de front.*
5. ⏸️ ~~Módulo 6 (panel de administración)~~ — **descartado por ahora.** Se priorizan CRUD de eventos e inscripciones. Consecuencia asumida: aprobar una organización sigue requiriendo SQL manual, y **un evento que la moderación no marca se publica directamente**.
6. **RF-2.7 / el UPDATE de eventos — hueco real del CRUD.** Verificado el 2026-09-09: `grep -rn "PutMapping\|PatchMapping"` devuelve **cero resultados en toda la aplicación**. El CRUD de eventos tiene C (`POST`), R (`GET` dashboard + catálogo público) y D (`DELETE`, baja lógica), pero **no tiene U**. El estado `EN_REVISION` ya está sembrado. **Es el próximo trabajo de código.**
8. **Alinear el código con ADR-14**: `PersonaJuridicaService.crearOrganizacion()` sigue escribiendo `REVISION_PENDIENTE`/`INACTIVO`. Hay que cambiar las dos búsquedas de catálogo por `APROBADO`/`ACTIVO`, ajustar el mensaje de respuesta y el email de alta, y actualizar los tests de `PersonaJuridicaServiceTest` que fijan el estado inicial.
9. **Alinear el código con ADR-13**: `AuthService`/`IntentosLoginService` siguen con la escalera 3/6/9, y `/unlock-account` no existe.
7. ✅ ~~Mitigar el password spraying~~ — **cubierto por ADR-13**: el bloqueo al 3° fallo por cuenta, sin oráculo de enumeración (respuesta indistinguible en cuerpo, cabeceras y tiempo, también para el pedido de recuperación), le quita al *spraying* tanto el margen de intentos como la señal que necesita para saber qué cuentas existen.

**Orden de trabajo acordado (2026-09-09):** CRUD de eventos → inscripciones → moderación con IA. El panel de administración y el frontend van después.

---

## Sprint 1 — Backend: Autenticación MVP + Modelo de Datos

**Estado:** ✅ Completado
**Inicio:** 2026-07-25

### 🎯 Objetivos del Sprint
- Definir arquitectura backend (Controller → Service → DTO → Repository).
- **Codificar el modelo de datos completo (entidades JPA) según DER/MER.** ✅
- Implementar registro y login con seguridad (rate limiting, bloqueo, JWT, BCrypt).
- Testing con JUnit 5 + Mockito.

### ✅ Entregables Completados
| Fecha | Entregable | Detalle |
|-------|-----------|---------|
| 2026-07-26 | **Modelo de datos JPA** | 37 entidades + 2 clases `@Embeddable` de clave compuesta, mapeadas 1:1 con el MER. Ver [sprint_1/2026-07-26_modelo_datos.md](./sprint_1/2026-07-26_modelo_datos.md) |
| 2026-07-26 | **Base de datos `enexia`** | Creada en MariaDB (XAMPP). 37 tablas generadas por Hibernate con todas las FK. |
| 2026-07-31 | **Repositories + DTOs (esqueleto Fase 2)** | 37 `JpaRepository` + 10 DTOs Request/Response. Ver [REGISTRO_ACADEMICO_FASE2_REPOSITORIES_DTOs.md](./REGISTRO_ACADEMICO_FASE2_REPOSITORIES_DTOs.md) |
| 2026-08-04 | **DFD Nivel 1 y 2 — Módulos 3 a 8** | 26 diagramas Mermaid (Participación, Interfaz Pública, Moderación, Admin, Perfiles Organización, Membresías) en `docs/tempDFD/`, validados con `check-rules`. Ver [REGISTRO_ACADEMICO_DFD_MODULOS_3-8.md](./REGISTRO_ACADEMICO_DFD_MODULOS_3-8.md) |
| 2026-08-12 | **Revisión y Corrección DFD Módulo 3** | 5 diagramas revisados; corrección transversal RBAC (JWT válido + usuario activo, no "rol participante"). Diagramas promovidos a `docs/diagrams/modulo_3_participacion/`. Ver [REGISTRO_ACADEMICO_REVISION_DFD_MODULO3_20260812.md](./REGISTRO_ACADEMICO_REVISION_DFD_MODULO3_20260812.md) |
| 2026-08-12 | **Implementación: JWT Multi-Roles Architecture** | Usuario.java: agregada relación @OneToMany + método getRoles(). UsuarioLoginResponse.java: agregado campo roles[]. CLAUDE.md: documentación actualizada. BD ya estaba preparada. Ver [REGISTRO_ACADEMICO_JWT_MULTIROLES_20260812.md](./REGISTRO_ACADEMICO_JWT_MULTIROLES_20260812.md) |

### 💡 Decisiones de Arquitectura (ADR)
- **ADR-01 — PKs numéricas como `Long`**: El MER indica `int`, pero se usa `Long` con `@GeneratedValue(IDENTITY)` (práctica estándar Java/JPA y consistente con el ejemplo de `CLAUDE.md`).
- **ADR-02 — Estados como entidades relacionadas, NO enums**: El MER normaliza los estados en tablas propias (`usuario_estado`, `evento_estado_sistema`, etc.) con FK. Se respetó el MER en lugar del enum sugerido en el borrador de `CLAUDE.md`.
- **ADR-03 — Relaciones 1:1 con PK compartida vía `@MapsId`**: `persona_fisica`→`persona` y `evento_detalle`→`evento` comparten PK/FK exactamente como el MER (`id_persona PK, FK`).
- **ADR-04 — Tablas de unión como entidades con `@EmbeddedId`**: `usuario_rol` y `miembros_organizacion` usan clave compuesta (esta última con atributo extra `rol_en_empresa`), fiel al MER.
- **ADR-05 — `Pago` con FK opcionales `@OneToOne`**: Las relaciones `Inscripcion ||--o| Pago` y `Suscripcion ||--o| Pago` (1:0..1) se modelan como `@OneToOne` en el lado `Pago`.
- **ADR-06 — Solo lado propietario (`@ManyToOne`)**: Cada relación se mapea una vez, en el lado que posee la FK. No se agregaron colecciones inversas `@OneToMany` (se añadirán cuando un caso de uso las requiera).
- **ADR-07 — `spring.jpa.hibernate.ddl-auto=update`** en desarrollo (genera/actualiza tablas sin borrar datos). Cambiar a `validate` en producción.

### ⚠️ Bloqueos / Lecciones Aprendidas
- **Servidor MySQL apagado**: El servicio MariaDB de XAMPP no estaba corriendo; el usuario lo inició manualmente. Root sin contraseña (default XAMPP).
- **Normalización de nombres camelCase**: El MER escribió `emailCorporativo` y `telefonoContacto` en camelCase (inconsistente con el resto). La estrategia de nombres físicos de Hibernate los normalizó a `email_corporativo` / `telefono_contacto`, quedando consistentes con las otras 36 tablas. **Desviación menor documentada.**
- **✅ Gap de login RESUELTO (opción A)**: El MER de `Usuario` no incluía los campos de control del login. Decisión del usuario: **actualizar primero la documentación**. Se agregaron al MER (`docs/diseño_bd/MER.md`) y a la entidad `Usuario`: `intentos_fallidos` (int), `requiere_captcha` (boolean), `fecha_desbloqueo_cooldown` (datetime). `fecha_registro` NO se agregó a `Usuario` porque ya existe en `Persona` (normalizado). Columnas verificadas en la BD tras `bootRun`.

### 📌 Resumen de Sesión
| Fecha | Evento |
|-------|--------|
| 2026-08-12 | **Sesión Mañana:** Revisión DFD M3 + JWT Multi-Roles Architecture. Ver [RESUMEN_SESION_20260812.md](./RESUMEN_SESION_20260812.md) |
| 2026-08-12 | **Sesión Tarde:** Verificación código roles ✅, Análisis M4 (4 DFDs) + Correcciones, Limpieza tempDFD ✅, Hallazgo M5.1. Ver [REGISTRO_ACADEMICO_REVISION_M4_20260812_TARDE.md](./REGISTRO_ACADEMICO_REVISION_M4_20260812_TARDE.md) |
| 2026-08-12 | **Sesión Noche:** Análisis sincronía moderación, Alineación doc ↔ Figma, Cambio M2.2/M5.1/M5.4 a asíncrona ✅. Ver [RESOLUCION_SINCRONIZACION_MODERACION_20260812.md](./RESOLUCION_SINCRONIZACION_MODERACION_20260812.md) |

### ⏭️ Próximos Pasos (cerrados en Sprint 2)
1. ✅ ~~Decidir sobre los campos de seguridad faltantes en `Usuario`~~ — Resuelto (opción A: MER + entidad actualizados).
2. ✅ ~~Crear capa `repository/`~~ — Completada.
3. ✅ ~~Fase 2: Registro de usuarios~~ — Persona Física en Sprint 1, Persona Jurídica en Sprint 2.
4. ✅ ~~Fase 3: Login seguro~~ — Reescrito en Sprint 2 con bloqueo silencioso (ADR-09).
5. ✅ ~~Fase 4: Testing~~ — 165 tests + 245 aserciones.

---
