package com.enexia.rg.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Restriccion unica compuesta (id_usuario, id_cronograma), tal cual la pide
 * RF-3.4 de forma literal: "maximo una (1) valoracion por cronograma".
 *
 * Antes de esto la unicidad se resolvia SOLO en el service
 * ({@code ValoracionRepository.existsByUsuarioIdUsuarioAndCronogramaIdCronograma}),
 * que es un chequeo de aplicacion, no de base: dos POST concurrentes del mismo
 * usuario para el mismo cronograma pueden ejecutar ese "existsBy" antes de que
 * ninguno de los dos haya confirmado su INSERT, y las dos pasan. Es la misma
 * clase de condicion de carrera que ya se corrigio para las inscripciones
 * duplicadas (ver InscripcionRepository.existeActivaDeUsuarioEnCronograma).
 * El indice UNIQUE es la red de seguridad real; el "existsBy" sigue estando
 * para dar el 409 legible en el caso comun (sin carrera).
 */
@Entity
@Table(name = "valoracion", uniqueConstraints =
        @UniqueConstraint(name = "uk_valoracion_usuario_cronograma", columnNames = {"id_usuario", "id_cronograma"}))
@Getter
@Setter
@NoArgsConstructor
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_valoracion")
    private Long idvaloracion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cronograma")
    private EventoCronograma cronograma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "valor")
    private Integer valor;

    /**
     * length=1000 explicito porque tiene que coincidir con el
     * {@code @Size(max = 1000)} de ValoracionRequest.comentario (RF-3.4): sin
     * esto, Hibernate asume el default de 255 al crear la columna, y un
     * comentario de entre 256 y 1000 caracteres pasa la validacion del DTO
     * pero explota en el INSERT con "Data too long for column 'comentario'"
     * bajo sql_mode=STRICT_TRANS_TABLES. Ver la migracion
     * 2026-09-29_valoracion_columna.sql para la columna ya existente (esto
     * solo aplica a una base creada desde cero).
     */
    @Column(name = "comentario", length = 1000)
    private String comentario;

    @Column(name = "fecha")
    private LocalDate fecha;
}
