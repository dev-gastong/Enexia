package com.enexia.rg.repository;

import com.enexia.rg.model.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

}
