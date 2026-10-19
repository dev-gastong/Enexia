# D1 — Módulo 1: Gestión de usuarios y autenticación

> **Parte D · Los módulos, uno por uno.** Los capítulos A, B y C enseñaron las **herramientas**. Acá se **aplican** a un módulo real, de punta a punta: requisitos → modelo de datos → capas del backend → API → pantallas → pruebas. Cada vez que aparezca un concepto nuevo para el módulo, se indica **en qué capítulo se explica**.
>
> **Módulo 1** responde a: *¿quién es esta persona, y qué puede hacer?* Es el cimiento: sin cuentas, sesiones y roles no hay nada más.

**Requisitos que cubre** (`docs/requisitos/requisitos_funcionales/modulo_1.md`)

| RF | Resumen |
|---|---|
| RF-1.1 | Registro de Persona Física (cuenta + roles). |
| RF-1.2 | Inicio de sesión por **nickname** y emisión de JWT. |
| RF-1.3 | Control de acceso por rol (RBAC). |
| RF-1.4 | Bloqueo automático y **silencioso** al 3.º intento fallido. |
| RF-1.5 | Restablecimiento de contraseña por correo. **No desbloquea** (decisión 2026-10-19). |
| RF-1.6 | Solo las cuentas `ACTIVO` pueden entrar; borrado lógico. |
| Perfil | "Mi perfil": ver y editar nombre, apellido y fecha de nacimiento. |

DFD: [`docs/diagrams/login_registro/login.md`](../diagrams/login_registro/login.md) y [`registro.md`](../diagrams/login_registro/registro.md); recuperación: [N.1](../diagrams/notificaciones/N.1_recuperacion_password.md).

---

## 1. Modelo de datos

```
Persona ──1:1── Persona_Fisica ──1:1── Usuario ──N:M── Rol   (por Usuario_Rol)
   (id, fecha_registro)  (dni, nombre,        │   (email, nickname, password,
                          apellido, nacim.)   │    intentos_fallidos, fecha_baja)
                                              ├── Usuario_Estado         (ACTIVO, BLOQUEADO, SUSPENDIDO, DE_BAJA)
                                              ├── Usuario_Estado_Sistema
                                              ├── Historial_Estado_Usuario   (cada cambio de estado)
                                              ├── Password_Reset_Token       (hash del token de recuperación)
                                              └── Historial_Interacciones    (auditoría de acciones)
```

| Tabla / entidad | Detalle de diseño |
|---|---|
| `persona` + `persona_fisica` | **Identidad civil.** `PersonaFisica` comparte la clave primaria con `Persona` mediante `@OneToOne` + `@MapsId` (B03 §2.5.1): `persona_fisica.id_persona` es a la vez PK y FK. Se eliminó la columna `tipo_persona` (decisión 2026-09-08): *una persona jurídica NO es una persona humana*. |
| `usuario` | **La cuenta.** Dos `UNIQUE` con nombre explícito: `uk_usuario_email`, `uk_usuario_nickname`. El `password` guarda el **hash BCrypt**. |
| `usuario_rol` | Tabla intermedia con **clave compuesta** (`UsuarioRolId`, con `equals`/`hashCode`: A02 §9). |
| `usuario_estado` | **Catálogo** de estados; el `enum EstadoUsuarioNombre` es solo el vocabulario (A02 §7.1). |
| `historial_estado_usuario` | Un renglón por cada cambio de estado (quién, cuándo). |
| `password_reset_token` | Guarda el **SHA-256 del token**, no el token (B04 §6). |
| `historial_interacciones` | Auditoría: acción, endpoint, IP, user-agent, fecha. |

**Dos identificadores, dos funciones** (decisión 2026-09-30): el **nickname** es lo que la persona *escribe* para entrar; el **email** es el *canal de contacto* (recuperación, avisos de seguridad) **y el `subject` del JWT**. Por eso el email **sigue siendo único e interno** aunque ya no se use para el login.

**Roles:** `PARTICIPANTE`, `ORGANIZADOR`, `ADMINISTRADOR`. Un organizador recibe **los dos** (`ORGANIZADOR` + `PARTICIPANTE`) para poder inscribirse a eventos ajenos sin una segunda cuenta; un administrador es **solo** administrador (`Usuario.getRoles()`, A02 §1.2).

---

## 2. Registro (RF-1.1)

### 2.1 Del formulario al backend: la ruta completa

