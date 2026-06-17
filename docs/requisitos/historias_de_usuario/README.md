
# Backlog de Historias de Usuario (HU)

---

## Módulo 1: Autenticación y Gestión de Usuarios

### HU 01: Registro de Usuarios (Multi-paso)

> **Como** usuario nuevo
> **Quiero** registrarme a través de un formulario guiado por pasos y pestañas
> **Para** crear una cuenta con los datos correctos según mi personería.

#### Criterios de Aceptación:

* **Paso 1 (Selección de Perfil):** El sistema debe ofrecer dos opciones excluyentes: *Participante* u *Organizador*. Si se escoge Organizador, la interfaz desplegará dos alternativas: *Persona Física* o *Persona Jurídica*.


* **Paso 2 - Pestaña Persona Física / Participante:**
* Requiere obligatoriamente *Nickname* (mínimo 5 caracteres, único), *Email* (`usuario@dominio.ext`, único) y *Contraseña* (8+ caracteres, mayúscula, minúscula, número y carácter especial).


* Exige datos de identidad civil: *Nombre*, *Apellido* (solo letras), *DNI* (único) y *Fecha de Nacimiento* (coherente con la fecha actual del sistema). Los datos se persisten en la tabla `Persona_Fisica`.




* **Paso 2 - Pestaña Persona Jurídica / Organización:**
* Inhabilita los campos de identidad civil. Requiere obligatoriamente las mismas credenciales de acceso globales (*Nickname*, *Email*, *Contraseña*).


* Exige datos fiscales institucionales: *Razón Social*, *CUIT* (validado algorítmicamente mediante módulo 11), *Teléfono de Contacto* y un *Nombre de Fantasía* opcional.


* Exige la carga obligatoria del **Domicilio Fiscal Legal** (*Provincia*, *Ciudad*, *Calle*, *Número*), validando que la provincia/ciudad existan en el maestro de normalización y persistiendo la dirección en la tabla `Ubicacion` y el comercio en `Persona_Juridica`.


* **Seguridad de Persistencia:** La contraseña nunca se guardará en texto plano; el backend debe procesarla con el algoritmo de hashing **BCrypt** antes de impactar en la base de datos.


* **Estado de Verificación Inicial (Persona Jurídica):** Toda organización registrada ingresará en estado `"PENDIENTE_VERIFICACION"`, bloqueando su capacidad de publicar hasta que un administrador valide sus datos fiscales de forma manual.

---

### HU 02: Inicio de Sesión y Autenticación

> **Como** usuario registrado
> **Quiero** ingresar mis credenciales de acceso
> **Para** recibir un token digital que me permita navegar según mi rol de forma segura.

#### Criterios de Aceptación:

* **Validación de Credenciales:** El sistema debe verificar que el email exista y que el hash de la contraseña coincida. Ante fallas, retornará el mensaje genérico: *"Email o contraseña incorrectos"*.


* **Generación y Persistencia de Identidad (JWT):** Ante una autenticación exitosa, el backend generará un **Token JWT** firmado que encapsule el `id_usuario`, `nickname` y `rol`. El cliente lo almacenará de forma segura en las cabeceras de sus peticiones.


* **Control de Intentos Fallidos (Bloqueo Automático):** El backend llevará un contador síncrono de intentos consecutivos por email. Al alcanzar el **tercer (3°) intento fallido**, mutará automáticamente el estado del usuario a `"BLOQUEADO"`, denegando accesos posteriores y redirigiendo al flujo de desbloqueo.


* **Control de Estado de Cuenta:** El sistema impedirá el inicio de sesión a cualquier cuenta cuyo estado sea diferente de `"ACTIVO"` (rebota usuarios en estado `"SUSPENDIDO"`, `"BLOQUEADO"` o `"DE_BAJA"`).



---

## Módulo 2: Panel del Organizador y Gestión de Eventos

### HU 03: Crear y Modificar Eventos

> **Como** Organizador
> **Quiero** registrar o actualizar la información de una actividad
> **Para** publicar la oferta cultural y parametrizar sus accesos.

#### Criterios de Aceptación:

