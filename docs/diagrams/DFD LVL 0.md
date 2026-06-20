```mermaid
graph LR
    %% Entidades Externas (Usuarios)
    Part([Actor: Participante])
    Org([Actor: Organizador])
    Admin([Actor: Administrador])

    %% Entidades Externas (Servicios)
    API_Mod([API Externa: Moderación / Cloudinary])

    %% Sistema Central
    Sistema(((Sistema Enexia)))

    %% Flujos del Participante
    Part -->|Petición de Registro, Login, Inscripción, Valoración| Sistema
    Sistema -->|Catálogo Filtrado, Confirmación, Estado de Pago Simulado| Part

    %% Flujos del Organizador
    Org -->|Datos de Evento, Archivos Multimedia, Solicitud de Suscripción| Sistema
    Sistema -->|Dashboard Estadístico, Estado de Publicación, Límites de Plan| Org

    %% Flujos del Administrador
    Admin -->|Altas/Bajas de Eventos, Bloqueo de Cuentas, CRUD Categorías| Sistema
    Sistema -->|Métricas Globales de la Plataforma, Alertas de Auditoría| Admin

    %% Flujos con la API de Moderación
    Sistema -->|Texto e Imagen Binaria a Analizar| API_Mod
    API_Mod -->|Resultado de Análisis: Limpio / Sensible| Sistema

    %% Estilos Visuales
    style Sistema fill:#f9f,stroke:#333,stroke-width:3px
    style API_Mod fill:#fff,stroke:#333,stroke-dasharray: 5 5