```
register-paso2.html ── API.registro(datos) ──► POST /api/auth/registro
                                                    │
                  AuthController.registrar(@Valid @RequestBody UsuarioRegistroRequest)
                                                    │  (si @Valid falla → 400 con errores por campo)
                  AuthService.registrar(...)   @Transactional
                                                    │
        ┌───────────────────────────────────────────┴───────────────────────────────┐
        │ crearCuentaPersonaFisica():                                                │
        │  1. password == passwordConfirmacion       (regla de negocio, no de @Valid)│
        │  2. unicidad: email, nickname, DNI         (RecursoDuplicadoException 409) │
        │  3. moderación de texto: nickname, nombre, apellido  (ANTES de persistir)  │
        │  4. BCrypt(password)                                                       │
        │  5. INSERT persona → persona_fisica → usuario                              │
        │  6. INSERT usuario_rol  (ORGANIZADOR ⇒ también PARTICIPANTE)               │
        │  7. auditoría + log                                                        │
        └────────────────────────────────────────────────────────────────────────────┘
                                                    │
                     201 Created + UsuarioRegistroResponse (sin token: registrarse no autentica)
```

### 2.2 El DTO de entrada (`UsuarioRegistroRequest`)

Cada campo trae sus validaciones (B02 §5):

```java
@NotBlank @Email @Size(max = 150)
private String email;

@NotBlank @Size(min = 3, max = 20)
@Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "El nickname solo admite letras, numeros y guion bajo")
private String nickname;

@NotBlank @Size(min = 8, max = 72)
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "...")
private String password;
```

(Las regex, explicadas una por una en A04 §2.4.) Incluye también `nombre` y `apellido` (`^[\p{L} '-]+$`: letras Unicode, espacios, apóstrofes y guiones), `dni` (`^\d{7,8}$`), `fechaNacimiento` (`@NotNull @Past`) y `perfil` (`^(PARTICIPANTE|ORGANIZADOR)$`: **un cliente no puede registrarse como `ADMINISTRADOR`**, mass assignment evitado).

### 2.3 El servicio, con las decisiones comentadas

Los puntos más instructivos de `AuthService.crearCuentaPersonaFisica`:

1. **Una transacción para todo.** `registrar` es `@Transactional`: se escriben cuatro tablas (`persona`, `persona_fisica`, `usuario`, `usuario_rol`) **o ninguna**. Si fallara en la última, no queda una cuenta sin roles (B03 §6).
2. **Unicidad en dos niveles.** El servicio hace `existsByEmailIgnoreCase(...)` para dar un **mensaje amable**, **pero** la defensa real son los `UNIQUE` de la base: dos altas simultáneas pasan ambas el chequeo y solo el índice frena a la segunda, que `GlobalExceptionHandler` traduce igualmente a `409` (B03 §7.2).
3. **El DNI identifica a la persona; email y nickname, a la cuenta.** Sin controlar el DNI, alguien abriría cuentas ilimitadas cambiando solo el email, y una suspensión no serviría de nada.
4. **Moderar antes de persistir.** *"Si se guardara primero, un nickname ofensivo quedaría en la base aunque después se rechace el registro."* Si la moderación rechaza, se escribe la auditoría con `registrarAparte` (otra transacción, `REQUIRES_NEW`) porque **el registro se revierte** al propagarse la excepción y el rechazo tiene que quedar asentado igual.
5. **`LinkedHashSet`** para los roles: conserva el orden, así el rol elegido aparece primero en la respuesta y en el JWT.
6. **`usuario.setUsuarioRoles(rolesPersistidos)` a mano:** la entidad recién creada no recarga sus colecciones; sin esto `getRoles()` devolvería vacío dentro de la misma transacción.
7. **Normalización:** `email.trim().toLowerCase()`, `nickname.trim()`.

### 2.4 Pantalla de registro (`pages/auth/register-paso2.html`)

- Dos "tipos" de cuenta (personal / organización) con un selector. El botón **de la organización está deshabilitado**: el aviso dice *"Registro de organizaciones en preparación"*. **El backend ya lo soporta** (`POST /api/auth/registro/organizacion`), pero **la pantalla no está conectada** (ver §8).
- `armarPeticion()` construye el objeto con **los nombres exactos del DTO**: *"si un nombre no coincide, Jackson deja el campo en null y la validación lo rechaza con un mensaje que no explica nada"* (C02 §9.1).
- Éxito (`201`): se guarda el resumen en `sessionStorage` y se redirige a `register-completado-pf.html`. **No hay token**: registrarse no inicia sesión.
- Errores `400`: `UI.pintarErrores(respuesta.cuerpo.errores)` marca cada input por `id`; el primer inválido recibe el foco y `scrollIntoView`.

---

## 3. Inicio de sesión (RF-1.2) y diseño defensivo

### 3.1 El flujo

