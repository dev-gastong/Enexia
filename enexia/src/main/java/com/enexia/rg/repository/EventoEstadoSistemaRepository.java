package com.enexia.rg.repository;

import com.enexia.rg.model.EventoEstadoSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoEstadoSistemaRepository extends JpaRepository<EventoEstadoSistema, Long> {

}
