package com.enexia.rg.repository;

import com.enexia.rg.model.EventoCronograma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoCronogramaRepository extends JpaRepository<EventoCronograma, Long> {

}
