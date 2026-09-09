package com.enexia.rg.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.EventoEstadoOrganizador;

/** Estados que gestiona el propio organizador (RF-2.8, RF-2.9). */
@Repository
public interface EventoEstadoOrganizadorRepository extends JpaRepository<EventoEstadoOrganizador, Long> {

    Optional<EventoEstadoOrganizador> findByEstadoOrganizadorIgnoreCase(String estadoOrganizador);
}
