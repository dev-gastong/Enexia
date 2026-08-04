package com.enexia.rg.repository;

import com.enexia.rg.model.UsuarioRol;
import com.enexia.rg.model.UsuarioRolId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRolId> {

}
