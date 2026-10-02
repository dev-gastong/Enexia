# 📋 Listado Temporal de Frames - Figma

**Proyecto:** Proyecto Fin de Año  
**URL:** https://www.figma.com/design/KdrwK2Enqwi9Rzt2KfcnCC/Proyecto-Fin-de-A%C3%B1o  
**Extracción:** 2026-09-03  
**Estado:** ✅ Acceso público confirmado

---

## 📑 Estructura

- **2 Páginas:** Page 1 y Page 2
- **28 Frames/Componentes** detectados
- **Categorías:** Cronogramas, Inscripciones, Recuperación de contraseña, Dashboard, Registro, Errores de validación

---

## 🎯 Listado Completo de Frames

### 📅 Módulo 4: Ticket System & Registrations (Cronogramas e Inscripciones)

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 1 | Cronograma seleccionado 2 | Modal/Screen | Vista de cronograma (seleccionado) | ⏳ Pendiente |
| 2 | Cronograma seleccionado 3 | Modal/Screen | Vista de cronograma (seleccionado) | ⏳ Pendiente |
| 3 | Inscripcion gratis | Modal | Estado de inscripción: Gratis | ⏳ Pendiente |
| 4 | Inscripcion paga | Modal | Estado de inscripción: Paga | ⏳ Pendiente |
| 5 | Inscripcion Cancelada | Modal | Estado de inscripción: Cancelada | ⏳ Pendiente |

### 🔐 Módulo 1: User Management & Authentication (Recuperación de Contraseña)

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 6 | Recuperar Pass 1 | Screen | Pantalla 1 recuperación contraseña | ⏳ Pendiente |
| 7 | Recuperar pass 2 | Screen | Pantalla 2 recuperación contraseña | ⏳ Pendiente |
| 8 | Recuperar Pass 3 | Screen | Pantalla 3 recuperación contraseña | ⏳ Pendiente |

### ⚠️ Módulo 1: Validación de Contraseña (Estados de Error)

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 9 | Error contraseña un numero | Alert | Error: falta un número | ⏳ Pendiente |
| 10 | Error contraseña mayuscula | Alert | Error: falta mayúscula | ⏳ Pendiente |
| 11 | Error contraseña no coincide | Alert | Error: contraseñas no coinciden | ⏳ Pendiente |
| 12 | Error Contraseña 8 Carac | Alert | Error: menos de 8 caracteres | ⏳ Pendiente |

### 📧 Módulo 1: Email & Registro (Errores de Validación)

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 13 | Error email | Alert | Error en validación de email | ⏳ Pendiente |

### 🏢 Módulo 6: Admin Panel

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 14 | Dashboard Admin recien registrado | Screen | Dashboard para admin recién registrado | ⏳ Pendiente |

### 👥 Módulo 1: Registro (Personas Físicas desde Personas Jurídicas)

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 15 | Registro PF desde PJ | Screen | Flujo de registro: Persona Física desde Persona Jurídica | ⏳ Pendiente |

### 🎨 Branding & Logo

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 16 | Enexia Logo | Component | Logo de Enexia | ✅ Ya existe |

### 📍 Módulo 2: Event Management (Errores de Ubicación)

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 17 | Error Latitud | Alert | Error en validación de latitud | ⏳ Pendiente |
| 18 | Error Longitud | Alert | Error en validación de longitud | ⏳ Pendiente |

### 🎟️ Módulo 2: Event Management (Errores de Cupo/Tickets)

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 19 | Error Cupo Maximo | Alert | Error: cupo máximo | ⏳ Pendiente |
| 20 | Error Cupo Maximo Maximo | Alert | Error: cupo máximo excedido | ⏳ Pendiente |
| 21 | Error Cupo Maximo Minimo | Alert | Error: cupo mínimo | ⏳ Pendiente |
| 22 | Error Cupo Maximo Obligatorio | Alert | Error: cupo es obligatorio | ⏳ Pendiente |
| 23 | Error Precio Ticket Maximo | Alert | Error: precio máximo de ticket | ⏳ Pendiente |
| 24 | Error Precio Ticket Minimo | Alert | Error: precio mínimo de ticket | ⏳ Pendiente |
| 25 | Error Precio Ticket Texto | Alert | Error: formato de precio inválido | ⏳ Pendiente |

### 🎲 Otros / Frame Genérico

| # | Nombre Frame | Tipo | Descripción | Estado |
|---|---|---|---|---|
| 26 | Frame | Component | Frame genérico (sin definir) | ❓ Revisar |
| 27 | div.flex | Component | Componente div con flex layout | ❓ Revisar |

---

## 📊 Resumen por Categoría

| Categoría | Cantidad | Estado |
|-----------|----------|--------|
| **Screens (Pantallas completas)** | 5 | ⏳ Pendiente |
| **Modals (Modales/Carteles)** | 8 | ⏳ Pendiente |
| **Alerts (Alertas/Errores)** | 14 | ⏳ Pendiente |
| **Components (Componentes reutilizables)** | 1 | ✅ Existe |
| **Indefinido/Revisar** | 2 | ❓ Revisar |
| **TOTAL** | 30 | - |

---

## 🎯 Próximas Acciones

### Paso 1: Revisión
- [ ] Revisar "Frame" y "div.flex" en Figma (pueden ser layouts base)
- [ ] Confirmar si hay más frames en Page 2

### Paso 2: Conversión a HTML
Una vez confirmado, extraeré TODOS los frames como HTML con:
- ✅ Paleta de colores: `HISTORICO_PALETA_COLORES.md`
- ✅ Tipografía: `DM Sans` y `Space Grotesk`
- ✅ Layout responsive
- ✅ Variables CSS en lugar de colores hardcodeados

### Paso 3: Generación de Archivos
Se crearán archivos por categoría:
```
enexia/src/main/resources/static/pages/
├── auth/
│   ├── recuperar-pass-1.html
│   ├── recuperar-pass-2.html
│   └── recuperar-pass-3.html
├── participant/
│   ├── cronograma-detalles.html
│   └── inscripcion-estado.html
├── organizer/
│   ├── crear-tickets.html
│   └── validaciones-eventos.html
├── admin/
│   └── dashboard-recien-registrado.html
└── modals/
    ├── error-email.html
    ├── error-password-*.html
    ├── error-ubicacion.html
    ├── error-cupo.html
    └── error-precio-ticket.html
```

---

## 🔗 Referencia Rápida

**Paleta de Colores:** `enexia/HISTORICO_PALETA_COLORES.md`
- Naranja acción: `#c2410c`
- Morado acento: `#7c3aed`
- Base clara: `#faf8fa`

**Tipografía:**
- Sans (body): `DM Sans`
- Marca (titulares): `Space Grotesk`
- Mono (código): `ui-monospace`

**Tokens disponibles:** 260+ variables CSS para aplicar automáticamente

---

## ✅ Checklist de Extracción

- [x] Acceso a Figma confirmado
- [x] Listado de frames completado
- [x] Estructura de módulos identificada
- [ ] Screenshots de cada frame (siguientes)
- [ ] Exportación de componentes
- [ ] Conversión a HTML
- [ ] Aplicación de paleta de colores
- [ ] Revisión de validación
- [ ] Envío a Claude Design para optimización

---

**Siguiente paso:** ¿Empiezo a extraer todos los frames como HTML o primero revisar "Frame" y "div.flex" en Figma?
