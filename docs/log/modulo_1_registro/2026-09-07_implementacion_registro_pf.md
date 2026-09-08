# Registro de Persona Física — Cierre del módulo (Sprint 1)

**Fecha:** 2026-09-07
**Módulo:** 1 — Gestión de Usuarios y Autenticación
**Requisito:** RF-1.1 (Registro de Usuarios Guiado y Dinámico)
**DFD de referencia:** [`docs/diagrams/login_registro/registro.md`](../../diagrams/login_registro/registro.md)
**Estado:** ✅ Persona Física operativa de extremo a extremo · ⏸️ Persona Jurídica diferida a Sprint 2

---

## 1. Punto de partida

Antes de esta sesión el registro estaba **partido en dos mitades que no se hablaban**:

| Capa | Estado previo |
|------|---------------|
| `AuthService.registrar()` | Implementado y correcto para PF |
| `POST /api/auth/registro` | Publicado y funcionando |
| `UsuarioRegistroRequest` | Con validaciones `@Valid` completas |
| **Formulario HTML** | **Maqueta** — el `submit` redirigía a la pantalla de éxito sin llamar a la API |

Una auditoría con `/check-rules` (misma sesión) detectó cinco bloqueantes. Este documento
registra la resolución de los que corresponden al alcance de Persona Física.

---

## 2. Cambios realizados

### 2.1 Backend — Unicidad del DNI (bloqueante crítico)

**Problema:** el alta verificaba email y nickname, pero **no el DNI**. Se podían crear
cuentas ilimitadas para la misma persona cambiando solo el correo. El DFD 7.1.2 ya exigía
un `409 DNI ya Registrado` que nunca se había implementado.

**Por qué importa (más allá del duplicado):** email y nickname identifican a la **cuenta**;
el DNI identifica a la **persona**. Sin este control, una suspensión administrativa se
esquiva abriendo otra cuenta con el mismo documento, y toda la moderación de usuarios
queda sin efecto real.

`PersonaFisicaRepository.java` — el repositorio estaba vacío:

```java
/**
 * Unicidad del documento (DFD 7.1.2, "Error 409: DNI ya Registrado").
 * Spring Data deriva el SELECT del nombre del metodo; no hace falta @Query.
 */
boolean existsByDni(String dni);
```

`AuthService.registrar()` — el chequeo se ubica junto a los otros dos, **antes** de la
moderación y de cualquier escritura:

```java
String dni = peticion.getDni().trim();
// ...
if (personaFisicaRepository.existsByDni(dni)) {
    throw new RecursoDuplicadoException("Ya existe una cuenta registrada con ese DNI");
}
```

El `GlobalExceptionHandler` ya mapeaba `RecursoDuplicadoException` → `409 RECURSO_DUPLICADO`,
así que no hizo falta tocar la capa de excepciones.

---

### 2.2 Frontend — Quitar el domicilio personal de Persona Física

**Decisión del usuario, ejecutada primero.** El formulario pedía provincia, ciudad, calle,
número exterior e interior para la persona física.

**Motivo técnico:** el MER **no modela una `Ubicacion` para `Persona_Fisica`**. Solo
`Persona_Juridica` tiene `id_ubicacion` (línea 93 del MER); la relación declarada es
`Ubicacion ||--o{ Persona_Juridica`. El DFD 7.1.3 ("Persistir Identidad Física") tampoco
escribe en el almacén `D1_Ubi`; ese almacén solo aparece en la rama jurídica (7.2.2).

Esos cinco campos no tenían **dónde persistirse**: se le pedían datos al usuario para
descartarlos en el `submit`. Se eliminó la sección completa (58 líneas) y quedó una nota
en el HTML explicando la razón y la condición para reponerla:

```html
<!-- El domicilio personal de la Persona Fisica se quito del
     formulario: el MER no modela una Ubicacion para
     Persona_Fisica (solo Persona_Juridica tiene id_ubicacion),
     asi que esos campos no tenian donde persistirse. Volveran
     cuando se decida agregar la relacion al modelo. -->
```

**Se conservó** el bloque *Domicilio fiscal legal* de la rama PJ: ese sí tiene respaldo en
el modelo (`Persona_Juridica.id_ubicacion`) y se usará en Sprint 2.

---

### 2.3 Frontend — Conectar el formulario con la API

**Problema:** el `submit` no llamaba a ningún endpoint.

```js
// ANTES — maqueta: redirige sin crear la cuenta
form.addEventListener('submit', function (e) {
    e.preventDefault();
    window.location.href = tipo === 'pj'
        ? 'register-completado-pj.html'
        : 'register-completado-pf.html';
});
```

Se reescribió el bloque completo. Piezas del cambio:

**a) Desalineación de nombres.** El input era `name="nacimiento"`, el DTO espera
`fechaNacimiento`. Con ese nombre, Jackson dejaba el campo en `null` y el registro moría
en un `400` sin explicación. Se renombraron `id` y `name` para que coincidan **exactamente**
con el DTO — esto además hace que `UI.pintarErrores()` encuentre el input por `id` sin
ninguna tabla de traducción.

