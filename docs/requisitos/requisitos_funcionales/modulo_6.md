## Módulo 6: Panel de Administración Global

* #### **RF-6.1: Anulación Manual de Moderación y Control de Estados de Eventos**


El sistema debe permitir a los usuarios con rol de *Administrador* forzar y sobreescribir el estado operacional de cualquier evento. Sabiendo que las APIs de moderación automática alteran de forma directa y síncrona el estado del evento ante una infracción (mutándolo a "RECHAZADO_SISTEMA"), el administrador tendrá la facultad exclusiva de actuar como segunda instancia. Podrá revertir de manera discrecional las decisiones automatizadas del software para pasar el registro de `Evento_Estado_Sistema` a estados como "APROBADO_MANUAL" o ratificarlo como "RECHAZADO_MANUAL", detallando los motivos en el campo `motivo_codigo`.
* #### **RF-6.2: Gestión Disciplinaria y Control de Estados de Cuentas de Usuario**


El sistema debe proveer al administrador herramientas de supervisión de identidades en la tabla `Usuario`. Ante reportes de comportamiento malicioso, fraudes o violaciones reiteradas a los términos de servicio detectadas por las APIs, el administrador podrá modificar manualmente el campo `estado` de cualquier cuenta (*Organizador* o *Participante*) a valores como "SUSPENDIDO" o "BANEADO", inhabilitando de inmediato su capacidad para emitir JWT válidos en el endpoint de login. Asimismo, contará con la opción de reversión a estado "ACTIVO".
* #### **RF-6.3: Administración del Catálogo de Clasificación (ABM de Categorías)**


El sistema debe garantizar al administrador el control exclusivo sobre el maestro de clasificaciones del sistema. Esto implica proveer una interfaz funcional para realizar el alta, baja y modificación (ABM/CRUD) de los registros en la tabla `Categoria`. En caso de ejecutar la baja de una categoría, el backend comprobará la integridad referencial y aplicará las restricciones correspondientes sobre los eventos vinculados para evitar registros huérfanos.
* #### **RF-6.4: Auditoría y Modificación Excepcional de Planes de Suscripción**


El sistema debe permitir al administrador intervenir de manera manual sobre los niveles de servicio de los organizadores en la tabla `Suscripcion`. Ante disputas comerciales, fallos en la simulación del módulo de pagos o excepciones administrativas, el administrador podrá forzar el cambio del campo `tipo_plan` (conmutando entre modalidades *Free* y *Pro*), actualizar las marcas temporales de inicio/fin de vigencia o revocar los privilegios del plan alterando el valor de su estado operacional.

---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Anterior:** [Moderación y Seguridad de Contenido](./modulo_5.md)
* **Siguiente:** [Tipificación de Perfiles de Organización](./modulo_7.md)
