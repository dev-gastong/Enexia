package com.enexia.rg.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Rol;

@Repository
public interface RolRepository extends JpaRepository<Rol, Long> {

    /** Busca un rol del catalogo por su nombre (PARTICIPANTE, ORGANIZADOR, ADMINISTRADOR). */
    Optional<Rol> findByNombreRolIgnoreCase(String nombreRol);
}
