# C02 — JavaScript vanilla: consumir la API desde el navegador

> **Parte C · Del servidor al navegador.** El backend devuelve JSON; alguien tiene que **convertirlo en pantalla**. Ese alguien es **JavaScript**, el único lenguaje que corre dentro del navegador. Enexia lo usa **"vanilla"**: sin React, Vue ni jQuery, solo el lenguaje y las herramientas que el navegador ya trae. Este capítulo enseña JavaScript desde cero **con el código real del frontend** (`enexia/src/main/resources/static/js/` y las páginas).

**Qué vas a aprender**
- Cómo se incluye y ejecuta JavaScript en una página; el *navegador* como entorno.
- Sintaxis esencial: variables (`const`/`let`), tipos, funciones, funciones flecha, objetos, arreglos, *template literals*.
- Métodos funcionales de arreglos: `map`, `filter`, `find`, `forEach`, `some`, `flatMap`.
- El **DOM**: seleccionar, modificar y crear elementos; eventos; delegación.
- **Asincronía**: promesas, `async`/`await`, `fetch`.
- Cómo está armado el cliente de la API (`api.js`), la sesión (`auth.js`) y los ayudantes (`utils.js`).
- Almacenamiento (`sessionStorage`), JWT en el cliente, formularios, paginación, *debounce*.
- **Seguridad en el cliente**: XSS y `UI.esc`.

---

## 1. JavaScript en el navegador

### 1.1 Cómo llega el código

Un archivo HTML puede **incluir** JavaScript de dos formas:

```html
<script src="../../js/api.js"></script>      <!-- un archivo externo -->

<script>
    // código escrito directamente en la página
    console.log('hola');
</script>
```

Las páginas de Enexia cargan primero las **librerías propias**, en este orden **que importa** (cada una usa a la anterior):

```html
<script src="../../js/api.js"></script>      <!-- define API  (usa Auth, que aún no existe pero se usa recién al llamar) -->
<script src="../../js/auth.js"></script>     <!-- define Auth -->
<script src="../../js/utils.js"></script>    <!-- define UI   -->
<script>
    /* acá el script propio de la página, que ya puede usar API, Auth y UI */
</script>
```

El navegador ejecuta los scripts **de arriba hacia abajo** y **bloquea** la lectura del HTML mientras tanto: por eso van **al final del `<body>`**, cuando los elementos ya existen.

### 1.2 Entorno de ejecución

JavaScript en el navegador tiene acceso a:
- **`document`**: el árbol de la página (el DOM, §6).
- **`window`**: la ventana (`window.location`, `window.history`).
- **`fetch`**: pedidos HTTP (§8).
- **`sessionStorage` / `localStorage`**: pequeño almacén del navegador (§10).
- **`console`**: `console.log(...)` aparece en las **DevTools** (F12 → pestaña *Console*).

**No** tiene acceso al disco ni a otros sitios (política del mismo origen: C01 §5).

### 1.3 Herramientas para aprender

Abrí `http://localhost:8080/index.html`, apretá **F12** y en la pestaña **Console** escribí código: se ejecuta en esa página. `Auth.token()`, `UI.esc('<b>')`, `document.title`... Es el mejor laboratorio.

---

## 2. Variables, tipos y operadores

```js
const BASE = 'http://localhost:8080';     // const: NO se puede reasignar
let pagina = 0;                            // let: sí se puede reasignar
pagina = pagina + 1;
```

- **`const`** por defecto; **`let`** solo cuando el valor cambia. **Evitá `var`** (tiene reglas de alcance confusas).
- Los **nombres** van en `camelCase` (`totalPaginas`); las constantes de configuración, en `MAYUSCULAS`.
- Las sentencias terminan con `;` (opcional, pero el proyecto lo usa).

### 2.1 Tipos

| Tipo | Ejemplos | Notas |
|---|---|---|
| `string` | `'hola'`, `"hola"`, `` `hola ${x}` `` | Comillas simples, dobles o *backticks*. |
| `number` | `3`, `3.14`, `NaN` | **Un único tipo** para enteros y decimales. |
| `boolean` | `true`, `false` | |
| `null` | `null` | "Ausencia deliberada de valor". |
| `undefined` | `undefined` | "Nunca se le dio un valor" (una variable sin inicializar, una propiedad inexistente). |
| `object` | `{ a: 1 }`, `[1, 2]`, `null` (¡sí!) | Objetos, arreglos y funciones. |

### 2.2 Comparar: `===`, no `==`

```js
3 == '3'     // true   ← conversión silenciosa de tipos: fuente de errores
3 === '3'    // false  ← estricta: tipo Y valor
```

**Usá siempre `===` y `!==`.** (La excepción deliberada del proyecto es `valor == null`, que es `true` tanto para `null` como para `undefined`: una forma corta de "no hay valor". `UI.esc` lo hace explícito.)

