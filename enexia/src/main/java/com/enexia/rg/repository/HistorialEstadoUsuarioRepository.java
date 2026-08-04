package com.enexia.rg.repository;

import com.enexia.rg.model.HistorialEstadoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistorialEstadoUsuarioRepository extends JpaRepository<HistorialEstadoUsuario, Long> {

}
