package com.enexia.rg.repository;

import com.enexia.rg.model.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    /**
     * Todas las suscripciones de un lote de usuarios, de la mas vieja a la mas
     * nueva (por id). El service se queda con la ULTIMA de cada uno: es la que
     * define su plan vigente. Una consulta para toda la pagina, no una por fila.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT s FROM Suscripcion s
            JOIN FETCH s.estadoSuscripcion
            WHERE s.usuario.idUsuario IN :ids
            ORDER BY s.idSuscripcion ASC
            """)
    java.util.List<Suscripcion> buscarPorUsuarios(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    /**
     * Relee la suscripcion tomando bloqueo de escritura (PESSIMISTIC_WRITE):
     * dos administradores operando sobre la misma, o el flujo de pago del
     * propio organizador, no pueden pisarse. Requiere transaccion activa.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Suscripcion s WHERE s.idSuscripcion = :id")
    java.util.Optional<Suscripcion> bloquearParaActualizar(
            @org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(s) FROM Suscripcion s WHERE s.estadoSuscripcion.nombreEstado = :estado")
    long contarPorEstado(@org.springframework.data.repository.query.Param("estado") String estado);

    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(s) FROM Suscripcion s
            WHERE s.estadoSuscripcion.nombreEstado = :estado AND s.tipoPlan = :plan
            """)
    long contarPorEstadoYPlan(@org.springframework.data.repository.query.Param("estado") String estado,
                              @org.springframework.data.repository.query.Param("plan") String plan);

    /** Suscripciones en un estado y plan cuyo titular tiene la cuenta ACTIVA (tarjetas de plan, RF-6.4). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(s) FROM Suscripcion s
            WHERE s.estadoSuscripcion.nombreEstado = :estado AND s.tipoPlan = :plan
              AND s.usuario.fechaBaja IS NULL
              AND s.usuario.estadoUsuario.estadoUsuario = 'ACTIVO'
            """)
    long contarPorEstadoYPlanConCuentaActiva(
            @org.springframework.data.repository.query.Param("estado") String estado,
            @org.springframework.data.repository.query.Param("plan") String plan);

}