### 2.3 Verdadero y falso (*truthy / falsy*)

En un `if`, estos valores cuentan como **falso**: `false`, `0`, `''` (texto vacío), `null`, `undefined`, `NaN`. Todo lo demás es **verdadero** (incluso `'0'` y `[]`). Eso permite escribir:

```js
if (!token) return null;               // "si no hay token"
const lugar = [ev.ciudad, ev.provincia].filter(Boolean).join(', ');   // descarta los vacíos
```

`.filter(Boolean)` pasa cada elemento por la función `Boolean`, que lo convierte en `true`/`false`: es un modismo para **quitar nulos y vacíos**.

### 2.4 Operadores útiles

```js
const nombre = u.nombreCompleto || u.nickname || '-';     // || : el primero "verdadero" (valor por defecto)
const resultado = condicion ? 'si' : 'no';                 // ternario
const largo = ev.cronogramas?.length;                      // ?. : encadenamiento opcional (no falla si es null)
const valor = x ?? 0;                                      // ?? : usar 0 solo si x es null/undefined (no si es 0)
```

---

## 3. Funciones

### 3.1 Tres formas de declararlas

```js
// 1) Declaración: se "eleva" (se puede llamar antes de definirla)
function fechaCorta(iso) {
    if (!iso) return null;
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d);
}

// 2) Función flecha (arrow function): corta, y no tiene su propio `this`
const doble = x => x * 2;
const suma = (a, b) => a + b;
const conCuerpo = (a, b) => { const s = a + b; return s; };

// 3) Método dentro de un objeto
const Auth = {
    token() { return sessionStorage.getItem('enexia_token'); }
};
```

Las **flechas** son las que se usan en `map`, `filter` y los *listeners*. Equivalen a las lambdas de Java (A03 §2).

### 3.2 Parámetros por defecto, *rest* y desestructuración

```js
listarEventos(parametros = {}) { ... }                 // valor por defecto

hasAnyRole(...roles) {                                  // ...roles : junta los argumentos en un arreglo
    return roles.some(r => this.hasRole(r));
}

const [y, m, d] = '2026-10-19'.split('-').map(Number); // desestructurar un arreglo
const { ok, status, cuerpo } = respuesta;              // desestructurar un objeto
```

### 3.3 Las funciones son valores

Como en Java funcional (A03), una función se puede **guardar en una variable, pasar como argumento y devolver**. Es lo que hace posible `array.map(fn)` y `elemento.addEventListener('click', fn)`.

### 3.4 `this` en una línea

`this` es "el objeto sobre el que se llamó el método". En `Auth.hasRole(...)`, dentro de `Auth.hasAnyRole`, `this` es `Auth`. **Las flechas no tienen `this` propio**: toman el del contexto. Por eso `roles.some(r => this.hasRole(r))` funciona dentro del método.

---

## 4. Objetos y arreglos

### 4.1 Objetos

Un **objeto** es un conjunto de pares clave → valor. Se usa para casi todo: datos, configuración y **módulos**.

```js
const usuario = { idUsuario: 3, email: 'ana@enexia.test', roles: ['ORGANIZADOR'] };
usuario.email;            // 'ana@enexia.test'
usuario['email'];         // igual (útil cuando la clave es una variable)
usuario.activo = true;    // agregar una propiedad
delete usuario.activo;    // quitarla

Object.keys(usuario);     // ['idUsuario', 'email', 'roles']
Object.entries(usuario);  // [['idUsuario', 3], ['email', '...'], ...]
```

**JSON ↔ objeto** (los dos métodos más usados del proyecto):

```js
JSON.stringify({ email: 'a@b.com' });        // objeto  → texto  '{"email":"a@b.com"}'
JSON.parse('{"email":"a@b.com"}');           // texto   → objeto
```

### 4.2 Los "módulos" de Enexia son objetos literales

No hay `import`/`export` (sin *bundler*): cada archivo define **un objeto global** que agrupa funciones relacionadas.

| Archivo | Objeto | Responsabilidad |
|---|---|---|
| `js/api.js` | `API` | Hablar con el backend (una función por endpoint). |
| `js/auth.js` | `Auth` | Sesión: guardar/leer el token, roles, expiración. |
| `js/utils.js` | `UI` | Ayudantes de interfaz: `esc`, errores por campo, avisos. |

```js
const API = {
    BASE: 'http://localhost:8080',
    async peticion(metodo, ruta, cuerpo = null, conToken = false) { /* ... */ },
    login(datos) { return this.peticion('POST', '/api/auth/login', datos); },
    // ...
};
```

Cada método es una **abreviatura** de `peticion` con una ruta fija: las pantallas llaman `API.login(...)` y no se preocupan por la URL ni por el token.

### 4.3 Arreglos y sus métodos funcionales

Un **arreglo** es una lista ordenada: `const ids = [1, 2, 3];`. Los métodos que más se usan **no modifican** el original y devuelven uno nuevo:

