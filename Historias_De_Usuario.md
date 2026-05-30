# Backlog de Historias de Usuario (HU)

---

## Módulo 1: Autenticación y Gestión de Usuarios

### HU 01: Registro de Usuarios
> **Como** usuario nuevo  
> **Quero** crear una cuenta eligiendo mi rol (Organizador o Participante)  
> **Para** poder acceder a las funcionalidades específicas de la plataforma.

#### Criterios de Aceptación:
* **Validación de Identidad de Acceso (Obligatorios):**
  * **Email:** Debe cumplir con el estándar `usuario@dominio.ext`.
  * **Contraseña:** Debe cumplir con la política de seguridad (**8+ caracteres, Mayúscula, Minúscula, Número y Carácter especial**).
  * **Nickname:** Mínimo 5 caracteres, sin espacios en blanco y **único** en la base de datos.
* **Validación de Datos Personales:**
  * **Nombre y Apellido:** No pueden contener números ni caracteres especiales (solo letras). Obligatorio.
  * **Fecha de Nacimiento:** Debe ser menor o igual a la fecha actual del sistema. Obligatorio.
* **Validación de Datos Corporativos (Persona Jurídica):**
  * Esta opción solo se habilita si el rol seleccionado es **"Organizador"**.
  * **CUIT/Identificador Fiscal:** Debe ser numérico y cumplir con el formato válido.
  * **Campos Obligatorios:** Razón Social, Domicilio Fiscal y Representante Legal no pueden estar vacíos.
* **Automatización del Sistema:**
  * El sistema debe asignar automáticamente el **Estado: ACTIVO** al momento de la creación.
  * El sistema debe persistir la **Fecha de Creación** exacta del registro sin intervención del usuario.
* **Seguridad de Persistencia:**
  * La contraseña nunca debe guardarse en texto plano; debe ser procesada por el algoritmo de hashing (**BCrypt**) antes de impactar en la base de datos.
* **Confirmación:** El sistema valida los campos, guarda la información correctamente y muestra un mensaje de confirmación al registrarse.

---

### HU 02: Inicio de Sesión
> **Como** usuario registrado (Participante, Organizador o Admin)  
> **Quiero** autenticarme en la plataforma  
> **Para** acceder a mis funciones y gestionar mis datos de forma segura.

#### Criterios de Aceptación:
* **Validación de Credenciales:**
  * El sistema debe verificar que el email exista en la base de datos y que el hash de la contraseña coincida con el ingresado.
  * En caso de error en cualquiera de los dos campos, el sistema debe mostrar el mensaje genérico: *"Email o contraseña incorrectos"*.
* **Generación de Identidad (JWT):**
  * Al ser una autenticación exitosa, el servidor debe generar un **Token JWT** que contenga en su interior el `id_persona`, el `nickname` y el `rol`.
  * El token debe estar firmado con la *Secret Key* definida en el servidor.
* **Persistencia de Sesión:**
  * El sistema debe enviar el token al cliente, y el navegador debe almacenarlo de forma segura (`localStorage` o *Cookie*) para enviarlo en las cabeceras de futuras peticiones.
* **Enrutamiento por Rol:**
  * **Participante:** Redirigir al catálogo de eventos con opciones de inscripción habilitadas.
  * **Organizador:** Redirigir al Dashboard de gestión de eventos propios y estadísticas.
  * **Admin:** Redirigir al panel de supervisión global y moderación.
* **Control de Estado:**
  * El sistema debe impedir el inicio de sesión si el campo estado del usuario es **INACTIVO** o **SUSPENDIDO**, mostrando un mensaje que indique que debe contactar al soporte.
* **Control de Intentos Fallidos (Seguridad de Cuenta):**
  * El sistema debe llevar un contador de intentos fallidos por email.
  * Al alcanzar el **tercer intento consecutivo** con contraseña incorrecta, el sistema cambiará automáticamente el estado del usuario a **BLOQUEADO** o **INACTIVO**.
  * Una vez bloqueada, el sistema rechazará cualquier intento de login (incluso con contraseña correcta) y mostrará el mensaje: *"Cuenta bloqueada por seguridad. Por favor, restablezca su contraseña"*.
* **Registro de Auditoría:**
  * Cada intento fallido debe quedar registrado en los **Logs del servidor** con la IP (opcional) y la fecha para detectar patrones de ataque.

---