```
login-desktop-claro.html ── API.login({nickname, password}) ──► POST /api/auth/login
          AuthService.login()   (SIN @Transactional, a propósito)
  1. buscarActivoPorNicknameConRoles(nickname)
        └─ no existe → nivelarTiempoDeRespuesta()  →  CredencialesInvalidasException(NICKNAME_INEXISTENTE)
  2. verificarEstadoHabilitado(): si no es ACTIVO → BLOQUEADO / SUSPENDIDO / DE_BAJA → misma respuesta
  3. passwordEncoder.matches(...)
        └─ no coincide → procesarIntentoFallido(): contador, ¿bloqueo?, auditoría, correo → excepción
  4. limpiarTrasLoginExitoso(): contador a 0
  5. jwtService.generarToken(email, roles)  →  200 + UsuarioLoginResponse
```

### 3.2 Por qué **no** lleva `@Transactional`

El camino de fallo necesita que el contador de intentos **quede confirmado ANTES de lanzar la excepción**. Si todo el método fuera una transacción, esa excepción la **revertiría** y el contador nunca avanzaría. Las escrituras las hacen `IntentosLoginService` y `AuditoriaService`, cada una con `REQUIRES_NEW` (B03 §6.5).

### 3.3 Las cuatro defensas del login (resumen de B04 §5)

| Defensa | Cómo |
|---|---|
| **Respuesta indistinguible** | Una familia de excepciones (`AutenticacionFallidaException`) con **un solo** `@ExceptionHandler`: 401 + `CREDENCIALES_INVALIDAS` + mismo mensaje + sin cabeceras extra. |
| **Tiempo constante** | `nivelarTiempoDeRespuesta()` paga un BCrypt contra un **hash señuelo** en toda ruta de rechazo. |
| **Bloqueo por cuenta, no por IP** | `IntentosLoginService.registrarFallo`: bloqueo de fila + `REQUIRES_NEW`; al 3.er fallo → `BLOQUEADO`. |
| **Aviso por el único canal seguro** | `notificarBloqueoConEnlaceDeRecuperacion` → correo al titular. |

El **umbral** (`enexia.security.login.intentos-bloqueo=3`) se inyecta con `@Value`. El **desbloqueo** lo hace **solo un administrador** desde su panel (`AdminUsuarioService`, acción `REACTIVAR`): decisión del 2026-10-19; se retiró la rama que lo hacía por correo.

### 3.4 El JWT y el filtro (RF-1.2, RF-1.3)

`JwtService.generarToken(email, roles)` firma con HMAC-SHA256; `JwtAuthenticationFilter` lo verifica en **cada** pedido, comprueba que la cuenta siga `ACTIVO` (ADR-20) y arma las autoridades `ROLE_*`. `SecurityConfig` aplica las reglas por prefijo (`/api/admin/**` → `ADMINISTRADOR`...) con **cierre por defecto** (`anyRequest().authenticated()`). Todo explicado en B04 §3–§4.

### 3.5 Pantalla de login

Ver C02 §8.4: el `submit` se intercepta con `e.preventDefault()`, se deshabilita el botón, se llama `API.login`, se guarda la sesión con `Auth.guardarSesion` y se redirige. **El mensaje de error del cliente es siempre "Credenciales inválidas"**: no hay forma de que la pantalla revele el motivo.

---

## 4. Recuperación de contraseña (RF-1.5)

Detalle completo del servicio en B04 §6 y del DFD en [N.1](../diagrams/notificaciones/N.1_recuperacion_password.md). Resumen de las piezas:

| Pieza | Función |
|---|---|
| `POST /api/auth/recuperacion` | Pide el enlace. **Siempre 202** con el mismo texto (oráculo de enumeración cerrado). |
| `RecuperacionCuentaService.solicitarRestablecimiento` | **Solo encola**; lo que depende de si la cuenta existe corre en otro hilo (B05 §2.3). |
| `reservarConLimite` | 3 enlaces cada 24 h, separados 2 min, **por destinatario**. |
| `emitirToken` | `SecureRandom` de 32 bytes, **solo el SHA-256** en la base, 30 min, un solo token vigente. |
| `POST /api/auth/recuperacion/confirmar` | Fija la contraseña nueva, `intentos_fallidos = 0`, borra el token, avisa por correo. **No toca el estado**. |

### 4.1 Pantallas (2026-10-17)

