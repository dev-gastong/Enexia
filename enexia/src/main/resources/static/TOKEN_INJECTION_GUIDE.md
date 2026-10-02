# 📋 Guía: Inyección de Token JWT en Requests

## Problema Original
El token se guardaba en `sessionStorage` pero **no se inyectaba automáticamente** en las requests posteriores al login.

## Solución
Se agregaron 4 métodos convenientes en `js/api.js` que inyectan el token automáticamente:

### Métodos Disponibles

```javascript
// GET autenticado
const respuesta = await API.get('/api/eventos');

// POST autenticado
const respuesta = await API.post('/api/eventos', { titulo: 'Mi evento' });

// PUT autenticado
const respuesta = await API.put('/api/eventos/1', { titulo: 'Evento editado' });

// DELETE autenticado
const respuesta = await API.delete('/api/eventos/1');
```

### ¿Cómo Funciona?

Cada método:
1. Obtiene el token de `sessionStorage` usando `Auth.token()`
2. Agrega el header `Authorization: Bearer <token>` automáticamente
3. Devuelve la respuesta estándar con formato `{ ok, status, cuerpo, ... }`

### Ejemplo Completo

```javascript
// 1. Usuario hace login desde login-desktop-claro.html
// → Token se guarda en sessionStorage (Auth.guardarSesion())

// 2. Usuario navega a dashboard.html
// 3. En dashboard.html hacemos una request autenticada:

const resultado = await API.get('/api/auth/me');

if (resultado.ok) {
    console.log('Usuario:', resultado.cuerpo.email);
    console.log('Roles:', resultado.cuerpo.roles);
} else {
    console.error('Error:', resultado.status, resultado.cuerpo);
}
```

### Qué Recibe el Backend

```
GET /api/auth/me HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJob2xhQGhvbGEuY29tIi...
Content-Type: application/json
```

El filtro JWT (`JwtAuthenticationFilter`) valida el token y lo pasa al endpoint.

## Verificación Funcional

Abre esta página para probar:
```
http://localhost:8000/pages/prueba-token.html
```

Después de hacer login en `login-desktop-claro.html`, la página `prueba-token.html` mostrará:
- ✓ Estado del token (activo, tiempo restante)
- ✓ Log de requests con headers enviados
- ✓ Respuesta del servidor

## Cambios Realizados

### Frontend (`js/api.js`)
```javascript
// ANTES: debías hacer esto
const respuesta = await API.peticion('GET', '/ruta', null, true);

// AHORA: puedes hacer esto
const respuesta = await API.get('/ruta');
```

### Backend (`controller/AuthController.java`)
```java
@GetMapping("/me")
public ResponseEntity<UsuarioLoginResponse> obtenerDatosActual(Principal principal) {
    return ResponseEntity.ok(authService.obtenerDatosActual(principal.getName()));
}
```

### Backend (`service/AuthService.java`)
```java
public UsuarioLoginResponse obtenerDatosActual(String email) {
    Usuario usuario = usuarioRepository.buscarActivoPorEmailConRoles(email)
        .orElseThrow(() -> new CredencialesInvalidasException("Usuario no encontrado"));
    
    return new UsuarioLoginResponse(usuario.getId(), usuario.getEmail(), 
        null, "Bearer", usuario.getRoles());
}
```

## Notas Importantes

1. **El token está en sessionStorage**, visible en DevTools:
   - Abre Console → escribe `sessionStorage.getItem('enexia_token')`

2. **Solo endpoints autenticados usan los métodos con token**:
   - `API.registro()` → sin token (público)
   - `API.login()` → sin token (público)
   - `API.get('/api/eventos')` → con token (autenticado)

3. **Seguridad**:
   - El token viaja en `Authorization: Bearer <token>` (header estándar HTTP)
   - Se borra al cerrar la pestaña (sessionStorage)
   - Para Sprint 2: migrar a HttpOnly cookies

4. **Debugging**:
   - Abre DevTools → Network
   - Haz una request desde `prueba-token.html`
   - Busca la request a `/api/auth/me`
   - Verifica que el header `Authorization` está presente

## Próximos Pasos

1. **En tus páginas autenticadas** (dashboards, etc.):
   ```javascript
   // Verificar que el usuario está autenticado
   if (!Auth.exigirSesion()) return;
   
   // Hacer requests con token automático
   const eventos = await API.get('/api/eventos');
   ```

2. **Para endpoints que necesiten token**, asegúrate de:
   - Anotarlos con `@GetMapping`, `@PostMapping`, etc.
   - No incluirlos en `permitAll()` de SecurityConfig
   - Recibir `Principal principal` para acceder al usuario

3. **Testing en Postman**:
   - Obtén el token del login
   - Copia el token completo
   - En Postman: Authorization → Bearer Token → pega el token
   - Haz la request

---

**Cualquier duda**: revisa `prueba-token.html` o ejecuta la request de ejemplo desde DevTools Console.
