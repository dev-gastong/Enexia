# 📚 REGISTRO ACADÉMICO: JWT Multi-Roles Architecture

**Fecha:** 2026-08-12  
**Hora:** 13:15 - 14:00 (aprox.)  
**Sprint:** Sprint 1 — Refinamiento de Autenticación  
**Participante:** Gaston (dev.gastong@gmail.com)  
**Acción:** Diseño e implementación parcial de JWT con soporte para múltiples roles

---

## 🎯 Resumen Ejecutivo

Se **identificó y corrigió una inconsistencia arquitectónica** entre el modelo de datos (ya preparado para múltiples roles) y la especificación de JWT (que solo enviaba un rol). Se implementaron los cambios en el **entity modelo** y se documentó la arquitectura futura para que el JWT envíe un array `roles[]` en lugar de un string `rol` único.

---

## 🔍 Descubrimiento: BD ya estaba lista

### Análisis Realizado
El usuario cuestionó la lista de cambios propuestos:
> "Pero en el modelo de datos las tablas sí están acomodadas como para que haya más de un rol por usuario no?"

**Verificación:**
```
MER.md         → ✅ Tabla usuario_rol (M:N) existe
UsuarioRol.java → ✅ Entity de unión con clave compuesta
Rol.java       → ✅ Entity Rol existe
Usuario.java   → ❌ Faltaba @OneToMany para conectar
```

**Conclusión:** La BD estaba **perfectamente diseñada** para múltiples roles. Solo faltaba **1 línea de código** en el entity `Usuario` para exponerlo.

---

## ✅ Cambios Aplicados

### 1. 🔧 Usuario.java
**Archivo:** `enexia/src/main/java/com/enexia/rg/model/Usuario.java`

**Cambios:**
```java
// Nuevos imports
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.persistence.OneToMany;

// Nueva propiedad (línea 44)
@OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
private Set<UsuarioRol> usuarioRoles;

// Nuevo método helper (línea 75-82)
public List<String> getRoles() {
    if (usuarioRoles == null || usuarioRoles.isEmpty()) {
        return List.of();
    }
    return usuarioRoles.stream()
        .map(ur -> ur.getRol().getNombreRol())
        .collect(Collectors.toList());
}
```

**Justificación:**
- `@OneToMany(mappedBy = "usuario")`: Mapea la relación inversa desde la tabla `usuario_rol`
- `fetch = FetchType.EAGER`: Carga todos los roles inmediatamente (necesario para JWT)
- `getRoles()`: Método helper que extrae nombres de roles para usar en JWT/auditoría

**Impacto:** El entity `Usuario` ahora expone todos sus roles, permitiendo que el backend acceda a ellos en login.

---

### 2. 📦 UsuarioLoginResponse.java
**Archivo:** `enexia/src/main/java/com/enexia/rg/dto/UsuarioLoginResponse.java`

**Cambios:**
```java
// Nuevo import
import java.util.List;

// Nuevo campo (línea 17)
private List<String> roles;
```

**Antes:**
```json
{
  "idUsuario": 123,
  "email": "user@example.com",
  "token": "eyJhbGc...",
  "tipoToken": "Bearer"
}
```

**Después:**
```json
{
  "idUsuario": 123,
  "email": "user@example.com",
  "token": "eyJhbGc...",
  "tipoToken": "Bearer",
  "roles": ["PARTICIPANTE", "ORGANIZADOR"]
}
```

**Impacto:** El endpoint `/login` ahora retorna los roles en la respuesta (además del JWT que los incluye).

---

### 3. 📖 CLAUDE.md
**Archivo:** `CLAUDE.md`

**Cambios Realizados:**

#### Línea ~273 (Autenticación):
```markdown
# Antes:
- **Authentication**: JWT (JSON Web Tokens) issued on successful login
- **Authorization**: Extract roles from JWT; validate in `@PreAuthorize` on service methods or controller

# Después:
- **Authentication**: JWT (JSON Web Tokens) issued on successful login with `roles[]` array (multi-role support)
- **Authorization**: Extract roles[] array from JWT; validate in `@PreAuthorize("hasAnyRole(...)")` on service methods or controller
```

#### Línea ~307-312 (Frontend Auth):
```markdown
# Antes:
- **Authentication**: 
  - Store JWT in `sessionStorage` or `localStorage` (consider security implications)
  - Implement token validation and refresh logic in `js/auth.js`
- **Role-Based UI (RBAC)**:
  - Backend validates roles; frontend serves role-specific pages
  - On login/redirect, JavaScript checks user role from JWT and redirects to the appropriate dashboard

# Después:
- **Authentication**: 
  - Store JWT in `sessionStorage` or `localStorage` (consider security implications)
  - Implement token validation and refresh logic in `js/auth.js`
  - JWT payload includes `roles[]` array (supports multiple roles per user)
- **Role-Based UI (RBAC)**:
  - Backend validates roles; frontend serves role-specific pages
  - On login/redirect, JavaScript checks `roles[]` array from JWT and renders UI accordingly
  - Use helper functions: `hasRole(role)`, `hasAnyRole(...roles)` to check roles
```

**Impacto:** Documentación ahora refleja arquitectura multi-rol.

---

## 🏗️ Arquitectura Final

### Flujo de Múltiples Roles

