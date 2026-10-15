package com.enexia.rg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria")
    private Long idCategoria;

    @Column(name = "nombre_categoria")
    private String nombreCategoria;

    /**
     * Baja LOGICA (RF-6.3). Una categoria inactiva deja de ofrecerse para
     * eventos nuevos y en los filtros del catalogo, pero NO se borra: los
     * eventos que ya la tienen conservan su clasificacion, sin registros
     * huerfanos. El DEFAULT en la columna es obligatorio: sin el, al agregar
     * la columna a una tabla con filas, MariaDB las dejaria todas en 0
     * (inactivas) y el catalogo se quedaria sin categorias.
     */
    @Column(name = "activa", nullable = false, columnDefinition = "BIT(1) NOT NULL DEFAULT 1")
    private boolean activa = true;
}
