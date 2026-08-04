package com.enexia.rg.repository;

import com.enexia.rg.model.UsuarioEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioEstadoRepository extends JpaRepository<UsuarioEstado, Long> {

}
