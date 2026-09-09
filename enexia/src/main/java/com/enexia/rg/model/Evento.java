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

import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento publicado en la plataforma (Modulo 2).
 *
 * DOS EJES DE ESTADO, NO UNO
 * {@code estadoSistema} lo mueve la moderacion automatica y el panel de admin
 * (EN_PROCESO -> APROBADO_SISTEMA / RECHAZADO_SISTEMA). {@code estadoOrganizador}
 * lo mueve el duenio del evento (PUBLICADO / CANCELADO / DADO_DE_BAJA). Son
 * independientes a proposito: un evento aprobado por el sistema puede estar
 * cancelado por su organizador, y el catalogo publico exige que AMBOS habiliten.
 *
 * DOS CAMPOS AGREGADOS EN SPRINT 2 (2026-09-08), ausentes del MER original:
 *
 *  - {@code fecha_creacion}: la exige RF-2.2 de forma literal como parte del
 *    registro "skeleton" que se persiste antes de moderar. Sin ella el dashboard
 *    del organizador no puede ordenar por antiguedad ni detectar eventos
 *    atascados en EN_PROCESO por una moderacion que nunca termino.
 *
 *  - {@code id_persona_juridica}: implementa RF-2.1 (Sprint 2) y RF-7.4. Es
 *    NULLABLE: null significa que el evento va a titulo personal del organizador
 *    (Persona Fisica), y con valor significa que se publica a nombre de esa
 *    organizacion. La autoria NO se guarda como texto copiado porque un cambio
 *    de razon social dejaria los eventos viejos con el nombre anterior.
 *
 * Ambos campos se agregaron tambien al MER (docs/diseno_bd/MER.md), siguiendo el
 * criterio del Sprint 1: primero se actualiza la documentacion, despues el codigo.
 *
 * POR QUE {@code @DynamicUpdate} (agregado el 2026-09-09 tras un bug real)
 * Por defecto Hibernate genera un UPDATE con TODAS las columnas, tenga o no
 * cambios cada una. En esta entidad eso provoca una PERDIDA DE ACTUALIZACION,
 * porque hay dos procesos que la escriben en paralelo y tocan campos distintos:
 *
 *   t0  el pipeline de moderacion carga el evento     (estado_organizador = PUBLICADO)
 *   t1  el organizador lo da de baja y confirma       (estado_organizador = DADO_DE_BAJA)
 *   t2  el pipeline confirma y reescribe TODO         (estado_organizador = PUBLICADO)  <-- se perdio la baja
 *
 * Consecuencia real, no teorica: un organizador que crea un evento y se
 * arrepiente antes de que termine la moderacion veia su evento PUBLICADO igual,
 * sin haber hecho nada. Lo detecto la prueba "409 al dar de baja dos veces"
 * despues de actualizar MariaDB: el servidor mas rapido angosto la ventana de
 * la carrera y la hizo reproducible.
 *
 * Con {@code @DynamicUpdate}, el UPDATE incluye SOLO las columnas que esa
 * transaccion modifico de verdad. El pipeline escribe nombre, categoria,
 * portada y estado_sistema; no toca estado_organizador, asi que la baja
 * sobrevive. Los dos procesos pueden seguir escribiendo en paralelo sin pisarse,
 * que es justo lo que corresponde: son decisiones independientes sobre ejes
 * distintos del evento.
 */
@Entity
@Table(name = "evento")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long idEvento;

    /** Persona humana que creo el evento. Siempre presente, incluso con organizacion. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_organizador")
    private Usuario organizador;

    /**
     * Organizacion bajo la que se publica (RF-2.1 Sprint 2). Null = a titulo personal.
     *
     * Que el organizador humano quede igual registrado en {@code organizador} no
     * es redundancia: la firma publica sale de aca (RF-7.4), pero la
     * responsabilidad y los permisos de edicion siguen siendo de la persona.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona_juridica")
    private PersonaJuridica personaJuridica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_sistema")
    private EventoEstadoSistema estadoSistema;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_organizador")
    private EventoEstadoOrganizador estadoOrganizador;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "url_portada")
    private String urlPortada;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;
}
