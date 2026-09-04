package com.enexia.rg.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.PersonaJuridicaEstadoSistema;

@Repository
public interface PersonaJuridicaEstadoSistemaRepository extends JpaRepository<PersonaJuridicaEstadoSistema, Long> {

    /** Busca un estado del catalogo por nombre (REVISION_PENDIENTE, APROBADO, RECHAZADO). */
    Optional<PersonaJuridicaEstadoSistema> findByEstadoPersonaJuridicaSistemaIgnoreCase(String estadoPersonaJuridicaSistema);
}
