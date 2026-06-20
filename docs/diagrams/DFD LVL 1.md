```mermaid
graph TD

    %% --- CONFIGURACIÓN DE ESPACIADO ---

    %% Modificamos la longitud de los conectores críticos para estirar el gráfico



    %% --- ENTIDADES EXTERNAS (ACTORES) ---

    Part([Actor: Participante])

    Org([Actor: Organizador])

    Admin([Actor: Administrador])

    API_Mod([API Externa: Moderación / Cloudinary])



    %% --- ALMACENES DE DATOS (TABLAS REALES) ---

    subgraph Almacenes [ ]

        style Almacenes fill:none,stroke:none

        D1[(D1: Persona / Usuario / Rol / Miembros_Org / Persona_Fisica / Persona_Juridica)]

        D2[(D2: Evento / Cronograma / Detalle / Ubicacion / Categoria / Ciudad / Provincia / Pais)]

        D3[(D3: Inscripcion / Cronograma_Ticket)]

        D4[(D4: Pago / Suscripcion)]

        D5[(D5: Valoracion / Visita)]

        D6[(D6: Historial_Interacciones / PasswordResetToken)]

    end



    %% Separadores invisibles para ensanchar el núcleo del diagrama

    D1 ~~~ D3

    D2 ~~~ D4

    D5 ~~~ D6



    %% --- PROCESOS PRINCIPALES ---

    P1[1.0 Gestión de Usuarios y Auth]

    P2[2.0 Gestión de Eventos]

    P3[3.0 Gestión de Participación e Inscripciones]

    P4[4.0 Interfaz Pública y Motor de Búsqueda]

    P5[5.0 Proceso Central de Moderación y Seguridad]

    P6[6.0 Panel de Administración Global]

    P7[7.0 Segmentación de Perfiles y Organización]

    P8[8.0 Membresías y Suscripciones Pro]



    %% --- FLUJOS: MÓDULO 1 & 7 (REGISTRO, AUTH Y MIEMBROS) ---

    Part --->|Formulario Registro o Login| P1

    Org --->|Formulario Registro o Login| P1

    P1 ---->|Texto de Registro a Analizar| P5

    P5 -.->|Registro Aprobado| P1

    P1 --->|Validar Tipo Persona| P7

    P7 --->|Perfil Validado| P1

    P1 ---->|Persistir Persona, Usuario y Rol| D1

    P1 --->|Emitir JWT o Token| Part

    P1 --->|Emitir JWT o Token| Org

    

    Org --->|Asignar Miembros a Organización| P7

    P7 ---->|Vincular Relación Miembros_Organizacion| D1

    P7 ---->|Guardar Domicilio Fiscal Legal de Org| D2



    %% --- FLUJOS: MÓDULO 2 (GESTIÓN DE EVENTOS) ---

    Org --->|Crear o Modificar o Baja Evento| P2

    P2 --->|Verificar Límites de Publicación| P8

    P8 -.->|Cupo Permitido| P2

    P2 ---->|Contenido a Analizar| P5

    P5 -.->|Evento Aprobado| P2

    P2 ---->|Guardar o Actualizar Evento y Tickets| D2

    P2 ---->|Borrado Lógico: Cancelación| D2

    P2 --->|Dashboard Personal y Métricas| Org



    %% --- FLUJOS: MÓDULO 3 (PARTICIPACIÓN Y COMPRAS) ---

    Part --->|Inscripción a Cronograma Gratis o Pago| P3

    P3 ---->|Consultar y Descontar cupo_actual| D3

    P3 ---->|Registrar Inscripción| D3

    P3 ---->|Simular Proceso de Pago| D4

    P3 --->|Confirmación de Inscripción Ticket| Part

    Part --->|Enviar Valoración Única por Cronograma| P3

    P3 ---->|Texto de Reseña a Analizar| P5

    P5 -.->|Reseña Aprobada| P3

    P3 ---->|Persistir Valoración| D5

    Part --->|Consultar Historial de Inscripciones| P3

    D3 ---->|Leer Inscripciones Pasadas y Activas| P3



    %% --- FLUJOS: MÓDULO 4 (INTERFAZ PÚBLICA Y VISITAS) ---

    Part --->|Búsqueda o Filtros Avanzados| P4

    Org --->|Búsqueda o Filtros Avanzados| P4

    D2 ---->|Consultar Catálogo, Fechas y Ubicaciones| P4

    P4 --->|Renderizar Ficha Técnica| Part

    P4 --->|Renderizar Ficha Técnica| Org

    Part --->|Acceso Pasivo a Detalle del Evento| P4

    Org --->|Acceso Pasivo a Detalle del Evento| P4

    P4 ---->|Registrar fecha_visita e id_usuario| D5



    %% --- FLUJOS: MÓDULO 5 (MODERACIÓN CENTRAL) ---

    P5 ---->|Petición Síncrona Texto o Binario| API_Mod

    API_Mod ---->|Resultado Limpio o Sensible| P5

    P5 -.->|Si falla: Rechazar Carga| P1

    P5 -.->|Si falla: Rechazar Carga| P2

    P5 -.->|Si falla: Rechazar Carga| P3

    P5 -.->|Si falla: Rechazar Carga| P6



    %% --- FLUJOS: MÓDULO 6 (PANEL DE ADMINISTRACIÓN GLOBAL) ---

    Admin --->|ABM de Categorías| P6

    P6 ---->|Texto de Nueva Categoría a Analizar| P5

    P5 -.->|Categoría Aprobada| P6

    P6 ---->|Guardar o Modificar Categoría| D2

    Admin --->|Modificar Estados de Usuarios| P6

    P6 ---->|Actualizar estado del Usuario| D1

    Admin --->|Modificar Estados de Eventos| P6

    P6 ---->|Actualizar id_estado_sistema| D2

    Admin --->|Modificar Suscripciones Excepcionales| P6

    P6 ---->|Forzar Tipo Plan, Vigencia o Estado| D4

    P6 --->|Alertas y Métricas Globales| Admin



    %% --- FLUJOS: MÓDULO 8 (SUSCRIPCIONES) ---

    Org --->|Solicitud Suscripción Plan Pro| P8

    P8 ---->|Procesar Pago Simulado Suscripción| D4

    P8 ---->|Activar Suscripción Pro| D4



    %% --- CONTROL DE AUDITORÍA GLOBAL ---

    P1 -.->|Auditoría Pasiva| D6

    P2 -.->|Auditoría Pasiva| D6

    P3 -.->|Auditoría Pasiva| D6

    P6 -.->|Auditoría Pasiva| D6
