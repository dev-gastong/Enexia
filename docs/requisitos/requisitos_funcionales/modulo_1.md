## Módulo 1: Gestión de Usuarios y Autenticación

* #### **RF-1.1: Registro de Usuarios e Identificación de Base**
  El sistema debe permitir a los usuarios registrarse en la plataforma proporcionando obligatoriamente un correo electrónico (único), una contraseña cifrada y un nickname. Por defecto, todo registro inicial creará una entidad asociada en `Persona_Fisica` y le asignará el rol de *Participante*, quedando habilitado para solicitar la extensión a *Organizador*.

* #### **RF-1.2: Autenticación Segura y Emisión de JWT**
  El sistema debe validar las credenciales de acceso (email y contraseña). Ante una autenticación exitosa, el backend debe generar y retornar un JSON Web Token (JWT) firmado digitalmente que encapsule la identidad del usuario y sus roles asignados para la posterior autorización de peticiones en el frontend.

* #### **RF-1.3: Control de Acceso Basado en Roles (RBAC)**
  El sistema debe interceptar cada petición a endpoints protegidos y validar que el JWT adjunto cuente con los permisos requeridos para dicha operación. Las interfaces y vistas del frontend deben renderizarse dinámicamente ocultando o mostrando componentes según el rol del usuario autenticado (*Participante*, *Organizador*, *Administrador*).

* #### **RF-1.4: Bloqueo Automático de Cuenta por Intentos Fallidos**
  El sistema debe contabilizar los intentos consecutivos de inicio de sesión fallidos para un mismo correo electrónico. Al alcanzar el **tercer (3°) intento fallido**, el sistema debe mutar el estado del usuario a "BLOQUEADO" de forma automática, denegando accesos posteriores y requiriendo un flujo de desbloqueo seguro.

* #### **RF-1.5: Recuperación de Contraseña Automatizada (Asíncrona)**
  El sistema debe permitir a los usuarios solicitar el restablecimiento de su contraseña mediante el ingreso de su email registrado. El backend debe generar un token único, de un solo uso y con tiempo de expiración acotado (guardado en `PasswordResetToken`), y enviarlo de forma asíncrona por correo electrónico mediante un enlace seguro.

* #### **RF-1.6: Control de Estado de Cuenta (Borrado Lógico)**
  El sistema debe impedir el inicio de sesión a cualquier usuario cuyo estado en la base de datos sea diferente de "ACTIVO" (por ejemplo: "SUSPENDIDO", "BLOQUEADO" o "DE_BAJA"). En caso de que un usuario solicite la baja de su cuenta, el sistema realizará un borrado lógico registrando la fecha exacta en el campo `fecha_baja`.

---
## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Siguiente:** [Gestión de Eventos (Organizadores)](./modulo_2.md)