| Método | Qué hace | Ejemplo real |
|---|---|---|
| `map(fn)` | Transforma cada elemento. | `ev.imagenes.map(url => ...)` arma el HTML de cada imagen. |
| `filter(fn)` | Se queda con los que cumplen. | `[ev.calle, ev.numero].filter(Boolean)` |
| `find(fn)` | El **primero** que cumple (o `undefined`). | `rp.cuerpo.find(p => /tierra del fuego/i.test(p.nombre))` |
| `forEach(fn)` | Recorre (sin devolver nada). | `r.cuerpo.forEach(cat => select.appendChild(...))` |
| `some(fn)` / `every(fn)` | ¿alguno / todos cumplen? | `roles.some(r => this.hasRole(r))` |
| `includes(x)` | ¿contiene? | `this.roles().includes(rol)` |
| `flatMap(fn)` | `map` + aplanar un nivel. | `cronogramas.flatMap(c => c.tickets.map(...))` |
| `join(sep)` | Une en un texto. | `[...].join(', ')` |
| `slice(a, b)` | Una copia parcial. | `.slice(-2)` los últimos dos |

Son los mismos conceptos que los *streams* de Java (A03 §3): **`map` = transformar, `filter` = filtrar**. Un ejemplo completo de `api.js`:

```js
const query = new URLSearchParams(
    Object.entries(parametros).filter(([, v]) => v !== null && v !== undefined && v !== '')
).toString();
```

Se lee: *"de los parámetros, tomá los pares clave/valor, descartá los que no tienen valor, y armá la cadena `a=1&b=2`"*. (`([, v])` desestructura cada par ignorando la clave.)

---

## 5. Template literals: texto con huecos

Los *backticks* permiten **insertar expresiones** con `${...}` y escribir **varias líneas**:

```js
const mensaje = `Ya tenés tu lugar en "${nombreTicket}".`;

el.innerHTML = `
    <article class="tarjeta-evento">
        <h3>${UI.esc(evento.nombre)}</h3>
        ${lugar ? `<div>${UI.esc(lugar)}</div>` : ''}
    </article>
`;
```

Dentro de `${ ... }` va **cualquier expresión**: una variable, un ternario, una llamada, incluso **otro template**. Es la forma en que las páginas arman HTML. **Y es el origen del mayor riesgo de seguridad del frontend** (§12).

---

## 6. El DOM: modificar la página

El **DOM** (*Document Object Model*) es el **árbol de objetos** que el navegador arma a partir del HTML. JavaScript lo lee y lo cambia; el navegador redibuja.

### 6.1 Seleccionar elementos

```js
document.getElementById('formLogin');                  // por id → UN elemento o null
document.querySelector('.tarjeta-evento__nombre');     // el primero que coincide con el selector CSS
document.querySelectorAll('.campo--invalido');         // TODOS (una NodeList, recorrible con forEach)
evento.target.closest('.js-btn-inscribir');            // el ancestro más cercano que coincide (§7.2)
```

Los **selectores** son los mismos de CSS (C03): `#id`, `.clase`, `etiqueta`, `[atributo]`.

### 6.2 Leer y cambiar contenido

| Propiedad | Qué hace | Usar para… |
|---|---|---|
| `el.textContent = '...'` | Pone **texto plano** (lo que haya se muestra literal). | Datos que vienen de afuera. **Seguro.** |
| `el.innerHTML = '...'` | Interpreta el valor **como HTML**. | HTML armado por vos, con los datos **escapados** (`UI.esc`). **Peligroso con datos sin escapar.** |
| `el.value` | El valor de un `<input>`. | Leer lo que escribió la persona. |
| `el.hidden = true/false` | Oculta/muestra (atributo `hidden`). | Alternar vistas. |
| `el.disabled = true` | Deshabilita un botón/campo. | Evitar el doble envío. |
| `el.className`, `el.classList.add/remove/toggle(...)` | Clases CSS. | Cambiar el aspecto (`campo--invalido`). |
| `el.dataset.idTicket` | Atributos `data-id-ticket="..."`. | Guardar datos en el HTML (kebab → camel). |
| `el.setAttribute('aria-label', ...)` | Cualquier atributo. | Accesibilidad. |

```js
const mensajeError = document.getElementById('mensajeError');
mensajeError.textContent = 'Credenciales inválidas';
mensajeError.hidden = false;
```

### 6.3 Crear elementos

```js
const opt = document.createElement('option');
opt.value = cat.id;
opt.textContent = cat.nombre;        // textContent: seguro por construcción
select.appendChild(opt);             // agregarlo al árbol
el.remove();                         // quitarlo
```

Construir con `createElement` + `textContent` es **más seguro** que armar un string HTML (no hay nada que escapar) y es lo que usa el proyecto para los `<option>` de los filtros. Cuando el HTML es largo (tarjetas), se usa un template literal **escapando cada dato**.

