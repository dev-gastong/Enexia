package com.enexia.rg.repository;

import com.enexia.rg.model.HistorialInteracciones;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistorialInteraccionesRepository extends JpaRepository<HistorialInteracciones, Long> {

}
