package com.enexia.rg.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;

import com.enexia.rg.model.HistorialEstadoInscripcion;

@Repository
public interface HistorialEstadoInscripcionRepository extends JpaRepository<HistorialEstadoInscripcion, Long> {

    /** Trazabilidad de una inscripcion, cronologica (RF-3.6, DFD 3.6.4). */
    @EntityGraph(attributePaths = "estadoInscripcion")
    @Query("""
            SELECT h FROM HistorialEstadoInscripcion h
            WHERE h.inscripcion.idInscripcion = :idInscripcion
            ORDER BY h.fechaCambio ASC
            """)
    List<HistorialEstadoInscripcion> buscarPorInscripcion(@Param("idInscripcion") Long idInscripcion);

    /** Misma consulta en bloque para varias inscripciones (evita N+1 en el historial paginado). */
    @EntityGraph(attributePaths = "estadoInscripcion")
    @Query("""
            SELECT h FROM HistorialEstadoInscripcion h
            WHERE h.inscripcion.idInscripcion IN :idsInscripcion
            ORDER BY h.fechaCambio ASC
            """)
    List<HistorialEstadoInscripcion> buscarPorInscripciones(@Param("idsInscripcion") List<Long> idsInscripcion);
}
