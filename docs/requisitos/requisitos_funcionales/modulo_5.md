## Módulo 5: Moderación y Seguridad de Contenido

* #### **RF-5.1: Integración con APIs de Moderación de Texto Basadas en IA (Asíncrona)**


El backend debe actuar como un cliente de servicios externos de procesamiento de lenguaje natural (NLP) e Inteligencia Artificial (ej: *API de Perspective* o *OpenAI Moderation*). Durante los procesos de creación/edición de eventos o envío de valoraciones, el sistema persiste el contenido con estado "EN_PROCESO" y dispara de forma asíncrona la interceptación de los textos hacia la capa de servicios que consumirá estas APIs. Si el servicio externo retorna métricas que superen los umbrales tolerables de toxicidad, insultos o discriminación, el sistema mutará el estado a "RECHAZADO_SISTEMA", notificará la infracción al usuario y enviará el registro al Panel de Administración para revisión manual. Si la validación es exitosa, el estado transitará a "APROBADO_SISTEMA".
* #### **RF-5.2: Control de Calidad y Restricciones Estrictas de Binarios (Multimedia)**


El backend debe actuar como barrera de seguridad perimetral para cualquier carga de archivos multimedia. Esta validación se ejecuta como parte de la Fase 2 del pipeline asíncrono (RF-2.2), únicamente si el texto fue aprobado. El sistema validará que cada archivo corresponda estrictamente a un formato de imagen válido (JPG o PNG) y que su tamaño en disco no exceda el límite máximo permitido de 2MB. Si el archivo no supera estas reglas de control, la petición será descartada antes de consumir recursos de red hacia Cloudinary. Cada imagen es procesada independientemente.
* #### **RF-5.3: Auditoría de Imágenes mediante APIs de Moderación Visual (Cloudinary)**


El sistema debe procesar las imágenes promocionales cargadas delegando el análisis en algoritmos de visión artificial mediante APIs especializadas de moderación visual en la nube (integradas nativamente en Cloudinary). Esta validación se ejecuta como parte de la Fase 2 del pipeline asíncrono (RF-2.2), SOLO si la validación de texto fue exitosa (optimización de recursos). Cada imagen es validada de forma independiente:
* Si el análisis detecta contenido explícito, desnudez o violencia gráfica: la imagen es rechazada y descartada (no vinculada al evento).
* Si el análisis aprueba la imagen: es persistida en la tabla `Evento_Multimedia` con su URL, tipo y orden.

**Lógica de Cierre del Pipeline:** Después de validar todas las imágenes:
* Si NINGUNA imagen fue aprobada: el evento transitará a "RECHAZADO_SISTEMA".
* Si AL MENOS UNA imagen fue aprobada: el evento transitará a "APROBADO_SISTEMA" y será visible en el catálogo público.
* #### **RF-5.4: Re-Moderación de Cambios en Eventos Publicados (Asíncrona)**


Cuando un organizador modifica contenido sensible (título, descripción, imágenes) de un evento YA PUBLICADO (RF-2.7), el backend dispara automáticamente un nuevo ciclo de moderación asíncrona idéntico al flujo de creación (RF-2.2 y RF-2.3). El evento transita a estado "EN_REVISIÓN_CAMBIOS" y los cambios permanecen en borrador (no visibles en catálogo). El backend procesa de forma asíncrona:

1. **Validación de Texto:** Intercepta los cambios en título y descripción mediante APIs de moderación.
   * Si rechazado: El evento revierte a la versión anterior PUBLICADA, se notifica al organizador, y se registra el intento en auditoría.
   * Si aprobado: Procede a validación de multimedia.

2. **Validación de Multimedia:** Procesa cada imagen modificada mediante Cloudinary.
   * Si todas las nuevas imágenes son rechazadas: El evento revierte a versión anterior, se notifica al organizador.
   * Si al menos una nueva imagen es aprobada: Los cambios se publican, el evento retorna a "APROBADO_SISTEMA" en catálogo.

Esta estrategia garantiza que los cambios en eventos publicados mantengan la misma integridad y seguridad que los eventos nuevos, previniendo la inyección de contenido inapropiado en eventos ya visibles.

---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Anterior:** [Interfaz Pública](./modulo_4.md)
* **Siguiente:** [Panel de Administración Global](./modulo_6.md)
