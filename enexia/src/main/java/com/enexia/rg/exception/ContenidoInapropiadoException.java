package com.enexia.rg.exception;

/**
 * El filtro de moderacion detecto lenguaje ofensivo en un campo de texto
 * (DFD Registro 1.1.1A, salida Return_Sensible).
 *
 * No se indica QUE palabra se detecto: exponer la lista de terminos prohibidos
 * permitiria evadirla por prueba y error.
 */
public class ContenidoInapropiadoException extends RuntimeException {

    public ContenidoInapropiadoException(String campo) {
        super("El campo '" + campo + "' contiene terminos no permitidos. Revise los datos ingresados.");
    }
}
