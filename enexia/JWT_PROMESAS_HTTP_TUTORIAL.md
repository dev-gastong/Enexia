# 🔐 JWT, Promesas y Peticiones HTTP - Tutorial Completo

> **Nivel:** Principiante → Intermedio  
> **Tiempo:** 45-60 minutos de lectura + ejercicios  
> **Requisitos:** Navegador web, editor de texto, acceso a DevTools (F12)

---

## 📚 Tabla de Contenidos

1. [JWT: Conceptos Fundamentales](#jwt-conceptos)
2. [Promesas en JavaScript](#promesas)
3. [Fetch API](#fetch)
4. [Headers y Autenticación](#headers)
5. [Cómo Funciona en Enexia](#enexia)
6. [Ejercicios Prácticos](#ejercicios)
7. [Debugging y Resolución de Problemas](#debugging)

---

## <a id="jwt-conceptos"></a>1️⃣ JWT: Conceptos Fundamentales

### ¿Qué es un JWT?

Un **JSON Web Token (JWT)** es una forma estándar y segura de enviar información entre dos partes (cliente ↔ servidor). Está compuesto por **tres partes** separadas por puntos:

```
header.payload.firma
```

#### Ejemplo Real de Enexia:

```
eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJob2xhQGhvbGEuY29tIiwicm9sZXMiOlsiUEFSVElDSVBBTlRFIl0sImlzcyI6ImVuZXhpYSIsImlhdCI6MTc4ODQ3NzI0OCwiZXhwIjoxNzg4NTYzNjQ4fQ.ueZPyvl6IWkEeSuo3VgBkIGDcOVSN_ISCCRsWeTU-O4APKnpwkoho-RHKbG6f0Ax
```

### Las Tres Partes del JWT

#### 1. **Header (Cabecera)**

Especifica el tipo de token y el algoritmo de firma:

```json
{
  "alg": "HS384",
  "typ": "JWT"
}
```

- `alg`: Algoritmo HMAC SHA-384 (el servidor lo elige automáticamente según el tamaño de la clave)
- `typ`: Tipo de token (siempre "JWT")

#### 2. **Payload (Carga)**

Contiene la información del usuario. **NOTA IMPORTANTE:** NO está cifrada, solo codificada en Base64. Cualquiera puede leerla.

```json
{
  "sub": "hola@hola.com",           // Subject (email del usuario)
  "roles": ["PARTICIPANTE"],          // Array de roles
  "iss": "enexia",                   // Issuer (quién emitió)
  "iat": 1788477248,                 // Issued At (cuándo se emitió - timestamp Unix)
  "exp": 1788563648                  // Expiration (cuándo vence - timestamp Unix)
}
```

**Claims Estándar:**
- `sub`: Subject (identidad del usuario)
- `iss`: Issuer (quién emitió el token)
- `iat`: Issued At (timestamp Unix de emisión)
- `exp`: Expiration (timestamp Unix de vencimiento)
- `roles`: Custom claim (propio de Enexia)

#### 3. **Firma**

Es un HMAC (código de autenticación) calculado usando:
- Header + Payload (en Base64URL)
- Clave secreta del servidor

```
HMACSHA384(
  base64url(header) + "." + base64url(payload),
  "clave-secreta-del-servidor"
)
```

**¿Por qué es importante?**
- Si alguien intenta alterar 1 carácter del payload, la firma no coincide
- El servidor rechaza el token adulterado
- La clave secreta NUNCA viaja al cliente, solo está en el servidor

### Seguridad: Mitos y Verdades

| Pregunta | Respuesta | Explicación |
|----------|-----------|-------------|
| ¿Está cifrado? | ❌ No | Base64 es codificación, no cifrado |
| ¿Puedo leer el payload? | ✅ Sí | Base64 es reversible |
| ¿Puedo modificarlo? | ❌ No | La firma lo detectaría |
| ¿Viaja en la URL? | ❌ No | Va en el header HTTP `Authorization` |
| ¿Es seguro para datos sensibles? | ❌ No | Nunca pongas contraseñas o DNI en el JWT |

**Regla de oro:** El JWT es para IDENTIFICAR y AUTORIZAR, no para guardar secretos.

### El Ciclo Completo en Enexia

```
1. Usuario hace login con email + contraseña
   ↓
2. Servidor valida credenciales contra la BD
   ↓
3. Servidor genera JWT (con clave secreta)
   ↓
4. Servidor devuelve JWT al cliente
   ↓
5. Cliente guarda JWT en sessionStorage
   ↓
6. Cliente envía JWT en cada petición (header Authorization)
   ↓
7. Servidor valida la firma del JWT
   ↓
8. Si es válido, procesa la petición con el usuario identificado
```

---

## <a id="promesas"></a>2️⃣ Promesas en JavaScript

### ¿Qué es una Promesa?

Una **Promesa** es un objeto que representa el resultado eventual de una operación asíncrona.

Piénsalo así:
```
"Haz esto en el futuro y avisame cuando termine, sin bloquear lo demás"
```

### Estados de una Promesa

Una promesa tiene exactamente 3 estados:

```
      PENDIENTE
         ↓
    ┌────┴────┐
    ↓         ↓
RESUELTA   RECHAZADA
   (✅)       (❌)
```

- **Pendiente:** La operación aún no termina
- **Resuelta:** La operación fue exitosa → `resolve(valor)`
- **Rechazada:** La operación falló → `reject(error)`

**Importante:** Una promesa cambia de estado una SOLA vez. No puede pasar de resuelta a rechazada.

### Sintaxis Básica

```javascript
const miPromesa = new Promise((resolve, reject) => {
  // Código asíncrono aquí
  
  if (exito) {
    resolve(resultado);  // Promesa resuelta
  } else {
    reject(error);       // Promesa rechazada
  }
});
```

### Consumir una Promesa con .then() y .catch()

```javascript
miPromesa
  .then(resultado => {
    // Se ejecuta si la promesa se resuelve
    console.log("Éxito:", resultado);
  })
  .catch(error => {
    // Se ejecuta si la promesa se rechaza
    console.log("Error:", error);
  });
```

### Ejemplo 1: Esperar Tiempo

```javascript
const esperar = new Promise((resolve) => {
  setTimeout(() => {
    resolve("¡2 segundos pasaron!");
  }, 2000);
});

esperar.then(msg => console.log(msg));
// Después de 2 segundos: "¡2 segundos pasaron!"
```

### Ejemplo 2: Promesa con Rechazo

```javascript
const validar = new Promise((resolve, reject) => {
  const edad = 15;
  
  if (edad >= 18) {
    resolve("Eres mayor de edad");
  } else {
    reject("Eres menor de edad");
  }
});

validar
  .then(msg => console.log(msg))
  .catch(error => console.log("Error:", error));

// Output: "Error: Eres menor de edad"
```

### Encadenamiento de Promesas

El verdadero poder de las promesas es el encadenamiento:

```javascript
// Obtener usuario → obtener perfil → obtener eventos

obtenerUsuario(1)
  .then(usuario => obtenerPerfil(usuario.id))
  .then(perfil => obtenerEventos(perfil.id))
  .then(eventos => console.log("Eventos:", eventos))
  .catch(error => console.log("Error:", error));
```

Cada `.then()` recibe el resultado del `.then()` anterior.

### async/await: La Forma Moderna (RECOMENDADO)

`async/await` es sintaxis moderna que hace el código asíncrono verse como síncrono:

#### Con .then():
```javascript
obtenerDatos()
  .then(datos => procesarDatos(datos))
  .then(resultado => mostrar(resultado))
  .catch(error => console.log(error));
```

#### Con async/await:
```javascript
async function main() {
  try {
    const datos = await obtenerDatos();
    const resultado = await procesarDatos(datos);
    mostrar(resultado);
  } catch (error) {
    console.log(error);
  }
}

main();
```

**¿Cuál usar?** → **async/await** siempre que sea posible. Es más legible.

---

## <a id="fetch"></a>3️⃣ Fetch API

### ¿Qué es Fetch?

`fetch()` es la función moderna para hacer peticiones HTTP en JavaScript. Devuelve una **Promesa**.

```javascript
const respuesta = await fetch(url, opciones);
const datos = await respuesta.json();
```

### Ciclo Completo de una Petición

```
1. Crear la petición (URL + opciones)
   ↓
2. Enviar al servidor (asíncrono)
   ↓
3. Esperar respuesta (await)
   ↓
4. Procesar datos (convertir a JSON)
```

### GET: Obtener Datos

```javascript
async function obtenerEventos() {
  try {
    const respuesta = await fetch(
      'http://localhost:8080/api/eventos'
    );

    const eventos = await respuesta.json();
    console.log(eventos);
  } catch (error) {
    console.log('Error:', error);
  }
}
```

**¿Qué pasa aquí?**
1. `fetch()` hace una petición GET (por defecto)
2. Espera la respuesta del servidor
3. `.json()` convierte el JSON de la respuesta a objeto JavaScript
4. Si hay error de red, cae el `catch()`

### POST: Enviar Datos

```javascript
async function crearEvento(evento) {
  try {
    const respuesta = await fetch(
      'http://localhost:8080/api/eventos',
      {
        method: 'POST',                    // Método HTTP
        headers: {
          'Content-Type': 'application/json'  // Tipo de dato que envío
        },
        body: JSON.stringify(evento)       // Cuerpo de la petición
      }
    );

    const resultado = await respuesta.json();
    console.log('Creado:', resultado);
  } catch (error) {
    console.log('Error:', error);
  }
}
```

### PUT: Actualizar Datos

```javascript
async function actualizarEvento(id, evento) {
  try {
    const respuesta = await fetch(
      `http://localhost:8080/api/eventos/${id}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(evento)
      }
    );

    const resultado = await respuesta.json();
    console.log('Actualizado:', resultado);
  } catch (error) {
    console.log('Error:', error);
  }
}
```

### DELETE: Eliminar Datos

```javascript
async function eliminarEvento(id) {
  try {
    const respuesta = await fetch(
      `http://localhost:8080/api/eventos/${id}`,
      { method: 'DELETE' }
    );

    console.log('Eliminado. Status:', respuesta.status);
  } catch (error) {
    console.log('Error:', error);
  }
}
```

### Códigos de Estado HTTP

| Rango | Significado | Ejemplos |
|-------|-------------|----------|
| **2XX** | ✅ Éxito | 200 OK, 201 Created, 204 No Content |
| **3XX** | ↪️ Redirección | 301 Moved, 302 Found |
| **4XX** | ❌ Error cliente | 400 Bad Request, 401 Unauthorized, 404 Not Found |
| **5XX** | 🔥 Error servidor | 500 Internal Server Error, 503 Service Unavailable |

### IMPORTANTE: Verificar respuesta.ok

**Fetch NO lanza error para códigos 4XX o 5XX.** Siempre debes verificar:

```javascript
// ❌ MALO: Asume que siempre es éxito
const respuesta = await fetch(url);
const datos = await respuesta.json();
// ¿Qué pasa si el servidor respondió 401? Intentarás procesar error como éxito

// ✅ BIEN: Verifica respuesta.ok
const respuesta = await fetch(url);
if (respuesta.ok) {
  const datos = await respuesta.json();
  console.log('Éxito:', datos);
} else {
  console.log('Error:', respuesta.status);
}
```

---

## <a id="headers"></a>4️⃣ Headers y Autenticación

### ¿Qué son los Headers?

Los **headers** son metadatos que acompañan a cada petición HTTP. Son pares clave-valor que le dicen al servidor información sobre la petición.

```
GET /api/eventos HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Authorization: Bearer eyJhbGc...
User-Agent: Mozilla/5.0
```

### Headers Comunes

| Header | Propósito | Ejemplo |
|--------|-----------|---------|
| **Content-Type** | Tipo de dato que envío | `application/json` |
| **Accept** | Tipo de dato que quiero recibir | `application/json` |
| **Authorization** | Token de autenticación | `Bearer eyJhbGc...` |
| **User-Agent** | Info del navegador | `Mozilla/5.0...` |
| **X-Custom-Header** | Headers personalizados | Depende de la API |

### El Header Authorization con JWT

Para enviar un JWT, usas el header `Authorization` con el formato:

```
Authorization: Bearer <token_jwt_completo>
```

**Ejemplo en Enexia:**
```
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJob2xhQGhvbGEuY29tIiwicm9sZXMiOlsiUEFSVElDSVBBTlRFIl0sImlzcyI6ImVuZXhpYSIsImlhdCI6MTc4ODQ3NzI0OCwiZXhwIjoxNzg4NTYzNjQ4fQ.ueZPyvl6IWkEeSuo3VgBkIGDcOVSN_ISCCRsWeTU-O4APKnpwkoho-RHKbG6f0Ax
```

### Flujo: Login → Guardar Token → Usar Token

#### Paso 1: Login y Obtener Token

```javascript
async function login(email, password) {
  const respuesta = await fetch(
    'http://localhost:8080/api/auth/login',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    }
  );

  const datos = await respuesta.json();
  return datos.token;  // Devuelve el JWT
}
```

#### Paso 2: Guardar Token en sessionStorage

```javascript
const token = await login('hola@hola.com', 'mi-contraseña');
sessionStorage.setItem('enexia_token', token);
```

#### Paso 3: Recuperar Token para Peticiones Autenticadas

```javascript
const token = sessionStorage.getItem('enexia_token');
```

#### Paso 4: Enviar Token en Header Authorization

```javascript
async function obtenerDatos() {
  const token = sessionStorage.getItem('enexia_token');

  const respuesta = await fetch(
    'http://localhost:8080/api/auth/me',
    {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + token  // ← Aquí va el token
      }
    }
  );

  const datos = await respuesta.json();
  console.log(datos);
}
```

### Función Helper: Petición Autenticada

Para no repetir el código del header en cada petición, crea una función helper:

```javascript
async function peticionAutenticada(url, opciones = {}) {
  const token = sessionStorage.getItem('enexia_token');

  const headersFinales = {
    'Content-Type': 'application/json',
    // Si hay token, lo agrega; si no, no hace nada
    ...(token && { 'Authorization': 'Bearer ' + token })
  };

  const respuesta = await fetch(url, {
    ...opciones,
    headers: headersFinales
  });

  return respuesta;
}

// Uso:
const respuesta = await peticionAutenticada(
  'http://localhost:8080/api/auth/me',
  { method: 'GET' }
);
const datos = await respuesta.json();
```

**El operador spread (`...`):**
- `...opciones` desempaqueta el objeto opciones
- `...(token && { ... })` añade el header solo si hay token

---

## <a id="enexia"></a>5️⃣ Cómo Funciona en Enexia

### Archivos Clave

| Archivo | Responsabilidad |
|---------|-----------------|
| `js/auth.js` | Guardar/recuperar token y usuario de sessionStorage |
| `js/api.js` | Wrapper sobre fetch() que inyecta token automáticamente |
| `pages/login-desktop-claro.html` | Formulario de login |
| `controller/AuthController.java` | Endpoints /login, /registro, /me (backend) |
| `security/JwtService.java` | Genera y valida JWT en el servidor |
| `security/JwtAuthenticationFilter.java` | Valida token en cada petición |

### El Flujo Completo: Login → Dashboard

```
1. Usuario abre login-desktop-claro.html
   ↓
2. Usuario ingresa email y contraseña
   ↓
3. JavaScript llama a API.login()
   ↓
4. Backend recibe POST /api/auth/login
   └─ AuthController valida credenciales
   └─ JwtService genera JWT
   ↓
5. Backend devuelve 200 OK con JWT
   {
     "idUsuario": 123,
     "email": "hola@hola.com",
     "token": "eyJhbGciOiJIUzM4NCJ9...",
     "roles": ["PARTICIPANTE"]
   }
   ↓
6. Frontend guarda token en sessionStorage
   sessionStorage.setItem('enexia_token', token)
   ↓
7. Frontend redirige al dashboard
   window.location.href = '../participant/dashboard.html'
   ↓
8. En el dashboard, JavaScript hace peticiones autenticadas
   const datos = await API.get('/api/auth/me')
   ↓
9. Backend valida el token en JwtAuthenticationFilter
   └─ Verifica firma
   └─ Verifica emisor
   └─ Verifica vigencia
   ↓
10. Si es válido, usuario es autenticado
    Principal principal contiene el email del usuario
   ↓
11. Backend procesa la petición con el usuario autenticado
    @GetMapping("/me")
    public ResponseEntity obtenerDatos(Principal principal) {
      // principal.getName() = email del usuario
      ...
    }
   ↓
12. Frontend recibe los datos del usuario
    {
      "email": "hola@hola.com",
      "roles": ["PARTICIPANTE"]
    }
```

### Los Métodos Convenientes en api.js

Para no tener que pasar `conToken = true` siempre, usamos métodos simplificados:

```javascript
// ❌ Antes (incómodo):
const respuesta = await API.peticion('GET', '/api/eventos', null, true);

// ✅ Ahora (limpio):
const respuesta = await API.get('/api/eventos');
```

**Métodos disponibles:**
```javascript
API.get(ruta)           // GET con token
API.post(ruta, datos)   // POST con token
API.put(ruta, datos)    // PUT con token
API.delete(ruta)        // DELETE con token
```

Internamente, cada uno llama a `API.peticion()` con `conToken = true`.

---

## <a id="ejercicios"></a>6️⃣ Ejercicios Prácticos

### Ejercicio 1: Crear una Promesa Básica

**Tarea:** Crea una función que devuelva una Promesa que se resuelve después de 3 segundos.

**Solución:**
```javascript
async function esperar3Segundos() {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve("¡3 segundos pasaron!");
    }, 3000);
  });
}

