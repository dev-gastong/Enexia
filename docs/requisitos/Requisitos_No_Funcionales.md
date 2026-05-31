# Requisitos No Funcionales (RNF)

## 1. Rendimiento
* **Concurrencia:** El sistema debe permitir el acceso concurrente de múltiples usuarios sin afectar la estabilidad.
* **Tiempos de respuesta:**
  * Para consultas de lectura (catálogo) no debe superar los **2 segundos** en condiciones normales.
  * Para procesos de escritura (registro de eventos) no debe superar los **4 segundos** en condiciones normales.

## 2. Seguridad
* **Protección de credenciales:** El sistema debe almacenar las contraseñas utilizando el algoritmo **BCrypt** para el hashing de las mismas.
* **Control de acceso:** El sistema debe autenticar y autorizar a los usuarios según su rol asignado:
  * Administrador
  * Organizador
  * Participante
* **Restricción de funciones:** El sistema debe restringir el acceso a funcionalidades no permitidas para cada tipo de usuario.
* **Mecanismo de sesión:** Se implementará autenticación basada en **JWT (JSON Web Tokens)**, permitiendo un manejo de sesiones sin estado y un control de acceso basado en los roles extraídos del token.

## 3. Disponibilidad
* **Tolerancia a fallos:** El sistema debe garantizar una alta disponibilidad mediante el manejo controlado de excepciones.
* **Continuidad del servicio:** Ante un error inesperado, el sistema no debe interrumpir su ejecución total. En su lugar, debe:
  1. Mostrar un mensaje amigable al usuario.
  2. Registrar el error técnico detalladamente en un log de eventos del servidor para su posterior auditoría y corrección.

## 4. Usabilidad
* **Diseño de interfaz:** La interfaz debe ser intuitiva, simple y fácil de navegar, priorizando la funcionalidad esencial sin sobrecargar la interfaz con opciones innecesarias.
* **Eficacia (Regla de los 3 clics):** El diseño debe permitir que un usuario realice las acciones principales (*inscribirse a un evento* o *buscar una categoría*) en **no más de tres interacciones** desde la pantalla principal.
* **Retroalimentación:** El sistema debe notificar al usuario el resultado de cada acción crítica mediante mensajes claros y visibles (ej. *"Inscripción exitosa"* o *"Error al cargar imagen"*).
* **Divulgación progresiva:** Se aplicará este principio mostrando primero la información esencial del evento y permitiendo expandir los detalles técnicos o estadísticas solo cuando el usuario lo requiera.

## 5. Mantenibilidad
* **Arquitectura:** El sistema se desarrollará siguiendo el patrón de **arquitectura en capas** (`Controller`, `Service`, `Repository`), asegurando la separación de responsabilidades.
* **Estándares de código:** El código fuente debe respetar las convenciones de nomenclatura de **Java** y estar correctamente documentado, permitiendo que futuros desarrolladores puedan corregir errores o agregar módulos sin afectar la lógica existente.

## 6. Compatibilidad
* **Navegadores:** La aplicación será accesible desde los navegadores modernos más utilizados (*Chrome, Firefox, Safari, Edge*).
* **Multiplataforma:** Gracias al uso de tecnologías estándar (`HTML5`, `CSS3`, `JavaScript`), el sistema garantizará la misma funcionalidad tanto en computadoras de escritorio como en dispositivos móviles (*Android / iOS*).

## 7. Escalabilidad
* **Expansión del sistema:** El sistema debe diseñarse con una arquitectura en capas que permita la expansión futura de funcionalidades y la integración con nuevos módulos.
* **Independencia del núcleo:** El diseño de la base de datos y del backend debe permitir la incorporación de nuevas funcionalidades (como *pasarelas de pago reales* o *geolocalización avanzada*) sin requerir una reestructuración del núcleo del sistema.

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

---

##  Secciones Relacionadas

* [Historias de Usuarios](Historias_De_Usuario.md)


