package com.enexia.rg.repository;

import java.util.Optional;

import com.enexia.rg.model.InscripcionEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InscripcionEstadoRepository extends JpaRepository<InscripcionEstado, Long> {

    Optional<InscripcionEstado> findByNombreEstadoIgnoreCase(String nombreEstado);
}
