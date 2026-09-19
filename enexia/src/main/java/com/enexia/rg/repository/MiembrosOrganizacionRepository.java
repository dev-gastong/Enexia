package com.enexia.rg.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.MiembrosOrganizacion;
import com.enexia.rg.model.MiembrosOrganizacionId;

/**
 * Vinculo entre personas humanas y organizaciones (RF-7.2).
 *
 * Es la tabla que sostiene el principio del modelo: una organizacion no actua
 * por si misma, actua a traves de sus miembros, que son siempre Personas
 * Fisicas con cuenta de Usuario.
 */
@Repository
public interface MiembrosOrganizacionRepository
        extends JpaRepository<MiembrosOrganizacion, MiembrosOrganizacionId> {

    /**
     * Recupera la membresia de un usuario en una organizacion concreta.
     *
     * Es el control de autoria de RF-2.1: antes de publicar un evento "bajo" una
     * organizacion hay que comprobar que quien lo crea pertenece a ella. Sin
     * esta verificacion, cualquier organizador podria publicar a nombre de una
     * empresa ajena con solo mandar su id.
     */
    @Query("""
            SELECT mo FROM MiembrosOrganizacion mo
            JOIN FETCH mo.personaJuridica pj
            LEFT JOIN FETCH pj.estadoPersonaJuridica
            LEFT JOIN FETCH pj.estadoPersonaJuridicaSistema
            WHERE mo.usuario.idUsuario = :idUsuario
              AND mo.personaJuridica.idPersonaJuridica = :idPersonaJuridica
            """)
    Optional<MiembrosOrganizacion> buscarMembresia(@Param("idUsuario") Long idUsuario,
                                                   @Param("idPersonaJuridica") Long idPersonaJuridica);

    /**
     * Todos los miembros de una organizacion, con la persona humana detras de
     * cada uno ya cargada (nombre/apellido/email para la pantalla "Mi Equipo").
     *
     * ADMINISTRADOR primero y despues por apellido: no hay un timestamp de
     * incorporacion en el MER (miembros_organizacion no lo declara), asi que no
     * hay forma de ordenar por antiguedad.
     */
    @Query("""
            SELECT mo FROM MiembrosOrganizacion mo
            JOIN FETCH mo.usuario u
            LEFT JOIN FETCH u.personaFisica pf
            LEFT JOIN FETCH pf.persona
            WHERE mo.personaJuridica.idPersonaJuridica = :idPersonaJuridica
            ORDER BY CASE WHEN mo.rolEnEmpresa = 'ADMINISTRADOR' THEN 0 ELSE 1 END, pf.apellido
            """)
    List<MiembrosOrganizacion> listarPorOrganizacion(@Param("idPersonaJuridica") Long idPersonaJuridica);

    /**
     * Cuenta cuantos ADMINISTRADOR tiene la organizacion.
     *
     * Es el control que evita dejarla sin nadie que pueda operarla: antes de
     * borrar o degradar al ultimo administrador, el service consulta esto.
     */
    @Query("""
            SELECT COUNT(mo) FROM MiembrosOrganizacion mo
            WHERE mo.personaJuridica.idPersonaJuridica = :idPersonaJuridica
              AND mo.rolEnEmpresa = 'ADMINISTRADOR'
            """)
    long contarAdministradores(@Param("idPersonaJuridica") Long idPersonaJuridica);
}
