package com.enexia.rg.repository;

import com.enexia.rg.model.EventoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoDetalleRepository extends JpaRepository<EventoDetalle, Long> {

}
