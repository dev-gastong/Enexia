package com.enexia.rg.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Ciudad;

/**
 * Catalogo geografico (RF-4.3, selectores en cascada Provincia -> Ciudad).
 */
@Repository
public interface CiudadRepository extends JpaRepository<Ciudad, Long> {

    /**
     * Ciudades de una provincia, para el segundo selector de la cascada.
     *
     * El JOIN FETCH de provincia y pais permite que la respuesta incluya el
     * nombre de la provincia sin una consulta extra por ciudad.
     */
    @Query("""
            SELECT c FROM Ciudad c
            JOIN FETCH c.provincia p
            LEFT JOIN FETCH p.pais
            WHERE p.idProvincia = :idProvincia
            ORDER BY c.nombre
            """)
    List<Ciudad> buscarPorProvincia(@Param("idProvincia") Long idProvincia);

    Optional<Ciudad> findByNombreIgnoreCase(String nombre);
}
