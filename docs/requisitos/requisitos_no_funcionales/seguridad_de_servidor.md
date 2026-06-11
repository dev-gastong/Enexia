---

# Plan de Despliegue e Infraestructura Segura

Como estrategia de puesta en producción para la aplicación web basada en Spring Boot, se optará por un despliegue en un **Servidor Virtual Privado (VPS)**. Con el objetivo de garantizar la integridad, disponibilidad y confidencialidad del sistema, se estructurará un proceso de endurecimiento (*hardening*) del servidor dividido en cuatro módulos principales de seguridad.

---

## Módulo 1: Configuración y Aseguramiento de SSH

Este módulo define las políticas de acceso remoto al servidor para mitigar los riesgos de intrusión y ataques de fuerza bruta.

* **Inhabilitación de credenciales estándar:** Se restringirá el acceso remoto mediante el uso de contraseñas tradicionales. El ingreso al servidor requerirá obligatoriamente mecanismos de autenticación criptográfica basados en llaves.
* **Bloqueo de acceso directo al usuario Administrador (Root):** Se prohibirá el inicio de sesión remoto directo con los privilegios máximos del sistema. Cualquier acceso deberá realizarse a través de un usuario con permisos limitados, escalando privilegios internamente solo cuando sea necesario.
* **Ofuscación del puerto de conexión:** El puerto por defecto del protocolo SSH será modificado por uno alternativo no estándar. Esto tiene como fin reducir el ruido de escaneos automatizados y prevenir ataques dirigidos en la frontera de la red.

---

## Módulo 2: Aislamiento de Red y Proxy Inverso

Este módulo establece el perímetro de red interna y externa, asegurando que los componentes críticos de la aplicación no queden expuestos directamente a Internet.

* **Aislamiento de la aplicación web:** El backend (Spring Boot) se ejecutará dentro de una red interna privada y aislada. Sus puertos nativos de comunicación no serán accesibles desde el exterior del servidor.
* **Frontera perimetral mediante Proxy Inverso:** Se implementará un servidor de Proxy Inverso (Nginx) como único punto de entrada público. Este componente se conectará tanto a la red externa como a la red interna.
* **Flujo de comunicación controlado:** El Proxy Inverso expondrá hacia el exterior únicamente los puertos estrictamente necesarios para el tráfico web. Toda la comunicación subsiguiente hacia la aplicación web se gestionará de manera interna y privada entre el Proxy Inverso y el contenedor de la aplicación.

---

## Módulo 3: Actualizaciones de Seguridad Automáticas

Este módulo garantiza el mantenimiento preventivo y autónomo del sistema operativo del servidor ante nuevas vulnerabilidades descubiertas.

* **Gestión autónoma de parches:** Se integrará una herramienta en el sistema operativo encargada de auditar, descargar e instalar actualizaciones en segundo plano de forma automatizada.
* **Acotación del alcance de actualización:** El proceso automatizado se limitará estrictamente a parches de seguridad críticos. Quedan excluidas las actualizaciones de versiones mayores de software para evitar conflictos de compatibilidad con la aplicación desplegada.

---

## Módulo 4: Gestión de Certificados SSL (HTTPS)

Este módulo asegura la confidencialidad de los datos en tránsito mediante el cifrado de las comunicaciones entre los clientes y el servidor.

* **Cifrado del tráfico web:** Se implementarán certificados digitales para habilitar el protocolo seguro HTTPS en el canal de comunicación provisto por el Proxy Inverso.
* **Automatización del ciclo de vida:** Se configurará un mecanismo de verificación y renovación automatizada para los certificados. Esto asegura la continuidad del cifrado y evita la expiración manual de las credenciales de seguridad.
