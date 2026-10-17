package com.enexia.rg.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de cada correo que el sistema intento mandar (D7 del DFD).
 *
 * CUMPLE DOS FUNCIONES
 *   1. Idempotencia: la restriccion UNIQUE (tipo, id_usuario, id_referencia)
 *      impide mandar dos veces el mismo aviso a la misma persona por el mismo
 *      motivo. Es lo que permite que el scheduler del recordatorio corra cada
 *      minuto sin duplicar correos. Es la defensa REAL, atomica: el chequeo
 *      previo del service solo ahorra un INSERT, pero bajo concurrencia solo el
 *      indice de la base frena al segundo (mismo criterio que {@link Usuario}).
 *   2. Limite por destinatario: contar filas recientes de un tipo para un
 *      usuario permite frenar el "email bombing" de la recuperacion de
 *      contrasena sin mirar la IP.
 *
 * {@code id_referencia} es NULL en los avisos que no se pueden repetir "por un
 * motivo" (recuperacion, contrasena cambiada). MariaDB trata los NULL como
 * distintos en un UNIQUE, asi que esas filas no chocan entre si, que es lo
 * buscado: se limitan por conteo, no por unicidad.
 *
 * id_usuario es un Long plano y no una relacion: es un registro de auditoria
 * liviano, y cargar un Usuario por cada fila solo para contarla seria costo
 * sin beneficio.
 */
@Entity
@Table(name = "notificacion_enviada",
        uniqueConstraints = @UniqueConstraint(name = "uk_notificacion_tipo_usuario_ref",
                columnNames = {"tipo", "id_usuario", "id_referencia"}),
        indexes = @Index(name = "idx_notificacion_tipo_usuario_fecha",
                columnList = "tipo, id_usuario, fecha"))
@Getter
@Setter
@NoArgsConstructor
public class NotificacionEnviada {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIADO = "ENVIADO";
    public static final String FALLIDO = "FALLIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long idNotificacion;

    @Column(name = "tipo", nullable = false, length = 40)
    private String tipo;

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    @Column(name = "id_referencia")
    private Long idReferencia;

    @Column(name = "estado", nullable = false, length = 12)
    private String estado;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
