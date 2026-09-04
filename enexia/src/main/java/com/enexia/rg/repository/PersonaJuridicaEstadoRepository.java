package com.enexia.rg.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.PersonaJuridicaEstado;

@Repository
public interface PersonaJuridicaEstadoRepository extends JpaRepository<PersonaJuridicaEstado, Long> {

    /** Busca un estado del catalogo por nombre (ACTIVO, INACTIVO). */
    Optional<PersonaJuridicaEstado> findByEstadoPersonaJuridicaIgnoreCase(String estadoPersonaJuridica);
}
