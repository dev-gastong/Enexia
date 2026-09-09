package com.enexia.rg.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.PasswordResetToken;

/**
 * Tokens de un solo uso para recuperar el acceso (RF-1.5).
 *
 * IMPORTANTE: la columna {@code token} NO guarda el token que viaja en el mail,
 * sino su hash SHA-256 (ver RecuperacionCuentaService). Por eso las busquedas de
 * aca reciben siempre el hash, nunca el valor original.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Recupera el token junto con su usuario y el estado de ese usuario.
     *
     * El JOIN FETCH es necesario porque {@code open-in-view=false}: sin el, leer
     * {@code token.getUsuario().getEstadoUsuario()} fuera de la transaccion
     * lanzaria LazyInitializationException.
     */
    @Query("""
            SELECT t FROM PasswordResetToken t
            JOIN FETCH t.usuario u
            LEFT JOIN FETCH u.estadoUsuario
            WHERE t.token = :hash
            """)
    Optional<PasswordResetToken> buscarPorHashConUsuario(@Param("hash") String hash);

    /**
     * Invalida los tokens previos de un usuario.
     *
     * Se llama antes de emitir uno nuevo: si convivieran varios vigentes, un
     * enlace viejo filtrado por email seguiria sirviendo despues de que el
     * usuario pidiera otro, y el "un solo uso" dejaria de ser cierto.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.usuario.idUsuario = :idUsuario")
    void borrarPorUsuario(@Param("idUsuario") Long idUsuario);

    /** Limpieza de tokens vencidos. Pensado para una tarea programada. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.fechaExpiracion < :momento")
    int borrarVencidos(@Param("momento") LocalDateTime momento);
}
