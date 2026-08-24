package com.enexia.rg.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.HistorialInteracciones;

/**
 * Bitacora de auditoria. Ademas de registrar acciones, alimenta el control de
 * rate limiting por IP del login (DFD Login 1.2.1).
 */
@Repository
public interface HistorialInteraccionesRepository extends JpaRepository<HistorialInteracciones, Long> {

    /**
     * Cuenta cuantas veces una IP ejecuto una accion dentro de una ventana de tiempo.
     *
     * Es la consulta que sostiene el rate limiting: si una IP acumula demasiados
     * LOGIN_FALLIDO en pocos minutos, se la frena antes de tocar la tabla usuario.
     *
     * SEGURIDAD: parametros enlazados, no concatenacion.
     */
    @Query("""
            SELECT COUNT(h) FROM HistorialInteracciones h
            WHERE h.ipOrigen = :ip
              AND h.accion = :accion
              AND h.fechaInteraccion >= :desde
            """)
    long contarPorIpYAccionDesde(@Param("ip") String ip,
                                 @Param("accion") String accion,
                                 @Param("desde") LocalDateTime desde);
}