- `password-reset.html`: pide el correo; al recibir `202` muestra "¡Correo enviado!" y bloquea el reenvío 2 minutos (cuenta regresiva con `setInterval`: espejo del límite del servidor).
- `recuperar.html?token=...`: lee el token con `URLSearchParams`, **lo borra de la barra** con `history.replaceState`, valida la política de contraseña **en vivo** (cuatro reglas que pasan a verde) y llama a `confirmarRecuperacion`. Estados: formulario / enlace no válido / contraseña actualizada.
- **Decisión de producto:** el éxito **no inicia sesión** (el endpoint no devuelve token) y **no desbloquea** (el texto lo aclara: *"si tu cuenta estaba bloqueada, solo un administrador de Enexia puede habilitarla"*).
- Diseño: paleta clara y esquinas cuadradas con `tokens.css` + una hoja compartida `recuperacion.css` (C03 §11).

---

## 5. Estado de cuenta y borrado lógico (RF-1.6)

| Estado | Quién lo pone | Puede iniciar sesión | Cómo se sale |
|---|---|---|---|
| `ACTIVO` | alta / administrador | sí | — |
| `BLOQUEADO` | el sistema (3 fallos) | no | **un administrador** (`REACTIVAR`) |
| `SUSPENDIDO` | un administrador | no | un administrador |
| `DE_BAJA` | el titular | no | nadie (decisión del titular) |

El filtro JWT y el login consultan este estado. `fecha_baja` distingue una cuenta dada de baja (borrado lógico, B03 §3).

---

## 6. Perfil (`/api/usuario/perfil`)

`PerfilService` está **separado de `AuthService` a propósito** (principio de responsabilidad única: A02 §13): uno cubre el *flujo de autenticación*, el otro la *gestión de una cuenta que ya existe*. Solo se editan **nombre, apellido y fecha de nacimiento**: el email y el nickname son identificadores de acceso y el DNI es la identidad civil, así que no se tocan desde acá. Los nombres pasan por moderación de texto; la acción se audita.

---

## 7. Pruebas del módulo

| Clase | Tipo | Qué protege |
|---|---|---|
| `AuthServiceLoginTest`, `AuthServiceRegistroTest` | servicio (Mockito) | reglas del login (estados, contador, bloqueo), unicidad, roles, moderación. |
| `RecuperacionCuentaServiceTest` | servicio | hash no token, un solo uso, mismo mensaje para token inválido, **no desbloquea**, límite por destinatario. |
| `AuthApiTest` | API | registro 201/409, login, recuperación (202 uniforme). |
| `LoginTest`, `RegistroTest` | UI (Playwright) | flujo en el navegador. |
| `CorreosTest` | UI + correo real | login, recuperación y **bloqueo en silencio con aviso por correo**. |

---

## 8. Límites conocidos y pendientes

1. **El registro de organizaciones no está conectado en la pantalla** (el backend sí): falta `API.registroOrganizacion` y habilitar el botón (ver D7).
2. **Los JWT siguen valiendo tras cambiar la contraseña** (`SUGERENCIAS_PENDIENTES.md`, C5).
3. **No hay canal de contacto** para pedirle a la administración un desbloqueo (C2).
4. **Password spraying:** sin alerta por volumen (C7).
5. Un comentario desactualizado en `AuthController.registrarConOrganizacion` menciona `REVISION_PENDIENTE` (etapa eliminada: ADR-14).

---

## 9. Conceptos que se aplicaron (mapa a los capítulos)

| Concepto | Capítulo |
|---|---|
| Jerarquía de excepciones, un solo manejador | A02 §3.1, A04 §1.8 |
| Hash, BCrypt, JWT, filtros | B04 |
| `REQUIRES_NEW`, bloqueo pesimista, `UNIQUE` | B03 §6–§7 |
| DTO y validación, `@Valid` | B02 §4–§5 |
| Correo y trabajo asíncrono | B05 |
| `sessionStorage`, `fetch`, delegación, XSS | C02 |
| Formularios accesibles, tokens CSS | C03 |

---

## 10. Para practicar

1. Seguí en el código **una cuenta que se bloquea**: desde `AuthService.login` hasta el correo. Anotá cuántas transacciones distintas se abren y por qué.
2. Escribí el test (en papel) "un participante no puede registrarse como administrador": ¿lo rechaza `@Pattern`, el servicio o la base? ¿En qué capa lo escribirías?
3. ¿Por qué el servidor responde `201` **sin token** al registrarse? ¿Qué cambiaría si lo devolviera?
4. Agregá (en papel) un campo `telefono` al perfil: ¿qué archivos tocás, desde la base hasta el HTML? (Respondé capa por capa.)
5. Probá con `curl` un login con un nickname inexistente y con uno existente pero mal la contraseña, 10 veces cada uno, midiendo el tiempo (`curl -w "%{time_total}"`): ¿se distinguen?

**Siguiente capítulo:** [D2 — Módulo 2: Gestión de eventos](./D2_modulo_2_eventos.md)
