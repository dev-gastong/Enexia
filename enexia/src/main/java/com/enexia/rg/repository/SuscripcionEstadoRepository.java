package com.enexia.rg.repository;

import com.enexia.rg.model.SuscripcionEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuscripcionEstadoRepository extends JpaRepository<SuscripcionEstado, Long> {

}