* **Validación de Campos de Texto Obligatorios:**
  * El formulario exige completar obligatoriamente: Nombre, Descripción, Categoría y Ubicacion (Con sus campos) o enlace si es virtual.


* **Vinculación Automática de Autoría:** Al crear el evento, el backend asocia el identificador extraído del JWT. Si el creador es *Persona Jurídica*, el evento se firma públicamente con su *Nombre de Fantasía* (o Razón Social); si es *Persona Física*, se firma con su *Nombre y Apellido* civil.


* **Estructuración de Agenda (Cronogramas):** El formulario exige definir de forma obligatoria una o múltiples instancias de días y horarios independientes representando los cronogramas de un evento. La fecha de inicio debe ser posterior a la hora actual del servidor.


* **Parametrización de Tickets por Cronograma:** El sistema no usará un cupo global. Exige asociar a cada cronograma específico uno o más tickets, definiendo tipo (*VIP*, *General*), precio decimal (donde 0.00 es gratuito) y `cupo_maximo`.


* **Regla de Consistencia en Ediciones:** El backend bloqueará la modificación de precios o la reducción de `cupo_maximo` en aquellos tickets que ya cuenten con inscripciones activas (`cupo_actual > 0`).
* **Control de Límites por Suscripción (Plan Free):** Al intentar guardar un evento, el backend comprobará en la tabla `Suscripcion` el plan del organizador. Si posee el **Plan Free** y alcanzó la cuota máxima de publicaciones permitidas, bloqueará la acción y solicitará un upgrade.

* **Validación de Archivos Multimedia (Imágenes):**
  * El sistema debe permitir la subida de una o varias imagenes promocionales (Maximo 3 por evento).
  * El backend validará estrictamente que el archivo corresponda a formatos de imagen permitidos (JPG/PNG) y que su peso no exceda el límite máximo de 2MB.
  * El sistema enviará estas imagenes a una API externa de moderacion.
  * Si la API detecta contenido sensible (material sexual o explícito, expresiones ofensivas, exhibición de armamento, promoción de sustancias ilícitas, manifestaciones doctrinarias de carácter político o religioso radical, o contenido que promueva la polarización ideológica y el activismo social de confrontación), el evento se guardará automáticamente con estado "Rechazado", se registrará el intento en la auditoría de logs y no se mostrará en la cartelera pública.
  * El backend debe procesar la subida a **Cloudinary**, recibir las URLs y guardarlas en la base de datos en caso de no haber problema.    

* **Filtro Avanzado de Moderación de Contenido (Segunda Capa Automática):**
  * Antes de persistir los datos, el backend enviará los textos (Título y Descripción) a una API externa de moderación.
  * Si la API detecta lenguaje ofensivo, inapropiado o que viole las políticas comunitarias, el evento se guardará automáticamente con estado "Rechazado", se registrará el intento en la auditoría de logs y no se mostrará en la cartelera pública.


* **Integridad de Autoría y Sesión:**
  * Al crear el evento, el sistema debe asociar automáticamente el `id_organizador` (obtenido del JWT) para asegurar que nadie más pueda editarlo.
  * Al modificar un evento, el backend validará de forma estricta que el ID extraído del JWT coincida con el dueño original de la publicación. Si no coincide, bloqueará la edición lanzando un Error 403 (Prohibido).

---

### HU 04: Visualización y Dashboard del Organizador

> **Como** Organizador
> **Quiero** visualizar un listado consolidado de mis actividades
> **Para** controlar las métricas y los estados de mis publicaciones.

#### Criterios de Aceptación:

* **Filtros e Integridad:** El listado se paginará y mostrará exclusivamente los eventos de la autoría del token activo. Permitirá filtrar por el estado del organizador (`"Publicado"`, `"Cancelado"`).


* **Visibilidad de Auditoría:** Si un evento fue rechazado de forma síncrona por las APIs de moderación automatizada, la tarjeta del evento mostrará el estado `"RECHAZADO_SISTEMA"` se podrá acceder al modal que contiene la informacion detallada del rechazo, con codigo y motivo.



---

### HU 05: Cancelación y Borrado Lógico de Eventos

