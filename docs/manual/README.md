# Manual de programación de Enexia

> **Qué es esto.** Un recorrido de **aprendizaje** que explica **todo lo que se hizo en Enexia**, desde cómo se crea una clase hasta cómo se comunica el frontend con la API. No es un manual de referencia: sigue **el orden en que se construyó** y **el orden en que conviene aprenderlo**, y en cada tema muestra **el código real del proyecto** (con la ruta del archivo) junto a la sintaxis y la lógica que hay detrás.
>
> Escrito al cierre del proyecto (2026-10-19). Está pensado para alguien que **sabe poco o nada de programación** y quiere llegar a **entender y modificar** una aplicación web completa.

---

## Cómo leerlo

1. **En orden.** Cada capítulo usa lo que explicó el anterior. Los de la Parte D aplican todo y **remiten** al capítulo donde se explicó cada concepto.
2. **Con el código abierto.** Cada ejemplo trae su ruta (`enexia/src/main/java/com/enexia/rg/...`). Abrilo y leelo entero: los comentarios del proyecto explican **por qué** se decidió algo, no solo qué hace.
3. **Probando.** Cada capítulo cierra con **ejercicios**. Algunos se resuelven en papel; otros, con el backend corriendo (`./gradlew bootRun`) y las DevTools del navegador (F12).
4. **Con los diagramas.** Los DFD de `docs/diagrams/` (lo programado) y `docs/tempDFD/` (lo pendiente) muestran el flujo de datos de cada módulo.

---

## Índice

### Parte A — Fundamentos de Java

| Capítulo | Temas |
|---|---|
| [A01 — Clases y objetos](./A01_clases_y_objetos.md) | Cómo corre un programa, clases, atributos, tipos (`Long`, `BigDecimal`, fechas), constructores, métodos, `static`/`final`, `if`/`switch`/bucles, paquetes, Gradle. |
| [A02 — Programación orientada a objetos](./A02_poo.md) | Encapsulamiento, herencia (la jerarquía de excepciones), interfaces, clases abstractas, polimorfismo, `enum`, `record`, `equals`/`hashCode`, genéricos, composición, SOLID. |
| [A03 — Java funcional y colecciones](./A03_java_funcional_y_colecciones.md) | `List`/`Set`/`Map`, lambdas, referencias a métodos, *streams* (`map`, `filter`, `groupingBy`, `toMap`), `Optional`, text blocks, `String`. |
| [A04 — Excepciones, regex y librerías](./A04_excepciones_regex_y_librerias.md) | `try`/`catch`, excepciones propias, `GlobalExceptionHandler`, **cada regex del proyecto**, logging, mapa de librerías, Lombok. |

### Parte B — Spring Boot

| Capítulo | Temas |
|---|---|
| [B01 — IoC y configuración](./B01_spring_ioc_configuracion.md) | Inyección de dependencias, beans, `@Configuration`, `application.properties`, `@Value`, el arranque, `@Scheduled`, proxies. |
| [B02 — MVC, DTO y validación](./B02_mvc_dto_validacion.md) | Capas, el recorrido de una petición, controladores, DTO, Bean Validation (y anotaciones propias), paginación, multipart. |
| [B03 — Persistencia](./B03_persistencia_jpa.md) | Bases relacionales, JPA/Hibernate, relaciones, N+1, repositorios, JPQL, **transacciones**, concurrencia, migraciones. |
| [B04 — Seguridad](./B04_seguridad.md) | BCrypt, JWT, Spring Security, **diseño defensivo del login**, tokens de un solo uso, XSS/CSRF/CORS. |
| [B05 — Asincronía y eventos](./B05_asincronia_y_transacciones.md) | Hilos, *pools*, `@Async`, eventos `AFTER_COMMIT`, correo después del commit, idempotencia, *schedulers*. |
| [B06 — Testing](./B06_testing.md) | JUnit 5, Mockito, AssertJ, tests de API y de interfaz (Playwright), Awaitility, mantenimiento. |

### Parte C — Del servidor al navegador

