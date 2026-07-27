package com.enexia.rg.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evento_multimedia")
@Getter
@Setter
@NoArgsConstructor
public class EventoMultimedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_multimedia")
    private Long idMultimedia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_evento")
    private Evento evento;

    @Column(name = "tipo_archivo")
    private String tipoArchivo;

    @Column(name = "url_archivo")
    private String urlArchivo;

    @Column(name = "orden")
    private Integer orden;

    @Column(name = "fecha_subida")
    private LocalDateTime fechaSubida;
}
