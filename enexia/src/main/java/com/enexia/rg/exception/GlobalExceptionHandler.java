package com.enexia.rg.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.enexia.rg.dto.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Traduce excepciones a respuestas HTTP en un solo lugar.
 *
 * {@code @RestControllerAdvice} intercepta las excepciones que escapan de
 * CUALQUIER controller. Sin esto, cada controller necesitaria su propio
 * try/catch y las respuestas de error terminarian siendo inconsistentes.
 *
 * Regla que sigue toda esta clase: al cliente se le da lo justo para corregir
 * su accion; el detalle tecnico (stack trace, motivo real del fallo de login)
 * va al log del servidor y no viaja en la respuesta.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---------- Errores de autenticacion (DFD Login) ----------

    /** 401. Mensaje deliberadamente ambiguo para no permitir enumerar cuentas. */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> manejarCredencialesInvalidas(CredencialesInvalidasException ex) {
        return construir("CREDENCIALES_INVALIDAS", ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    /** 403. La cuenta existe y la clave puede ser correcta, pero esta bloqueada. */
    @ExceptionHandler(CuentaBloqueadaException.class)
    public ResponseEntity<ErrorResponse> manejarCuentaBloqueada(CuentaBloqueadaException ex) {
        return construir("CUENTA_BLOQUEADA", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    /** 403 + cabecera con el momento en que se libera la penalizacion. */
    @ExceptionHandler(CuentaEnCooldownException.class)
    public ResponseEntity<ErrorResponse> manejarCooldown(CuentaEnCooldownException ex) {
        ErrorResponse cuerpo = new ErrorResponse(
                "CUENTA_EN_COOLDOWN", ex.getMessage(), HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("X-Reintentar-Despues", String.valueOf(ex.getDisponibleDesde()))
                .body(cuerpo);
    }

    /** 429 Too Many Requests: el codigo estandar para rate limiting. */
    @ExceptionHandler(RateLimitExcedidoException.class)
    public ResponseEntity<ErrorResponse> manejarRateLimit(RateLimitExcedidoException ex) {
        return construir("RATE_LIMIT_EXCEDIDO", ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    // ---------- Errores de registro (DFD Registro) ----------

    /** 409 Conflict: el recurso choca con uno existente (email/nickname tomado). */
    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<ErrorResponse> manejarDuplicado(RecursoDuplicadoException ex) {
        return construir("RECURSO_DUPLICADO", ex.getMessage(), HttpStatus.CONFLICT);
    }

    /**
     * 422 Unprocessable Content: la peticion esta bien formada y es
     * sintacticamente valida, pero su contenido es inaceptable. Es exactamente
     * el caso de un texto rechazado por moderacion, y por eso no se usa 400.
     *
     * La constante se llamaba UNPROCESSABLE_ENTITY; la RFC 9110 renombro el
     * codigo a "Unprocessable Content" y Spring dejo el nombre viejo deprecado.
     */
    @ExceptionHandler(ContenidoInapropiadoException.class)
    public ResponseEntity<ErrorResponse> manejarContenidoInapropiado(ContenidoInapropiadoException ex) {
        return construir("CONTENIDO_INAPROPIADO", ex.getMessage(), HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /** 400: regla de negocio incumplida. */
    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> manejarReglaNegocio(ReglaNegocioException ex) {
        return construir("REGLA_NEGOCIO", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // ---------- Validacion de DTOs ----------

    /**
     * 400 con el detalle campo por campo.
     *
     * Spring lanza MethodArgumentNotValidException cuando un parametro anotado
     * con {@code @Valid} no cumple sus restricciones. Se devuelve el mapa
     * completo para que el formulario pueda marcar cada input que fallo, en vez
     * de mostrar un unico error global.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // putIfAbsent: si un campo viola varias reglas, se reporta la primera
            // y no se pisa con las siguientes.
            errores.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse cuerpo = new ErrorResponse(
                "VALIDACION_FALLIDA",
                "Hay campos con errores. Revise el detalle.",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                errores);

        return ResponseEntity.badRequest().body(cuerpo);
    }

    // ---------- Red de contencion ----------

    /**
     * 500 para cualquier excepcion no prevista.
     *
     * El stack trace se escribe en el log del servidor y NUNCA se envia al
     * cliente: revelaria nombres de clases, versiones de librerias y estructura
     * interna, todo material util para preparar un ataque.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorInesperado(Exception ex) {
        log.error("Error no controlado en la API", ex);
        return construir(
                "ERROR_INTERNO",
                "Ocurrio un error inesperado. Intente nuevamente en unos minutos.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> construir(String codigo, String mensaje, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(codigo, mensaje, status.value()));
    }
}
