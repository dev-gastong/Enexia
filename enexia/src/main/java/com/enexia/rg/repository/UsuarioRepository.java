package com.enexia.rg.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Carga el usuario autenticado (JWT ya validado: filtro de seguridad,
     * servicios de Modulo 2 a 5 reconstruyendo el principal a partir del
     * subject del token). El subject sigue siendo el EMAIL -- es un
     * identificador interno estable, nunca lo escribe un usuario -- y nada de
     * esto cambia con el login por nickname (ver {@link #buscarActivoPorNicknameConRoles}).
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
     *
     * Tambien trae {@code personaFisica} (y su {@code persona}): es la misma
     * relacion 1:1 que "Mi Perfil" necesita (nombre, apellido, DNI, fecha de
     * nacimiento), y este es el punto unico donde ya se carga un Usuario
     * completo para el resto de los servicios autenticados. Agregarla aca evita
     * una consulta aparte por cada pantalla de perfil sin duplicar el metodo.
     */
    @Query("""
            SELECT u FROM Usuario u
            LEFT JOIN FETCH u.usuarioRoles ur
            LEFT JOIN FETCH ur.rol
            LEFT JOIN FETCH u.estadoUsuario
            LEFT JOIN FETCH u.personaFisica pf
            LEFT JOIN FETCH pf.persona
            WHERE LOWER(u.email) = LOWER(:email)
              AND u.fechaBaja IS NULL
            """)
    Optional<Usuario> buscarActivoPorEmailConRoles(@Param("email") String email);

    /**
     * Carga el usuario para el PASO DE LOGIN (DFD Login 1.2.2), que identifica
     * la cuenta por NICKNAME y no por email (decision 2026-09-30): el email
     * queda como dato de contacto (recuperacion de cuenta, avisos de
     * seguridad), separado de la credencial que se escribe para entrar.
     *
     * Mismos JOIN FETCH que {@link #buscarActivoPorEmailConRoles} y por el
     * mismo motivo: evitar el N+1 de roles y dejar el Usuario completo listo
     * para emitir el JWT sin una segunda consulta.
     */
    @Query("""
            SELECT u FROM Usuario u
            LEFT JOIN FETCH u.usuarioRoles ur
            LEFT JOIN FETCH ur.rol
            LEFT JOIN FETCH u.estadoUsuario
            LEFT JOIN FETCH u.personaFisica pf
            LEFT JOIN FETCH pf.persona
            WHERE LOWER(u.nickname) = LOWER(:nickname)
              AND u.fechaBaja IS NULL
            """)
    Optional<Usuario> buscarActivoPorNicknameConRoles(@Param("nickname") String nickname);

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

    /**
     * Directorio de usuarios del administrador (RF-6.2), paso 1: la PAGINA de
     * ids. Se pagina sobre ids y no sobre entidades porque cargar los roles
     * (coleccion) con JOIN FETCH junto con la paginacion obliga a Hibernate a
     * paginar en memoria (HHH000104). El paso 2 es {@link #cargarParaAdmin}.
     *
     * Incluye las cuentas de baja (fecha_baja no nula) a proposito: el
     * administrador las ve con estado DE_BAJA, sin acciones disponibles.
     */
    @Query(value = """
            SELECT u.idUsuario FROM Usuario u
            LEFT JOIN u.personaFisica pf
            LEFT JOIN u.estadoUsuario eu
            WHERE (:estado IS NULL OR eu.estadoUsuario = :estado)
              AND (:texto IS NULL
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(CONCAT(pf.nombre, ' ', pf.apellido)) LIKE LOWER(CONCAT('%', :texto, '%')))
            """,
            countQuery = """
            SELECT COUNT(u) FROM Usuario u
            LEFT JOIN u.personaFisica pf
            LEFT JOIN u.estadoUsuario eu
            WHERE (:estado IS NULL OR eu.estadoUsuario = :estado)
              AND (:texto IS NULL
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(CONCAT(pf.nombre, ' ', pf.apellido)) LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    Page<Long> buscarIdsParaAdmin(@Param("texto") String texto,
                                  @Param("estado") String estado,
                                  Pageable paginado);

    /** Paso 2 del directorio de usuarios: las entidades de la pagina, con roles y estado en una sola consulta. */
    @Query("""
            SELECT DISTINCT u FROM Usuario u
            LEFT JOIN FETCH u.usuarioRoles ur
            LEFT JOIN FETCH ur.rol
            LEFT JOIN FETCH u.estadoUsuario
            LEFT JOIN FETCH u.personaFisica pf
            LEFT JOIN FETCH pf.persona
            WHERE u.idUsuario IN :ids
            """)
    List<Usuario> cargarParaAdmin(@Param("ids") Collection<Long> ids);

    /**
     * Si la cuenta puede seguir usando un JWT ya emitido: estado ACTIVO y sin
     * baja logica. Lo consulta JwtAuthenticationFilter en cada peticion, asi que
     * es un COUNT acotado por el indice unico de email y no carga la entidad.
     */
    @Query("""
            SELECT COUNT(u) > 0 FROM Usuario u
            WHERE LOWER(u.email) = LOWER(:email)
              AND u.fechaBaja IS NULL
              AND u.estadoUsuario.estadoUsuario = 'ACTIVO'
            """)
    boolean cuentaHabilitada(@Param("email") String email);

    /**
     * Organizadores (rol ORGANIZADOR) para el control de suscripciones del
     * administrador (RF-6.4): pagina de ids, con busqueda por nombre, nickname o
     * email. Solo ids por la misma razon que {@link #buscarIdsParaAdmin}.
     */
    @Query(value = """
            SELECT u.idUsuario FROM Usuario u
            LEFT JOIN u.personaFisica pf
            WHERE EXISTS (SELECT 1 FROM UsuarioRol ur WHERE ur.usuario = u AND ur.rol.nombreRol = 'ORGANIZADOR')
              AND u.fechaBaja IS NULL
              AND (:texto IS NULL
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(CONCAT(pf.nombre, ' ', pf.apellido)) LIKE LOWER(CONCAT('%', :texto, '%')))
            """,
            countQuery = """
            SELECT COUNT(u) FROM Usuario u
            LEFT JOIN u.personaFisica pf
            WHERE EXISTS (SELECT 1 FROM UsuarioRol ur WHERE ur.usuario = u AND ur.rol.nombreRol = 'ORGANIZADOR')
              AND u.fechaBaja IS NULL
              AND (:texto IS NULL
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :texto, '%'))
                   OR LOWER(CONCAT(pf.nombre, ' ', pf.apellido)) LIKE LOWER(CONCAT('%', :texto, '%')))
            """)
    Page<Long> buscarIdsOrganizadoresParaAdmin(@Param("texto") String texto, Pageable paginado);

    /** Total de organizadores activos (base del plan Gratuito en el panel RF-6.4). */
    @Query("""
            SELECT COUNT(u) FROM Usuario u
            WHERE u.fechaBaja IS NULL
              AND EXISTS (SELECT 1 FROM UsuarioRol ur WHERE ur.usuario = u AND ur.rol.nombreRol = 'ORGANIZADOR')
            """)
    long contarOrganizadores();

    /** Total de cuentas en un estado del catalogo, para las tarjetas del panel (RF-6.2). */
    @Query("SELECT COUNT(u) FROM Usuario u WHERE u.estadoUsuario.estadoUsuario = :estado")
    long contarPorEstado(@Param("estado") String estado);

    /** Unicidad de email en el registro (DFD Registro 1.1.1). Ignora mayusculas. */
    boolean existsByEmailIgnoreCase(String email);

    /** Unicidad de nickname en el registro (DFD Registro 1.1.1). Ignora mayusculas. */
    boolean existsByNicknameIgnoreCase(String nickname);
}