---

## 7. Eventos

Un **evento** es algo que pasa en la página: un clic, una tecla, un envío de formulario, una carga. Se **escucha** con `addEventListener(tipo, función)`.

```js
document.getElementById('formLogin').addEventListener('submit', async (e) => {
    e.preventDefault();         // frena el comportamiento por defecto (recargar la página)
    // ... leer los campos y llamar a la API
});

document.getElementById('btnBuscar').addEventListener('click', () => cargar(true));
filtroTexto.addEventListener('keydown', e => { if (e.key === 'Enter') cargar(true); });
filtroOrden.addEventListener('change', () => cargar(true));
```

- **`e`** (el objeto *evento*) trae datos: `e.target` (el elemento), `e.key` (la tecla), `e.preventDefault()`.
- Los tipos más usados: `click`, `submit`, `input` (cada cambio del campo), `change`, `keydown`.
- **`submit` va en el `<form>`**, no en el botón: así funciona también al apretar Enter.

### 7.1 `preventDefault`

Un `<form>` por defecto **recarga la página** al enviarse. Como acá la pantalla se maneja con JavaScript y `fetch`, se la frena con `e.preventDefault()` y se hace el pedido por código.

### 7.2 Delegación de eventos

**Problema:** en `evento-detalle.html` los botones "Inscribirme" se **vuelven a crear** cada vez que se redibuja la ficha. Un *listener* puesto sobre cada botón se **pierde** con el botón viejo.

**Solución:** **un solo listener sobre un ancestro estable** (`document`) que pregunta "¿el clic fue dentro de un botón de inscripción?":

```js
document.addEventListener('click', async function (evento) {
    const boton = evento.target.closest('.js-btn-inscribir');   // ¿el clic ocurrió en (o dentro de) uno?
    if (!boton) return;                                          // no: ignorar

    const idCronogramaTicket = Number(boton.dataset.idTicket);
    // ...
});
```

Los eventos **burbujean**: un clic en un hijo sube por sus ancestros, así que `document` lo ve. Una convención útil del proyecto: las clases que empiezan con **`js-`** (`js-btn-inscribir`) son **ganchos para JavaScript**, no para estilos.

---

## 8. Asincronía: promesas, `async` y `await`

### 8.1 Por qué

Un pedido de red tarda. JavaScript **no se queda esperando** (congelaría la página): lanza el pedido y **sigue**; cuando llega la respuesta, ejecuta lo pendiente. Eso se modela con **promesas**.

Una **promesa** (`Promise`) es "un valor que **todavía no está**, pero va a estar (o va a fallar)". Tres estados: *pendiente*, *cumplida* (con un valor), *rechazada* (con un error).

### 8.2 `async` / `await`

Hacen que el código asíncrono se lea **como si fuera secuencial**:

```js
async function cargar(reiniciar) {
    // ...
    const r = await API.publico('/api/publico/eventos' + (query ? '?' + query : ''));
    //        ↑ "esperá el resultado de la promesa; mientras tanto, la página sigue respondiendo"

    if (!r.ok) { /* mostrar el error */ return; }
    const eventos = r.cuerpo.content;
    // ...
}
```

- `async function` **siempre devuelve una promesa**.
- `await` solo puede usarse **dentro de una función `async`** (o a nivel módulo).
- Un `await` **no bloquea la página**: solo suspende **esa** función.
- **Cuidado:** si te olvidás el `await`, `r` es una **promesa**, no el resultado, y `r.ok` es `undefined`.

### 8.3 `fetch`

`fetch(url, opciones)` hace un pedido HTTP y devuelve una promesa de `Response`. El núcleo de `api.js` (resumido):

```js
async peticion(metodo, ruta, cuerpoEnvio = null, conToken = false) {
    const url = this.BASE + ruta;
    const opciones = { method: metodo, headers: { 'Content-Type': 'application/json' } };

    if (conToken && Auth.token()) {
        opciones.headers['Authorization'] = 'Bearer ' + Auth.token();
    }
    if (cuerpoEnvio !== null) {
        opciones.body = JSON.stringify(cuerpoEnvio);          // objeto → texto JSON
    }

    try {
        const respuesta = await fetch(url, opciones);
        const texto = await respuesta.text();                  // leer el cuerpo como texto
        let cuerpo = null;
        if (texto) {
            try { cuerpo = JSON.parse(texto); } catch { cuerpo = texto; }   // JSON, o el texto crudo
        }
        // ...
        return { ok: respuesta.ok, status: respuesta.status, cuerpo, /* ... */ errorRed: false };

    } catch (error) {
        // fetch SOLO rechaza ante fallos de red (backend apagado, DNS, CORS)
        return { ok: false, status: 0, cuerpo: { mensaje: error.message }, errorRed: true };
    }
}
```

Conceptos clave:

