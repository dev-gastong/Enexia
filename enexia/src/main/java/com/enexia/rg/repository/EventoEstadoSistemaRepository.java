package com.enexia.rg.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.EventoEstadoSistema;

/**
 * Estados de moderacion del evento (RF-2.2, DFD 2.5).
 *
 * POR QUE LA BUSQUEDA LLEVA ESTADO **Y** MOTIVO
 * El MER pone {@code motivo_codigo} en esta tabla de catalogo, no en 'evento'.
 * La consecuencia es que un mismo estado puede tener varias filas, una por
 * motivo: RECHAZADO_SISTEMA/MODERACION_TEXTO y RECHAZADO_SISTEMA/MODERACION_IMAGEN
 * son filas distintas. Buscar solo por nombre de estado devolveria cualquiera de
 * las dos y el motivo del rechazo se perderia.
 */
@Repository
public interface EventoEstadoSistemaRepository extends JpaRepository<EventoEstadoSistema, Long> {

    @Query("""
            SELECT e FROM EventoEstadoSistema e
            WHERE UPPER(e.estadoSistema) = UPPER(:estado)
              AND (:motivo IS NULL AND e.motivoCodigo IS NULL
                   OR UPPER(e.motivoCodigo) = UPPER(:motivo))
            """)
    Optional<EventoEstadoSistema> buscarPorEstadoYMotivo(@Param("estado") String estado,
                                                         @Param("motivo") String motivo);
}
