## Módulo 2: Gestión de Eventos (Organizadores)

* #### **RF-2.1: Creación de Eventos y Vinculación de Identidad**


El sistema debe permitir a los usuarios con permisos de *Organizador* registrar un nuevo evento en la plataforma, capturando obligatoriamente el nombre, la categoría, la descripción detallada y la ubicación geográfica de la actividad. El backend debe resolver de forma automática la autoría del contenido:
* Si el usuario se registró como **Persona Jurídica**, el evento se publicará bajo el nombre de la Razón Social o Nombre de Fantasía validado.
* Si el usuario se registró como **Persona Física**, el evento figurará respaldado a título personal por el Nombre y Apellido civiles del creador.


* #### **RF-2.2: Moderación Síncrona de Texto en la Carga**


Durante los procesos de creación o edición de un evento, el backend debe interceptar el título y la descripción para procesarlos mediante el filtro de seguridad nativo. Si el algoritmo detecta lenguaje ofensivo, discriminatorio o inapropiado, el sistema bloqueará la persistencia en la base de datos, retornará un mensaje de advertencia descriptivo al frontend y mutará el estado del sistema del evento a un código de rechazo por auditoría (`Evento_Estado_Sistema`).
* #### **RF-2.3: Persistencia y Validación Multimedia (Integración Cloudinary)**


El sistema debe permitir la carga de una URL de portada y galerías de imágenes promocionales para el evento. El backend validará estrictamente que los archivos cumplan con los formatos JPG o PNG y que su peso no exceda el límite estricto de 2MB. El almacenamiento de estos recursos se delegará de forma optimizada en Cloudinary, supeditado a sus análisis automáticos contra contenido sensible (violencia o desnudez).
* #### **RF-2.4: Estructuración de Agenda (Eventos Cronograma)**


El sistema debe permitir al organizador configurar una o múltiples fechas de presentación independientes para un mismo evento mediante la entidad `Evento_Cronograma`. Por cada registro de agenda, se exigirá de manera obligatoria especificar la fecha del calendario junto con la hora exacta de inicio y de finalización.
* #### **RF-2.5: Parametrización y Variedad de Tickets por Cronograma**


El sistema debe permitir al organizador configurar la disponibilidad y las condiciones de acceso vinculadas de forma exclusiva a una fecha de la agenda a través de la entidad `Cronograma_Ticket`. Se permitirá la creación de múltiples tipos de tickets (ej: "Inscripción General", "Pase VIP", "Preventa") asignándoles un nombre identificatorio para parametrizar la oferta comercial del cronograma.
* #### **RF-2.6: Control Financiero y Gestión de Cupos**


El sistema debe permitir definir si un tipo de ticket es gratuito o de pago (con tipo y precio decimal). Asimismo, el organizador establecerá el cupo máximo disponible para cada modalidad de entrada. El sistema inicializará el campo `cupo_actual` en cero y bloqueará automáticamente las solicitudes de inscripción cuando este valor iguale al `cupo_maximo` configurado.
* #### **RF-2.7: Modificación y Actualización de Eventos**


El sistema debe permitir al organizador modificar toda la información general, el material multimedia y las descripciones de sus eventos existentes. Sin embargo, para proteger la integridad de las transacciones ya realizadas, el backend restringirá la modificación de precios o la reducción de cupos máximos en aquellos tipos de tickets que ya cuenten con inscripciones activas por parte de los participantes.
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
