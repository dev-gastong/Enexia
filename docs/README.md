# Documentación del Proyecto: Enexia

## 1. Introducción

### 1.1 Propósito del Documento

El presente documento tiene como propósito describir y documentar el proceso de desarrollo de **Enexia**, una plataforma web de gestión de eventos.

Este archivo sirve como:

* **Referencia técnica** para el desarrollador a lo largo del ciclo de vida del proyecto.
* **Base de evaluación académica**, detallando el relevamiento del problema, los requisitos del sistema, el modelado, el diseño técnico y el prototipo de interfaz.

### 1.2 Alcance del Sistema 

**Enexia** es una plataforma web orientada a la centralización, gestión y difusión de eventos en la provincia de Tierra del Fuego, con un foco inicial en la ciudad de Río Grande.

El sistema se delimita bajo los siguientes parámetros:

* **Roles de Usuario:**
* **Participantes:** Diseñado para usuarios que buscan explorar el catálogo de eventos, inscribirse, asistir y valorar las experiencias.
* **Organizadores:** Permite crear y gestionar eventos, acceder a paneles de estadísticas de sus publicaciones.
* **Administrador:** Responsable del control, soporte y gestión general de toda la plataforma.


* **Funcionalidades Clave del MVP:**
* **Moderación automática:** Validación del contenido publicado mediante librerías de software especializadas, optimizando tiempos sin intervención humana directa.
* **Planes de Suscripción:** Control de permisos y límites de uso según el nivel de cuenta del usuario (organizador), dividido en niveles **Free** y **Pro**.

* **Exclusiones de esta versión (Fuera de Alcance):**
*  No incluye aplicación móvil nativa.
*  No incluye aplicacion de escritorio local.
*  No Incluye pagos integrados pero se realizará la simulacion de los mismos en sus correspondientes secciones (Suscripcion, inscripcion)

---

## 2. Definiciones y Siglas Importantes

### 2.1 Conceptos de Negocio

* **Enexia:** Nombre oficial de la plataforma web de gestión y difusión de eventos.
* **MVP (Producto Mínimo Viable):** Versión básica y funcional del sistema que incluye únicamente las características esenciales para salir al mercado.
* **Persona jurídica:** Organización legalmente constituida como empresa, ONG, club o institución.
* **Persona no jurídica:** Individuo o particular autónomo que se registra en la plataforma para organizar eventos o participar de los mismos.
* **Plan de suscripción:** Modalidad de acceso a funcionalidades avanzadas mediante un pago periódico.

### 2.2 Componentes de Arquitectura y Desarrollo

* **Frontend:** Capa visual de la aplicación; la interfaz gráfica con la que interactúa el usuario final.
* **Backend:** Capa lógica del sistema que procesa las reglas de negocio, la seguridad y gestiona los datos.
* **API REST:** Interfaz de programación que permite la comunicación fluida entre el frontend y el backend mediante el intercambio de datos en formato JSON.
* **BD (Base de Datos):** Sistema estructurado donde se almacena de forma permanente toda la información del sistema.

### 2.3 Seguridad y Modelado Técnico

* **JWT (JSON Web Token):** Token de autenticación compacto y seguro usado para verificar la identidad del usuario en cada petición al servidor.
* **CRUD:** Siglas en inglés para las operaciones básicas sobre datos: Crear (*Create*), Leer (*Read*), Actualizar (*Update*) y Eliminar (*Delete*).
* **UML:** Lenguaje de Modelado Unificado, utilizado para representar gráficamente los diagramas de estructura y comportamiento del sistema.
* **MER (Modelo Entidad-Relación):** Representación gráfica y técnica que define la estructura y las relaciones de la base de datos.

---

## 3. Atajos de Navegación 
* [Objetivos del Proyecto y Requisitos Funcionales](./requisitos/README.md)
* [Diagrama MER](./diseño-bd/MER.md)
* [Diagrama DER](./diseño-bd/DER.md)
