## Módulo 8: Gestión de Membresías y Niveles de Acceso

* #### **RF-8.1: Actualización Automatizada de Nivel de Cuenta (Transacción de Suscripción)**


El sistema debe procesar las solicitudes de ascenso de nivel de cuenta (*Upgrade*) para los organizadores de forma automatizada. Al iniciar la solicitud de suscripción al *Plan Pro*, el backend creará un registro con estado "PENDIENTE" en la tabla `Suscripcion`. Tras la confirmación exitosa de la transacción mediante el módulo de simulación de pagos, el sistema actualizará de forma síncrona el estado de la suscripción a "ACTIVO", definirá el campo `tipo_plan` como "PRO", calculará los campos `fecha_inicio` y `fecha_fin` de la cobertura, e impactará el registro correspondiente en la tabla `Pago`.
* #### **RF-8.2: Control de Cuotas y Límites de Publicación (Plan Free vs. Plan Pro)**


El backend debe actuar como un interceptor de negocio que valide los privilegios de creación de eventos basándose estrictamente en el plan activo del usuario.
* Si el organizador posee un **Plan Free**, el sistema comprobará en la tabla `Evento` el conteo de sus publicaciones activas; si este número iguala o supera el límite estricto parametrizado para cuentas gratuitas, el backend bloqueará la creación de nuevos eventos y notificará al usuario la necesidad de un *Upgrade*.
* Si el organizador posee un **Plan Pro**, el sistema omitirá esta restricción, otorgándole una cuota de publicación ilimitada.


* #### **RF-8.3: Restricción y Autorización de Características Avanzadas (Métricas)**


El sistema debe evaluar los reclamos de permisos contenidos en el JWT del usuario o consultar dinámicamente el estado de su membresía para autorizar el acceso a módulos de software específicos. El backend restringirá los endpoints de consultas estadísticas avanzadas (como gráficos de tendencias, reportes analíticos de la tabla `Visita` o exportación de datos de asistencia) exclusivamente a los usuarios con una suscripción activa donde `tipo_plan = 'PRO'`. Cualquier intento de acceso desde cuentas del nivel *Free* será rebotado con un código de error de permisos insuficientes (*HTTP 403 Forbidden*).

---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Anterior:** [Tipificación de Perfiles de Organización](./modulo_7.md)
* **Siguiente:** [Requisitos no Funcionales](../requisitos_no_funcionales/README.md)