// Prueba:
const resultado = await esperar3Segundos();
console.log(resultado);  // "¡3 segundos pasaron!" (después de 3s)
```

### Ejercicio 2: Hacer una Petición GET Simple

**Tarea:** Crea una función que haga GET a `http://jsonplaceholder.typicode.com/users/1` y muestre el nombre del usuario.

**Solución:**
```javascript
async function obtenerUsuario() {
  try {
    const respuesta = await fetch(
      'http://jsonplaceholder.typicode.com/users/1'
    );
    
    if (!respuesta.ok) {
      throw new Error(`HTTP error! status: ${respuesta.status}`);
    }
    
    const usuario = await respuesta.json();
    console.log('Nombre:', usuario.name);
  } catch (error) {
    console.log('Error:', error);
  }
}

obtenerUsuario();
```

### Ejercicio 3: Hacer una Petición POST

**Tarea:** Crea una función que haga POST a `http://jsonplaceholder.typicode.com/posts` con un nuevo post y muestre el ID asignado.

**Solución:**
```javascript
async function crearPost() {
  try {
    const respuesta = await fetch(
      'http://jsonplaceholder.typicode.com/posts',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'Mi primer post',
          body: 'Contenido del post',
          userId: 1
        })
      }
    );

    const post = await respuesta.json();
    console.log('Post creado con ID:', post.id);
  } catch (error) {
    console.log('Error:', error);
  }
}

crearPost();
```

