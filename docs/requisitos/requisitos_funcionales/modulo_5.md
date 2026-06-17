## Módulo 5: Moderación y Seguridad de Contenido

* #### **RF-5.1: Integración con APIs de Moderación de Texto Basadas en IA**


El backend debe actuar como un cliente de servicios externos de procesamiento de lenguaje natural (NLP) e Inteligencia Artificial (ej: *API de Perspective* o *OpenAI Moderation*). Durante los procesos de creación/edición de eventos o envío de valoraciones, la capa de servicios interceptará los textos y consumirá de forma síncrona estas APIs. Si el servicio externo retorna métricas que superen los umbrales tolerables de toxicidad, insultos o discriminación, el sistema rechazará la solicitud y notificará la infracción al usuario.
* #### **RF-5.2: Control de Calidad y Restricciones Estrictas de Binarios (Multimedia)**


El backend debe actuar como barrera de seguridad perimetral para cualquier carga de archivos multimedia. El sistema validará que el archivo corresponda estrictamente a un formato de imagen válido (JPG o PNG) y que su tamaño en disco no exceda el límite máximo permitido de 2MB. Si el archivo no supera estas reglas de control, la petición será abortada antes de consumir recursos de red hacia los servicios de almacenamiento en la nube.
* #### **RF-5.3: Auditoría de Imágenes mediante APIs de Moderación Visual (Cloudinary)**


El sistema debe procesar las imágenes promocionales cargadas delegando el análisis en algoritmos de visión artificial mediante APIs especializadas de moderación visual en la nube (integradas nativamente en la API de Cloudinary). Si el análisis automatizado detecta e identifica que la imagen infringe las políticas internas debido a la presencia de contenido explícito, desnudez o violencia gráfica, la API bloqueará la vinculación de dicha portada promocional al evento.
* #### **RF-5.4: Degradación de Estado y Bloqueo Automatizado de Eventos**


En caso de que un evento publicado en la plataforma dispare alertas por vulneración de directrices en las respuestas de las APIs de moderación, el backend ejecutará una acción correctiva inmediata: mutará el valor relacional en la tabla `Evento` vinculándolo a un registro restrictivo en la tabla `Evento_Estado_Sistema` (ej: "RECHAZADO_SISTEMA"). El sistema guardará el código de la infracción retornado por la API en el campo `motivo_codigo` e inhabilitará automáticamente el evento del catálogo público de forma síncrona.

---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Anterior:** [Interfaz Pública](./modulo_4.md)
* **Siguiente:** [Panel de Administración Global](./modulo_6.md)