| Capítulo | Temas |
|---|---|
| [C01 — API REST y HTTP](./C01_api_rest_http.md) | HTTP, JSON, REST, autenticación, CORS, multipart, `curl`, **el contrato completo de la API**. |
| [C02 — JavaScript vanilla](./C02_javascript_vanilla.md) | Sintaxis, DOM, eventos, `fetch`/`async`, `api.js`/`auth.js`/`utils.js`, `sessionStorage`, paginación, *debounce*, **XSS y `UI.esc`**. |
| [C03 — HTML y CSS](./C03_html_y_css.md) | HTML semántico, formularios, accesibilidad, selectores, cascada, *flexbox*/*grid*, variables y **tokens**, `@media`, BEM, contraste. |

### Parte D — Los módulos, uno por uno

| Capítulo | Módulo |
|---|---|
| [D1](./D1_modulo_1_autenticacion.md) | **M1** Usuarios y autenticación (registro, login, bloqueo, recuperación). |
| [D2](./D2_modulo_2_eventos.md) | **M2** Gestión de eventos (crear con moderación asíncrona, editar, baja, estadísticas). |
| [D3](./D3_modulo_3_participacion.md) | **M3** Participación (inscripción, pago, cancelación, **valoración por cronograma**). |
| [D4](./D4_modulo_4_catalogo_publico.md) | **M4** Interfaz pública (catálogo, filtros, ficha, visitas). |
| [D5](./D5_modulo_5_moderacion.md) | **M5** Moderación de contenido. |
| [D6](./D6_modulo_6_administracion.md) | **M6** Panel de administración. |
| [D7](./D7_modulo_7_organizaciones.md) | **M7** Organizaciones y equipos (CUIT, firma). |
| [D8](./D8_modulo_8_membresias.md) | **M8** Membresías y estadísticas por cronograma (**lo que falta programar**). |
| [D9](./D9_modulo_N_notificaciones.md) | **N** Notificaciones por correo. |

[**Glosario**](./G_glosario.md) — los términos del manual, con su capítulo.

---

## Mapa: de dónde viene cada concepto

| Si querés entender… | Leé |
|---|---|
| Por qué el login responde siempre lo mismo | B04 §5 → D1 §3 |
| Cómo se evita sobrevender un cupo | B03 §7 → D3 §3.3 |
| Por qué un pago rechazado no revierte la inscripción | D3 §3.2 |
| Cómo funciona la moderación asíncrona | B05 §4 → D2 §2.4 → D5 |
| Qué pasa cuando apretás "Inscribirme" | C02 §7.2 → C01 §8.3 → D3 |
| Por qué hay tantas clases `*Service` separadas | A02 §13 → B03 §6.6 |
| Cómo se escribe un endpoint nuevo | C01 §9 → B02 |
| Cómo se escribe una pantalla nueva | C03 §12 → C02 §11 |
| Cómo se prueba un servicio | B06 §4 |
| Qué falta programar | D8 → [`docs/tempDFD`](../tempDFD/README.md) |

---

## Convenciones del proyecto que conviene conocer de antemano

- **Idioma:** código y comentarios en español rioplatense, sin tildes ni `ñ` en los textos de correo; `CLAUDE.md` en inglés (con versión en español).
- **Capas estrictas:** *controller → service → repository*; **nunca** se expone una entidad en la API.
- **Excepciones propias** que extienden `RuntimeException`; **un único manejador** global.
- **Borrado lógico**: nunca `DELETE` de usuarios ni de eventos.
- **Moderar antes de persistir.**
- **Secretos por variables de entorno**; sin credenciales, **modo simulado**.
- **Diseño:** paleta clara con `tokens.css`, **esquinas cuadradas**, sin colores en hexadecimal fuera de `tokens.css`.
- **Una sola fuente de servidor:** el frontend se sirve desde Spring Boot (`http://localhost:8080`). **Nunca** levantar un servidor estático aparte.

---

## Referencias del repositorio

- Requisitos: `docs/requisitos/` · Diagramas: `docs/diagrams/` · Pendientes: `docs/tempDFD/` · Modelo de datos: `docs/diseño_bd/MER.md`
- Bitácora de decisiones: `docs/log/` y `CLAUDE.md` · **Historia del proyecto, de punta a punta**: [`docs/log/HISTORICO_LINEA_DE_TIEMPO.md`](../log/HISTORICO_LINEA_DE_TIEMPO.md) (línea de tiempo por fases y sprints, desde la especificación del 2026-05-15 hasta el cierre)
- Preguntas abiertas y sugerencias: [`docs/SUGERENCIAS_PENDIENTES.md`](../SUGERENCIAS_PENDIENTES.md)
