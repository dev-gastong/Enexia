package com.enexia.rg.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Visita;

/** Trafico de la ficha publica (RF-4.5) y su agregacion para el organizador (RF-2.10). */
@Repository
public interface VisitaRepository extends JpaRepository<Visita, Long> {

    /**
     * Visitas UNICAS por usuario identificado.
     *
     * La unicidad se calcula al LEER, no al escribir: el DFD 4.4/4.5 lo decide
     * asi para el MVP. Insertar una fila por visita es una sola escritura sin
     * bloqueos ni consultas previas (importa: pasa en cada carga de ficha), y el
     * COUNT DISTINCT del panel es barato porque se ejecuta pocas veces.
     *
     * Las visitas anonimas (id_usuario NULL) quedan fuera de este conteo por
     * definicion: no hay forma de saber si son una persona o cien.
     */
    @Query("""
            SELECT COUNT(DISTINCT v.usuario.idUsuario) FROM Visita v
            WHERE v.evento.idEvento = :idEvento
              AND v.usuario IS NOT NULL
            """)
    long contarUnicasPorEvento(@Param("idEvento") Long idEvento);

    @Query("SELECT COUNT(v) FROM Visita v WHERE v.evento.idEvento = :idEvento")
    long contarTotalesPorEvento(@Param("idEvento") Long idEvento);
}
