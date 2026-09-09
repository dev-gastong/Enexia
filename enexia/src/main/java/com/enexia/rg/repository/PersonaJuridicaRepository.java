package com.enexia.rg.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.PersonaJuridica;

/**
 * Acceso a datos de organizaciones (RF-7.2).
 *
 * El CUIT se guarda SIEMPRE normalizado (solo digitos, ver ValidadorCuit), asi
 * que estas consultas asumen ese formato: pasarles "30-71659554-0" no encontraria
 * nada aunque la organizacion exista.
 */
@Repository
public interface PersonaJuridicaRepository extends JpaRepository<PersonaJuridica, Long> {

    /** Unicidad fiscal (DFD 7.2.3). Recibe el CUIT ya normalizado. */
    boolean existsByCuit(String cuit);

    Optional<PersonaJuridica> findByCuit(String cuit);

    /**
     * Organizaciones donde el usuario figura como miembro, con los estados ya
     * cargados.
     *
     * El JOIN FETCH de los dos estados evita el N+1 al armar la respuesta: sin
     * el, listar 10 organizaciones dispararia 21 consultas (1 + 2 por fila).
     */
    @Query("""
            SELECT pj FROM MiembrosOrganizacion mo
            JOIN mo.personaJuridica pj
            LEFT JOIN FETCH pj.estadoPersonaJuridica
            LEFT JOIN FETCH pj.estadoPersonaJuridicaSistema
            WHERE mo.usuario.idUsuario = :idUsuario
            ORDER BY pj.razonSocial
            """)
    List<PersonaJuridica> buscarPorMiembro(@Param("idUsuario") Long idUsuario);
}
