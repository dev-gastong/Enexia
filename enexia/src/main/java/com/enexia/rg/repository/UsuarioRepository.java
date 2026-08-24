package com.enexia.rg.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Usuario;

import jakarta.persistence.LockModeType;

/**
 * Acceso a datos de {@link Usuario}.
 *
 * SEGURIDAD: todas las consultas usan parametros con nombre ({@code :email}) o
 * derivacion de nombre de metodo. En ambos casos Spring Data genera un
 * PreparedStatement y el valor viaja como parametro enlazado, nunca concatenado
 * al SQL. Por eso no hay superficie para inyeccion SQL.
 * Nunca construir consultas concatenando strings.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Carga el usuario para el flujo de login (DFD Login 1.2.4).
     *
     * Trae en UNA sola consulta el usuario, sus roles y su estado. Sin los
     * JOIN FETCH esto provocaria N+1: la coleccion usuarioRoles es EAGER pero
     * cada {@code UsuarioRol.rol} es LAZY, asi que leer los nombres de rol
     * dispararia una consulta por rol. Ademas, con
     * {@code spring.jpa.open-in-view=false} cualquier acceso perezoso fuera de
     * la transaccion lanzaria LazyInitializationException.
     *
     * Filtra {@code fecha_baja IS NULL} para respetar el borrado logico (RF-1.6):
     * una cuenta dada de baja se comporta como inexistente.
     */
    @Query("""
            SELECT u FROM Usuario u
            LEFT JOIN FETCH u.usuarioRoles ur
            LEFT JOIN FETCH ur.rol
            LEFT JOIN FETCH u.estadoUsuario
            WHERE LOWER(u.email) = LOWER(:email)
              AND u.fechaBaja IS NULL
            """)
    Optional<Usuario> buscarActivoPorEmailConRoles(@Param("email") String email);

    /**
     * Relee el usuario tomando un bloqueo de escritura sobre la fila.
     *
     * Se usa al contabilizar intentos fallidos: {@code intentos_fallidos} es un
     * contador leer-modificar-escribir. Si dos intentos fallidos del mismo email
     * corren en paralelo sin bloqueo, ambos leen el mismo valor y uno de los dos
     * incrementos se pierde, permitiendo superar el umbral sin ser penalizado.
     * PESSIMISTIC_WRITE serializa esos accesos a nivel de base de datos
     * (SELECT ... FOR UPDATE). Requiere transaccion activa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.idUsuario = :id")
    Optional<Usuario> bloquearParaActualizarSeguridad(@Param("id") Long id);

    /** Unicidad de email en el registro (DFD Registro 1.1.1). Ignora mayusculas. */
    boolean existsByEmailIgnoreCase(String email);

    /** Unicidad de nickname en el registro (DFD Registro 1.1.1). Ignora mayusculas. */
    boolean existsByNicknameIgnoreCase(String nickname);
}
