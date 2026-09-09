

## Módulo 7: Módulo de Segmentación de Perfiles de Organización

---

### **Sprint 1: Personas Físicas**

* #### **RF-7.1: Registro y Validación de Organizadores Independientes (Persona Física)**


El sistema debe permitir el registro de usuarios que operen de forma autónoma bajo la modalidad de organizador independiente. Al activar el formulario correspondiente, el backend exigirá de forma obligatoria y estricta las características civiles de identidad: *Nombre*, *Apellido*, *DNI* y *Fecha de Nacimiento*. El sistema validará la unicidad del DNI antes de impactar los datos en la tabla `Persona_Fisica` para evitar duplicidades de identidad en la plataforma.

---

### **Sprint 2: Personas Jurídicas como Contenedores Administrativos**

* #### **RF-7.2: Registro e Integridad Fiscal de Organizaciones (Persona Jurídica)**


El sistema debe permitir el alta de perfiles institucionales para empresas, organizaciones o instituciones, como **contenedor administrativo** vinculado a una o más Personas Físicas.

El sistema solicitará obligatoriamente los datos fiscales y corporativos: *Razón Social*, *CUIT*, *Teléfono de Contacto*, *Correo Corporativo* y *Domicilio Fiscal*, dejando el *Nombre de Fantasía* como un campo opcional. Estos datos serán persistidos de forma aislada en la tabla `Persona_Juridica`.

**Principio clave:** Una Persona Jurídica no puede participar directamente en eventos, ni constituye una identidad de acceso — **no existe el "login de empresa"**. Los participantes son siempre Personas Físicas. Cuando un evento se crea "bajo" una organización, es un miembro de esa organización (Persona Física) quien lo organiza, pero aparece a nombre corporativo (RF-7.4).

#### Puntos de entrada

El alta de la organización es un **proceso propio, con sus propias validaciones**, y nunca se mezcla con el alta de la persona. Eso es independiente de *cuándo* la persona lo ejecute, y por eso el sistema admite dos puntos de entrada que resuelven contra la **misma** lógica de negocio:

| Punto de entrada | Quién lo usa | Comportamiento |
|---|---|---|
| **Durante el registro inicial** | Visitante que, al registrarse, elige el perfil *Persona Jurídica* | En una **única transacción**: se crea la `Persona`, su `Persona_Fisica` y su `Usuario` con rol ORGANIZADOR; se crea la `Persona_Juridica`; y se vincula a ambas en `Miembros_Organizacion`. Si cualquier paso falla, no se persiste nada. |
| **Desde el panel, ya autenticado** | Persona Física con rol ORGANIZADOR y cuenta activa | Da de alta una organización adicional sobre una cuenta que ya existe. |

En ambos casos la cuenta que se crea o se usa es **siempre la de la persona humana**, y la Persona Física queda automáticamente registrada como **miembro con `rol_en_empresa = "ADMINISTRADOR"`** de esa organización, por ser quien la dio de alta. La incorporación de miembros adicionales desde el panel queda diferida al Módulo 8.

> **Integridad transaccional (requisito, no detalle de implementación):** la creación de la organización y la de su primera membresía deben ser **atómicas**. Una organización persistida sin ningún miembro sería inadministrable — nadie podría operarla ni darla de baja — y su CUIT quedaría reservado de forma permanente.

#### Resolución del alta: inmediata, sin estado de revisión

**Decisión 2026-09-09.** El alta de una organización se resuelve **de forma inmediata y automática**, en la misma petición. **No existe un estado de revisión previo ni una aprobación diferida:**

* Si el CUIT supera la validación de RF-7.3, la organización queda **aprobada y activa** en el acto (`estado_persona_juridica_sistema = APROBADO`, `estado_persona_juridica = ACTIVO`) y **habilitada desde ese momento para publicar eventos**.
* Si el CUIT no la supera, **la petición completa se rechaza** con un error de datos inválidos y no se persiste ninguna organización. El CUIT no queda reservado, de modo que la persona puede corregirlo y reintentar.

**Fundamento y límite conocido.** Se evaluó una verificación asíncrona contra el padrón fiscal (AFIP/ARCA) para comprobar la **existencia real** de la entidad. No hay un servicio público, gratuito y estable que lo permita: el padrón exige clave fiscal y certificado digital. Antes que sostener un estado de revisión que ningún proceso podría cerrar — y que dejaría a toda organización permanentemente inhabilitada para publicar —, se opta por resolver con la única comprobación efectivamente disponible.

Queda por lo tanto **explícitamente asumido** que la validación de RF-7.3 es **aritmética y no probatoria**: acredita que el CUIT está *bien formado*, no que exista una entidad jurídica detrás. Un CUIT inventado cuyo dígito verificador cierre será aceptado. La verificación de existencia real queda diferida a una futura integración con el padrón fiscal, o a la revisión manual del Módulo 6 cuando ese módulo exista.

> **Los estados `REVISION_PENDIENTE` y `RECHAZADO` se conservan** en el catálogo `Persona_Juridica_Estado_Sistema` y en la tabla de historial, tal como los declara el MER. No se usan en el alta, pero son necesarios para la suspensión o baja de una organización por parte de un administrador (Módulo 6) y para no perder la trazabilidad de esos cambios cuando existan.
* #### **RF-7.3: Validación Algorítmica y Criptográfica del CUIT**


Antes de autorizar la persistencia de cualquier registro en la tabla `Persona_Juridica`, el backend debe interceptar el string del CUIT ingresado y someterlo a una rutina de validación criptográfica y matemática. El sistema calculará el algoritmo del dígito verificador (módulo 11 estándar para claves fiscales de Argentina) para comprobar la veracidad y el formato del identificador impositivo. Si el CUIT no supera la validación aritmética, la petición HTTP será rechazada con un código de error de datos inválidos. El sistema validará además la **unicidad** del CUIT, rechazando el alta si ya existe otra organización registrada con ese identificador.

Superada esta validación, el alta se aprueba de forma inmediata: es el **único** control que determina la resolución del registro (ver RF-7.2). Sobre su alcance —aritmético, no probatorio— y el motivo de esa limitación, ver el fundamento en RF-7.2.
* #### **RF-7.4: Adaptación Dinámica de la Firma del Organizador**


El sistema debe procesar en el backend la resolución de identidades para adaptar la firma visual del organizador en el detalle público del evento (`EventoDetalle`). El sistema resolverá de forma condicional la autoría del contenido:
* Si el evento pertenece a una **Persona Física**, la interfaz pública desplegará obligatoriamente la combinación de los campos *Nombre* y *Apellido*.
* Si el evento pertenece a una **Persona Jurídica**, la interfaz priorizará el despliegue de su *Nombre de Fantasía*; en caso de encontrarse vacío este campo opcional, el sistema renderizará la *Razón Social* legal de la organización.



---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Anterior:** [Panel de Administración Global](./modulo_6.md)
* **Siguiente:** [Gestión de Membresías y Niveles de Acceso](./modulo_8.md)