## Módulo 2: Panel del Organizador

### HU 03: Crear y Modificar Eventos
> **Como** Organizador  
> **Quiero** crear y editar mis eventos  
> **Para** mantener la información actualizada.

#### Criterios de Aceptación:
* **Validación de Fechas:**
  * La fecha del evento debe ser posterior a la fecha actual.
  * La fecha de fin no puede ser anterior a la de inicio.
* **Gestión de Imágenes:**
  * El sistema debe permitir subir una imagen promocional.
  * El backend debe procesar la subida a **Cloudinary**, recibir la URL y guardarla en la base de datos.
  * Se deben validar formatos (**JPG/PNG**) y un tamaño máximo de **2MB**.
* **Integridad de Autoría:**
  * Al crear el evento, el sistema debe asociar automáticamente el `id_organizador` (obtenido del JWT) para asegurar que nadie más pueda editarlo.
* **Campos Obligatorios:**
  * Nombre (mín. 10 caracteres), Descripción, Categoría, Dirección y Cupo Máximo (debe ser un número entero positivo).

---

### HU 04: Visualización y Listado de Eventos
> **Como** Organizador  
> **Quiero** ver un listado de todos los eventos que he creado  
> **Para** tener un control de mis actividades y acceder rápidamente a sus opciones.

#### Criterios de Aceptación:
* El listado solo debe mostrar eventos donde el `id_organizador` coincida con el usuario logueado.
* El listado debe indicar claramente el estado actual del evento: **"Activo"**, **"Finalizado"** o **"Dado de Baja"**.

---

### HU 05: Dar de Baja un Evento
> **Como** Organizador  
> **Quiero** poder cancelar o dar de baja un evento  
> **Para** informar a los interesados que la actividad no se realizará.

#### Criterios de Aceptación:
* El sistema **no debe borrar el registro** de la base de datos (borrado lógico), sino cambiar su estado a **INACTIVO** o **CANCELADO**.
* Una vez dado de baja, el sistema debe impedir nuevas inscripciones de manera automática.

---

### HU 06: Consultar Estadísticas de Eventos
> **Como** Organizador  
> **Quiero** visualizar métricas de mis eventos (Visitas y puntuación)  
> **Para** evaluar el éxito de mis convocatorias.

#### Criterios de Aceptación:
* **Conteo de Visitas Únicas:** El sistema debe registrar cada vez que un usuario distinto accede al detalle del evento.
* **Cálculo de Puntuación:** El promedio debe calcularse en tiempo real (o mediante una consulta agregada) basada en las reseñas de los participantes.
* **Privacidad:** Estos datos sólo son visibles para el Organizador dueño del evento y para el Administrador.

---

### HU 07: Diferenciación de Organizadores (Físico vs. Jurídico)
> **Como** Organizador  
> **Quiero** elegir si mi perfil es personal o institucional  
> **Para** que mis eventos reflejen correctamente quién es el responsable legal.

#### Criterios de Aceptación:
* **Comportamiento Dinámico:**
  * Si se selecciona **"Persona Física"**, el sistema oculta los campos de empresa y obliga a llenar Nombre/Apellido.
  * Si se selecciona **"Persona Jurídica"**, obliga a ingresar CUIT y Razón Social.
* **Validación:** El sistema debe aplicar el algoritmo de validación de CUIT para asegurar que el número sea real antes de guardar en la tabla `Persona_Juridica`.
* **Visualización Pública:**
  * Si el organizador es persona física, el detalle del evento mostrará: `Nombre + Apellido`.
  * Si es persona jurídica, mostrará: `nombre_fantasia`.

---

### HU 08: Gestión de Membresías
> **Como** Organizador  
> **Quiero** subir de nivel mi cuenta a Pro  
> **Para** acceder a estadísticas avanzadas y eliminar límites de publicaciones.

#### Criterios de Aceptación:
* **Proceso Automático:** Al confirmar la suscripción, el sistema debe disparar un proceso automático que cambie el `id_plan` de la cuenta de "Gratis" a "Pro".
* **Lógica por Plan:**
  * Los usuarios con **Plan Gratis** solo pueden ver el conteo de visitas simple y el sistema validará que no superen el límite de eventos activos permitidos.
  * Los usuarios con **Plan Pro** desbloquean el acceso al botón de **"Estadísticas Avanzadas"** (promedio de puntuación detallado y gráficos).

---