**b) Armado del cuerpo con los nombres del DTO:**

```js
function armarPeticion() {
    const valor = function (id) { return document.getElementById(id).value.trim(); };
    const perfilElegido = document.querySelector('input[name="perfil"]:not([disabled]):checked')
                       || perfilPjForzado;
    return {
        email: valor('email'), nickname: valor('nickname'),
        password: document.getElementById('password').value,
        passwordConfirmacion: document.getElementById('passwordConfirmacion').value,
        nombre: valor('nombre'), apellido: valor('apellido'),
        dni: valor('dni'), fechaNacimiento: valor('fechaNacimiento'),
        perfil: perfilElegido ? perfilElegido.value : 'PARTICIPANTE'
    };
}
```

**c) Traducción de la respuesta.** Se reutilizaron los helpers que ya existían en
`utils.js` en lugar de escribir un manejo de errores nuevo:

| Respuesta | Qué hace la pantalla |
|-----------|----------------------|
| `201` | Guarda el resumen en `sessionStorage` y navega a `register-completado-pf.html` |
| `400` con `errores{}` | `UI.pintarErrores()` marca cada input y escribe el motivo debajo |
| `409` / `422` / red caída | `UI.mensajeDeError()` compone el texto en el aviso general |

**d) Infraestructura visual que faltaba.** El formulario no tenía dónde mostrar errores:
se agregaron ocho `<span class="campo__error">` (uno por campo), el contenedor
`#avisoRegistro` y las reglas CSS `.campo--invalido`, `.aviso--ok/--error/--alerta` al
`<style>` de la página. Los estilos van inline porque `register-paso2.html` no enlaza
`styles.css`, solo `tokens.css`.

**e) Foco y desplazamiento.** El formulario es largo; si el error queda fuera de pantalla
el usuario no lo ve. Al recibir un `400`, el primer campo inválido se lleva el foco y se
centra en la ventana.

---

### 2.4 Frontend — Persona Jurídica: decir la verdad, no fingir

El toggle PJ ahora muestra un aviso y **deshabilita el botón de envío**:

> **Registro de organizaciones en preparacion.** Por ahora solo se puede crear una cuenta
> personal. Elegí "Cuenta personal" para continuar.

**Razón:** el backend de Sprint 1 no tiene rama PJ. Dejar el botón activo llevaría al
usuario a completar razón social, CUIT, teléfono, email corporativo y domicilio fiscal
para recibir un error recién al final — o peor, lo que hacía antes: navegar a
`register-completado-pj.html` fingiendo un alta que nunca ocurrió.

---

### 2.5 Frontend — Pantalla de éxito

Tenía dos defectos:

1. El botón apuntaba a `../participant/dashboard.html`, **que no existe** → 404.
2. El chip de rol decía "Participante" fijo en el HTML, aunque el usuario se hubiera
   registrado como Organizador.

Correcciones:

- El CTA ahora va a `login-desktop-claro.html`. **El registro no autentica**: la respuesta
  `201` no trae token (por diseño), así que el paso siguiente real es iniciar sesión.
- El chip y un saludo con el nickname se pintan desde el resumen que el paso 2 dejó en
  `sessionStorage`, con `try/catch` y texto por defecto si no está (recarga, entrada
  directa o modo privado). La clave se borra tras leerla: un solo uso.

---

### 2.6 Build — Mockito

`build.gradle` no tenía Mockito; los *starters* de test de Spring Boot 4.1 son modulares y
no lo arrastran. Se agregó sin versión (la fija el BOM de Spring Boot → 5.23.0):

```gradle
testImplementation 'org.mockito:mockito-core'
testImplementation 'org.mockito:mockito-junit-jupiter'
```

---

## 3. Suites de prueba creadas

| Suite | Ubicación | Qué cubre |
|-------|-----------|-----------|
| **Unitaria** (JUnit 5 + Mockito) | `src/test/java/com/enexia/rg/service/AuthServiceRegistroTest.java` | 15 tests sobre `AuthService.registrar()` con todas las dependencias mockeadas |
| **E2E** (Playwright + POM) | `src/test/java/com/enexia/rg/RegistroTest.java` + `pages/RegisterPage.java` | 12 tests: navegador real → HTML/JS → Spring → MariaDB |
| **API** (Postman / Newman) | `enexia/pruebas/postman/` | 17 peticiones, 95 aserciones contra los endpoints |

Las tres capas son complementarias y **no redundantes**:

- La **unitaria** cubre cada rama de error del service en milisegundos, sin base de datos.
  Es la que se corre en cada guardado.
- La **E2E** es la única que detecta errores de integración: un `name` de input que no
  coincide con el DTO, un CORS mal configurado, o un mensaje que el backend manda pero la
  pantalla nunca pinta. Justamente el tipo de error que tenía este módulo.
- La **API** documenta el contrato HTTP y sirve de referencia ejecutable para quien
  consuma los endpoints sin pasar por el frontend.

