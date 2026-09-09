package com.enexia.rg.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.TipoTicket;

/**
 * Catalogo de tipos de ticket (RF-2.5).
 *
 * El MER modela Tipo_Ticket como catalogo compartido, no como texto libre por
 * evento. Si un organizador escribe un nombre que no existe, el service crea la
 * fila: asi el catalogo crece con el uso real sin obligar a un ABM previo, y
 * los filtros del catalogo publico siguen pudiendo agrupar por tipo.
 */
@Repository
public interface TipoTicketRepository extends JpaRepository<TipoTicket, Long> {

    Optional<TipoTicket> findByNombreIgnoreCase(String nombre);
}
