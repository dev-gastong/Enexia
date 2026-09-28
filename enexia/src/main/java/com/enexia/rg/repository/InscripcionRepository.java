package com.enexia.rg.repository;

import java.util.Optional;

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
}
