## Módulo 1: Gestión de Usuarios y Autenticación

* #### **RF-1.1: Registro de Usuarios Guiado y Dinámico (Multi-paso)**


El sistema debe proveer un flujo de registro estructurado en pasos e interfaces interactivas para segmentar los perfiles de acceso:
* **Paso 1 (Selección de Perfil):** El sistema debe permitir al usuario elegir su propósito en la plataforma, distinguiendo entre *Participante* (para explorar y asistir) y *Organizador* (para crear y gestionar eventos). 
  * **Asignación de roles (Sprint 1):** 
    - Los usuarios que se registren como **PARTICIPANTE** reciben solo ese rol.
    - Los usuarios que se registren como **ORGANIZADOR** reciben **ambos roles: ORGANIZADOR + PARTICIPANTE**, permitiéndoles crear eventos propios Y participar en eventos de otros organizadores.
    - **Justificación:** Evita que los organizadores deban crear una cuenta separada solo para participar en eventos. Garantiza que todos los participantes en eventos sean siempre Personas Físicas (individuos), manteniendo coherencia conceptual en la plataforma.

* **Paso 2 (Pestaña Persona Física):** Al activar esta pestaña, el sistema exigirá obligatoriamente las credenciales de acceso (*Nickname/Usuario*, *Correo Electrónico* y *Contraseña*) junto con los datos de identidad civil (*Nombre*, *Apellido*, *DNI* y *Fecha de Nacimiento*), persistiendo la información en la tabla `Persona_Fisica`.
  - **Nota Sprint 2:** En versiones futuras (Sprint 2), los organizadores podrán crear Personas Jurídicas (empresas/organizaciones) como contenedores administrativos a través de un flujo posterior al registro inicial. Los miembros de esas organizaciones seguirán siendo Personas Físicas que participan en eventos a través de roles administrativos, no mediante registros separados de Persona Jurídica.


* #### **RF-1.2: Autenticación Segura y Emisión de JWT**


El sistema debe validar las credenciales de acceso (email y contraseña). Ante una autenticación exitosa, el backend debe generar y retornar un JSON Web Token (JWT) firmado digitalmente que encapsule la identidad del usuario (independientemente de su personería de origen) y sus roles asignados para la posterior autorización de peticiones seguras en el frontend.
* #### **RF-1.3: Control de Acceso Basado en Roles (RBAC)**


El sistema debe interceptar cada petición dirigida a endpoints protegidos del backend y validar que el JWT adjunto en las cabeceras cuente con los permisos requeridos para dicha operación. Las interfaces del frontend deben renderizarse dinámicamente, ocultando o mostrando vistas, botones y componentes según el rol del usuario autenticado (*Participante*, *Organizador*, *Administrador*).
* #### **RF-1.4: Bloqueo Automático y Silencioso de Cuenta por Intentos Fallidos**


