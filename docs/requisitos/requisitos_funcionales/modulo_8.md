## Módulo 8: Gestión de Membresías y Niveles de Acceso

> **Alcance (decisión del 2026-10-19): suscripción básica.** Hay **dos planes**: *Gratuito* (por defecto) y *Pro* (de pago simulado, mensual). **Ningún plan limita la cantidad de eventos.** Lo que el plan Pro agrega son las **estadísticas avanzadas por cronograma**. El plan pertenece al usuario organizador, no a la organización. Los DFD de lo que falta programar están en `docs/tempDFD/modulo_8_membresias/`.

* #### **RF-8.1: Contratación, Cancelación y Vencimiento de la Suscripción Pro**


El sistema debe permitir al organizador contratar el *Plan Pro* de forma automatizada. Al iniciar el pedido, el backend creará un registro con estado "PENDIENTE" en la tabla `Suscripcion`; tras la confirmación de la transacción mediante el módulo de simulación de pagos, actualizará de forma síncrona el estado a "ACTIVA", definirá `tipo_plan` como "PRO", fijará `fecha_inicio` en la fecha de hoy y `fecha_fin` un mes después, e impactará el registro correspondiente en la tabla `Pago`. Un pago rechazado deja la suscripción en "PENDIENTE" y registra el pago como fallido. El organizador podrá **cancelar** su Plan Pro (vuelve al plan Gratuito de inmediato) y consultar su plan vigente. Un proceso diario marcará como "VENCIDA" toda suscripción activa cuya `fecha_fin` haya pasado. No hay renovación automática: renovar es volver a contratar.
* #### **RF-8.2: Resolución del Plan Vigente (sin tope de eventos)**


El backend debe poder responder, para cualquier organizador, si su plan vigente es *Pro* o *Gratuito*. Es *Pro* únicamente si su última suscripción es de tipo "PRO", está "ACTIVA" y su `fecha_fin` no pasó; en cualquier otro caso —incluida la ausencia de suscripciones— es *Gratuito*. El plan se consulta siempre en la base y no a partir de un reclamo del JWT. **Este requisito ya no impone cuotas de publicación:** tanto el plan Gratuito como el Pro permiten publicar una cantidad ilimitada de eventos, y un cambio de plan nunca modifica los eventos ya publicados.
* #### **RF-8.3: Estadísticas Avanzadas por Cronograma (solo Plan Pro)**


Las estadísticas básicas por cronograma (RF-2.10) están disponibles para todos los organizadores. El backend restringirá las **estadísticas avanzadas por cronograma** —distribución de puntajes, tasa de cancelación, ingresos, series diarias de inscripciones y visitas, conversión y exportación de asistentes— a los eventos cuyo organizador tenga *Plan Pro* vigente (RF-8.2). Cualquier intento desde una cuenta *Gratuita* será rechazado con *HTTP 403 Forbidden* y el código `PLAN_PRO_REQUERIDO`, y quedará auditado. La verificación se hace después de comprobar que el evento es propio (un evento ajeno responde 404 antes que 403).

---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Anterior:** [Tipificación de Perfiles de Organización](./modulo_7.md)
* **Siguiente:** [Requisitos no Funcionales](../requisitos_no_funcionales/README.md)
