# D7 — Módulo 7: Perfiles de organización y equipos

> **Parte D · Los módulos.** Una **empresa o institución** quiere publicar eventos **a su nombre**. Este módulo modela la **Persona Jurídica** como un **contenedor administrativo** (no una cuenta), valida su **CUIT**, la vincula a **personas humanas** mediante una tabla intermedia y resuelve la **firma pública** del evento. Es el módulo donde el **modelado de datos** pesa más que el código: una decisión de diseño (no usar herencia) evita toda una clase de errores.

**Requisitos que cubre** (`requisitos_funcionales/modulo_7.md`)

| RF | Resumen |
|---|---|
| RF-7.1 | Registro de un organizador independiente (persona física). |
| RF-7.2 | **Alta de una organización** (Persona Jurídica), por dos puntos de entrada. |
| RF-7.3 | Validación del **CUIT** (formato + dígito verificador, módulo 11). |
| RF-7.4 | **Firma del organizador** en la ficha pública (persona vs organización). |
| (equipo) | "Mi Equipo": agregar/quitar miembros. *No tiene RF formal*: son decisiones de producto documentadas en el código. |

DFD: [`modulo_7_perfiles_organizacion/`](../diagrams/modulo_7_perfiles_organizacion/nivel1_perfiles_organizacion.md) (movidos desde `tempDFD` el 2026-10-19 por estar implementados).

---

## 1. El modelo: por qué **no** hay herencia

```
Usuario ──1:1── Persona_Fisica ── Persona        (la persona HUMANA: único origen de un login)
   │
   └──< Miembros_Organizacion >── Persona_Juridica   (el CONTENEDOR: razón social, CUIT, estados)
         (id_usuario, id_persona_juridica,                │
          rol_en_empresa: ADMINISTRADOR | MIEMBRO)        ├── Ubicacion (domicilio fiscal)
                                                          ├── Persona_Juridica_Estado_Sistema  (APROBADO ...)
                                                          ├── Persona_Juridica_Estado          (ACTIVO / INACTIVO)
                                                          └── Historial_Estado_Persona_Juridica
Evento ──> id_organizador (SIEMPRE una persona)  +  id_persona_juridica (OPCIONAL: "a nombre de")
```

> **El principio que ordena todo el módulo:** *una Persona Jurídica no es una identidad de acceso*. No tiene usuario, no tiene contraseña, no se inscribe a eventos. Existe **únicamente** para que un evento se publique a nombre corporativo, mientras **la responsabilidad sigue siendo de una persona humana concreta**.

### 1.1 La decisión del 2026-09-08 (modelado, no código)

El diseño original sugería una jerarquía `Persona → (Física | Jurídica)` con una columna `tipo_persona`. Se descartó (A02 §12):

- La jerarquía **nunca existió en el MER**: `Persona_Juridica` no tiene FK a `Persona`.
- La columna valía `"FISICA"` en **el 100 %** de las filas: no discriminaba nada.
- Admitir `"JURIDICA"` habría permitido **crear una persona jurídica sin persona física detrás**, rompiendo el invariante *"todo `Usuario` es un ser humano"* (y habilitando el **"login de empresa"**).

La relación correcta entre una empresa y las personas es **"tiene miembros"** (composición/asociación), no "es una persona" (herencia).

### 1.2 La tabla intermedia con atributo propio: `Miembros_Organizacion`

Es la relación **N:M** entre usuarios y organizaciones, con un atributo propio (`rol_en_empresa`). En JPA se mapea con una **clave compuesta embebida** y `@MapsId` (B03 §2.5.1):

```java
@Entity
@Table(name = "miembros_organizacion")
public class MiembrosOrganizacion {
    @EmbeddedId
    private MiembrosOrganizacionId id = new MiembrosOrganizacionId();

    @ManyToOne(fetch = FetchType.LAZY) @MapsId("idUsuario")
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY) @MapsId("idPersonaJuridica")
    @JoinColumn(name = "id_persona_juridica")
    private PersonaJuridica personaJuridica;

    @Column(name = "rol_en_empresa")
    private String rolEnEmpresa;      // "ADMINISTRADOR" o "MIEMBRO" (texto, sin catálogo propio)
}
```

---

## 2. RF-7.3 — Validación del CUIT: `ValidadorCuit`

La pieza más "algorítmica" del proyecto (código completo en A01 §6, `util/ValidadorCuit.java`).