## Módulo 3: Panel del Participante

### HU 09: Inscripción a Eventos
> **Como** Participante  
> **Quiero** inscribirme en un evento de mi interés  
> **Para** asegurar mi lugar y recibir información sobre la actividad.

#### Criterios de Aceptación:
* **Validación de Cupo:** El sistema debe verificar que la cantidad de inscritos actuales sea menor al `cupo_maximo`. Si no hay cupo, el botón de inscripción debe estar deshabilitado.
* **Unicidad:** Un participante no puede inscribirse dos veces al mismo evento.
* **Restricción de Estado:** Solo los usuarios con estado **ACTIVO** pueden inscribirse.
* **Persistencia:** Al procesar la inscripción, se registra la fecha exacta y se muestra un mensaje de éxito.

---

### HU 10: Cancelación de Inscripción
> **Como** Participante  
> **Quiero** poder cancelar mi inscripción a un evento  
> **Para** liberar mi cupo si decido no asistir y que otro usuario pueda aprovecharlo.

#### Criterios de Aceptación:
* **Límite Temporal:** La cancelación sólo es permitida hasta un tiempo determinado antes del inicio del evento (**ej. 24 horas antes**), para evitar perjuicios al organizador.
* **Estado:** Al cancelar, se cambia el estado de la inscripción a **CANCELADO**.
* **Actualización:** El sistema debe reflejar inmediatamente que hay un lugar disponible más en el cupo del evento.

---

### HU 11: Historial de Inscripciones
> **Como** Participante  
> **Quiero** ver un listado de todos los eventos a los que me he inscrito  
> **Para** recordar mis actividades pasadas y las próximas a las que debo asistir.

#### Criterios de Aceptación:
* El historial debe permitir segmentar y diferenciar claramente entre **"Próximos eventos"** y **"Eventos pasados"**.
* Desde el historial, el usuario puede hacer clic para ver la información completa del evento o acceder a la opción de **puntuar** si el evento ya finalizó.

---

### HU 12: Puntuación y Reseña de Eventos
> **Como** Participante  
> **Quiero** calificar y dejar un comentario sobre un evento al que asistí  
> **Para** ayudar a otros usuarios a conocer la calidad de la actividad y dar feedback al organizador.

#### Criterios de Aceptación:
* **Escala:** El sistema debe permitir una puntuación numérica de **1 a 5 estrellas**.
* **Restricción:** Cada participante puede dejar **solo una reseña** por evento.
* **Recálculo:** Al guardar la puntuación, el promedio general del evento debe actualizarse automáticamente.

---

## Módulo 4: Catálogo Público y UX

### HU 13: Navegación y Catálogo Público
> **Como** visitante o usuario de la plataforma  
> **Quiero** ver un catálogo de eventos organizados  
> **Para** conocer qué actividades hay disponibles en la ciudad.

#### Criterios de Aceptación:
* **Filtro de Estado:** El catálogo solo debe mostrar eventos que tengan el estado **APROBADO** y **ACTIVO**. No aparecen eventos borrados o pendientes de moderación.
* **Filtro Temporal:** El sistema debe ocultar automáticamente los eventos cuya fecha de finalización ya haya pasado.
* **Información Básica:** Cada tarjeta del catálogo debe mostrar: imagen, título, fecha, ubicación y categoría.

---

### HU 14: Búsqueda y Filtros de Eventos
> **Como** usuario interesado  
> **Quiero** filtrar y buscar eventos por nombre, categoría, fecha y ubicación  
> **Para** encontrar rápidamente las actividades que se ajusten a mis preferencias.

#### Criterios de Aceptación:
* **Flexibilidad del Buscador:** Debe ser **insensible a mayúsculas** (*case-insensitive*) y permitir coincidencias parciales (ej: buscar *"básquet"* debe retornar *"Torneo de Básquetbol"*).
* **Multi-filtro:** El usuario debe poder aplicar varios filtros en simultáneo (ej: Categoría *"Deportes"* + Ubicación *"Centro"*).
* **Vistas Vacías:** Si no hay resultados, el sistema debe mostrar el mensaje claro: *"No se encontraron eventos que coincidan con tu búsqueda"*.

---

### HU 15: Detalle del Evento y Registro de Visitas
> **Como** usuario  
> **Quiero** acceder a la página de detalle de un evento  
> **Para** ver la descripción completa, el cupo disponible y los datos del organizador.

