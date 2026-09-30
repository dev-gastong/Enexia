package com.enexia.rg.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Inscripcion;

/** Reservas y compras de tickets (RF-3.1, RF-3.3, RF-3.6). */
@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    /**
     * Una inscripcion con todo lo *-a-uno resuelto, para cancelarla o
     * mostrarla en el historial sin N+1 (ticket -> cronograma -> evento,
     * tipo de ticket y estado).
     */
    @EntityGraph(attributePaths = {
            "cronogramaTicket", "cronogramaTicket.tipoTicket", "cronogramaTicket.cronograma",
            "cronogramaTicket.cronograma.evento", "estadoInscripcion", "usuario"})
    @Query("SELECT i FROM Inscripcion i WHERE i.idInscripcion = :idInscripcion")
    Optional<Inscripcion> buscarConAsociaciones(@Param("idInscripcion") Long idInscripcion);

    /** Historial personal del participante, paginado y cronologico (RF-3.6). */
    @EntityGraph(attributePaths = {
            "cronogramaTicket", "cronogramaTicket.tipoTicket", "cronogramaTicket.cronograma",
            "cronogramaTicket.cronograma.evento", "estadoInscripcion"})
    @Query("SELECT i FROM Inscripcion i WHERE i.usuario.idUsuario = :idUsuario")
    Page<Inscripcion> listarDeUsuario(@Param("idUsuario") Long idUsuario, Pageable paginado);

    /**
     * Elegibilidad para valorar (RF-3.4, DFD 3.4.1): el participante necesita
     * una inscripcion CONFIRMADA (no cancelada, no pendiente) para ese
     * cronograma puntual.
     */
    @Query("""
            SELECT COUNT(i) > 0 FROM Inscripcion i
            WHERE i.usuario.idUsuario = :idUsuario
              AND i.cronogramaTicket.cronograma.idCronograma = :idCronograma
              AND i.estadoInscripcion.nombreEstado = 'CONFIRMADA'
            """)
    boolean existeConfirmadaDeUsuarioEnCronograma(@Param("idUsuario") Long idUsuario,
                                                   @Param("idCronograma") Long idCronograma);

    /**
     * Version en lote de {@link #existeConfirmadaDeUsuarioEnCronograma}: la
     * ficha publica (RF-4.4) la cruza con
     * {@code ValoracionRepository.idsDeCronogramasYaValoradosPorUsuario} y con
     * "finalizado" para decidir en que cronogramas ofrecer el formulario de
     * valorar (RF-3.4). Mismo motivo que
     * {@code idsDeCronogramasConInscripcionActiva}: una consulta por fecha
     * seria el N+1 de siempre.
     */
    @Query("""
            SELECT DISTINCT i.cronogramaTicket.cronograma.idCronograma FROM Inscripcion i
            WHERE i.usuario.idUsuario = :idUsuario
              AND i.cronogramaTicket.cronograma.idCronograma IN :idsCronograma
              AND i.estadoInscripcion.nombreEstado = 'CONFIRMADA'
            """)
    Set<Long> idsDeCronogramasConfirmadosDeUsuario(@Param("idUsuario") Long idUsuario,
                                                    @Param("idsCronograma") List<Long> idsCronograma);

    /**
     * Restriccion de unicidad de la inscripcion (no declarada en el MER, a
     * diferencia del UNIQUE compuesto que RF-3.4 SI exige para Valoracion):
     * un usuario no puede tener mas de una inscripcion VIVA para el mismo
     * cronograma, sin importar el tipo de ticket elegido.
     *
     * "VIVA" excluye CANCELADA a proposito: cancelar (RF-3.3) tiene que dejar
     * el camino libre para volver a inscribirse a ese mismo cronograma, igual
     * que liberar el cupo del ticket.
     */
    @Query("""
            SELECT COUNT(i) > 0 FROM Inscripcion i
            WHERE i.usuario.idUsuario = :idUsuario
              AND i.cronogramaTicket.cronograma.idCronograma = :idCronograma
              AND i.estadoInscripcion.nombreEstado <> 'CANCELADA'
            """)
    boolean existeActivaDeUsuarioEnCronograma(@Param("idUsuario") Long idUsuario,
                                              @Param("idCronograma") Long idCronograma);

    /**
     * Version en lote de {@link #existeActivaDeUsuarioEnCronograma}: la ficha
     * publica de un evento (RF-4.4) necesita saber, para TODOS los cronogramas
     * de la agenda a la vez, en cuales ya esta inscripto el visitante -- para
     * no mostrarle el boton "Inscribirme" donde ya tiene lugar (ver el bug de
     * doble inscripcion que esto reemplaza en evento-detalle.html). Una
     * consulta por cronograma seria el N+1 de siempre.
     */
    @Query("""
            SELECT DISTINCT i.cronogramaTicket.cronograma.idCronograma FROM Inscripcion i
            WHERE i.usuario.idUsuario = :idUsuario
              AND i.cronogramaTicket.cronograma.idCronograma IN :idsCronograma
              AND i.estadoInscripcion.nombreEstado <> 'CANCELADA'
            """)
    Set<Long> idsDeCronogramasConInscripcionActiva(@Param("idUsuario") Long idUsuario,
                                                    @Param("idsCronograma") List<Long> idsCronograma);

    /**
     * Existe AL MENOS UNA fila de Inscripcion para ese ticket, sin importar el
     * estado (incluida CANCELADA).
     *
     * Distinto a proposito de {@code existeActivaDeUsuarioEnCronograma}: aca no
     * importa si la inscripcion sigue "viva" para el negocio, importa si existe
     * la FILA -- porque eso es lo que determina si un DELETE fisico del ticket
     * (por ejemplo al reemplazar la agenda en una edicion, RF-2.7) viola la
     * clave foranea. Una inscripcion CANCELADA sigue bloqueando ese DELETE
     * igual que una CONFIRMADA.
     */
    boolean existsByCronogramaTicketIdCronogramaTicket(Long idCronogramaTicket);
}