**El algoritmo (módulo 11):**
1. El CUIT son **11 dígitos**: 2 de tipo + 8 del DNI + 1 verificador.
2. Se multiplican los 10 primeros por `5,4,3,2,7,6,5,4,3,2`.
3. Se suman los productos; **resto** de dividir por 11.
4. Verificador esperado = `11 - resto`, con dos ajustes: si da **11 → 0**; si da **10 → 9**.
5. Se compara con el último dígito.

**Ejemplo** `30-71659554-0`: `3·5 + 0·4 + 7·3 + 1·2 + 6·7 + 5·6 + 9·5 + 5·4 + 5·3 + 4·2 = 198`; `198 % 11 = 0`; `11 - 0 = 11 → 0`; el último dígito es `0` ✔.

**Una discrepancia documentada** (excelente ejemplo de **contradecir un diagrama con razón**): el DFD 7.3 decía literalmente *"Resto = 11 → dígito 0"* y *"Resto = 0 → dígito 9"*. Ninguna se puede leer al pie de la letra: **un resto módulo 11 nunca vale 11**, y con resto 0 el verificador correcto es **0**, no 9; implementarlo tal cual **rechazaría todo CUIT terminado en 0** (como el del ejemplo, que es válido). El diagrama describía la **variable intermedia** (`11 - resto`), no el resto. Se implementó el algoritmo estándar y **se documentó** (y hay un test para el caso crítico: B06 §2.1).

**Detalles de implementación:**
- `normalizar`: `replaceAll("[^0-9]", "")` → acepta `30-71659554-0`, `30 71659554 0` o `30716595540`. Normalizar **antes de comparar y persistir** evita que la misma organización se registre dos veces con formatos distintos (el `UNIQUE` compararía cadenas).
- `digitos.charAt(i) - '0'` en vez de `Character.getNumericValue`: este último acepta **dígitos Unicode de otros alfabetos** (árabe-índico) que romperían más adelante; la resta de `'0'` solo funciona con ASCII, *"que es exactamente lo que se quiere"*.
- Es una **clase de utilidad** (constructor `private`, `final`, métodos `static`): sin estado, sin Spring, **testeable en milisegundos**.
- `formatear(cuit)` → `30-71659554-0` **solo para mostrar**; lo que se persiste son los 11 dígitos.

> **Limitación asumida (ADR-14):** el módulo 11 es **aritmético, no probatorio**: prueba que el CUIT está *bien formado*, **no** que exista una entidad detrás. Un CUIT inventado cuyo dígito "cierre" será aceptado. Verificarlo contra el padrón de AFIP/ARCA exige clave fiscal y certificado digital; no hay servicio público, gratuito y estable.

---

## 3. RF-7.2 — Alta de organización: dos entradas, un solo método

```
Entrada A (pública):    POST /api/auth/registro/organizacion        (cuenta + organización en UN paso)
                         AuthService.registrarConOrganizacion()  ─┐
Entrada B (autenticada): POST /api/organizador/organizaciones      │   ambas terminan en el MISMO método
                         OrganizacionController.crear()          ──┤
                                                                    ▼
                                       PersonaJuridicaService.crearOrganizacion(usuario, peticion, request)
```

> *"Separate flow" significaba una **entidad** distinta, no un **momento** distinto*: el alta de la organización es un proceso con sus validaciones, **independientemente de cuándo** la persona lo ejecute. Por eso **ambas** entradas son legítimas y **comparten un único método de negocio**: así *ninguna regla puede divergir* entre los dos caminos.

### 3.1 Los controles, **en este orden** (importa)

1. **Rol:** debe ser `ORGANIZADOR` (barato, corta antes que nada).
2. **CUIT:** formato + dígito verificador (**puro cálculo, sin base**).
3. **CUIT único** (una consulta: `existsByCuit`).
4. **Moderación** de razón social y nombre de fantasía (**antes de persistir nada**; son la firma pública).
5. **Recién entonces, las escrituras**.

(Es el mismo criterio que el registro: *lo barato primero; la moderación siempre antes de la primera escritura*.)

### 3.2 Escribir cuatro tablas encadenadas, en **una** transacción

`ubicacion` → `persona_juridica` → `historial_estado_persona_juridica` → `miembros_organizacion`. `@Transactional` es obligatorio: *"si fallara la última sin transacción, quedaría una empresa registrada **sin ningún miembro**: nadie podría administrarla ni darla de baja, y su **CUIT quedaría tomado para siempre**"*. **Atomicidad** (B03 §6.1) aplicada a un caso concreto.

