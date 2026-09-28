package com.enexia.rg.repository;

import com.enexia.rg.model.Valoracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {

    /** Restriccion unica compuesta (RF-3.4): maximo una valoracion por usuario y cronograma. */
    boolean existsByUsuarioIdUsuarioAndCronogramaIdCronograma(Long idUsuario, Long idCronograma);
}
