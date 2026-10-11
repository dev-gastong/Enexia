package com.enexia.rg.repository;

import com.enexia.rg.model.HistorialEstadoEvento;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistorialEstadoEventoRepository extends JpaRepository<HistorialEstadoEvento, Long> {

    /**
     * Ultimo asiento del historial del evento. El desempate por id importa:
     * dos cambios dentro del mismo segundo comparten fecha_cambio.
     */
    Optional<HistorialEstadoEvento> findFirstByEventoIdEventoOrderByFechaCambioDescIdHistorialDesc(Long idEvento);
}