El **fundador** queda como `ADMINISTRADOR` en `miembros_organizacion`; sus **estados** (`APROBADO`/`ACTIVO`) son de la **organización**, **no** del usuario: la cuenta personal queda `ACTIVO` desde el primer momento.

### 3.3 Nace aprobada y activa (ADR-14, 2026-09-09)

El diseño original dejaba la organización en `REVISION_PENDIENTE` + `INACTIVO` hasta que un administrador verificara el CUIT. **Se eliminó** — y la razón es una lección de diseño de **máquinas de estados**:

> *"Ningún código escribía nunca APROBADO. La única vía era el Módulo 6, que quedó fuera de alcance. Resultado: `resolverOrganizacionHabilitada()` rechazaba SIEMPRE, ninguna organización podía publicar jamás, y la firma corporativa de RF-7.4 era inalcanzable: **una máquina de estados sin transición de salida**."*

Entre *sostener una puerta sin llave* y *resolver con el único control disponible* (el módulo 11), se eligió lo segundo. Los estados `REVISION_PENDIENTE` y `RECHAZADO` **siguen en el catálogo** y el historial **se sigue escribiendo** desde el alta: son el punto de partida para una futura suspensión desde el panel.

### 3.4 Alta sin correo

*"Un mail que solo dijera 'se creó' no aporta nada que el usuario no esté viendo en pantalla."* (Decisión de producto.)

---

## 4. Publicar "a nombre de": `resolverOrganizacionHabilitada`

Al crear o editar un evento con `idPersonaJuridica`, `EventoService` pide a `PersonaJuridicaService` resolver la organización y comprobar que **el usuario es miembro** y que está **habilitada**. Sin `idPersonaJuridica`, el evento es **a título personal**. La **responsabilidad y los permisos de edición** siguen siendo del usuario (`Evento.organizador`), aunque la **firma** sea la de la organización.

---

## 5. RF-7.4 — La firma pública

En la ficha, `EventoMapper.firmaOrganizador(evento)` decide qué mostrar:

| Evento | Firma |
|---|---|
| A título personal | **Nombre y Apellido** del organizador. |
| Bajo una organización | **Nombre de fantasía** si existe; si no, **razón social**. |

La firma se **resuelve al leer** (no se copia como texto en el evento): *"un cambio de razón social dejaría los eventos viejos con el nombre anterior"*. Es **normalización** en acción (B03 §1.4). (Y, en la ficha, se **escapa** con `UI.esc`: la razón social también la escribió una persona: C02 §12.)

---

## 6. "Mi Equipo" — `MiembrosOrganizacionService`

Sin RF formal, con las **decisiones de producto** documentadas en el javadoc:

| Regla | Detalle |
|---|---|
| **Quién puede** | Solo un `ADMINISTRADOR` **de esa organización** agrega o quita. **Cualquier miembro** puede **ver** el equipo (no es información sensible dentro del equipo). |
| **A quién** | Solo una **cuenta activa existente** (se busca por **email**). **No hay invitaciones** por correo. |
| **No duplicar** | Esa persona no puede ser ya miembro (`RecursoDuplicadoException`). |
| **Nunca sin administrador** | **No se puede quitar al último administrador** (`contarAdministradores(...) <= 1` → `ReglaNegocioException`). Un administrador **sí** puede quitarse a sí mismo si hay otro. |
| **404, no 403** | Una organización ajena responde *"No se encontró la organización indicada entre las tuyas"*: no se puede **sondear** la existencia de organizaciones ajenas. |
| **Roles** | Solo `ADMINISTRADOR` o `MIEMBRO` (texto libre en `rol_en_empresa`, validado con `@Pattern`; sin tabla de roles jerárquicos). |

**Dos autorizaciones distintas → dos métodos auxiliares:** `exigirMembresia` (¿es miembro?) y `exigirAdministrador` (¿es **administrador de esa** organización?). El servicio está **separado de `PersonaJuridicaService`** (A02 §13, SRP): *crear* una organización no exige pertenecer a nada; *gestionar su equipo* exige ser administrador de esa en particular.

**Aviso al agregado:** `emailService.enviarAgregadoAEquipo(...)` ("Te sumaron al equipo de ...") **después del commit**; **quitar no avisa** (DFD N.4, D9).

