package com.enexia.rg.repository;

import com.enexia.rg.model.HistorialEstadoInscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistorialEstadoInscripcionRepository extends JpaRepository<HistorialEstadoInscripcion, Long> {

}
