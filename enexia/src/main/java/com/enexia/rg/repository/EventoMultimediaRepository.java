package com.enexia.rg.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.EventoMultimedia;

/** Imagenes aprobadas de un evento (RF-2.3, RF-5.3). */
@Repository
public interface EventoMultimediaRepository extends JpaRepository<EventoMultimedia, Long> {

    List<EventoMultimedia> findByEventoIdEventoOrderByOrdenAsc(Long idEvento);
}
