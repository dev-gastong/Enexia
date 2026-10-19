# DFD — Consulta de estadísticas de un evento (estado actual del código)

> **Qué describe:** lo que **existe hoy** (`GET /api/organizador/eventos/{id}/estadisticas`, `EventoService.estadisticas`). Las métricas son **del evento completo**. Lo que falta —estadísticas **por cronograma** y las avanzadas del plan Pro— está especificado en [tempDFD](../../tempDFD/README.md): [2.10](../../tempDFD/modulo_2_estadisticas/2.10_estadisticas_por_cronograma.md) y [8.3](../../tempDFD/modulo_8_membresias/8.3_restriccion_features_pro.md).
>
> Reescrito el 2026-10-19: la versión anterior mostraba una bifurcación por plan Pro que nunca se programó.

```mermaid
graph TD
    INICIO([INICIO: Solicitud de métricas de un evento]) --> Org([Organizador en su panel])
    Org -->|GET /api/organizador/eventos/id/estadisticas + JWT| P2_6_0[2.6.0: Validar JWT y rol ORGANIZADOR]

    subgraph Almacenes
        D10_Ev[(Evento)]
        D5_Vis[(Visita)]
        D12_Tic[(Cronograma_Ticket)]
        D11_Cro[(Evento_Cronograma)]
    end

    P2_6_0 --> C_Token{¿JWT válido<br>y rol ORGANIZADOR?}
    C_Token -->|No| Err_Auth([Error 401 / 403])
    C_Token -->|Sí| P2_6_1[2.6.1: Verificar propiedad del evento]
    P2_6_1 -->|Evento con su organizador| D10_Ev
    P2_6_1 --> C_Dueno{¿El evento existe<br>y es del organizador?}
    C_Dueno -->|No| Err_404([Error 404: no se distingue ajeno de inexistente])
    C_Dueno -->|Sí| P2_6_2[2.6.2: Calcular métricas del evento]
    P2_6_2 -->|COUNT DISTINCT usuario y COUNT total| D5_Vis
    P2_6_2 -->|COUNT de cronogramas| D11_Cro
    P2_6_2 -->|SUM de cupo máximo y cupo actual| D12_Tic
    P2_6_2 --> Ok([Response 200: visitas únicas y totales, cronogramas, cupo ofrecido y ocupado])

    Err_Auth --> FIN([FIN])
    Err_404 --> FIN
    Ok --> FIN
```

## Notas

- **No hay valoraciones en esta respuesta.** El promedio por función es justamente lo que agrega 2.10.
- **No hay bifurcación por plan:** hoy cualquier organizador ve estas métricas. La restricción Pro aplicará solo a las **avanzadas** (8.3).
- **Evento ajeno = 404**, no 403, para no revelar qué ids existen.
