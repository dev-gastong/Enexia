package com.enexia.rg.repository;

import java.util.Optional;

import com.enexia.rg.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

    /** El pago (si existe) asociado a una inscripcion, para revertirlo al cancelar (RF-3.3). */
    Optional<Pago> findByInscripcionIdInscripcion(Long idInscripcion);

    /**
     * Ingresos por suscripciones en un periodo (panel RF-6.4): solo pagos de
     * suscripcion (no de entradas) y solo los COMPLETADOS; un pago fallido o
     * reembolsado no es ingreso.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COALESCE(SUM(p.monto), 0) FROM Pago p
            WHERE p.suscripcion IS NOT NULL
              AND p.estadoPago.nombreEstado = 'COMPLETADO'
              AND p.fechaPago >= :desde AND p.fechaPago < :hasta
            """)
    java.math.BigDecimal sumarIngresosPorSuscripciones(
            @org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde,
            @org.springframework.data.repository.query.Param("hasta") java.time.LocalDateTime hasta);
}
