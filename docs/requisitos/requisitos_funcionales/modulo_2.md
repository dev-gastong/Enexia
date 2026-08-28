## Módulo 2: Gestión de Eventos (Organizadores)

* #### **RF-2.1: Creación de Eventos y Vinculación de Identidad**


El sistema debe permitir a los usuarios con permisos de *Organizador* registrar un nuevo evento en la plataforma, capturando obligatoriamente el nombre, la categoría, la descripción detallada y la ubicación geográfica de la actividad. El backend debe resolver de forma automática la autoría del contenido:

**Sprint 1:**
* El evento figurará respaldado a título personal por el Nombre y Apellido civiles del creador (Persona Física).

**Sprint 2 (cuando Persona Jurídica esté implementada):**
* Si el evento es creado por una Persona Física actuando como miembro/administrador de una Persona Jurídica, el evento se publicará bajo el nombre de la Razón Social o Nombre de Fantasía de la organización.
* Si el evento es creado directamente por una Persona Física sin asociación a empresa, figurará a título personal por el Nombre y Apellido civiles del creador.


* #### **RF-2.2: Moderación Asíncrona de Texto en la Carga**


Durante los procesos de creación o edición de un evento, el backend debe crear un registro inicial de Evento con estado "EN_PROCESO" persistiendo ÚNICAMENTE los metadatos mínimos requeridos:
* **id_evento** (PK autogenerado)
* **id_organizador** (FK del JWT del creador)
* **estado_sistema** = "EN_PROCESO"
* **fecha_creacion** (timestamp)

El organizador carga el formulario completo en una única petición: título, descripción e imágenes promocionales (máximo 3). Este registro "skeleton" permite que el organizador vea inmediatamente su evento en el dashboard con status "Validando contenido...". El backend dispara de forma asíncrona un pipeline secuencial de validaciones:

**Fase 1 - Validación de Texto:** El sistema intercepta el título y la descripción mediante APIs de moderación (Perspective, OpenAI, etc.). 
* Si el algoritmo detecta lenguaje ofensivo, discriminatorio o inapropiado: el sistema mutará el estado del evento a "RECHAZADO_SISTEMA", notificará la infracción al usuario, **descartará las imágenes cargadas SIN procesarlas** (optimización de recursos de red y cómputo), la remitirá al Panel de Administración para revisión manual, y NO persistirá datos del evento (título, descripción, ubicación, cronogramas, multimedia, tickets). Pipeline terminado.
* Si la validación es exitosa: procede a **Fase 2 - Validación de Multimedia**.

**Fase 2 - Validación de Multimedia:** Solo se ejecuta si el texto fue aprobado. El backend procesa cada imagen de forma asíncrona mediante Cloudinary (RF-5.3). Las imágenes aprobadas son persistidas en `Evento_Multimedia`; las rechazadas son descartadas.
* Si TODAS las imágenes son rechazadas: el evento transitará a "RECHAZADO_SISTEMA" y quedará pendiente de revisión manual.
* Si AL MENOS UNA imagen es aprobada: el backend persiste los datos completos del evento (EventoDetalle, Evento_Cronograma, Cronograma_Ticket), muta el estado a "APROBADO_SISTEMA", y el evento se hace visible en el catálogo público.

Esta estrategia garantiza que solo información moderada e íntegra ingrese a la base de datos, cumpliendo con la regla arquitectónica de "moderación antes de persistir datos de contenido".
* #### **RF-2.3: Persistencia y Validación Multimedia (Integración Cloudinary)**


El sistema debe permitir la carga de una o varias imágenes promocionales (máximo 3 por evento) como parte del formulario de creación de evento. El procesamiento de estas imágenes ocurre de forma asíncrona, SOLO si la validación de texto (RF-2.2, Fase 1) fue exitosa. El backend validará estrictamente que cada archivo cumpla con los formatos JPG o PNG y que su peso no exceda el límite estricto de 2MB (RF-5.2). Cada imagen será enviada a Cloudinary para análisis automático de contenido sensible (RF-5.3). Las imágenes aprobadas serán vinculadas al evento; las rechazadas serán descartadas. **La lógica final es:** Si al menos una imagen es aprobada, el evento alcanza estado "APROBADO_SISTEMA" y es visible en catálogo. Si ninguna imagen es aprobada, el evento es rechazado.
* #### **RF-2.4: Estructuración de Agenda (Eventos Cronograma)**