> **Como** Organizador
> **Quiero** dar de baja una actividad publicada
> **Para** suspender la venta de entradas e invalidar los accesos previos.

#### Criterios de Aceptación:

* **Borrado Lógico:** El sistema nunca eliminará físicamente los registros, sino que mutará el estado del evento a `"DADO_DE_BAJA"`.


* **Efecto en Cascada Automático:** Al dar de baja un evento con inscripciones activas, el sistema inhabilitará inmediatamente la compra de nuevos tickets y disparará una rutina interna para invalidar las inscripciones realizadas, devolviendo los pagos realizados por las mismas en caso de haber sido entradas de pago.



---

### HU 06: Gestión de Membresías (Plan Pro)

> **Como** Organizador
> **Quiero** adquirir la suscripción premium
> **Para** remover los límites de publicación y habilitar analíticas avanzadas.

#### Criterios de Aceptación:

* **Proceso Transaccional Automatizado:** Al confirmarse la simulación del pago, el backend creará el registro en la tabla `Pago` y mutará síncronamente la tabla `Suscripcion` definiendo el `tipo_plan = 'PRO'`, calculando los tiempos de vigencia.

* Los usuarios en **Plan Free** solo visualizarán el conteo simple de visitas únicas y la puntuación promedio.


* Los usuarios en **Plan Pro** desbloquearán el acceso a los endpoints de estadísticas avanzadas y gráficos analíticos aggregate.



---

##  Módulo 3: Panel del Participante y Asistencia

### HU 07: Inscripción y Adquisición de Tickets

> **Como** Participante
> **Quiero** reservar una entrada para un cronograma específico
> **Para** asegurar mi asistencia al evento.

#### Criterios de Aceptación:

* **Validación Síncrona de Cupos:** Antes de procesar la inscripción, el backend validará que el `cupo_actual` sea estrictamente menor al `cupo_maximo` configurado en el ticket. Si se agota, el sistema bloqueará la transacción.
* **Flujo Transaccional de Pago:** Para ticket de pago, tras aprobarse la simulación de la pasarela, se creará el registro en la tabla `Pago`, se cambiará el estado de la `Inscripcion` a `"CONFIRMADA"`, se incrementará el `cupo_actual` en uno (+1) y se generará un hash criptográfico único (`codigo_qr_hash`).
* **Unicidad:** El backend aplicará una restricción única para asegurar que un participante no pueda generar inscripciones duplicadas para el mismo cronograma de evento.



---

### HU 08: Cancelación de Inscripciones

> **Como** Participante
> **Quiero** anular una reserva previa
> **Para** liberar mi cupo y permitir el ingreso de otro usuario.

#### Criterios de Aceptación:

* **Límite Temporal de Negocio:** La cancelación voluntaria solo estará permitida mediante el frontend hasta **24 horas antes** del inicio fijado en el cronograma.


* **Liberación de Recursos:** Al procesar la baja, el estado de la inscripción mutará a `"CANCELADA"`, se decrementará en uno (-1) el campo `cupo_actual` del ticket.



---

### HU 09: Puntuación y Reseña de Eventos

> **Como** Participante
> **Quiero** calificar con estrellas y comentarios una actividad finalizada
> **Para** aportar feedback cualitativo al organizador.

#### Criterios de Aceptación:

* **Escala y Restricción Estricta:** El sistema capturará un valor entero obligatorio (1 al 5 estrellas) y un campo de texto para la reseña. Solo se permite **una (1) valoración por usuario por cronograma**.


* **Moderación Inteligente Obligatoria:** Al enviar el formulario, el texto del comentario será analizado de forma síncrona por las APIs externas de moderación NLP. Si la API detecta toxicidad o insultos, la persistencia en la tabla `Valoracion` se bloqueará de inmediato.

---

## Módulo 4: Catálogo Público e Interfaz

### HU 10: Navegación, Búsqueda y Multi-Filtros

> **Como** visitante de la plataforma
> **Quiero** explorar la cartelera pública mediante buscadores y selectores combinables
> **Para** encontrar rápidamente actividades de mi interés.

#### Criterios de Aceptación:

