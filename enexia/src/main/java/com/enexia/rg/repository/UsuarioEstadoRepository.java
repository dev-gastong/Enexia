package com.enexia.rg.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.UsuarioEstado;

@Repository
public interface UsuarioEstadoRepository extends JpaRepository<UsuarioEstado, Long> {

    /** Busca un estado del catalogo por nombre (ACTIVO, BLOQUEADO, SUSPENDIDO, DE_BAJA). */
    Optional<UsuarioEstado> findByEstadoUsuarioIgnoreCase(String estadoUsuario);
}
