package com.enexia.rg.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Provincia;

/** Catalogo geografico (RF-4.3). */
@Repository
public interface ProvinciaRepository extends JpaRepository<Provincia, Long> {

    @Query("SELECT p FROM Provincia p JOIN FETCH p.pais ORDER BY p.nombre")
    List<Provincia> listarConPais();

    Optional<Provincia> findByNombreIgnoreCase(String nombre);
}