* **Filtros de Visibilidad Públicos:** El catálogo indexará de forma anónima y paginada únicamente los eventos cuyo estado de sistema sea `"APROBADO"` y estado de organizador sea `"Publicado"`, ocultando automáticamente eventos pasados.


* **Motor Case-Insensitive:** La barra de búsqueda de texto permitirá coincidencias parciales y será insensible a mayúsculas/minúsculas o tildes.


* **Estructura de Multi-filtrado:** El usuario podrá combinar en simultáneo filtros por `Categoria`, rangos de fechas de `Evento_Cronograma` y selectores geográficos anidados en cascada resolviendo las tablas `Provincia` y `Ciudad`.



---

### HU 11: Detalle del Evento y Registro de Tráfico

> **Como** usuario
> **Quiero** acceder a la ficha técnica completa de un evento seleccionado
> **Para** consultar su descripción multimedia, cronogramas y disponibilidad de tickets.

#### Criterios de Aceptación:

* **Registro de Auditoría Pasivo (Métricas):** Al renderizarse la página de detalle, el backend capturará la interacción insertando un registro en la tabla `Visita` (almacenando el ID del evento, la marca de tiempo y el ID del usuario si está autenticado).


* **Cálculo de Disponibilidad:** La interfaz mostrará la cabtudad de cupos actuales disponibles por cada cronograma.


---

## Módulo 5: Automatización de Seguridad y Moderación Externa

### HU 12: Moderación Inteligente Automática (Texto y Multimedia)

> **Como** Sistema de Seguridad
> **Quiero** interceptar las cargas de datos e imágenes hacia APIs externas de IA
> **Para** sanitizar la plataforma y mitigar contenido inapropiado o archivos maliciosos de forma síncrona.

#### Criterios de Aceptación:

* **Capa de Moderación NLP (Texto):** Durante cualquier inserción de texto plano (Registros, Eventos, Reseñas), el backend consultará síncronamente APIs externas (ej. *Perspective* o *OpenAI Moderation*). Si se superan los umbrales de toxicidad, se abortará la transacción devolviendo un mensaje de error.


* **Validación Multimedia:** El backend comprobará que los archivos promocionales sean estrictamente `.jpg` o `.png` y que no excedan el tamaño de **2MB** por archivo, abortando la petición antes de consumir ancho de banda de almacenamiento.


* **Capa de Moderación Visual (Cloudinary):** Las imágenes cargadas pasarán por los algoritmos de moderación visual automatizados de la API de Cloudinary. Si se clasifica la imagen como contenido sensible (*Adulto, Violencia, Desnudez*), el backend mutará automáticamente el estado del evento, guardará el código del error en `motivo_codigo` e impedirá su publicacion.



---

## Módulo 6: Administración Global

### HU 13: Panel de Control Administrativo (Superusuario)

> **Como** Administrador Global
> **Quiero** disponer de herramientas de anulación manual, ABM de categorias y cambio de estado de suscripciones, 
> **Para** supervisar la calidad del ecosistema y actuar ante excepciones comerciales.

#### Criterios de Aceptación:

* **Gestion de los estados de los Eventos:** El administrador podrá forzar y sobreescribir manualmente el estado de cualquier evento, actuando como segundo nivel humano frente a las decisiones de las APIs automatizadas. Podrá mutar el estado tanto para aprobacion como para rechazo manual, registrando la justificación.


* **Gestión de Cuentas:** Permitirá modificar de forma discrecional el estado de cualquier `Usuario` a `"SUSPENDIDO"` o `"BANEADO"`, provocando la invalidación inmediata de sus credenciales en los endpoints de autenticación.


* **Restricciones en el ABM de Categorías:** Control completo (CRUD) de la tabla `Categoria`. El sistema bloqueará de forma estricta el borrado de una categoría si la base de datos detecta integridad referencial con eventos asociados (evita registros huérfanos).


* **Bypass de Privilegios:** El administrador podrá intervenir manualmente la tabla `Suscripcion` de cualquier organizador para alterar su `tipo_plan` (*Free* o *Pro*) o extender la vigencia sin requerir transacciones de pago en la pasarela simulada.



