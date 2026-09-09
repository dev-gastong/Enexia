package com.enexia.rg.exception;

import lombok.Getter;

/**
 * Raiz de TODOS los fallos de autenticacion (RF-1.2, RF-1.4, RF-1.6).
 *
 * POR QUE UNA JERARQUIA CON UN SOLO MENSAJE PUBLICO
 * Decision de seguridad del 2026-09-08. El login tiene varias razones posibles
 * de rechazo -- email inexistente, contrasena incorrecta, cuenta BLOQUEADA,
 * cuenta en cooldown, cuenta SUSPENDIDA o DE_BAJA -- pero hacia afuera todas
 * son indistinguibles: mismo status HTTP, mismo codigo, mismo texto, mismas
 * cabeceras y (ver AuthService) el mismo costo en tiempo.
 *
 * El motivo real viaja en {@link #getCodigoInterno()}, que NUNCA se serializa
 * en la respuesta: solo se usa para el log del servidor y la auditoria.
 *
 * QUE ATAQUE EVITA
 * Si el 401 de "contrasena incorrecta" y el 403 de "cuenta bloqueada" fueran
 * distinguibles, un atacante obtendria dos regalos:
 *   1. Enumeracion: sabria que ese email existe en la plataforma.
 *   2. Confirmacion de exito parcial: sabria que su ataque de fuerza bruta
 *      efectivamente disparo el bloqueo, y por lo tanto que la cuenta es real
 *      y vale la pena insistir por otro canal (phishing, credential stuffing).
 * Con la respuesta uniforme, el atacante no puede distinguir "no existe" de
 * "existe y la acabo de bloquear". El unico que se entera del bloqueo es el
 * dueno legitimo, por email (ver RecuperacionCuentaService).
 */
@Getter
public abstract class AutenticacionFallidaException extends RuntimeException {

    /** Texto unico que ve el cliente, sea cual sea el motivo real. */
    public static final String MENSAJE_PUBLICO = "Email o contrasena incorrectos";

    /** Codigo unico que ve el cliente, sea cual sea el motivo real. */
    public static final String CODIGO_PUBLICO = "CREDENCIALES_INVALIDAS";

    /**
     * Motivo real del rechazo. Solo para logs y auditoria del servidor:
     * jamas debe incluirse en el cuerpo, las cabeceras ni el status de la
     * respuesta HTTP.
     */
    private final String codigoInterno;

    protected AutenticacionFallidaException(String codigoInterno) {
        // El mensaje de la excepcion es ya el publico: asi, si algun handler
        // futuro lo devolviera por descuido, seguiria sin filtrar informacion.
        super(MENSAJE_PUBLICO);
        this.codigoInterno = codigoInterno;
    }
}
