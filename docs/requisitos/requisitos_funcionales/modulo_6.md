## Módulo 6: Panel de Administración Global

* #### **RF-6.1: Moderación Administrativa General de Eventos**


El sistema debe permitir a los usuarios con rol de *Administrador* actuar como autoridad de moderación sobre **cualquier evento**, sea cual sea su estado vigente en `Evento_Estado_Sistema`, cubriendo dos escenarios distintos bajo el mismo mecanismo:

**(a) Revisión de segunda instancia:** cuando las APIs de moderación automática rechazan un evento o una modificación posterior (mutándolo a "RECHAZADO_SISTEMA" o "CAMBIO_RECHAZADO"), el administrador podrá revertir esa decisión a "APROBADO_MANUAL"/"CAMBIO_APROBADO", o ratificarla dejándolo en "RECHAZADO_MANUAL"/"CAMBIO_RECHAZADO", con `motivo_codigo = ADMIN_REVISO`.

**(b) Suspensión disciplinaria:** ante una denuncia, fraude o infracción detectada *después* de que el evento ya se encuentra aprobado y visible en el catálogo ("APROBADO_SISTEMA" o "APROBADO_MANUAL"), el administrador podrá forzar discrecionalmente su pase a "RECHAZADO_MANUAL", retirándolo de inmediato del catálogo público, seleccionando un `motivo_codigo` específico para este caso (p. ej. "DENUNCIA_FUNDADA", "FRAUDE_DETECTADO", "INFRACCION_POST_PUBLICACION"). Esta acción es reversible: el administrador puede restituir el evento a "APROBADO_MANUAL" si la denuncia resulta infundada.

En ambos escenarios el sistema registra el cambio en `Historial_Estado_Evento` (con `tipo_agente = ADMIN`) y audita la decisión en `Historial_Interacciones`. No se admite una transición que no cambie nada (ej. aprobar algo ya aprobado, o rechazar algo ya rechazado): el backend responde `409` en ese caso.
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