### 6.1 Pantalla `pages/organizador/equipo.html`

Lista el equipo, permite **agregar por email** y **quitar** (con confirmación). Escapa los textos con una función local `escapar` (C02 §12). Maneja los errores del servidor (`403`/`404`/`409`) con toasts: `UI.mensajeDeError(r).replace(/<[^>]+>/g, '')` **quita las etiquetas HTML** del mensaje antes de mostrarlo como texto plano.

---

## 7. Pantallas del organizador relacionadas

- `pages/organizador/perfil.html`: perfil personal + **tarjeta de organización** (razón social, CUIT, correo, teléfono). **Solo lectura**: la edición de organizaciones **no tiene endpoint** (anotado en el javadoc de `PerfilService`).
- `evento-form.html`: **no tiene** un selector "Publicar como" (a título personal / a nombre de una organización): el backend acepta `idPersonaJuridica` en `EventoCrearRequest`, pero el formulario **no lo envía** (ver §9).

---

## 8. Pruebas

| Clase | Qué protege |
|---|---|
| `ValidadorCuitTest` | Casos **reales** (parametrizado), CUIT terminados en 0, con guiones, longitudes, caracteres no numéricos. |
| `PersonaJuridicaServiceTest` | Orden de controles; solo `ORGANIZADOR`; CUIT inválido/duplicado; moderación **antes** de escribir; fundador = `ADMINISTRADOR`; historial inicial. |
| `MiembrosOrganizacionServiceTest` | Solo administrador gestiona; no duplicar; **nunca el último administrador**; ajena = 404; el correo. |
| `OrganizacionApiTest` | De punta a punta con la base real. |

---

## 9. Límites y pendientes

1. **La pantalla de registro de organizaciones no está conectada**: `register-paso2.html` deshabilita la opción ("*Registro de organizaciones en preparación*") aunque `POST /api/auth/registro/organizacion` **ya funciona**. Falta `API.registroOrganizacion` y habilitar el botón. Hoy una organización solo se crea por la API (o por el endpoint autenticado).
2. **El formulario de evento no ofrece publicar a nombre de una organización** (el backend sí lo soporta con `idPersonaJuridica`); y no hay pantalla para **crear** una organización. Juntos, hoy el flujo de organizaciones **solo es utilizable por API**.
3. **Verificación real del CUIT** (padrón AFIP/ARCA) pendiente (§2).
4. **No hay edición ni baja de organizaciones** desde la aplicación; tampoco suspensión desde el panel admin.
5. **No hay invitaciones** ni roles jerárquicos en los equipos.
6. **El plan de suscripción es del usuario**, no de la organización (D8, decisión D6).

---

## 10. Conceptos aplicados

| Concepto | Dónde |
|---|---|
| Composición contra herencia (modelado) | A02 §12, §1.1 |
| Clave compuesta `@EmbeddedId` + `@MapsId` | B03 §2.5.1 |
| Clase de utilidad con algoritmo + tests parametrizados | A01 §6, B06 §2.1 |
| `normalizar` antes de comparar/persistir | A04 §2.4 |
| Una transacción para varias tablas | B03 §6 |
| Un método de negocio, dos puntos de entrada | §3 |
| 404 en vez de 403 | B04 §7.5 |
| Máquina de estados sin salida | §3.3 |

---

## 11. Para practicar

1. Calculá **a mano** el dígito verificador de `20-12345678-?` y comprobá con `ValidadorCuit.esValido`.
2. Un usuario es `ADMINISTRADOR` de una organización con dos administradores. Se quita a sí mismo, y luego el otro intenta quitarse. Trazá las dos llamadas: ¿cuál da error y por qué?
3. Dibujá el modelo **si** se hubiera usado herencia (`Persona → Física | Jurídica`) y marcá **un caso** que permitiría y que hoy es imposible.
4. Escribí el JSON de `OrganizacionRegistroRequest` (mirá sus validaciones, incluido `domicilioFiscal` con `@Valid` anidado) y el de la respuesta.
5. Diseñá (en papel) la "edición de razón social" respetando: moderación, auditoría, solo administrador, y qué pasa con la firma de los eventos ya publicados.

**Siguiente capítulo:** [D8 — Módulo 8: Membresías y estadísticas](./D8_modulo_8_membresias.md)
