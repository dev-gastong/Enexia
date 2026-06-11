# Enexia - Plataforma Web de Gestión de Eventos

**Enexia** es una plataforma web orientada a la centralización, gestión y difusión de eventos culturales, educativos, sociales, deportivos y de gaming en la provincia de Tierra del Fuego, con un foco inicial en la ciudad de Río Grande. 

El sistema reemplaza los métodos fragmentados de difusión actuales, brindando a los organizadores herramientas de gestión digital y a los participantes un catálogo centralizado para explorar e inscribirse a actividades desde cualquier dispositivo.

---

## Características Principales

### Gestión de Roles y Usuarios
*   **Participantes:** Exploración de catálogo, inscripciones con control de cupos, cancelación de asistencia y sistema de puntuación/reseñas con cálculo de promedios en tiempo real.
*   **Organizadores:** Panel de control (*Dashboard*) para gestionar eventos propios. El sistema diferencia entre **Personas Físicas** (requiere Nombre/Apellido) y **Personas Jurídicas** (requiere validación de CUIT y Razón Social).
*   **Administradores:** Panel de supervisión global para dar de alta/baja eventos, suspender/rehabilitar cuentas de usuarios y gestionar el CRUD de categorías.

### Automatización y Seguridad
*   **Moderación Automática de Contenido:** Filtro nativo en el backend que detecta y rechaza lenguaje ofensivo o discriminatorio en títulos, descripciones y comentarios antes de persistir en la base de datos.
*   **Control de Membresías (Planes de Acceso):** Sistema de suscripción con planes **Free** (límites de carga y métricas simples) y **Pro** (sin límites y acceso a estadísticas avanzadas).
*   **Seguridad en Autenticación:** Control de acceso basado en roles mediante **JWT (JSON Web Tokens)** y bloqueo automático de cuentas al tercer intento fallido de inicio de sesión.

---

## Detalles Técnicos y Arquitectura

La plataforma está diseñada bajo un enfoque de **MVP (Producto Mínimo Viable)** y sigue buenas prácticas de desarrollo de software:

*   **Arquitectura:** Backend estructurado en un patrón de diseño en capas (**Controller, Service, Repository**), garantizando la separación de responsabilidades y alta mantenibilidad.
*   **Persistencia de Contenido Multimedia:** Integración con **Cloudinary** para el almacenamiento optimizado de imágenes promocionales, incluyendo validación de formatos (JPG/PNG), límite de tamaño (2MB) y análisis automático de contenido sensible (adultos/violencia).
*   **Seguridad de Datos:** Hashing de contraseñas en la base de datos mediante el algoritmo **BCrypt**.
*   **Seguridad de autorizacion mediante Roles:** Se utilizara separacion de visualizaciones y operaciones mediante el uso de roles que permitan / anulen operaciones segun el contexto del usuario.
*   **Diseño Adaptativo:** Interfaz intuitiva basada en el principio de *divulgación progresiva* y compatible con navegadores modernos en entornos de escritorio y móviles.

---

## Requisitos del Sistema (Prerrequisitos)

*(A completar: *Versiones*)*
*   Java JDK 17 o superior
*   Spring Boot 3.x / 4.x (Proximamente version Seleccionada)
*   MySQL (Proximamente version seleccionada)
*   Next.js (con React)

---

* ### Enlace a [Documentación](./docs/README.md)

---
## Mapa General de Dependencias del Sistema
```mermaid
graph TD
    System[Enexia Core] --> M_Auth[Módulo de Usuarios y Seguridad]
    System --> M_Org[Módulo del Organizador y Membresías]
    System --> M_Part[Módulo del Participante y Catálogo]
    System --> M_Admin[Módulo de Administración]

    
