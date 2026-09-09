package com.enexia.rg.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.CronogramaTicket;

/** Oferta comercial de cada fecha (RF-2.5, RF-2.6). */
@Repository
public interface CronogramaTicketRepository extends JpaRepository<CronogramaTicket, Long> {

    /**
     * Tickets de todas las fechas de un evento, en una consulta.
     *
     * El JOIN FETCH de tipoTicket evita una consulta por ticket al leer su
     * nombre. No hay riesgo de paginacion en memoria porque este metodo no
     * pagina: devuelve la agenda completa de UN evento.
     */
    @Query("""
            SELECT t FROM CronogramaTicket t
            JOIN FETCH t.tipoTicket
            WHERE t.cronograma.idCronograma IN :idsCronograma
            ORDER BY t.precio ASC
            """)
    List<CronogramaTicket> buscarPorCronogramas(@Param("idsCronograma") List<Long> idsCronograma);

    @Query("""
            SELECT COALESCE(SUM(t.cupoMaximo), 0) FROM CronogramaTicket t
            WHERE t.cronograma.evento.idEvento = :idEvento
            """)
    long sumarCupoMaximo(@Param("idEvento") Long idEvento);

    @Query("""
            SELECT COALESCE(SUM(t.cupoActual), 0) FROM CronogramaTicket t
            WHERE t.cronograma.evento.idEvento = :idEvento
            """)
    long sumarCupoActual(@Param("idEvento") Long idEvento);

    /**
     * Tickets con inscripciones vivas (RF-2.7).
     *
     * Es el control que impide bajar el precio o el cupo de una entrada que
     * alguien ya compro: cambiar esos valores despues de una transaccion
     * confirmada rompe el acuerdo con el participante.
     */
    @Query("""
            SELECT COUNT(t) FROM CronogramaTicket t
            WHERE t.cronograma.evento.idEvento = :idEvento
              AND t.cupoActual > 0
            """)
    long contarConInscripciones(@Param("idEvento") Long idEvento);
}
