package com.enexia.rg.repository;

import com.enexia.rg.model.HistorialEstadoSuscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistorialEstadoSuscripcionRepository extends JpaRepository<HistorialEstadoSuscripcion, Long> {

}
