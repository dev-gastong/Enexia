package com.enexia.rg.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Evento;

/**
 * Consultas del catalogo publico (RF-4.1 a RF-4.3) y del dashboard del
 * organizador (RF-2.8).
 *
 * POR QUE @EntityGraph Y NO "JOIN FETCH" EN LAS CONSULTAS PAGINADAS
 * Un JOIN FETCH sobre una COLECCION rompe la paginacion: la base devuelve una
 * fila por elemento de la coleccion, asi que "20 filas" ya no son 20 eventos, y
 * Hibernate resuelve el desajuste trayendose TODO a memoria para recortar ahi
 * (avisa con HHH000104 y el rendimiento se desploma al crecer la tabla).
 * {@code @EntityGraph} sobre asociaciones *-a-uno no tiene ese problema -- no
 * multiplica filas -- y evita igual el N+1 de categoria, estados y organizador.
 *
 * Ciudad, provincia y proxima fecha NO se traen aca: viven en EventoDetalle y
 * EventoCronograma, y el service las resuelve con UNA consulta por pagina
 * (no por fila) usando los metodos de los repositorios correspondientes.
 *
 * SEGURIDAD: todo parametro va enlazado (:nombre). Nunca concatenar strings.
 */
@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

    /** Estados de sistema que hacen visible un evento en el catalogo publico. */
    List<String> ESTADOS_VISIBLES = List.of("APROBADO_SISTEMA", "APROBADO_MANUAL");

    /**
     * Catalogo publico con busqueda y filtros combinables (RF-4.1, 4.2, 4.3).
     *
     * EL PATRON ":param IS NULL OR condicion"
     * Permite que un unico metodo cubra las 16 combinaciones de filtros. La
     * alternativa (Criteria API o Specifications) es mas flexible pero mucho
     * menos legible, y aca los filtros son fijos y conocidos. El coste es que la
     * base evalua las condiciones nulas igual; con indices en id_categoria y
     * estado el plan sigue siendo bueno.
     *
     * DOBLE CONDICION DE VISIBILIDAD: aprobado por el sistema Y publicado por su
     * organizador. Comprobar solo una dejaria en el catalogo eventos cancelados
     * (aprobados pero retirados) o eventos aun EN_PROCESO.
     *
     * El filtro de fechas usa EXISTS y no un JOIN: con JOIN, un evento con tres
     * fechas dentro del rango aparecería tres veces en la grilla.
     */
    @EntityGraph(attributePaths = {"categoria", "estadoSistema", "estadoOrganizador",
                                   "organizador", "organizador.personaFisica", "personaJuridica"})
    @Query("""
            SELECT e FROM Evento e
            WHERE e.estadoSistema.estadoSistema IN :estadosVisibles
              AND e.estadoOrganizador.estadoOrganizador = :estadoPublicado
              AND (:texto IS NULL OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :texto, '%')))
              AND (:idCategoria IS NULL OR e.categoria.idCategoria = :idCategoria)
              AND (:idCiudad IS NULL OR EXISTS (
                    SELECT 1 FROM EventoDetalle d
                    WHERE d.evento = e AND d.ubicacion.ciudad.idCiudad = :idCiudad))
              AND (:idProvincia IS NULL OR EXISTS (
                    SELECT 1 FROM EventoDetalle d2
                    WHERE d2.evento = e AND d2.ubicacion.ciudad.provincia.idProvincia = :idProvincia))
              AND (:desde IS NULL OR EXISTS (
                    SELECT 1 FROM EventoCronograma c
                    WHERE c.evento = e AND c.fecha >= :desde))
              AND (:hasta IS NULL OR EXISTS (
                    SELECT 1 FROM EventoCronograma c2
                    WHERE c2.evento = e AND c2.fecha <= :hasta))
            """)
    Page<Evento> buscarCatalogoPublico(@Param("estadosVisibles") List<String> estadosVisibles,
                                       @Param("estadoPublicado") String estadoPublicado,
                                       @Param("texto") String texto,
                                       @Param("idCategoria") Long idCategoria,
                                       @Param("idProvincia") Long idProvincia,
                                       @Param("idCiudad") Long idCiudad,
                                       @Param("desde") LocalDate desde,
                                       @Param("hasta") LocalDate hasta,
                                       Pageable paginado);

    /**
     * Un evento visible al publico, con todo lo *-a-uno ya cargado.
     *
     * Repite la doble condicion de visibilidad a proposito: si la ficha usara
     * findById a secas, cualquiera podria leer un evento RECHAZADO_SISTEMA
     * pegando su id en la URL, y el rechazo por moderacion no serviria de nada.
     *
     * El eje del organizador acepta una LISTA (PUBLICADO y FINALIZADO), a
     * diferencia del catalogo, que solo lista PUBLICADO: un evento que ya
     * termino sale de la grilla pero su ficha tiene que seguir abriendose, porque
     * es desde ahi que quien asistio lo valora (RF-3.4) y desde "Mis
     * inscripciones" que lo vuelve a consultar.
     */
    @EntityGraph(attributePaths = {"categoria", "estadoSistema", "estadoOrganizador",
                                   "organizador", "organizador.personaFisica", "personaJuridica"})
    @Query("""
            SELECT e FROM Evento e
            WHERE e.idEvento = :idEvento
              AND e.estadoSistema.estadoSistema IN :estadosVisibles
              AND e.estadoOrganizador.estadoOrganizador IN :estadosOrganizador
            """)
    Optional<Evento> buscarPublicoPorId(@Param("idEvento") Long idEvento,
                                        @Param("estadosVisibles") List<String> estadosVisibles,
                                        @Param("estadosOrganizador") List<String> estadosOrganizador);

    /**
     * Eventos que ya terminaron y siguen figurando como PUBLICADO (o, con
     * {@code idEvento}, la misma comprobacion para un solo evento).
     *
     * Un evento termino cuando tiene al menos una fecha y NINGUNA queda por
     * delante: ni un dia futuro ni una funcion de hoy que aun no acabo. Misma
     * regla que la ficha usa para marcar una funcion como finalizada.
     *
     * Solo cuentan los aprobados: un evento rechazado o suspendido cuya fecha
     * paso conserva su estado, porque ahi el organizador (y el administrador)
     * necesitan seguir viendo POR QUE no salio, y un revertir posterior lo
     * deja aprobado y en la proxima pasada se finaliza solo.
     */
    @Query("""
            SELECT e.idEvento FROM Evento e
            WHERE e.estadoOrganizador.estadoOrganizador = :estadoPublicado
              AND e.estadoSistema.estadoSistema IN :estadosAprobados
              AND (:idEvento IS NULL OR e.idEvento = :idEvento)
              AND EXISTS (SELECT 1 FROM EventoCronograma c WHERE c.evento = e)
              AND NOT EXISTS (
                    SELECT 1 FROM EventoCronograma c2
                    WHERE c2.evento = e
                      AND (c2.fecha > :hoy OR (c2.fecha = :hoy AND c2.horaFin > :ahora)))
            """)
    List<Long> buscarIdsFinalizables(@Param("estadoPublicado") String estadoPublicado,
                                     @Param("estadosAprobados") List<String> estadosAprobados,
                                     @Param("idEvento") Long idEvento,
                                     @Param("hoy") LocalDate hoy,
                                     @Param("ahora") java.time.LocalTime ahora);

    /**
     * Dashboard del organizador (RF-2.8).
     *
     * A diferencia del catalogo, aca NO se filtra por estado de sistema: el
     * organizador tiene que ver sus eventos EN_PROCESO y los RECHAZADOS, que son
     * justamente los que requieren su atencion.
     */
    @EntityGraph(attributePaths = {"categoria", "estadoSistema", "estadoOrganizador",
                                   "organizador", "organizador.personaFisica", "personaJuridica"})
    @Query("""
            SELECT e FROM Evento e
            WHERE e.organizador.idUsuario = :idOrganizador
              AND (:texto IS NULL OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :texto, '%')))
              AND (:idCategoria IS NULL OR e.categoria.idCategoria = :idCategoria)
              AND (:estadoOrganizador IS NULL
                   OR e.estadoOrganizador.estadoOrganizador = :estadoOrganizador)
            """)
    Page<Evento> buscarDeOrganizador(@Param("idOrganizador") Long idOrganizador,
                                     @Param("texto") String texto,
                                     @Param("idCategoria") Long idCategoria,
                                     @Param("estadoOrganizador") String estadoOrganizador,
                                     Pageable paginado);

    /** Un evento propio, con sus asociaciones. Se usa para editar, dar de baja y ver metricas. */
    @EntityGraph(attributePaths = {"categoria", "estadoSistema", "estadoOrganizador",
                                   "organizador", "organizador.personaFisica", "personaJuridica"})
    @Query("SELECT e FROM Evento e WHERE e.idEvento = :idEvento")
    Optional<Evento> buscarConAsociaciones(@Param("idEvento") Long idEvento);

    /**
     * Relee el evento tomando un bloqueo de escritura sobre la fila
     * (SELECT ... FOR UPDATE). Requiere transaccion activa.
     *
     * POR QUE HACE FALTA
     * Sobre un mismo evento pueden escribir DOS procesos a la vez, y tocan cosas
     * distintas del mismo registro:
     *
     *   - el pipeline de moderacion, que fija estado_sistema y el contenido;
     *   - el organizador, que da de baja y fija estado_organizador.
     *
     * Sin bloqueo se pisan de dos maneras, ambas observadas en la suite:
     *   1. PERDIDA DE ACTUALIZACION: el pipeline reescribia la fila con los
     *      valores que habia leido antes de la baja y resucitaba el evento.
     *   2. ERROR 1020 "Record has changed since last read": las dos
     *      transacciones insertaban en historial_estado_evento, cuya clave
     *      foranea obliga a bloquear la fila de evento que la otra esta
     *      modificando. La baja respondia 500.
     *
     * Con FOR UPDATE, la segunda transaccion espera a que la primera confirme y
     * despues lee el estado ya actualizado. Ambas rutas toman ESTE bloqueo antes
     * que ningun otro, asi que el orden de adquisicion es siempre el mismo y no
     * puede haber interbloqueo.
     *
     * Es el mismo patron que IntentosLoginService usa para el contador de
     * intentos fallidos, y por la misma razon de fondo: leer-modificar-escribir
     * sin proteccion pierde escrituras en cuanto hay concurrencia.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evento e WHERE e.idEvento = :idEvento")
    Optional<Evento> bloquearParaActualizar(@Param("idEvento") Long idEvento);

    /**
     * Cola de moderacion del administrador (RF-6.1): TODOS los eventos, de
     * cualquier organizador, sin filtrar por estado de sistema ni de
     * organizador -- es justamente el panel que tiene que ver tanto los
     * rechazados por el sistema (escenario a) como los ya aprobados y
     * publicados (escenario b, candidatos a una suspension disciplinaria).
     * El filtro por estado concreto lo resuelve el cliente sobre esta misma
     * pagina, igual que ya hace mis-eventos.html con sus pseudo-filtros.
     */
    @EntityGraph(attributePaths = {"categoria", "estadoSistema", "estadoOrganizador",
                                   "organizador", "organizador.personaFisica", "personaJuridica"})
    @Query("""
            SELECT e FROM Evento e
            WHERE (:texto IS NULL OR LOWER(e.nombre) LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    Page<Evento> buscarParaModeracion(@Param("texto") String texto, Pageable paginado);

    /**
     * Eventos por categoria para el ABM del administrador (RF-6.3). Cada fila es
     * {@code [idCategoria, cantidad]}. Una sola consulta para todas las
     * categorias; las que no tienen eventos no aparecen (cuentan 0).
     * {@code soloVisibles} = publicados y aprobados, es decir, los que se ven en
     * el catalogo; si no, todos los que no fueron dados de baja.
     */
    @Query("""
            SELECT e.categoria.idCategoria, COUNT(e) FROM Evento e
            WHERE e.categoria IS NOT NULL
              AND e.estadoOrganizador.estadoOrganizador <> :estadoBaja
              AND (:soloVisibles = FALSE OR (
                    e.estadoOrganizador.estadoOrganizador = :estadoPublicado
                    AND e.estadoSistema.estadoSistema IN :estadosVisibles))
            GROUP BY e.categoria.idCategoria
            """)
    List<Object[]> contarPorCategoria(@Param("soloVisibles") boolean soloVisibles,
                                      @Param("estadoBaja") String estadoBaja,
                                      @Param("estadoPublicado") String estadoPublicado,
                                      @Param("estadosVisibles") List<String> estadosVisibles);

    /**
     * Eventos PUBLICADOS cuyo cronograma ya termino por completo: tienen al
     * menos una fecha y ninguna termina despues de {@code horaActual} de hoy.
     * Es la definicion de FINALIZADO. {@code idOrganizador} nulo = todos.
     */
    @Query("""
            SELECT e.idEvento FROM Evento e
            WHERE e.estadoOrganizador.estadoOrganizador = :estadoPublicado
              AND (:idOrganizador IS NULL OR e.organizador.idUsuario = :idOrganizador)
              AND EXISTS (SELECT 1 FROM EventoCronograma c WHERE c.evento = e)
              AND NOT EXISTS (
                    SELECT 1 FROM EventoCronograma c2
                    WHERE c2.evento = e
                      AND (c2.fecha > :hoy OR (c2.fecha = :hoy AND c2.horaFin > :horaActual)))
            """)
    List<Long> buscarVencidosPublicados(@Param("estadoPublicado") String estadoPublicado,
                                        @Param("idOrganizador") Long idOrganizador,
                                        @Param("hoy") LocalDate hoy,
                                        @Param("horaActual") java.time.LocalTime horaActual);

    /**
     * Cantidad de eventos de cada organizador del lote, para el directorio de
     * usuarios del administrador (RF-6.2). Una sola consulta agrupada en vez de
     * una por fila. Cada elemento es {@code [idUsuario, cantidad]}; los
     * organizadores sin eventos no aparecen (el service los toma como 0).
     * No cuenta los dados de baja, que el propio organizador ya no ve.
     */
    @Query("""
            SELECT e.organizador.idUsuario, COUNT(e) FROM Evento e
            WHERE e.organizador.idUsuario IN :ids
              AND e.estadoOrganizador.estadoOrganizador <> :estadoBaja
            GROUP BY e.organizador.idUsuario
            """)
    List<Object[]> contarPorOrganizador(@Param("ids") java.util.Collection<Long> ids,
                                        @Param("estadoBaja") String estadoBaja);
}