### Ejercicio 4: Cadena de Promesas

**Tarea:** Obtén un post y luego sus comentarios (dependen uno del otro).

**Solución:**
```javascript
async function obtenerPostConComentarios() {
  try {
    // Obtener post
    const respuestaPost = await fetch(
      'http://jsonplaceholder.typicode.com/posts/1'
    );
    const post = await respuestaPost.json();
    
    // Obtener comentarios del post
    const respuestaComentarios = await fetch(
      `http://jsonplaceholder.typicode.com/posts/${post.id}/comments`
    );
    const comentarios = await respuestaComentarios.json();

    console.log('Post:', post.title);
    console.log('Comentarios:', comentarios.length);
  } catch (error) {
    console.log('Error:', error);
  }
}

obtenerPostConComentarios();
```

### Ejercicio 5: Simular Login (Enexia)

**Tarea:** Simula un login, guarda el token, y luego haz una petición autenticada.

**Solución:**
```javascript
// Simular backend
function simularcLoginBackend(email, password) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (password === '123456') {
        resolve({
          token: 'token-simulado-' + email,
          email: email
        });
      } else {
        reject('Contraseña incorrecta');
      }
    }, 500);
  });
}

async function login() {
  try {
    // 1. Login
    const respuesta = await simularcLoginBackend('hola@hola.com', '123456');
    
    // 2. Guardar token
    sessionStorage.setItem('enexia_token', respuesta.token);
    
    // 3. Usar token en petición siguiente
    const token = sessionStorage.getItem('enexia_token');
    console.log('Token guardado:', token);
    
    // Simular petición autenticada
    console.log('Petición con header Authorization: Bearer ' + token);
    
  } catch (error) {
    console.log('Error:', error);
  }
}

