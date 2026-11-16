Actúa como un diseñador UI/UX experto y genera la estructura detallada para un prototipo interactivo en Figma de un "Portal de Adopción Responsable de Mascotas". El diseño debe estar pensado para ser maquetado posteriormente de forma limpia y ordenada utilizando exclusivamente HTML semántico, CSS básico, Box Model y Flexbox. Evita superposiciones complejas, mampostería (masonry) o grillas avanzadas; todas las estructuras deben resolverse mediante alineaciones de filas y columnas simples.

**1. Vistas Principales y Flujo de Navegación**
El prototipo debe construirse en un tamaño a elección (Desktop o Mobile) y contar con un flujo de navegación completamente cerrado (con menús y botones de regreso) para que el usuario no quede atrapado en ninguna pantalla.

* **Pantalla 1: Galería interactiva (Inicio).** Un listado estructurado mediante Flexbox que contenga tarjetas informativas de los animales. Debe incluir un panel de filtros lateral o superior por: especie (perro/gato), edad, tamaño y compatibilidad con otros animales o niños.


* **Pantalla 2: Ficha detallada del animal.** Una vista individual que presente la galería de fotos del animal, su historia de rescate, estado sanitario (vacunas, castración) y requisitos particulares de cuidado.


* **Pantallas 3, 4 y 5: Formulario de postulación por etapas.** Un asistente (wizard) dividido en pasos secuenciales para recopilar: 1) Datos del adoptante, 2) Tipo de vivienda, y 3) Experiencia previa.


* **Pantalla 6: Confirmación.** Una pantalla final de éxito tras enviar la postulación que brinde retroalimentación visual clara y un botón para regresar a la página principal.



**2. Componentes Reutilizables y Variantes (Auto Layout / Flexbox)**
Debes utilizar componentes de Figma con Auto Layout, ya que su comportamiento replica directamente el de Flexbox (dirección, alineación, gap y padding/Box Model). Incluye obligatoriamente:

* **Botones:** Variantes de estado *Activo*, *Inactivo* y *Hover* (para retroalimentación visual).


* **Tarjetas de mascotas:** Variantes de estado *Default* y *Hover* o *Seleccionada*.


* **Campos de texto (Inputs):** Variantes *Default*, *Focus* (estado focal), *Error* y *Éxito*.



**3. Integración de Heurísticas de Nielsen (Visibles en el Prototipo)**
El diseño debe reflejar gráficamente las siguientes heurísticas para facilitar el desarrollo web y cumplir con la rúbrica:

* **Visibilidad del estado del sistema:** Indicadores de paso actual en el formulario por etapas y efectos visuales (hover) en todos los elementos clickeables.


* **Prevención de errores:** Mensajes de validación en rojo debajo de los campos vacíos del formulario y botones de "Siguiente" bloqueados (estado inactivo) si falta información.


* **Consistencia y estándares:** Un único componente de barra de navegación (header) y botones de cancelación estandarizados en todo el recorrido para facilitar el reconocimiento visual.