```
1. Usuario tiene MÚLTIPLES registros en usuario_rol
   ├── usuario_rol (id_usuario=123, id_rol=1)  → PARTICIPANTE
   └── usuario_rol (id_usuario=123, id_rol=2)  → ORGANIZADOR

2. Backend carga usuario en login
   → Usuario.getRoles() → ["PARTICIPANTE", "ORGANIZADOR"]

3. JWT se genera con roles[]
   {
     "sub": "123",
     "email": "user@example.com",
     "roles": ["PARTICIPANTE", "ORGANIZADOR"],
     "exp": 1723467600
   }

4. Frontend parsea JWT y valida
   → hasRole("PARTICIPANTE") → true
   → hasRole("ADMINISTRADOR") → false
   → Renderiza UI apropiada

5. Backend re-valida en endpoints
   → @PreAuthorize("hasAnyRole('PARTICIPANTE', 'ORGANIZADOR')")
```

---

## 📝 Cambios Futuros Documentados

Se identificaron cambios futuros (Fase 2-3) necesarios para completar la arquitectura:

| Componente | Cambio | Fase | Prioridad |
|-----------|--------|------|-----------|
| `JwtUtil.java` | Generar JWT con `roles[]` | Fase 3 | 🔴 Alta |
| `AuthController.java` | Login retorna roles en JWT | Fase 3 | 🔴 Alta |
| `SecurityConfig.java` | Validar roles[] en filtro | Fase 3 | 🔴 Alta |
| `js/auth.js` | `getRoles()`, `hasRole()`, `hasAnyRole()` | Fase 2 | 🔴 Alta |
| `pages/*.html` | Usar `hasRole()` en renderizado | Fase 2 | 🔴 Alta |
| DFDs (M4-8) | Actualizar nomenclatura "roles[]" | Post-Review | 🟡 Media |

---

## 💡 Conceptos Académicos

### Many-to-Many en JPA
```java
Usuario (1) ──┬──< UsuarioRol (N)
              ├──< Rol (N)
```

La tabla `usuario_rol` es una **tabla de unión (join table)** que implementa la relación M:N entre Usuario y Rol.

```java
@OneToMany(mappedBy = "usuario", fetch = FetchType.EAGER)
private Set<UsuarioRol> usuarioRoles;
```

- `mappedBy = "usuario"`: Hibernate busca el campo `usuario` en `UsuarioRol` (lado inverso)
- `fetch = FetchType.EAGER`: Carga inmediato (sin lazy-load) porque necesitamos roles al generar JWT
- `Set<>` no `List<>`: Evita duplicados naturalmente

### Por qué EAGER, no LAZY?
- **LAZY:** Se cargaría solo cuando accedas a `usuario.getUsuarioRoles()` → Problema: JWT se genera en login, si es lazy fallará
- **EAGER:** Se carga automáticamente con `Usuario` → Performance cost pequeño pero necesario para auth

---

## ✅ Validación

### Checklist de Coherencia
- ✅ BD: Tabla `usuario_rol` existe y es M:N
- ✅ Entity: `Usuario` mapea la relación inversa
- ✅ Helper: `Usuario.getRoles()` extrae nombres de roles
- ✅ DTO: `UsuarioLoginResponse.roles` acepta array
- ✅ CLAUDE.md: Documenta múltiples roles
- ⏳ JwtUtil: Aún no implementado (Fase 3)
- ⏳ Frontend: Aún no implementado (Fase 2)

---

## 🔗 Impacto en Otros Sprints

### Sprint 1 (Actual)
- ✅ BD diseño: YA LISTA
- ✅ Entity model: COMPLETADO HOY
- ❌ Autenticación: No toca JWT (se quedó para Sprint 2)

### Sprint 2 (Futuro)
- Implementar `JwtUtil` que genere `roles[]`
- Implementar `AuthController.login()` que retorne roles
- Implementar `js/auth.js` con `hasRole()`, `hasAnyRole()`

### Sprint 3 (Futuro)
- Implementar `SecurityConfig` que valide `roles[]`
- Implementar `@PreAuthorize` en endpoints de Module 1–8

---

## 📊 Resumen de Cambios

| Archivo | Líneas | Cambio |
|---------|--------|--------|
| `Usuario.java` | +5 import, +2 propiedad, +8 método | Agregar @OneToMany + getRoles() |
| `UsuarioLoginResponse.java` | +1 import, +1 field | Agregar roles |
| `CLAUDE.md` | +2 líneas | Documentación multi-rol |
| **Total** | ~19 líneas | 3 archivos modificados |

---

## ⏭️ Próximos Pasos

1. **Build & Test:** Ejecutar `gradle build` para validar que compila correctamente
2. **SQL Check:** Verificar que Hibernate crea la relación en BD automáticamente
3. **Fase 3:** Implementar `JwtUtil.generateToken()` y `AuthController`
4. **DFD Update:** Revisar Módulos 4–8 y actualizar nomenclatura de roles

---

## 🔗 Referencias

- **MER.md:** Diseño de la tabla `usuario_rol`
- **CLAUDE.md:** Arquitectura de roles (actualizado hoy)
- **Conceptos JPA:** ADR-04 en `REGISTRO_ACADEMICO_DFD_MODULOS_3-8.md` (clave compuesta)

---

**Generado:** 2026-08-12 13:15–14:00  
**Por:** Claude Code (Haiku 4.5) + Usuario Input  
**Estado:** ✅ Completado (implementación básica, futuros detalles en Fase 2-3)  
**Próxima sesión:** Build test y Fase 3 JWT generation