El sistema debe permitir al organizador configurar una o múltiples fechas de presentación independientes para un mismo evento mediante la entidad `Evento_Cronograma`. Por cada registro de agenda, se exigirá de manera obligatoria especificar la fecha del calendario junto con la hora exacta de inicio y de finalización.
* #### **RF-2.5: Parametrización y Variedad de Tickets por Cronograma**


El sistema debe permitir al organizador configurar la disponibilidad y las condiciones de acceso vinculadas de forma exclusiva a una fecha de la agenda a través de la entidad `Cronograma_Ticket`. Se permitirá la creación de múltiples tipos de tickets (ej: "Inscripción General", "Pase VIP", "Preventa") asignándoles un nombre identificatorio para parametrizar la oferta comercial del cronograma.
* #### **RF-2.6: Control Financiero y Gestión de Cupos**


El sistema debe permitir definir si un tipo de ticket es gratuito o de pago (con tipo y precio decimal). Asimismo, el organizador establecerá el cupo máximo disponible para cada modalidad de entrada. El sistema inicializará el campo `cupo_actual` en cero y bloqueará automáticamente las solicitudes de inscripción cuando este valor iguale al `cupo_maximo` configurado.
* #### **RF-2.7: Modificación y Actualización de Eventos**


El sistema debe permitir al organizador modificar toda la información general, el material multimedia y las descripciones de sus eventos existentes. **Cualquier cambio en contenido sensible (título, descripción, imágenes) de un evento PUBLICADO dispara automáticamente un nuevo ciclo de moderación asíncrona** (RF-2.2 y RF-2.3) antes de permitir que los cambios sean visibles en el catálogo. Durante este período, el evento transitará a estado "EN_REVISIÓN" y los cambios quedarán en borrador. Si la re-moderación es exitosa, los cambios se publican; si es rechazada, se revierte a la versión anterior y se notifica al organizador. 

Sin embargo, para proteger la integridad de las transacciones ya realizadas, el backend restringirá la modificación de precios o la reducción de cupos máximos en aquellos tipos de tickets que ya cuenten con inscripciones activas por parte de los participantes. Estos cambios (precio/cupo) NO requieren re-moderación, solo validación de integridad transaccional.
* #### **RF-2.8: Listado Centralizado y Panel de Control Personal**


El sistema debe proveer al organizador un espacio centralizado (*Dashboard*) donde visualizar de forma paginada todos los eventos creados bajo su autoría. La interfaz debe permitir realizar búsquedas rápidas y aplicar filtros avanzados basados en las categorías y el estado del evento asignado por el organizador (ej: "Publicado", "Cancelado").
* #### **RF-2.9: Borrado Lógico de Eventos (Cancelación)**


El sistema debe permitir al organizador dar de baja un evento activo. Esta acción ejecutará un borrado lógico, mutando su estado a "DADO_DE_BAJA" sin eliminar los registros históricos de las tablas. Si el evento contaba con participantes anotados, el sistema disparará una rutina interna automatizada para invalidar las inscripciones vinculadas y retornar los pagos realizados en caso de que los haya.
* #### **RF-2.10: Consulta de Estadísticas y Analíticas de Rendimiento**


El sistema debe recopilar, procesar y exponer al organizador las métricas de rendimiento de sus eventos. Esto incluye el conteo acumulado de visualizaciones provenientes de la tabla `Visita` (filtrando por visitas únicas por usuario) y el cálculo del promedio aritmético de las puntuaciones cualitativas recolectadas en la tabla `Valoracion` para cada uno de sus cronogramas finalizados.

---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Anterior:** [Gestión de Usuarios y Autenticación](./modulo_1.md)
* **Siguiente:** [Participación](./modulo_3.md)
