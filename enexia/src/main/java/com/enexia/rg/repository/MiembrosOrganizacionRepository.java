package com.enexia.rg.repository;

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
}
