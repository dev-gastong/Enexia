# DFD Nivel 1 — Módulo 8: Membresías (suscripción básica)

**Estado:** pendiente de programar. Reescrito el 2026-10-19 con las decisiones D1 a D6 del [README de tempDFD](../README.md): dos planes (`GRATUITO` y `PRO`), **sin límite de eventos**, y estadísticas **por cronograma**.

```mermaid
graph TD
    %% --- ENTIDADES EXTERNAS ---
    Org([Organizador])
    Admin([Administrador])
    Reloj([Temporizador del sistema - Scheduler])
    Pasarela([Pasarela de pago simulada])
    P2_Ext([Proceso 2.10: Estadísticas por cronograma])

    %% --- ALMACENES DE DATOS ---
    subgraph Almacenes [ ]
        style Almacenes fill:none,stroke:none
        D20_Sus[(D20: Suscripcion / Suscripcion_Estado)]
        D20_Hist[(D20: Historial_Estado_Suscripcion)]
        D18_Pago[(D18: Pago)]
        D10_Ev[(D10: Evento)]
        D6_Logs[(D6: Historial_Interacciones)]
    end

    %% --- SUB-PROCESOS ---
    P8_1[8.1 Ciclo de vida de la suscripción Pro: upgrade, cancelar, vencer]
    P8_2[8.2 Resolver el plan vigente - sin tope de eventos]
    P8_3[8.3 Autorizar estadísticas avanzadas por cronograma]
    P6_4[6.4 Intervención del administrador - ya implementado]

    %% --- 8.1 ---
    Org -->|Pide upgrade a Pro / cancela / consulta su plan| P8_1
    P8_1 -->|Crear PENDIENTE, activar o cancelar + vigencia| D20_Sus
    P8_1 -->|Asentar cada cambio de estado| D20_Hist
    P8_1 -->|Cobrar el mes de Pro| Pasarela
    Pasarela -->|Aprobado o rechazado| P8_1
    P8_1 -->|Registrar el pago COMPLETADO o FALLIDO| D18_Pago
    P8_1 -->|Auditoría| D6_Logs
    Reloj -->|Cada día: ACTIVA con fecha_fin vencida pasa a VENCIDA| P8_1
    P8_1 -->|Plan, estado y vigencia| Org

    %% --- 8.2 ---
    Org -->|Crea un evento: SIN consultar cuota| D10_Ev
    P8_2 -->|Leer la última suscripción del usuario| D20_Sus
    P8_2 -.->|Plan vigente: PRO o GRATUITO| P8_1
    P8_2 -.->|Plan vigente| P8_3

    %% --- 8.3 ---
    Org -->|Pide estadísticas avanzadas de un evento| P8_3
    P8_3 -->|Leer el plan del DUEÑO del evento| P8_2
    P8_3 -->|Autorizar o 403| P2_Ext
    P2_Ext -->|Métricas avanzadas por cronograma| Org
    P8_3 -->|Registrar intento denegado| D6_Logs

    %% --- Admin ---
    Admin -->|REVOCAR / CAMBIAR_PLAN / RENOVAR| P6_4
    P6_4 -->|Modificar suscripción + historial con motivo| D20_Sus

    style P2_Ext fill:#fff,stroke:#333,stroke-dasharray: 5 5
    style P6_4 fill:#eee,stroke:#333,stroke-dasharray: 5 5
```

## Qué cambió respecto del diseño original

| Antes | Ahora |
|---|---|
| 8.2 contaba los eventos activos del organizador Free y bloqueaba la creación al llegar al límite. | **8.2 ya no bloquea nada.** Solo resuelve "¿PRO o GRATUITO?" para que 8.3 sepa si habilita lo avanzado. El flujo de creación de eventos no consulta cuotas. |
| 8.3 protegía reportes agregados por evento. | 8.3 protege las métricas **avanzadas por cronograma**; las básicas por cronograma son para todos (DFD 2.10). |
| 8.1 dejaba abierta la duración y la baja. | Pro **mensual**, sin renovación automática; el organizador puede cancelar; un job diario marca `VENCIDA`. |

## Lo que ya existe y no hay que rehacer

- Modelo: `Suscripcion`, `SuscripcionEstado` (PENDIENTE, ACTIVA, VENCIDA, CANCELADA), `HistorialEstadoSuscripcion`, `Pago` (con `id_suscripcion`).
- Panel de administración (RF-6.4): lectura de planes, ingresos del mes y las intervenciones `REVOCAR`, `CAMBIAR_PLAN`, `RENOVAR` (`AdminSuscripcionService`).
- Pasarela simulada (`PasarelaPagoSimuladaService`): un método de pago que contenga "rechazar" fuerza el rechazo.
- Configuración: `enexia.planes.pro.precio-mensual` (9.99 por defecto).

## Lo que falta (esta carpeta)

Todo el lado del **organizador** (no hay pantalla ni endpoint propio), el **vencimiento automático** (hoy nadie pasa una suscripción a `VENCIDA`: solo hay filas sembradas a mano) y las **estadísticas por cronograma**.
