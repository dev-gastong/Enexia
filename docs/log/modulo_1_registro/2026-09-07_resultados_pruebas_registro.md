# Resultados de pruebas — Registro de Persona Física

**Fecha de ejecución:** 2026-09-07, 06:42–06:47 (UTC-3)
**Módulo:** 1 — Gestión de Usuarios y Autenticación · RF-1.1
**Cambios evaluados:** [`2026-09-07_implementacion_registro_pf.md`](./2026-09-07_implementacion_registro_pf.md)

## Resultado global

| Suite | Herramienta | Casos | ✅ | ❌ | Duración |
|-------|-------------|------:|---:|---:|---------:|
| Unitaria — `AuthService.registrar()` | JUnit 5 + Mockito | 15 | 15 | 0 | 0,17 s |
| E2E — Formulario de registro | Playwright (Chromium) | 12 | 12 | 0 | 24,9 s |
| Regresión — Login + contexto | JUnit 5 + Playwright | 3 | 3 | 0 | 8,4 s |
| API — Endpoints de registro | Postman / Newman | 17 peticiones · **95 aserciones** | 95 | 0 | 2,7 s |
| **TOTAL** | | **30 tests + 95 aserciones** | **todos** | **0** | |

```
BUILD SUCCESSFUL in 48s
30 tests completed, 0 failed, 0 skipped
```

**Entorno:** Windows 10 · OpenJDK Temurin 17.0.19 · Gradle 9.5.1 · Spring Boot 4.1.0 ·
MariaDB (XAMPP) puerto 3306 · Playwright Java 1.61.0 · Newman 6.2.2 · backend en
`localhost:8080`.

---

## 1. Suite unitaria — `AuthServiceRegistroTest` (15/15)

Sin Spring, sin base de datos, sin navegador: todas las dependencias del service son
`@Mock`. Por eso corre en 170 ms y sirve como red de seguridad en cada guardado.

### Camino feliz (5/5)

| Test | ms | Verifica |
|------|---:|----------|
| `PARTICIPANTE: crea la cuenta ACTIVA y recibe un unico rol` | 46 | Paso 1.1.6 · un solo INSERT en `usuario_rol` |
| `ORGANIZADOR: recibe ORGANIZADOR + PARTICIPANTE` | 8 | Regla de CLAUDE.md · dos INSERT |
| `El email se normaliza a minusculas y los textos se recortan` | 5 | `" Maria.G@ENEXIA.COM "` → `maria.g@enexia.com` |
| `La cuenta nace sin penalizaciones de login y sin fecha_baja` | 8 | RF-1.6 · `intentos=0`, `captcha=false`, `fechaBaja=null` |
| `Se deja constancia del alta en auditoria` | 8 | `ACCION_REGISTRO_EXITOSO` en `Historial_Interacciones` |

> **Por qué importa la normalización:** sin ella `Maria@x.com` y `maria@x.com` serían dos
> cuentas distintas y el chequeo de unicidad no serviría de nada.

### Contraseña — paso 1.1.4 (2/2)

| Test | ms | Verifica |
|------|---:|----------|
| `Se persiste el hash BCrypt, nunca la contrasena en claro` | 36 | Lo guardado ≠ lo enviado |
| `Contrasenas distintas: ReglaNegocioException y no se escribe nada` | 8 | `verifyNoInteractions` sobre los 3 repositorios |

> La segunda usa `verifyNoInteractions`: no basta con que lance la excepción, hay que
> probar que **ni siquiera consultó la base**. La comparación va antes que todo lo demás.

### Unicidad — paso 1.1.1 (4/4)

| Test | ms | Verifica |
|------|---:|----------|
| `Email repetido: 409 RecursoDuplicado` | 1252 | Corta antes de `personaRepository.save()` |
| `Nickname repetido: 409 RecursoDuplicado` | 11 | Ídem |
| `DNI repetido: 409 RecursoDuplicado (DFD 7.1.2)` | 12 | **Control nuevo de esta sesión** |
| `El DNI se consulta ya recortado` | 10 | `" 40123456 "` → consulta `"40123456"` |

### Moderación — paso 1.1.1A (2/2)

| Test | ms | Verifica |
|------|---:|----------|
| `Nickname ofensivo: 422, se audita el rechazo y no se persiste nada` | 9 | Auditoría en transacción aparte + cero escrituras |
| `Se moderan nickname, nombre y apellido` | 7 | Los tres campos visibles para terceros |

> El primero verifica **el orden**: la moderación corre *antes* de escribir. Si corriera
> después, el texto ofensivo quedaría en la base aunque el alta se rechace.

### Catálogos (2/2)

| Test | ms | Verifica |
|------|---:|----------|
| `Sin estado ACTIVO cargado: ReglaNegocioException explicita` | 7 | Mensaje accionable, no `NoSuchElementException` |
| `Sin el rol cargado: ReglaNegocioException explicita` | 6 | Ídem |

