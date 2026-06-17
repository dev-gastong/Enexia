## Módulo 3: Participación

* #### **RF-3.1: Inscripción Transaccional a Cronogramas de Eventos**


El sistema debe permitir a los usuarios con rol de *Participante* reservar un cupo en una fecha y hora específicas de la agenda mediante la selección de un `Cronograma_Ticket`. El backend debe validar síncronamente que el `cupo_actual` sea menor al `cupo_maximo` antes de confirmar la operación, creando un registro en la tabla `Inscripcion` con estado "PENDIENTE".
* #### **RF-3.2: Simulación del Flujo de Pago**


Para las inscripciones a tickets de pago, el sistema debe disparar un flujo de simulación transaccional. Una vez que la pasarela simulada aprueba la operación, el backend creará el registro en la tabla `Pago` vinculando la inscripción, actualizará el estado de la misma a "CONFIRMADA", incrementará el campo `cupo_actual` del ticket.
* #### **RF-3.3: Cancelación Voluntaria de Inscripciones**


El sistema debe permitir al participante anular una inscripción previamente adquirida antes de la fecha de inicio del cronograma. Al procesar la cancelación, el backend realizará un borrado lógico mutando el estado de la inscripción a "CANCELADA", liberará el lugar decrementando en uno el campo `cupo_actual` del ticket correspondiente.
* #### **RF-3.4: Sistema de Valoración Cuantitativa y Cualitativa**


El sistema debe permitir al participante calificar su experiencia en un cronograma de evento una vez finalizado el mismo. La entidad `Valoracion` capturará un valor entero obligatorio (escala del 1 al 5) y un campo de texto para comentarios y reseñas cualitativas. El backend aplicará una restricción única compuesta para asegurar que un usuario pueda dejar un **máximo de una (1) valoración por cronograma**.
* #### **RF-3.5: Moderación Síncrona de Reseñas y Comentarios**


Al momento de enviar una valoración, el backend debe procesar el texto del comentario a través del filtro nativo de seguridad. Si el algoritmo de software detecta insultos, vocabulario discriminatorio o lenguaje ofensivo, bloqueará la persistencia del registro en la tabla `Valoracion` y notificará al participante la violación de las directrices de la comunidad sin afectar la base de datos.
* #### **RF-3.6: Historial Centralizado de Inscripciones y Asistencia**


El sistema debe proveer al participante un panel privado donde visualizar, de forma paginada y cronológica, el registro completo de sus interacciones con la plataforma. La interfaz listará tanto las inscripciones activas (con acceso directo a la visualización de sus códigos QR para el ingreso) como las pasadas, canceladas o pendientes de pago.

---

## Otras rutas

* **Anterior:** [Objetivos](https://www.google.com/search?q=../README.md)
* **Anterior:** [Gestión de Eventos (Organizadores)](https://www.google.com/search?q=./modulo_2.md)
* **Siguiente:** [Interfaz Pública](https://www.google.com/search?q=./modulo_4.md)