1. **`fetch` no rechaza ante un 400, 401 o 500**: la promesa se **cumple** y `respuesta.ok` vale `false`. Solo rechaza cuando **no hubo respuesta** (red caída, CORS). Por eso hay que mirar `ok`/`status`.
2. **El diseño del proyecto: `peticion` nunca lanza.** Siempre devuelve **un objeto con la misma forma** (`{ ok, status, cuerpo, errorRed, ... }`), tanto con un 201 como con un 429 o con el backend apagado. Así **las pantallas no necesitan `try/catch`** y deciden según el `status`. El comentario del código lo dice: *"No lanza excepciones: quien llama decide qué hacer según el status."*
3. `await respuesta.text()` y luego `JSON.parse` (en vez de `respuesta.json()`) porque algunas respuestas (un `204`, o un error HTML) **no tienen JSON**.
4. **401 con token → cerrar la sesión y mandar al login**: si un pedido **autenticado** devuelve 401, la sesión murió (venció o suspendieron la cuenta). Pero **no** se hace en el login ni el registro, que devuelven 401 como respuesta normal: redirigir ahí "pisaría el cartel de error apenas se pinta".

### 8.4 Usar la API desde una pantalla (el login completo)

`pages/auth/login-desktop-claro.html`:

```js
document.getElementById('formLogin').addEventListener('submit', async (e) => {
    e.preventDefault();

    const nickname = document.getElementById('nickname').value.trim();
    const password = document.getElementById('password').value;
    const btnEnviar = document.getElementById('btnEnviar');
    const mensajeError = document.getElementById('mensajeError');

    mensajeError.hidden = true;                       // limpiar el error anterior
    mensajeError.textContent = '';

    if (!nickname || !password) {                     // validación local mínima
        mostrarError('Completa todos los campos');
        return;
    }

    btnEnviar.disabled = true;                        // evitar el doble envío
    btnEnviar.textContent = 'Iniciando sesión...';

    try {
        const respuesta = await API.login({ nickname, password });

        if (respuesta.ok && respuesta.cuerpo) {
            Auth.guardarSesion(respuesta.cuerpo);          // guardar el token
            window.location.href = '../../index.html';     // ir al inicio
        } else {
            mostrarError('Credenciales inválidas');        // MISMO mensaje, sea cual sea el motivo
            btnEnviar.disabled = false;
            btnEnviar.textContent = 'Iniciar sesión →';
        }
    } catch (error) {
        mostrarError('No se pudo conectar al servidor');
        btnEnviar.disabled = false;
        btnEnviar.textContent = 'Iniciar sesión →';
    }
});
```

Observá:
- `{ nickname, password }` es **notación abreviada**: equivale a `{ nickname: nickname, password: password }`.
- El **botón se deshabilita** mientras dura el pedido y se rehabilita al fallar: es la defensa contra el doble clic.
- **El mensaje de error del login es siempre el mismo** (`Credenciales inválidas`): el frontend respeta la política de no revelar el motivo (B04 §5). Aunque el backend mandara más información, esta pantalla **no la muestra**.
- `try/catch` acá es una red de seguridad extra; `peticion` ya no lanza.

---

## 9. Manejo de errores del backend en pantalla

### 9.1 Errores de validación por campo

El backend responde `400` con `errores: { campo: motivo }`. `UI.pintarErrores` los **vuelca sobre el formulario** usando una convención: *el `id` del input coincide con el nombre del campo del DTO*.

```js
pintarErrores(errores) {
    this.limpiarErrores();
    if (!errores) return;

    Object.entries(errores).forEach(([campo, motivo]) => {
        const input = document.getElementById(campo);       // id = nombre del campo del DTO
        if (!input) return;
        const contenedor = input.closest('.campo');
        if (!contenedor) return;
        contenedor.classList.add('campo--invalido');
        const span = contenedor.querySelector('.campo__error');
        if (span) span.textContent = motivo;                // textContent: seguro
    });
}
```

Esa convención de nombres (`nickname`, `password`, `dni`...) es la que **conecta el backend con el HTML** sin código por campo.

### 9.2 `switch` sobre el código de error

`ErrorResponse.error` es una etiqueta **estable**, pensada para esto (B02 §4.4):

```js
switch (cuerpo.error) {
    case 'VALIDACION_FALLIDA':   return 'Hay campos con errores. Revisa el detalle marcado en el formulario.';
    case 'RECURSO_DUPLICADO':    return cuerpo.mensaje || 'Ese dato ya esta registrado.';
    case 'CONTENIDO_INAPROPIADO': return '<strong>Contenido rechazado por moderacion.</strong> ' + (cuerpo.mensaje || '');
    case 'REGLA_NEGOCIO':        return cuerpo.mensaje || 'La operacion no cumple una regla de negocio.';
    default:                     return cuerpo.mensaje || 'Ocurrio un error inesperado.';
}
```

---

## 10. La sesión en el cliente: `Auth` y `sessionStorage`