---

## 2. Suite E2E — `RegistroTest` (12/12)

Navegador real → HTML/JS → Spring → MariaDB. Es la única capa capaz de detectar los
errores de integración que tenía este módulo.

### Estructura de la pantalla (3/3)

| Test | s | Verifica |
|------|--:|----------|
| `La pantalla de registro carga con su titulo` | 1,51 | Humo |
| `El bloque de domicilio personal ya no esta en el formulario` | 1,23 | `#calle` y `#provincia` con `hasCount(0)` |
| `Persona Juridica avisa que aun no esta disponible y bloquea el envio` | 1,66 | Aviso visible + botón `isDisabled()` |

### Camino feliz (2/2)

| Test | s | Verifica |
|------|--:|----------|
| `Alta de PARTICIPANTE: llega a la pantalla de cuenta activa` | 1,96 | `201` → redirección → chip "Participante" + saludo con nickname |
| `Alta de ORGANIZADOR: la pantalla de exito refleja el rol elegido` | 2,85 | Chip "Organizador" |

> El segundo prueba algo que la unitaria no puede: que el rol **viajó de verdad** por la
> red y no es un texto fijo del HTML — que es exactamente lo que era antes.

### Errores del backend vistos desde la pantalla (5/5)

| Test | s | Verifica |
|------|--:|----------|
| `Email duplicado: el aviso lo explica y no se navega` | 2,31 | Alta + reintento · `409` traducido · sigue en el formulario |
| `DNI duplicado: rechazado aunque cambien email y nickname` | 2,32 | **Control nuevo**, verificado punta a punta |
| `Nickname ofensivo: la moderacion lo frena antes de crear la cuenta` | 1,62 | `422` → "moderacion" en el aviso |
| `Contrasena debil: el 400 se pinta sobre el campo password` | 1,62 | `.campo--invalido` + `.campo__error` con texto |
| `Contrasenas distintas: se avisa sin ir al backend` | 1,48 | Aviso + botón vuelve a habilitarse |

### Seguridad y continuidad (2/2)

| Test | s | Verifica |
|------|--:|----------|
| `El formulario nunca deja la contrasena en la URL` | 2,39 | Sin `preventDefault()` el `<form>` haría GET y arrastraría las credenciales a la query string, el historial y los logs |
| `La cuenta recien creada puede iniciar sesion` | 3,96 | **Circuito completo:** registro → login → dashboard |

> El último es el más valioso de la suite: si el alta guardara la clave en claro o con otro
> algoritmo, el `BCrypt.matches()` del login fallaría aquí y en ningún otro lugar.

### Regresión — pruebas preexistentes (3/3)

`EnexiaApplicationTests.contextLoads` (0,41 s) · `LoginTest.ejecutarPrueba` (1,66 s) ·
`LoginTest.loginExitoso` (6,36 s). Ninguna se rompió con los cambios.

---

## 3. Suite de API — Newman (17 peticiones · 95/95 aserciones)

Tres aserciones transversales corren en **toda** petición: respuesta JSON, tiempo < 4 s
(RNF de escritura) y **la respuesta no filtra credenciales** — ni `Segura123` ni un prefijo
`$2a$`/`$2b$` de BCrypt pueden aparecer en el cuerpo.

| # | Petición | HTTP | ms | Aserciones |
|---|----------|-----:|---:|-----------:|
| 00 | Sonda: el backend está arriba | 400 | 27 | 4 ✅ |
| 01 | Alta PARTICIPANTE | **201** | 367 | 9 ✅ |
| 02 | Alta ORGANIZADOR: recibe dos roles | **201** | 295 | 6 ✅ |
| 03 | Email duplicado | 409 | 8 | 6 ✅ |
| 04 | Nickname duplicado | 409 | 8 | 5 ✅ |
| 05 | DNI duplicado (DFD 7.1.2) | 409 | 9 | 5 ✅ |
| 06 | Cuerpo vacío: validación por campo | 400 | 10 | 6 ✅ |
| 07 | Contraseña débil | 400 | 4 | 5 ✅ |
| 08 | DNI con formato inválido | 400 | 5 | 5 ✅ |
| 09 | Email mal formado | 400 | 5 | 5 ✅ |
| 10 | Fecha de nacimiento futura | 400 | 4 | 5 ✅ |
| 11 | Contraseñas que no coinciden | 400 | 5 | 6 ✅ |
| 12 | **Escalada de privilegios: perfil ADMINISTRADOR** | 400 | 4 | 6 ✅ |
| 13 | Moderación: nickname ofensivo | 422 | 13 | 6 ✅ |
| 14 | Moderación: evasión con l33t speak | 422 | 20 | 5 ✅ |
| 15 | La cuenta creada puede iniciar sesión | 200 | 379 | 6 ✅ |
| 16 | El token del alta sirve en `/me` | 200 | 122 | 5 ✅ |

