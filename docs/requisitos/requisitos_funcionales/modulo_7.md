

## Módulo 7: Módulo de Segmentación de Perfiles de Organización

* #### **RF-7.1: Registro y Validación de Organizadores Independientes (Persona Física)**


El sistema debe permitir el registro de usuarios que operen de forma autónoma bajo la modalidad de organizador independiente. Al activar el formulario correspondiente, el backend exigirá de forma obligatoria y estricta las características civiles de identidad: *Nombre*, *Apellido*, *DNI* y *Fecha de Nacimiento*. El sistema validará la unicidad del DNI antes de impactar los datos en la tabla `Persona_Fisica` para evitar duplicidades de identidad en la plataforma.
* #### **RF-7.2: Registro e Integridad Fiscal de Organizaciones (Persona Jurídica)**


El sistema debe permitir el alta de perfiles institucionales para empresas, organizaciones o instituciones. Al activar esta pestaña en la interfaz, el sistema solicitará obligatoriamente los datos fiscales y corporativos: *Razón Social*, *CUIT*, *Teléfono de Contacto* y *Correo Corporativo*, dejando el *Nombre de Fantasía* como un campo opcional. Estos datos serán persistidos de forma aislada en la tabla `Persona_Juridica`.
* #### **RF-7.3: Validación Algorítmica y Criptográfica del CUIT**


Antes de autorizar la persistencia de cualquier registro en la tabla `Persona_Juridica`, el backend debe interceptar el string del CUIT ingresado y someterlo a una rutina de validación criptográfica y matemática. El sistema calculará el algoritmo del dígito verificador (módulo 11 estándar para claves fiscales de Argentina) para comprobar la veracidad y el formato del identificador impositivo. Si el CUIT no supera la validación aritmética, la petición HTTP será rechazada con un código de error de datos inválidos.
* #### **RF-7.4: Adaptación Dinámica de la Firma del Organizador**


El sistema debe procesar en el backend la resolución de identidades para adaptar la firma visual del organizador en el detalle público del evento (`EventoDetalle`). El sistema resolverá de forma condicional la autoría del contenido:
* Si el evento pertenece a una **Persona Física**, la interfaz pública desplegará obligatoriamente la combinación de los campos *Nombre* y *Apellido*.
* Si el evento pertenece a una **Persona Jurídica**, la interfaz priorizará el despliegue de su *Nombre de Fantasía*; en caso de encontrarse vacío este campo opcional, el sistema renderizará la *Razón Social* legal de la organización.



---

## Otras rutas

* **Anterior:** [Objetivos](../README.md)
* **Anterior:** [Panel de Administración Global](./modulo_6.md)
* **Siguiente:** [Gestión de Membresías y Niveles de Acceso](./modulo_8.md)