### 10.1 Web Storage

Dos almacenes clave → valor (**siempre texto**) del navegador:

| | `sessionStorage` | `localStorage` |
|---|---|---|
| Dura | hasta cerrar **la pestaña** | **indefinidamente** |
| Alcance | solo esa pestaña | todo el sitio |

```js
sessionStorage.setItem('enexia_token', token);
sessionStorage.getItem('enexia_token');      // texto o null
sessionStorage.removeItem('enexia_token');
```

Como solo guardan texto, un objeto se guarda con `JSON.stringify` y se lee con `JSON.parse`:

```js
sessionStorage.setItem('enexia_usuario', JSON.stringify({ idUsuario, email, roles }));
const usuario = JSON.parse(sessionStorage.getItem('enexia_usuario'));
```

**Por qué `sessionStorage`** (comentario de `auth.js`): *"se borra al cerrar la pestaña; acota la ventana de exposición"*. Con una advertencia honesta: **ninguno protege contra XSS** (cualquier script inyectado los puede leer); la defensa de fondo es **escapar la salida** (§12) o, a futuro, una cookie `HttpOnly`.

### 10.2 Leer el JWT en el navegador

`Auth.payload()` decodifica el **payload** del token:

```js
const partes = token.split('.');                              // header . payload . firma
const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');   // Base64URL → Base64
const relleno = base64 + '='.repeat((4 - base64.length % 4) % 4); // agregar el relleno "="
const json = decodeURIComponent(
    Array.from(atob(relleno))                                 // atob: Base64 → bytes
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
);
return JSON.parse(json);
```

- `atob` decodifica Base64 a bytes; el truco con `decodeURIComponent` recupera correctamente los **acentos** (UTF-8).
- Una **regex** (`/-/g`, `/_/g`) con la bandera `g` (global) reemplaza **todas** las apariciones.
- **Decodificar no es validar**: la firma solo la puede verificar el backend. En el cliente el payload **se lee solo para mostrar** (roles en el menú, expiración); **nunca** para decidir permisos reales (B04 §4.4).

### 10.3 Roles y guardas

```js
hasRole(rol)             { return this.roles().includes(rol); },
hasAnyRole(...roles)     { return roles.some(r => this.hasRole(r)); },
exigirSesion(rutaLogin = '/pages/auth/login-desktop-claro.html') {
    if (!this.autenticado()) { window.location.href = rutaLogin; return false; }
    return true;
}
```

Las páginas "privadas" llaman `Auth.exigirSesion()` al cargar y muestran u ocultan botones según el rol. **Es comodidad de interfaz**; el backend es quien manda.

### 10.4 Verificar la sesión al abrir cada página

Al final de `auth.js` hay una **IIFE** (función que se ejecuta en el acto: `(function () { ... })();`):

```js
(function verificarSesionVigente() {
    if (typeof window === 'undefined' || !Auth.autenticado()) return;
    if (window.location.pathname.indexOf('/pages/auth/') === 0) return;

    fetch('/api/usuario/perfil', { headers: { 'Authorization': 'Bearer ' + Auth.token() } })
        .then(function (respuesta) {
            if (respuesta.status === 401) {
                Auth.cerrarSesion();
                window.location.href = '/pages/auth/login-desktop-claro.html';
            }
        }).catch(function () { /* sin red: no se concluye nada */ });
})();
```

Resuelve el efecto de ADR-20 en el cliente: aunque el token todavía no venció, si el servidor ya no lo acepta (cuenta suspendida o bloqueada), **la página se entera apenas carga** y cierra la sesión. Ejemplo de **promesas encadenadas** con `.then(...)` / `.catch(...)`, la sintaxis anterior a `async/await`. **Un error de red no cierra la sesión** (backend caído ≠ sesión inválida).

Una **IIFE** sirve para **no ensuciar el ámbito global**: sus variables viven solo dentro de la función.

---

## 11. Patrones de pantalla del proyecto

### 11.1 Cargar y pintar con tres estados

Toda lista de Enexia maneja **cargando → éxito → vacío/error**:

```js
grilla.innerHTML = '<div class="cargando">Cargando eventos...</div>';       // 1) cargando
const r = await API.publico('/api/publico/eventos?...');
if (!r.ok) {                                                                 // 2) error
    grilla.innerHTML = '<div class="cargando">No se pudieron cargar los eventos. Intenta de nuevo...</div>';
    return;
}
const eventos = r.cuerpo.content || [];
if (!eventos.length) {                                                       // 3) vacío
    grilla.innerHTML = '<div class="vacio"><p class="vacio__titulo">No encontramos eventos con estos filtros</p></div>';
} else {
    eventos.forEach(e => grilla.appendChild(tarjetaEvento(e)));              // 4) datos
}
```

Nunca dejes una pantalla en blanco: **siempre** hay un mensaje para cada estado.

### 11.2 Paginación con "Cargar más"

