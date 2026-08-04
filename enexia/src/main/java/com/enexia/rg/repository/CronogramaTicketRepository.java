package com.enexia.rg.repository;

import com.enexia.rg.model.CronogramaTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CronogramaTicketRepository extends JpaRepository<CronogramaTicket, Long> {

}
