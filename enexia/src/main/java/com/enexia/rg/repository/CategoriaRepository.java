package com.enexia.rg.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Categoria;

/** Catalogo de categorias de evento (RF-4.3, RF-6.3). */
@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    Optional<Categoria> findByNombreCategoriaIgnoreCase(String nombreCategoria);

    List<Categoria> findAllByOrderByNombreCategoriaAsc();
}
