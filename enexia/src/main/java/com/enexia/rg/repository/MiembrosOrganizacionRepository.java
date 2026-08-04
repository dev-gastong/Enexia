package com.enexia.rg.repository;

import com.enexia.rg.model.MiembrosOrganizacion;
import com.enexia.rg.model.MiembrosOrganizacionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MiembrosOrganizacionRepository extends JpaRepository<MiembrosOrganizacion, MiembrosOrganizacionId> {

}
