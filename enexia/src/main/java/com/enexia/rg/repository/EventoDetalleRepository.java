package com.enexia.rg.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.EventoDetalle;

/**
 * Detalle del evento con su ubicacion resuelta (RF-4.4).
 *
 * Existe una consulta "por lista de ids" en vez de una por evento: al pintar una
 * grilla de 20 tarjetas, pedir la ciudad de cada una serian 20 consultas (el
 * clasico N+1). Con esta, es UNA sola para toda la pagina.
 */
@Repository
public interface EventoDetalleRepository extends JpaRepository<EventoDetalle, Long> {

    @Query("""
            SELECT d FROM EventoDetalle d
            LEFT JOIN FETCH d.ubicacion u
            LEFT JOIN FETCH u.ciudad c
            LEFT JOIN FETCH c.provincia p
            LEFT JOIN FETCH p.pais
            WHERE d.idEvento IN :ids
            """)
    List<EventoDetalle> buscarConUbicacionPorIds(@Param("ids") List<Long> ids);

    @Query("""
            SELECT d FROM EventoDetalle d
            LEFT JOIN FETCH d.ubicacion u
            LEFT JOIN FETCH u.ciudad c
            LEFT JOIN FETCH c.provincia p
            LEFT JOIN FETCH p.pais
            WHERE d.idEvento = :idEvento
            """)
    Optional<EventoDetalle> buscarConUbicacion(@Param("idEvento") Long idEvento);
}
