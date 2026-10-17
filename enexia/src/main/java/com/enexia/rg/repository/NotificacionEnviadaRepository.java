package com.enexia.rg.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.NotificacionEnviada;

/** Registro de correos enviados: idempotencia y limite por destinatario. */
@Repository
public interface NotificacionEnviadaRepository extends JpaRepository<NotificacionEnviada, Long> {

    boolean existsByTipoAndIdUsuarioAndIdReferencia(String tipo, Long idUsuario, Long idReferencia);

    long countByTipoAndIdUsuarioAndFechaAfter(String tipo, Long idUsuario, LocalDateTime desde);

    Optional<NotificacionEnviada> findFirstByTipoAndIdUsuarioOrderByFechaDesc(String tipo, Long idUsuario);
}