login();
```

---

## <a id="debugging"></a>7️⃣ Debugging y Resolución de Problemas

### Ver el Token Guardado

**En DevTools (F12):**

1. **Console:**
   ```javascript
   sessionStorage.getItem('enexia_token')
   ```

2. **Application/Storage → Session Storage:**
   - Busca la clave `enexia_token`

3. **Network tab:**
   - Haz una petición
   - Verifica que el header `Authorization` está presente

### Decodificar un JWT

**Opción 1: En JavaScript**
```javascript
const token = sessionStorage.getItem('enexia_token');
const payload = JSON.parse(
  atob(token.split('.')[1])
);
console.log(payload);
```

**Opción 2: Online**
- Ve a https://jwt.io
- Pega el token completo
- Verás header + payload + firma

### Errores Comunes

#### ❌ "Token is undefined"

**Causa:** El token no se guardó en sessionStorage.

**Solución:** Verifica que `Auth.guardarSesion()` se llama después del login.

#### ❌ "401 Unauthorized"

**Causa:** El token es inválido, expiró, o no se envía correctamente.

**Solución:**
1. Verifica que el header `Authorization` está presente
2. Verifica que el formato es `Bearer <token>` (con espacio)
3. Verifica que el token no ha expirado

#### ❌ "Network error: fetch failed"

**Causa:** El servidor no responde (está apagado, wrong URL, CORS bloqueado).

**Solución:**
1. Verifica que el backend está corriendo (`gradle bootRun`)
2. Verifica la URL (¿localhost:8080?)
3. Verifica CORS en SecurityConfig

#### ❌ Promise never resolves

**Causa:** `await` está esperando algo que nunca termina.

**Solución:**
1. Agrega `console.log()` antes del `await`
2. Verifica que la función devuelve una Promesa
3. Agrega timeout en peticiones: `fetch(url, { signal: AbortSignal.timeout(5000) })`

### Depuración Paso a Paso

```javascript
async function debug() {
  console.log('1. Empezando...');
  
  try {
    console.log('2. Haciendo fetch...');
    const respuesta = await fetch('http://localhost:8080/api/eventos');
    
    console.log('3. Status:', respuesta.status);
    console.log('4. OK?', respuesta.ok);
    
    const datos = await respuesta.json();
    console.log('5. Datos:', datos);
    
  } catch (error) {
    console.log('ERROR:', error);
  }
}

