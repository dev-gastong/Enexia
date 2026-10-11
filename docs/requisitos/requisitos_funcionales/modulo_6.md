## Módulo 6: Panel de Administración Global

* #### **RF-6.1: Moderación Administrativa General de Eventos**


El sistema debe permitir a los usuarios con rol de *Administrador* actuar como autoridad de moderación sobre los eventos de **cualquier organizador**, cubriendo dos escenarios distintos bajo el mismo mecanismo (un único `PATCH` cuya rama depende del estado vigente de `Evento_Estado_Sistema`, nunca de un parámetro que el cliente pudiera enviar mal):

**(a) Revisión de segunda instancia:** cuando el pipeline de moderación automática rechaza un evento (lo muta a "RECHAZADO_SISTEMA"), el administrador podrá revertir esa decisión a "APROBADO_MANUAL" (acción *Aprobar de todas formas*), sin necesidad de justificarla. **Ratificar el rechazo no es una decisión del administrador:** el evento ya está fuera del catálogo y marcarlo "RECHAZADO_MANUAL" no cambiaría nada para nadie, por lo que un intento de rechazar un evento ya rechazado (por el sistema o por un administrador) se responde con `409`.

**(b) Suspensión disciplinaria:** ante una denuncia, fraude o infracción detectada *después* de que el evento ya se encuentra aprobado y visible en el catálogo ("APROBADO_SISTEMA" o "APROBADO_MANUAL"), el administrador podrá forzar su pase a "RECHAZADO_MANUAL", retirándolo de inmediato del catálogo público. La suspensión exige dos datos, ambos obligatorios:
* un `motivo_codigo` específico ("DENUNCIA_FUNDADA", "FRAUDE_DETECTADO" o "INFRACCION_POST_PUBLICACION"), que categoriza el caso y permite filtrarlo;
* una `observacion` de texto libre (entre 10 y 500 caracteres) que deja constancia del caso concreto y **es la explicación que lee el organizador**: la ve en el motivo de su evento suspendido. Por eso el panel advierte al administrador que ese texto no es una nota interna. Se guarda en `Historial_Estado_Evento`, junto con el administrador que decidió y la fecha, y no en el catálogo de estados.

Esta acción es reversible: el administrador puede restituir el evento a "APROBADO_MANUAL" si la denuncia resulta infundada, previa confirmación explícita, ya que el evento vuelve a publicarse de inmediato.

Los eventos en moderación automática ("EN_PROCESO") o con cambios en remoderación ("EN_REVISION", RF-2.7) quedan fuera de este mecanismo: todavía no hay una versión firme del contenido que aprobar o rechazar, y el backend responde `409`.

En ambos escenarios el sistema registra el cambio en `Historial_Estado_Evento` (con el administrador como `id_usuario`) y audita la decisión en `Historial_Interacciones`. No se admite una transición que no cambie nada (ej. aprobar algo ya aprobado, o rechazar algo ya rechazado): el backend responde `409` en ese caso.

Para decidir con información, el administrador podrá consultar el **detalle** de cualquier evento, sea cual sea su estado (categoría, cupo, ubicación, organizador, motivo del rechazo automático o, si está suspendido, motivo y observación de la suspensión). Esta consulta no registra una visita, a diferencia de la ficha pública.
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
