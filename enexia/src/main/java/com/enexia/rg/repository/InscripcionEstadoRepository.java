package com.enexia.rg.repository;

import com.enexia.rg.model.InscripcionEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InscripcionEstadoRepository extends JpaRepository<InscripcionEstado, Long> {

}