debug();
```

---

## 📖 Referencia Rápida

### Promesas
```javascript
new Promise((resolve, reject) => { ... })
promesa.then(resultado => { ... })
promesa.catch(error => { ... })
await promesa
async function nombre() { ... }
```

### Fetch
```javascript
fetch(url)
fetch(url, { method, headers, body })
respuesta.ok
respuesta.status
await respuesta.json()
```

### Headers
```javascript
headers: {
  'Content-Type': 'application/json',
  'Authorization': 'Bearer ' + token
}
```

### sessionStorage
```javascript
sessionStorage.setItem('clave', valor)
sessionStorage.getItem('clave')
sessionStorage.removeItem('clave')
sessionStorage.clear()
```

---

## 🎯 Siguientes Pasos

1. **Lee la guía interactiva:** https://claude.ai/code/artifact/bb6bf1a0-81f3-4879-9b2a-97d077c08f89

2. **Abre DevTools (F12) y prueba:**
   ```javascript
   // Obtener usuario desde jsonplaceholder
   fetch('http://jsonplaceholder.typicode.com/users/1')
     .then(r => r.json())
     .then(u => console.log(u))
   ```

3. **Practica con tu proyecto:**
   - Abre el login en `http://localhost:8000/pages/auth/login-desktop-claro.html`
   - Haz login
   - Ve a la página de prueba en `http://localhost:8000/pages/prueba-token.html`
   - Observa cómo se inyecta el token automáticamente

