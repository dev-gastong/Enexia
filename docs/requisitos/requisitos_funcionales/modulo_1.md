## Módulo 1: Gestión de Usuarios y Autenticación

* #### **RF-1.1: Registro de Usuarios Guiado y Dinámico (Multi-paso)**


El sistema debe proveer un flujo de registro estructurado en pasos e interfaces interactivas para segmentar los perfiles de acceso:
* **Paso 1 (Selección de Perfil):** El sistema debe permitir al usuario elegir su propósito en la plataforma, distinguiendo entre *Participante* (para explorar y asistir) y *Organizador* (para crear y gestionar eventos). Si opta por la vía de organizador, la interfaz ofrecerá dos botones excluyentes para especificar si operará como *Persona Física* o *Persona Jurídica*.
* **Paso 2 (Pestaña Persona Física / Participante):** Al activar esta pestaña, el sistema exigirá obligatoriamente las credenciales de acceso (*Nickname/Usuario*, *Correo Electrónico* y *Contraseña*) junto con los datos de identidad civil (*Nombre*, *Apellido*, *DNI* y *Fecha de Nacimiento*), persistiendo la información en la tabla `Persona_Fisica`.
* **Paso 2 (Pestaña Persona Jurídica / Organización):** Al activar esta pestaña, el sistema inhabilitará los campos de identidad física y exigirá de forma obligatoria las credenciales de acceso, los datos fiscales institucionales (*Razón Social*, *CUIT*, *Teléfono de Contacto* y un *Nombre de Fantasía* opcional) junto con la estructura de Domicilio Fiscal Legal (*Provincia*, *Ciudad*, *Calle*, *Número Exterior* e *Interior/Depto* opcional), guardando la dirección en la tabla `Ubicacion` y el comercio en `Persona_Juridica`.


* #### **RF-1.2: Autenticación Segura y Emisión de JWT**


El sistema debe validar las credenciales de acceso (email y contraseña). Ante una autenticación exitosa, el backend debe generar y retornar un JSON Web Token (JWT) firmado digitalmente que encapsule la identidad del usuario (independientemente de su personería de origen) y sus roles asignados para la posterior autorización de peticiones seguras en el frontend.
* #### **RF-1.3: Control de Acceso Basado en Roles (RBAC)**


El sistema debe interceptar cada petición dirigida a endpoints protegidos del backend y validar que el JWT adjunto en las cabeceras cuente con los permisos requeridos para dicha operación. Las interfaces del frontend deben renderizarse dinámicamente, ocultando o mostrando vistas, botones y componentes según el rol del usuario autenticado (*Participante*, *Organizador*, *Administrador*).
* #### **RF-1.4: Bloqueo Automático de Cuenta por Intentos Fallidos**


El sistema debe contabilizar los intentos consecutivos de inicio de sesión fallidos para un mismo correo electrónico. Al alcanzar el **tercer (3°) intento fallido**, el backend debe mutar el estado del usuario a "BLOQUEADO" de forma automática, denegando accesos posteriores en la API y requiriendo un flujo de desbloqueo seguro (a ser detallado y validado en el DFD de Login).
* #### **RF-1.5: Recuperación de Contraseña Automatizada (Asíncrona)**


El sistema debe permitir a los usuarios solicitar el restablecimiento de sus credenciales mediante el ingreso de su email registrado. El backend debe generar un token único, de un solo uso y con tiempo de expiración acotado (almacenado en `PasswordResetToken`), y enviarlo de forma asíncrona por correo electrónico a través de un enlace seguro hipervinculado.
* #### **RF-1.6: Control de Estado de Cuenta (Borrado Lógico)**


El sistema debe impedir el inicio de sesión a cualquier usuario cuyo estado en la base de datos sea diferente de "ACTIVO" (rebotando cuentas con estados "SUSPENDIDO", "BLOQUEADO" o "DE_BAJA"). En caso de que un usuario solicite la baja voluntaria de su cuenta, el sistema realizará un borrado lógico, mutando el estado y registrando la marca temporal exacta en el campo `fecha_baja`.

---

## Otras rutas

* **Anterior:** [Objetivos](https://www.google.com/search?q=../README.md)
* **Siguiente:** [Gestión de Eventos (Organizadores)](https://www.google.com/search?q=.%2Fmodulo_2.md)