Todas generan identidad única por corrida (sufijo derivado del reloj). Es obligatorio: el
alta **no se puede deshacer** — el sistema usa borrado lógico, nunca `DELETE` — así que una
suite con datos fijos pasaría una sola vez y fallaría con `409` en todas las siguientes.

---

## 4. Dos hallazgos durante la ejecución de las pruebas

Ambos surgieron al correr las suites por primera vez, y ambos eran errores **de la prueba**,
no del código de producción. Quedan documentados porque son trampas reutilizables:

**a) `tipoToken`, no `tipo`.** La aserción de Postman esperaba `cuerpo.tipo === 'Bearer'`;
el campo real de `UsuarioLoginResponse` es `tipoToken`. Corregido en la colección.

**b) Los radios ocultos no se pueden clickear.** Playwright fallaba con
*"the label intercepts pointer events"* en los toggles de tipo de cuenta y perfil. Causa:
esos `<input type="radio">` están ocultos por CSS (`opacity:0; pointer-events:none`) y el
control visible es el `<label>`. La corrección correcta no es forzar el click con `force`,
sino **localizar la etiqueta**, que es exactamente lo que toca un usuario real:

```java
this.tipoPersonaJuridica = page.locator("label[for='tipo-pj']");
```

---

## 5. Lo que queda pendiente

### Persona Jurídica (Sprint 2 — se retoma mañana)

| Bloqueante | Detalle |
|------------|---------|
| Catálogos sin sembrar | `DatosInicialesConfig` solo carga `Rol` y `UsuarioEstado`. Faltan `PersonaJuridicaEstado` y `PersonaJuridicaEstadoSistema` para poder fijar `REVISION_PENDIENTE` + `INACTIVO` |
| Sin validador de CUIT | El DFD 7.3 pide dígito verificador; hoy no existe |
| Sin DTO ni rama en el service | `registrar()` fija `tipoPersona = "FISICA"` |
| Repositorios vacíos | `PersonaJuridicaRepository`, `UbicacionRepository`, `MiembrosOrganizacionRepository`, `CiudadRepository` |
| Sin catálogo geográfico | No hay seed de `Pais/Provincia/Ciudad` ni endpoint que alimente los `<select>` del domicilio fiscal |
| **Inconsistencia de modelo** | `PersonaJuridica` **no tiene FK a `Persona`**, pero `Persona.tipoPersona` admite `"JURIDICA"`. En el MER solo existe `Persona ||--\|\| Persona_Fisica`. **Hay que definir esto antes de escribir código**, no durante |

### Fuera del alcance de este módulo, detectado de paso

**Divergencia RF-1.4 ↔ configuración.** El requisito dice bloquear la cuenta al **tercer**
intento fallido. `application.properties` bloquea al **noveno**:

```properties
enexia.security.login.intentos-captcha=3        # 3 fallos -> captcha + cooldown 5 min
enexia.security.login.intentos-cooldown-largo=6 # 6 fallos -> cooldown 30 min
enexia.security.login.intentos-bloqueo=9        # 9 fallos -> BLOQUEADO
```

La escalera progresiva es defendible (y probablemente mejor que el requisito original),
pero **el documento y el código dicen cosas distintas**. Hay que alinear uno de los dos.
Corresponde al módulo de Login, no a este.

---

## 6. Archivos tocados

```
enexia/build.gradle                                              (+ Mockito)
enexia/src/main/java/com/enexia/rg/repository/PersonaFisicaRepository.java
enexia/src/main/java/com/enexia/rg/service/AuthService.java
enexia/src/main/resources/static/pages/auth/register-paso2.html
enexia/src/main/resources/static/pages/auth/register-completado-pf.html

NUEVOS
enexia/src/test/java/com/enexia/rg/service/AuthServiceRegistroTest.java
enexia/src/test/java/com/enexia/rg/RegistroTest.java
enexia/src/test/java/com/enexia/rg/pages/RegisterPage.java
enexia/pruebas/postman/Enexia-Registro.postman_collection.json
enexia/pruebas/postman/Enexia-Local.postman_environment.json
```

---

## 7. Cómo correr todo

Requiere MariaDB (XAMPP) con la base `enexia` creada.

```bash
cd enexia && ./gradlew test
```

Corre las 30 pruebas (unitarias + E2E). Las E2E levantan Spring en el 8080 por sí mismas
(`@SpringBootTest(webEnvironment = DEFINED_PORT)`), **así que el puerto tiene que estar
libre**: si hay un `bootRun` abierto, cerrarlo antes.

Para la suite de API, con el backend ya levantado (`./gradlew bootRun`):

```bash
cd enexia/pruebas/postman && npx newman run Enexia-Registro.postman_collection.json -e Enexia-Local.postman_environment.json
```

Resultados de la corrida del 2026-09-07:
[`2026-09-07_resultados_pruebas_registro.md`](./2026-09-07_resultados_pruebas_registro.md)