4. **Lee el código:**
   - `js/auth.js`: Cómo se guarda/recupera el token
   - `js/api.js`: Cómo se inyecta en headers
   - `security/JwtService.java`: Cómo se genera/valida en backend

---

## 📞 Resolución de Dudas

**P: ¿Dónde pongo el código de los ejercicios?**  
R: En DevTools Console (F12) o en un archivo `<script>` en el HTML.

**P: ¿Es unsafe guardar el token en sessionStorage?**  
R: Es más seguro que localStorage (se borra al cerrar), pero menos que HttpOnly cookies (Sprint 2).

**P: ¿Puedo leer el email del payload sin decodificar?**  
R: No, está en Base64. Necesitas decodificar con `atob()` o usar jwt.io.

**P: ¿Qué pasa si el token expira?**  
R: El servidor devuelve 401. El frontend debe pedir otro login.

---

---

## 8️⃣ Cookies HttpOnly: La Forma Segura

### ¿Por qué las Cookies HttpOnly?

Una **cookie HttpOnly** es la forma SEGURA y moderna de almacenar tokens de autenticación:

- **JavaScript NO puede leerla** → Protege contra XSS (inyección maliciosa)
- **Se envía automáticamente** → Funciona con navegación de páginas
- **SameSite=Strict** → Protección contra CSRF