`index.html` mantiene dos variables de módulo (`pagina`, `totalPaginas`) y:

```js
document.getElementById('btnCargarMas').addEventListener('click', () => { pagina++; cargar(false); });
// ...
contMas.hidden = (pagina + 1) >= totalPaginas;     // ocultar el botón en la última página
```

`cargar(true)` **reinicia** (limpia la grilla y vuelve a la página 0); `cargar(false)` **agrega** la siguiente. El **tamaño de página** se calcula como múltiplo de las **columnas visibles** de la grilla (`getComputedStyle(...).gridTemplateColumns`) para que cada tanda complete filas.

### 11.3 *Debounce*: no disparar con cada tecla

Si el panel buscara en el servidor **en cada letra**, haría una ráfaga de pedidos. El **debounce** espera a que la persona **deje de escribir**:

```js
let debounceTexto = null;
filtroTexto.addEventListener('input', () => {
    clearTimeout(debounceTexto);                    // cancelar el pedido pendiente
    debounceTexto = setTimeout(cargar, 350);        // y programar uno nuevo en 350 ms
});
```

(`admin/usuarios.html` y `moderacion-eventos.html`.) Junto con un contador `pideCarga` que **descarta respuestas viejas** si llegan fuera de orden (*"descarta respuestas viejas si el admin sigue tecleando"*): es el problema de las **condiciones de carrera** también en el cliente.

### 11.4 Evitar el doble envío

Deshabilitar el botón durante el pedido (`btn.disabled = true`) y restaurar su texto al terminar. En el backend, además, la **unicidad** (`UNIQUE`) cubre lo que el botón no pueda.

### 11.5 Subir archivos: `FormData`

```js
crearEvento(datos, imagenes) {
    const formData = new FormData();
    formData.append('datos', this.parteJson(datos));                       // parte JSON
    (imagenes || []).forEach(archivo => formData.append('imagenes', archivo));
    return this.multipart('POST', '/api/organizador/eventos', formData);
}

parteJson(objeto) {
    return new Blob([JSON.stringify(objeto)], { type: 'application/json' });   // un "archivo" JSON
}
```

- `FormData` arma el cuerpo `multipart/form-data`.
- La parte `datos` es un **`Blob`** con tipo `application/json`, que es lo que espera `@RequestPart` del backend.
- **No se fija `Content-Type`**: el navegador lo calcula con el `boundary`. Por eso hay un método `multipart(...)` aparte que **no pasa por `peticion`** (esa siempre manda JSON).

### 11.6 La URL como estado: `URLSearchParams` y `history`

```js
const params = new URLSearchParams(window.location.search);   // lee "?token=abc"
const token = params.get('token');

history.replaceState(null, '', window.location.pathname);     // quita el "?token=..." de la barra, sin recargar
```

En `recuperar.html` el token se **lee y se borra de la barra** de inmediato: no queda en el historial ni se copia por accidente (B04 §6).

---

## 12. Seguridad en el cliente: XSS y `UI.esc`

### 12.1 El problema

`innerHTML` **interpreta como HTML todo lo que recibe**. Si le pasás texto que **escribió otra persona**, esa persona puede escribir etiquetas:

```js
// nombre del evento (lo escribió un organizador): <img src=x onerror="robarToken()">
h3.innerHTML = evento.nombre;      // ❌ el navegador crea la <img>, falla la carga y EJECUTA onerror
```

Eso es **XSS almacenado**: el código malicioso queda guardado en la base y se ejecuta **en el navegador de cada visitante**, con acceso a su `sessionStorage` (¡el JWT!).

### 12.2 La defensa: escapar

`UI.esc` (en `js/utils.js`) convierte los cinco caracteres peligrosos en **entidades HTML** inofensivas:

```js
esc(valor) {
    return String(valor === null || valor === undefined ? '' : valor)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
```

`<img src=x onerror=...>` pasa a `&lt;img src=x onerror=...&gt;`, que el navegador **muestra como texto** y no interpreta. El **orden importa**: `&` se reemplaza **primero** (si fuera después, escaparía los `&` de las entidades recién creadas).

Aplicado:

```js
<h3 class="tarjeta-evento__nombre">${UI.esc(evento.nombre)}</h3>
<img src="${UI.esc(evento.urlPortada)}" alt="${UI.esc(evento.nombre)}">
```

**Los atributos también:** `alt="${evento.nombre}"` con un nombre que contenga `"` permite **salirse del atributo** (`x" onmouseover="..."`) sin necesitar ninguna etiqueta `<`. Por eso se escapan también `"` y `'`.

### 12.3 Qué se corrigió el 2026-10-19

