package com.enexia.rg.repository;

import com.enexia.rg.model.PersonaFisica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonaFisicaRepository extends JpaRepository<PersonaFisica, Long> {

}
