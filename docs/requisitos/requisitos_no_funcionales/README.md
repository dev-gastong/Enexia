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

##  Secciones Relacionadas

### [Seguridad del Servidor](./seguridad_de_servidor.md)

### [Historias de Usuarios](Historias_De_Usuario.md)


