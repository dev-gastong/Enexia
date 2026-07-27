package com.enexia.rg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pais")
@Getter
@Setter
@NoArgsConstructor
public class Pais {

    @Id
    @Column(name = "id_pais")
    private String idPais;

    @Column(name = "nombre")
    private String nombre;
}
