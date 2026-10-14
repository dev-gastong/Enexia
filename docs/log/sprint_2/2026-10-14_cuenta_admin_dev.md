# Cuenta administrador de desarrollo (2026-10-14)

Cuenta creada directamente en la base de dev para poder probar el panel de administración
(`/pages/admin/*`). No existe todavía un flujo en la app para otorgar `ADMINISTRADOR`.

| Campo | Valor |
|---|---|
| nickname (login) | `admin_enexia` |
| contraseña | `Ax9#4eba6uWTZxVN` |
| email | `admin@enexia.local` |
| id_usuario | 956 |
| rol | `ADMINISTRADOR` (solo ese) |
| DNI ficticio | `90000001` |

Filas escritas, en una transacción: `persona` → `persona_fisica` → `usuario` (estado ACTIVO,
`intentos_fallidos = 0`) → `usuario_rol` (ADMINISTRADOR) → `historial_estado_usuario`.

> Cuenta de prueba solo para la base local. No reutilizar en ningún entorno compartido.