#### Criterios de Aceptación:
* **Métrica de Visita:** Al cargar la página de detalle, el backend debe disparar un evento que sume una visita al contador del evento.
* **Disponibilidad:** Se debe mostrar el cupo actualizado calculando: `Cupo Total - Inscritos actuales`.
* **Responsable:** Si el organizador es Persona Jurídica muestra la Razón Social; si es Física, muestra Nombre y Apellido.

---

### HU 16: Interfaz Adaptativa (Menú Dinámico)
> **Como** usuario del sistema  
> **Quiero** que el menú de navegación cambie según mi estado de sesión  
> **Para** acceder solo a las opciones que me corresponden por mi rol.

#### Criterios de Aceptación:
* **Sin Sesión:** Si no hay un JWT válido, el menú público muestra: `Inicio`, `Explorar`, `Login` y `Registro`.
* **Con Sesión (Según Rol extraído del JWT):**
  * **Organizador:** Muestra `Mis Eventos`, `Crear Evento`, `Estadísticas`, `Mi Perfil`.
  * **Participante:** Muestra `Mis Inscripciones`, `Mi Perfil`.
  * **Administrador:** Muestra `Panel de Moderación`, `Gestión de Usuarios`.

---

## Módulo 5: Administración y Seguridad Global

### HU 17: Filtro Automático de Contenido Inadecuado
> **Como** Administrador del sistema  
> **Quiero** que la plataforma detecte y rechace palabras ofensivas automáticamente  
> **Para** mantener un entorno respetuoso sin necesidad de revisar cada publicación manualmente.

#### Criterios de Aceptación:
* **Motor de Filtrado:** El sistema debe contar con una librería o componente que detecte y bloquee palabras prohibidas (profanidades, insultos, términos discriminatorios).
* **Puntos de Control:** Al momento de crear/editar un evento, registrar un usuario o realizar un comentario, el backend debe escanear cada entrada de texto de los formularios.
* **Bloqueo:** Si se detecta una palabra prohibida, el sistema **no debe persistir** los datos y devolverá el mensaje: *"El contenido contiene lenguaje no permitido. Por favor, revísalo"*.

---

### HU 18: Validación de Integridad de Archivos (Multimedia)
> **Como** Administrador  
> **Quiero** asegurar que solo se suban imágenes válidas y livianas  
> **Para** optimizar el almacenamiento y evitar archivos maliciosos.

#### Criterios de Aceptación:
* **Formatos Soportados:** Solo se aceptan extensiones `.jpg`, `.jpeg` y `.png`. Cualquier otra extensión (como `.exe` o `.pdf`) será rechazada en el filtro de Spring Boot o Cloudinary.
* **Control de Peso:** Tamaño máximo fijado en **2MB**. Si lo supera, arroja el mensaje: *"La imagen excede el tamaño máximo permitido"*.
* **Sanitización:** El sistema debe renombrar el archivo internamente antes de la subida para mitigar ataques de inyección por nombre de archivo.

---

### HU 19: Detección de Contenido Sensible
> **Como** Sistema de seguridad  
> **Quiero** disparar alertas ante patrones de contenido sensible  
> **Para** proteger a los ciudadanos de Río Grande de contenido inadecuado.

#### Criterios de Aceptación:
* **Análisis Automatizado:** La imagen se analizará mediante la API de Cloudinary al subirse; si se detecta contenido sensible (**Adulto o Violencia**), la URL no se guardará en la base de datos y el evento quedará automáticamente bloqueado.

---

### HU 20: Administración Global
> **Como** Administrador  
> **Quiero** gestionar estados de eventos, usuarios y categorías  
> **Para** mantener la calidad, seguridad y orden de la plataforma.

#### Criterios de Aceptación:
* **Modificación de Eventos:** El administrador puede sobrescribir el estado de cualquier evento (Activo/Inactivo) mediante acciones rápidas en su panel.
* **Modificación de Usuarios:** Puede cambiar el estado de cualquier persona a **SUSPENDIDO** o **ACTIVO**. Si está suspendido, el Login rechazará su acceso.
* **CRUD de Categorías:** Control total de la tabla Categorías. **Regla de integridad:** No se puede eliminar una categoría si cuenta con eventos asociados.
* **Bypass de Membresías:** El administrador tiene la facultad de cambiar el plan de un organizador manualmente sin requerir pasarela de pago.
