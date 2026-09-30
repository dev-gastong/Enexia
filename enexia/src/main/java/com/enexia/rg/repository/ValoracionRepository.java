package com.enexia.rg.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Valoracion;

@Repository
public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    /** Restriccion de aplicacion (RF-3.4): el indice UNIQUE real vive en la @Entity. */
    boolean existsByUsuarioIdUsuarioAndCronogramaIdCronograma(Long idUsuario, Long idCronograma);

    /**
     * Listado publico de un evento, mas reciente primero (ficha, RF-4.4).
     *
     * *-a-uno con @EntityGraph (usuario, cronograma, cronograma.evento) y no
     * @Query con JOIN FETCH: con JOIN FETCH, Spring Data sigue generando bien
     * el count query para la paginacion, pero @EntityGraph evita reescribir a
     * mano la misma consulta que ya da findByCronogramaEventoIdEventoOrderByFechaDesc.
     * {@code cronograma.evento} tiene que estar en la ruta: {@code mapear()}
     * lee {@code cronograma.getEvento().getNombre()}, y sin resolverlo aca
     * ese acceso lazy explota con LazyInitializationException apenas el
     * metodo del service termina (con open-in-view=false no queda sesion
     * abierta para resolverlo mas tarde).
     */
    @EntityGraph(attributePaths = {"usuario", "cronograma", "cronograma.evento"})
    Page<Valoracion> findByCronogramaEventoIdEventoOrderByFechaDesc(Long idEvento, Pageable pageable);

    /**
     * Promedio del evento. {@code COALESCE} a null explicito (no 0): un evento
     * sin valoraciones no tiene "cero estrellas", tiene "todavia sin calificar",
     * y el frontend necesita distinguir ambos casos para no mostrar "0.0 ★".
     */
    @Query("SELECT AVG(v.valor) FROM Valoracion v WHERE v.cronograma.evento.idEvento = :idEvento")
    Double promedioDeEvento(@Param("idEvento") Long idEvento);

    /** Cantidad total de valoraciones del evento, para el "N opiniones" del header de la ficha. */
    long countByCronogramaEventoIdEvento(Long idEvento);

    /**
     * Cronogramas de la lista dada que el usuario YA valoro.
     *
     * Version en lote de {@code existsByUsuarioIdUsuarioAndCronogramaIdCronograma},
     * mismo motivo que InscripcionRepository.idsDeCronogramasConInscripcionActiva:
     * la ficha necesita la respuesta para TODOS los cronogramas de la agenda a
     * la vez, para decidir en cual de ellos ofrecer el formulario de valorar.
     */
    @Query("""
            SELECT v.cronograma.idCronograma FROM Valoracion v
            WHERE v.usuario.idUsuario = :idUsuario
              AND v.cronograma.idCronograma IN :idsCronograma
            """)
    Set<Long> idsDeCronogramasYaValoradosPorUsuario(@Param("idUsuario") Long idUsuario,
                                                     @Param("idsCronograma") List<Long> idsCronograma);
}
