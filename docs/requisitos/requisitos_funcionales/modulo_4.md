## Módulo 4: Interfaz Pública

* #### **RF-4.1: Catálogo Público Dinámico y Divulgación Progresiva**


El sistema debe proveer una interfaz principal pública, indexable y accesible de forma anónima que despliegue el catálogo de eventos en formato de grilla de tarjetas (*cards*). La carga de los datos debe realizarse de manera paginada optimizado en el frontend para garantizar tiempos mínimos de respuesta tanto en entornos móviles como de escritorio.
* #### **RF-4.2: Motor de Búsqueda de Texto Predictivo**


El sistema debe integrar una barra de búsqueda en el catálogo público que permita a los usuarios localizar eventos ingresando cadenas de texto coincidentes con el campo nombre. El backend procesará las solicitudes mediante consultas optimizadas (filtros *LIKE* o búsquedas de texto indexadas) para retornar coincidencias parciales o completas en tiempo real.
* #### **RF-4.3: Sistema de Filtrado Multivariable Avanzado**


La interfaz pública debe permitir segmentar las opciones de búsqueda mediante un panel de filtros combinables. El sistema cruzará la información relacional de la base de datos para permitir filtros estrictos por:
* **Categoría:** Cargando dinámicamente los registros de la tabla `Categoria`.
* **Fecha:** Permitiendo rangos de calendario validados contra las fechas futuras de `Evento_Cronograma`.
* **Ubicación Geográfica:** Desplegando selectores anidados en cascada resolviendo la jerarquía de las tablas `Provincia` y `Ciudad`.


* #### **RF-4.4: Vista Detallada del Evento (Ficha Técnica)**


Al seleccionar un evento, el sistema debe renderizar una página dedicada con la información de la tabla `EventoDetalle`. Esta vista debe exponer de forma jerárquica la descripción enriquecida (texto libre), la URL de la portada multimedia, la dirección exacta física resuelta (calle, número y latitud/longitud), la agenda de cronogramas disponibles con sus horarios y precios por tipo de ticket.
* #### **RF-4.5: Auditoría y Registro Pasivo de Tráfico (Métricas de Visitas)**


Cada vez que un usuario (autenticado o anónimo) acceda a la vista detallada de un evento, el backend debe registrar la interacción en la tabla `Visita`. El sistema capturará el identificador del evento, la marca de tiempo exacta (`fecha_visita`) y el identificador de usuario si la sesión está activa, permitiendo la posterior agregación de visualizaciones únicas en el panel estadístico del organizador.
* #### **RF-4.6: Renderizado Condicional del Entorno según el Contexto de Sesión**


La interfaz pública debe adaptar dinámicamente su menú de navegación superior (*Navbar*) y sus componentes interactivos basándose en el análisis del rol encapsulado en el JWT activo. Si no hay sesión, ofrecerá los botones de login y registro; si el rol es *Participante*, habilitará los botones de compra/inscripción rápida; si el rol es *Organizador*, sustituirá estas opciones por accesos directos al panel de administración de eventos propios.

---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Anterior:** [Participación](./modulo_3.md)
* **Siguiente:** [Moderación y Seguridad de Contenido](./modulo_5.md)
