package com.enexia.rg.repository;

import java.util.Optional;

import com.enexia.rg.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

    /** El pago (si existe) asociado a una inscripcion, para revertirlo al cancelar (RF-3.3). */
    Optional<Pago> findByInscripcionIdInscripcion(Long idInscripcion);
}