El sistema debe contabilizar los intentos consecutivos de inicio de sesión fallidos para un mismo correo electrónico. Al alcanzar el **tercer (3°) intento fallido**, el backend debe mutar el estado del usuario a `BLOQUEADO` de forma automática y denegar todo acceso posterior a esa cuenta en la API hasta que se ejecute el flujo de desbloqueo de RF-1.5.

  * **Conteo por cuenta, no por IP.** La contabilidad es **exclusivamente por cuenta** (`usuario.intentos_fallidos`). El sistema **no** aplica límites por dirección IP: detrás de una CGNAT o del wifi de una institución, cientos de dispositivos legítimos comparten una misma IP pública, de modo que bloquearla dejaría fuera de servicio a toda esa población por causa de un único atacante — una denegación de servicio que el atacante podría provocar a voluntad.
  * **El contador se reinicia a 0** ante cualquier inicio de sesión exitoso.

  * **Opacidad total del bloqueo (requisito de seguridad, no de usabilidad).** El estado de bloqueo **no debe ser observable por quien está intentando entrar**. Toda petición de login rechazada — sea porque el email no existe, porque la contraseña es incorrecta, porque la cuenta está bloqueada, suspendida o dada de baja — debe responder de forma **indistinguible**:
    - mismo código de estado HTTP (`401`),
    - mismo código de error de aplicación (`CREDENCIALES_INVALIDAS`),
    - mismo mensaje al usuario ("Email o contraseña incorrectos"),
    - **sin** cabeceras adicionales que revelen el motivo o un tiempo de espera,
    - y con **coste temporal constante**: toda ruta de rechazo debe pagar una comparación BCrypt (contra un hash señuelo cuando no hay usuario), para que el tiempo de respuesta no permita inferir si la cuenta existe.

    Esta opacidad aplica a **todos** los canales visibles para un atacante, sin excepción: cuerpo de la respuesta, cabeceras, códigos de estado, tiempo de respuesta, interfaz de frontend, mensajes de consola del navegador y cualquier salida de depuración del cliente. **El motivo real del rechazo existe internamente** — se registra con su código específico (`EMAIL_INEXISTENTE`, `PASSWORD_INCORRECTA`, `CUENTA_BLOQUEADA`, `CUENTA_SUSPENDIDA`) en el log del servidor y en `Historial_Interacciones` — pero **nunca** viaja en la respuesta HTTP.

  * **El único canal de aviso es el correo del titular.** Al producirse el bloqueo, el sistema debe notificar por email a la persona propietaria de la cuenta (ver RF-1.5). Es el único canal que el atacante no controla, y por lo tanto el único por el que es seguro informar el bloqueo.

  * **Riesgo residual aceptado — *password spraying*.** Sin control por IP, un ataque de contraseña única contra miles de emails distintos no acumula fallos en ninguna cuenta y, por lo tanto, no dispara esta escalera. La mitigación prevista **no** consiste en rechazar peticiones, sino en **alertar sobre volumen anómalo** a partir de `Historial_Interacciones`. Queda registrado como trabajo diferido.
* #### **RF-1.5: Recuperación de Cuenta y Desbloqueo por Correo (Asíncrono)**


El sistema debe permitir a los usuarios solicitar el restablecimiento de sus credenciales mediante el ingreso de su email registrado. El backend debe generar un token único, de un solo uso y con tiempo de expiración acotado (almacenado en `PasswordResetToken`), y enviarlo de forma asíncrona por correo electrónico a través de un enlace seguro hipervinculado.

  * **Desbloqueo directo desde el correo.** El mismo mecanismo cubre el desbloqueo de la cuenta bloqueada por RF-1.4. El email disparado por el bloqueo debe incluir un **enlace de desbloqueo directo** de la forma:

    ```
    https://<dominio>/unlock-account?token=<token-de-un-solo-uso>
    ```

    Al consumir ese enlace, el sistema debe: validar que el token exista, no esté vencido y no haya sido usado; **revertir `estado_usuario` a `ACTIVO`**; poner `intentos_fallidos = 0`; limpiar `fecha_desbloqueo_cooldown`; e invalidar el token. A partir de ese momento la persona vuelve a iniciar sesión **con sus credenciales habituales**, sin necesidad de cambiar la contraseña.

  * **La solicitud de recuperación también es opaca.** El endpoint que pide el enlace debe responder siempre lo mismo ("si el email está registrado, recibirás un correo"), exista o no la cuenta, para no convertirse en un oráculo de enumeración de usuarios que RF-1.4 justamente evita.

  * **Ámbito de implementación.** El bloqueo automático de RF-1.4 es de implementación inmediata; el flujo de desbloqueo por enlace descrito aquí queda especificado y pendiente de implementación.
* #### **RF-1.6: Control de Estado de Cuenta (Borrado Lógico)**


El sistema debe impedir el inicio de sesión a cualquier usuario cuyo estado en la base de datos sea diferente de `ACTIVO` (rechazando cuentas con estados `SUSPENDIDO`, `BLOQUEADO` o `DE_BAJA`). Ese rechazo debe emitirse con la **misma respuesta indistinguible** que define RF-1.4: el estado de la cuenta no puede deducirse desde fuera. En caso de que un usuario solicite la baja voluntaria de su cuenta, el sistema realizará un borrado lógico, mutando el estado y registrando la marca temporal exacta en el campo `fecha_baja`.

---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Siguiente:** [Gestión de Eventos (Organizadores)](./modulo_2.md)