### Comparación: sessionStorage vs HttpOnly

| Aspecto | sessionStorage | HttpOnly Cookie |
|---------|---|---|
| **Vulnerable a XSS** | ❌ SÍ | ✅ NO |
| **Se envía automáticamente** | ❌ NO | ✅ SÍ |
| **Funciona con window.location** | ❌ NO | ✅ SÍ |
| **JavaScript puede leerlo** | ✅ SÍ | ❌ NO |
| **Complejidad** | Baja | Media |

### Implementación desde Cero

#### Paso 1: Backend - Generar Cookie HttpOnly

```java
// En AuthService.java (método login exitoso)

String token = jwtService.generarToken(usuario.getEmail(), roles);

// Crear ResponseCookie HttpOnly
ResponseCookie cookie = ResponseCookie
    .from("auth_token", token)
    .httpOnly(true)           // ← JavaScript NO puede leer
    .secure(true)             // ← Solo HTTPS (producción)
    .sameSite("Strict")       // ← Protección CSRF
    .path("/")
    .maxAge(86400)            // ← 24 horas
    .build();

response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

// LA RESPUESTA JSON NO INCLUYE EL TOKEN
// La cookie se envía en un header separado
return new UsuarioLoginResponse(
    usuario.getIdUsuario(),
    usuario.getEmail(),
    null,  // ← NO incluir token aquí
    "Bearer",
    roles);
```

#### Paso 2: Backend - Leer Cookie en Filtro JWT

```java
// En JwtAuthenticationFilter.java

private String extraerTokenDeCookie(HttpServletRequest request) {
    if (request.getCookies() == null) {
        return null;
    }

    for (Cookie cookie : request.getCookies()) {
        if ("auth_token".equals(cookie.getName())) {
            return cookie.getValue();
        }
    }
    return null;
}

// En el filtro:
String token = extraerTokenDeCookie(request);
if (token != null && jwtService.esValido(token)) {
    // Autenticar usuario
}
```

#### Paso 3: Frontend - Fetch Automático

```javascript
// ✅ La cookie se envía AUTOMÁTICAMENTE
fetch('http://localhost:8080/api/eventos', {
    credentials: 'include'  // ← IMPORTANTE: incluye cookies
});

// ✅ También funciona con navegación normal
window.location.href = '/pages/dashboard.html';
// La cookie se envía automáticamente

// ❌ INCORRECTO: Cookie NO se envía (sin credentials)
fetch('http://localhost:8080/api/eventos');
```

#### Paso 4: Frontend - Verificar Autenticación

```javascript
// Ya no puedes leer el token directamente
// Necesitas un endpoint que devuelva si estás autenticado

async function verificarAutenticacion() {
    const respuesta = await fetch(
        'http://localhost:8080/api/auth/me',
        { credentials: 'include' }
    );

    if (respuesta.status === 401) {
        // No autenticado
        window.location.href = '/pages/auth/login.html';
        return;
    }

    const usuario = await respuesta.json();
    console.log('Autenticado como:', usuario.email);
}
```

### Propiedades Críticas de la Cookie

| Propiedad | Valor | Razón |
|-----------|-------|-------|
| **HttpOnly** | true | Protege contra XSS |
| **Secure** | true | Solo HTTPS (producción) |
| **SameSite** | Strict | Protección CSRF |
| **Path** | / | Disponible en todo el sitio |
| **MaxAge** | 86400 | Expira en 24 horas |

### Punto Importante: credentials

Cuando haces fetch **CROSS-ORIGIN** (diferente dominio), DEBES incluir `credentials: 'include'`:

```javascript
// ❌ MALO: Cookie NO se envía (Same-Origin: OK, Cross-Origin: NO)
fetch('http://localhost:8080/api/eventos');

// ✅ BIEN: Cookie se envía
fetch('http://localhost:8080/api/eventos', {
    credentials: 'include'
});
```

### Resumen

- **Para desarrollo:** Puedes usar `secure(false)` con HTTP
- **Para producción:** Siempre `secure(true)` con HTTPS
- **El estándar moderno:** Google, GitHub, Facebook usan HttpOnly cookies

**Creado:** Septiembre 2026  
**Para:** Enexia Sprint 1  
**Nivel:** Principiante → Intermedio
