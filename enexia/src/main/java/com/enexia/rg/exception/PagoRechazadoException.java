package com.enexia.rg.exception;

/**
 * La pasarela de pago (simulada, RF-3.2) rechazo la operacion.
 *
 * Se traduce a 402 Payment Required: el dato de la peticion es correcto (el
 * ticket existe, hay cupo) pero la operacion de pago en si no se pudo
 * completar. La inscripcion no se pierde: queda en PENDIENTE_PAGO para que el
 * participante pueda reintentar.
 */
public class PagoRechazadoException extends RuntimeException {

    public PagoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