El nombre, la descripción, la dirección, el organizador, los nombres de ticket y **los comentarios de valoraciones** se insertaban **sin escapar** en `index.html`, `evento-detalle.html` y los paneles. Se agregó `UI.esc` y se aplicó en esos puntos (incluidos los *toasts*, que a veces incluyen el nombre de un ticket). Se verificó en el navegador con un evento **simulado** (respuesta de la API interceptada) cuyo nombre es `<img src=x onerror="window.__pwned=true">Fest`: la tarjeta muestra el texto literal y `window.__pwned` sigue `false`.

> **Pendiente** (`SUGERENCIAS_PENDIENTES.md`, X1): **no se auditaron todas las pantallas** (por ejemplo `evento-form.html`, los dos `perfil.html` y las de registro) y falta un **test automatizado** de XSS. Las pantallas de administración y "Mi Equipo" ya escapaban con su propia función local (`esc`, `escapar`). La regla es simple: **todo dato que escribió una persona y entra a un template pasa por `UI.esc`**, o se asigna con `textContent`.

### 12.4 Otras reglas del cliente

- **Nunca `eval`** ni `new Function(...)` con datos externos.
- **Nunca construir una URL** con texto del usuario sin `encodeURIComponent(...)` (se aplicó al `id` del evento en los enlaces).
- Un enlace con `href="${algo}"` **podría ser `javascript:...`**: solo armar enlaces con rutas propias.
- **Validar en el cliente es comodidad**, no seguridad.

---

## 13. Funciones auxiliares útiles de `utils.js` y de las páginas

| Función | Qué hace |
|---|---|
| `UI.mensajeDeError(resultado)` | Traduce `errorRed`/`error` a un mensaje de usuario. |
| `UI.pintarErrores(errores)` | Marca los campos con error de validación. |
| `UI.limpiarErrores()` | Los limpia. |
| `UI.aviso(id, tipo, mensaje)` | Muestra una caja de aviso (usa `innerHTML`: **solo con texto propio**). |
| `UI.esc(valor)` | Escape de HTML. |
| `fechaCorta(iso)` | `"2026-11-01"` → `"Dom, 1 nov"` con `toLocaleDateString('es-AR', {...})`. |
| `tiempoRelativo(fecha)` | "hace 3 días" para las reseñas. |

Un detalle de fechas en `fechaCorta`:

```js
const [y, m, d] = iso.split('-').map(Number);
const fecha = new Date(y, m - 1, d);       // ¡mes de 0 a 11! (enero = 0)
```

> **Trampa:** `new Date('2026-11-01')` interpreta el texto como **UTC** y, en Argentina (UTC-3), muestra **el 31 de octubre**. Armar la fecha con `new Date(año, mes, día)` la toma en **hora local**: es lo que hace el proyecto.

---

## 14. Errores típicos

| Error | Síntoma | Remedio |
|---|---|---|
| Olvidar `await`. | `r.ok` es `undefined`; se trabaja con una promesa. | `await` dentro de una función `async`. |
| Confiar en que `fetch` rechaza ante un 400. | Errores ignorados. | Mirar `respuesta.ok`/`status`. |
| `innerHTML` con datos de usuario. | XSS. | `UI.esc` o `textContent`. |
| Un listener por botón en elementos que se recrean. | Dejan de funcionar tras redibujar. | Delegación sobre un ancestro estable. |
| `==` en vez de `===`. | Coerciones inesperadas. | Siempre `===`. |
| Fijar `Content-Type` en un `FormData`. | El servidor no encuentra el `boundary`. | Dejar que el navegador lo calcule. |
| Guardar un objeto en `sessionStorage` sin `JSON.stringify`. | Se guarda `"[object Object]"`. | `JSON.stringify` / `JSON.parse`. |
| Orden de scripts incorrecto. | `API is not defined`. | `api.js` → `auth.js` → `utils.js` → script de la página. |
| `new Date('2026-11-01')` para mostrar fechas. | Un día corrido por la zona horaria. | `new Date(y, m - 1, d)`. |

---

## 15. Para practicar

1. En la consola del navegador, con una sesión iniciada, ejecutá `Auth.payload()` y `Auth.segundosRestantes()`. ¿Cuándo vence tu token?
2. Escribí una función `etiquetaOcupacion(inscriptos, cupo)` que devuelva `"Lleno"`, `"Casi lleno"` (≥ 80 %) o `"Hay lugar"`, y usala con `map` sobre un arreglo de cronogramas simulados.
3. Reescribí `cargarCategorias()` de `index.html` usando `map` y `join` para armar los `<option>` **como un solo string** (y compará qué cuidado habría que tener con `esc`).
4. Dibujá el recorrido de un clic en "Inscribirme": qué listener lo capta, qué llama a la API, qué pasa con un `402`, cómo se refresca la ficha.
5. Escribí un `<script>` que, en una página de prueba, pida `GET /api/publico/categorias` y llene un `<select>`. Después probalo desde `file://` y desde `http://localhost:8080` y explicá la diferencia (C01 §5).

**Siguiente capítulo:** [C03 — HTML y CSS](./C03_html_y_css.md)