**Tiempo medio: 75 ms** · mínimo 4 ms · máximo 379 ms · duración total 2,7 s.

### Dos casos que merecen destacarse

**#12 — Escalada de privilegios.** No es una prueba de validación sino **de seguridad**. El
registro es un endpoint público (`permitAll()`); si aceptara cualquier valor de `perfil`,
bastaría con editar el JSON en las DevTools para crearse una cuenta de administrador. El
`@Pattern(regexp = "^(PARTICIPANTE|ORGANIZADOR)$")` del DTO lo impide, y se verifica además
que la respuesta **no traiga `idUsuario`**: no se creó nada.

**#14 — Evasión l33t.** `b0lud0` es rechazado. `ModeracionTextoService.normalizar()` deshace
la sustitución de caracteres antes de comparar, así que cambiar letras por números no
alcanza para pasar el filtro.

### Cumplimiento del RNF de rendimiento

CLAUDE.md fija **4 s como máximo para escrituras**. La operación más lenta —el alta, que
incluye el ciclo de BCrypt— tardó **367 ms**, un **9 % del presupuesto**. Amplio margen.

---

## 4. Fallos encontrados y corregidos durante la ejecución

Ambos eran errores **de las pruebas**, no del código de producción. Se documentan porque
son trampas que van a repetirse.

### 4.1 `tipoToken` vs `tipo` (Newman, petición 15)

```
AssertionError  Emite un JWT
                expected undefined to deeply equal 'Bearer'
```

La aserción esperaba `cuerpo.tipo`; el campo de `UsuarioLoginResponse` es `tipoToken`.
Corregido en la colección. **Primera corrida: 94/95. Tras la corrección: 95/95.**

### 4.2 Radios ocultos no clickeables (Playwright, 2 tests)

```
TimeoutError: Timeout 30000ms exceeded.
  - attempting click action
  - <label class="tipo__opcion" for="perfil-participante">…</label>
    intercepts pointer events
```

Los `<input type="radio">` del toggle están ocultos por diseño
(`opacity:0; pointer-events:none`); el control real es el `<label>`.

**La corrección correcta no era forzar el click** con `setForce(true)` —eso habría hecho
pasar el test simulando algo que ningún usuario puede hacer—, sino localizar la etiqueta:

```java
// ANTES: page.locator("#tipo-pj")  →  el label intercepta el click
this.tipoPersonaJuridica = page.locator("label[for='tipo-pj']");
```

**Primera corrida: 10/12. Tras la corrección: 12/12.**

> El caso `PARTICIPANTE` pasaba y el de `ORGANIZADOR` no, porque el primero ya viene
> marcado por defecto y `check()` es idempotente: no hacía falta ningún click. Un fallo
> así se esconde fácil si solo se prueba el camino por defecto.

---

## 5. Qué queda sin cobertura

| Área | Motivo |
|------|--------|
| **Registro de Persona Jurídica** | No implementado (Sprint 2). Lo único que se prueba es que la pantalla lo bloquea con un aviso |
| **Validación de CUIT** | No existe el validador (DFD 7.3) |
| **Concurrencia en el alta** | Dos registros simultáneos con el mismo email/DNI dependen del índice `UNIQUE` de la base, no del chequeo previo. Sin cubrir |
| **Escenarios sin base de datos** | La E2E asume MariaDB arriba; no se prueba la degradación con la base caída |
| **Compatibilidad de navegadores** | Solo Chromium. Los RNF piden además Firefox, Safari y Edge |
| **Responsive** | Sin pruebas de viewport móvil |

---

## 6. Reproducir esta corrida

```bash
cd enexia && ./gradlew test
```

Requiere MariaDB en el 3306 y el **puerto 8080 libre** (las E2E levantan Spring por sí
mismas con `@SpringBootTest(DEFINED_PORT)`).

```bash
cd enexia/pruebas/postman && npx newman run Enexia-Registro.postman_collection.json -e Enexia-Local.postman_environment.json
```

Requiere el backend ya corriendo (`./gradlew bootRun`).

**Artefactos generados:**

- Reporte HTML: `enexia/build/reports/tests/test/index.html`
- XML JUnit: `enexia/build/test-results/test/*.xml`
- Traza Playwright: `enexia/build/traces/trace.zip` (abrir con `npx playwright show-trace`)
- Capturas de fallos: `enexia/evidencias/<nombre del test>.png` (solo si algo falla)

---

## 7. Conclusión

Las tres capas pasan al 100 %. El registro de Persona Física está **operativo de extremo a
extremo** y con red de seguridad: formulario → API → base de datos → login.

El módulo queda cerrado para Persona Física. Persona Jurídica arranca con los bloqueantes
ya inventariados en la sección 5 del documento de implementación — en particular, la
decisión de modelo sobre si `Persona_Juridica` cuelga de `Persona`, que conviene resolver
**antes** de escribir la primera línea de esa rama.
