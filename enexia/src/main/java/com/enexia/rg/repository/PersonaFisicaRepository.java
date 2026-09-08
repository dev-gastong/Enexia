package com.enexia.rg.repository;

import com.enexia.rg.model.PersonaFisica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonaFisicaRepository extends JpaRepository<PersonaFisica, Long> {

    /**
     * Unicidad del documento (DFD 7.1.2, "Error 409: DNI ya Registrado").
     *
     * El DNI identifica a una persona real, asi que no puede repetirse aunque
     * cambien el email y el nickname: sin este control una misma persona podria
     * abrir cuentas ilimitadas, y despues no habria forma de saber cual es la
     * legitima al momento de moderar o de dar de baja.
     *
     * Spring Data deriva el SELECT del nombre del metodo; no hace falta @Query.
     */
    boolean existsByDni(String dni);
}
