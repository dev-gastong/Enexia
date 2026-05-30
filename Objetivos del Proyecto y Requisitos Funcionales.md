# Objetivos del Proyecto y Requisitos Funcionales

## 1. Objetivo General

Desarrollar una plataforma web de gestión y difusión de eventos para la provincia de Tierra del Fuego, Argentina (con foco inicial en la ciudad de Río Grande), que centralice la oferta de actividades culturales, educativas, sociales, deportivas y de gaming en un único espacio digital.

El sistema busca reemplazar los métodos fragmentados de difusión y gestión manual actuales, brindando:

* **A los organizadores:** Herramientas digitales para publicar y administrar sus eventos e inscripciones.
* **A los participantes:** La posibilidad de explorar, inscribirse y hacer seguimiento de su historial de participación desde cualquier dispositivo.

---

## 2. Objetivos Específicos y Requisitos Funcionales (RF)

### 2.1 Gestión de Usuarios y Autenticación

> **Objetivo Específico:** Implementar un sistema seguro de registro, control de acceso y autenticación de usuarios.

* **RF-01:** El sistema debe permitir el registro de usuarios con asignación de roles específicos (*Organizador* y *Participante*).
* **RF-02:** El sistema debe permitir el inicio de sesión seguro utilizando únicamente email y contraseña.
* **RF-03:** El sistema debe restringir y controlar el acceso a las funcionalidades según el rol del usuario autenticado.
* **RF-04:** El sistema debe permitir la recuperación de contraseña de forma automatizada mediante el envío de un email.

### 2.2 Módulo de Gestión de Eventos (Organizadores)

> **Objetivo Específico:** Desarrollar un módulo integral que permita a los organizadores administrar todo el ciclo de vida de sus publicaciones.

* **RF-05:** El sistema debe permitir al organizador crear nuevos eventos en la plataforma.
* **RF-06:** El sistema debe permitir al organizador modificar y actualizar la información de sus eventos existentes.
* **RF-07:** El sistema debe permitir al organizador visualizar un listado centralizado con todos sus eventos creados.
* **RF-08:** El sistema debe permitir al organizador dar de baja (desactivar) un evento.
* **RF-09:** El sistema debe permitir al organizador consultar estadísticas de sus eventos, incluyendo el conteo de visitas únicas y el promedio de puntuación recibido.

### 2.3 Módulo de Participación

> **Objetivo Específico:** Desarrollar las herramientas necesarias para que los usuarios finales interactúen activamente con los eventos.

* **RF-10:** El sistema debe permitir al participante inscribirse en un evento disponible.
* **RF-11:** El sistema debe permitir al participante cancelar su inscripción previa a un evento.
* **RF-12:** El sistema debe permitir al participante puntuar y valorar un evento en el que haya asistido/participado.
* **RF-13:** El sistema debe permitir al participante visualizar un historial completo de sus inscripciones pasadas y activas.

### 2.4 Interfaz Pública y Descubrimiento

> **Objetivo Específico:** Desarrollar la cara pública de la plataforma, optimizando la búsqueda y navegación de los usuarios.

* **RF-14:** El sistema debe mostrar un catálogo público compuesto únicamente por eventos aprobados y activos.
* **RF-15:** El sistema debe permitir filtrar los eventos del catálogo por categoría, fecha y ubicación.
* **RF-16:** El sistema debe permitir buscar eventos en tiempo real mediante su nombre.
* **RF-17:** El sistema debe mostrar una página con el detalle completo de cada evento seleccionado.
* **RF-18:** El sistema debe adaptar dinámicamente el menú de navegación y las opciones disponibles según el rol de la sesión activa.
* **RF-19:** El sistema debe registrar las visitas únicas a la página de detalle de cada evento para alimentar el módulo de estadísticas.

### 2.5 Moderación y Seguridad de Contenido

> **Objetivo Específico:** Asegurar la calidad, integridad y seguridad del contenido publicado mediante herramientas de filtrado automatizado.

* **RF-20:** El sistema debe filtrar y rechazar automáticamente eventos que contengan lenguaje ofensivo o inapropiado en el título o descripción.
* **RF-21:** El sistema debe validar que los archivos multimedia subidos correspondan a formatos de imagen válidos y no excedan el peso máximo permitido.
* **RF-22:** El sistema debe rechazar de forma automatizada aquellos eventos que disparen alertas de contenido sensible según las políticas de la plataforma.

### 2.6 Panel de Administración Global

> **Objetivo Específico:** Desarrollar un panel centralizado de supervisión y control total del sistema para los administradores.

* **RF-23:** El sistema debe permitir al administrador forzar el estado de cualquier evento a *"Inactivo"* (baja) o *"Activo"* (alta), anulando cualquier resultado previo de la moderación automática.
* **RF-24:** El sistema debe permitir al administrador suspender o rehabilitar cuentas de usuarios (*Organizadores* o *Participantes*) que infrinjan los términos de servicio.
* **RF-25:** El sistema debe permitir al administrador gestionar el ABM (Alta, Baja, Modificación) de las categorías globales disponibles para los eventos.
* **RF-26:** El sistema debe permitir al administrador modificar o revocar de forma manual el nivel de suscripción de cualquier organizador ante excepciones técnicas o administrativas.

### 2.7 Tipificación de Perfiles de Organización

> **Objetivo Específico:** Diferenciar el perfil del organizador según su naturaleza legal, adaptando los formularios de registro y la visualización pública.

* **RF-27:** El sistema debe permitir el registro de **Personas No Jurídicas** (organizadores independientes), solicitando obligatoriamente fields como *Nombre, Apellido y Fecha de Nacimiento*.
* **RF-28:** El sistema debe permitir el registro de **Personas Jurídicas** (organizaciones, empresas o instituciones), solicitando obligatoriamente *Razón Social, Nombre de Fantasía y CUIT*.
* **RF-29:** El sistema debe adaptar la firma del organizador en el detalle público del evento: si es persona física mostrará *Nombre y Apellido*; si es jurídica, su *Nombre de Fantasía*.
* **RF-30:** El sistema debe validar criptográficamente que el formato y dígito verificador del CUIT ingresado para personas jurídicas sea válido antes de persistir los datos en la BD.

### 2.8 Gestión de Membresías y Niveles de Acceso

> **Objetivo Específico:** Implementar un sistema de control de niveles de cuenta para regular el uso de herramientas avanzadas.

* **RF-31:** El sistema debe actualizar el nivel de cuenta del organizador a **Plan Pro** de forma automatizada tras confirmar la acción correspondiente (simulación de pasarela o validación).
* **RF-32:** El sistema debe habilitar o bloquear funcionalidades específicas (como el acceso a métricas avanzadas o límites en la cantidad de publicaciones) basándose estrictamente en el plan activo del usuario (*Free* o *Pro*).

---

## 🔗 Secciones Relacionadas

* 
