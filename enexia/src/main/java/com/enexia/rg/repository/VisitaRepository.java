package com.enexia.rg.repository;

import com.enexia.rg.model.Visita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitaRepository extends JpaRepository<Visita, Long> {

}
