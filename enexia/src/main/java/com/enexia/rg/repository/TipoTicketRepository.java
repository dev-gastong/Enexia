package com.enexia.rg.repository;

import com.enexia.rg.model.TipoTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TipoTicketRepository extends JpaRepository<TipoTicket, Long> {

}
