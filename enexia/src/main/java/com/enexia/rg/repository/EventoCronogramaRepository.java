package com.enexia.rg.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.EventoCronograma;

/** Agenda de un evento (RF-2.4, RF-4.4). */
@Repository
public interface EventoCronogramaRepository extends JpaRepository<EventoCronograma, Long> {

    List<EventoCronograma> findByEventoIdEventoOrderByFechaAscHoraInicioAsc(Long idEvento);

    /**
     * Proxima fecha futura de cada evento de una pagina, en UNA sola consulta.
     *
     * Devuelve {@code Object[]{idEvento, fecha}} porque JPQL no puede construir
     * un DTO con una funcion de agregacion sin declarar una clase para eso; para
     * dos columnas de uso interno, no vale la pena.
     */
    @Query("""
            SELECT c.evento.idEvento, MIN(c.fecha) FROM EventoCronograma c
            WHERE c.evento.idEvento IN :ids
              AND c.fecha >= :desde
            GROUP BY c.evento.idEvento
            """)
    List<Object[]> buscarProximaFechaPorEvento(@Param("ids") List<Long> ids,
                                               @Param("desde") LocalDate desde);

    long countByEventoIdEvento(Long idEvento);
}
