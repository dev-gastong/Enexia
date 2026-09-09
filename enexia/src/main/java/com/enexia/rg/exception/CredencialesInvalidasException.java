package com.enexia.rg.exception;

/**
 * El email no existe o la contrasena no coincide (DFD Login, Err_Gen1/Err_Gen2).
 *
 * Es el caso "base" de {@link AutenticacionFallidaException}: el resto de los
 * rechazos existen para poder auditarlos por separado, pero se responden
 * exactamente igual que este.
 */
public class CredencialesInvalidasException extends AutenticacionFallidaException {

    public static final String CODIGO_EMAIL_INEXISTENTE = "EMAIL_INEXISTENTE";
    public static final String CODIGO_PASSWORD_INCORRECTA = "PASSWORD_INCORRECTA";

    public CredencialesInvalidasException() {
        this(CODIGO_PASSWORD_INCORRECTA);
    }

    public CredencialesInvalidasException(String codigoInterno) {
        super(codigoInterno);
    }
}
