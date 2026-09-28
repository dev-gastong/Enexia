package com.enexia.rg.repository;

import java.util.Optional;

import com.enexia.rg.model.PagoEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PagoEstadoRepository extends JpaRepository<PagoEstado, Long> {

    Optional<PagoEstado> findByNombreEstadoIgnoreCase(String nombreEstado);
}
