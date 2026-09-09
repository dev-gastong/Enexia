package com.enexia.rg.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * RESPUESTA UNICA para todos los fallos de login (politica 2026-09-08).
     *
     * Cubre email inexistente, contrasena incorrecta, cuenta BLOQUEADA, cuenta
     * en cooldown, SUSPENDIDA y DE_BAJA. Las cinco salen por aca con el MISMO
     * status (401), el MISMO codigo, el MISMO texto y SIN cabeceras extra.
     *
     * Este metodo es el unico punto del sistema donde esa uniformidad se puede
     * romper por descuido, y por eso es deliberadamente corto: cualquier
     * ramificacion segun el tipo concreto de excepcion volveria a abrir el
     * canal de enumeracion que se cerro. El motivo real se escribe en el log
     * del servidor (nivel WARN, ver abajo) y en historial_interacciones.
     *
     * NOTA PARA QUIEN VENGA DESPUES: no agregar aca un @ExceptionHandler mas
     * especifico para CuentaBloqueadaException ni para CuentaEnCooldownException.
     * Spring elegiria el mas especifico y la respuesta volveria a delatar el
     * estado de la cuenta.
     */
    @ExceptionHandler(AutenticacionFallidaException.class)
    public ResponseEntity<ErrorResponse> manejarFalloDeAutenticacion(AutenticacionFallidaException ex) {
        // El codigo interno queda del lado del servidor. Es lo que permite
        // investigar un incidente sin publicar nada.
        log.warn("Fallo de autenticacion. Motivo interno: {}", ex.getCodigoInterno());

        return construir(
                AutenticacionFallidaException.CODIGO_PUBLICO,
                AutenticacionFallidaException.MENSAJE_PUBLICO,
                HttpStatus.UNAUTHORIZED);
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

    /**
     * 409 ante una violacion de constraint UNIQUE de la base.
     *
     * Es la red de seguridad de la condicion de carrera del registro: dos altas
     * concurrentes pasan ambas el chequeo existsBy... del service (ninguna ve la
     * fila aun sin confirmar de la otra) y chocan recien en el INSERT contra el
     * indice UNIQUE. La base rechaza a la segunda con esta excepcion; sin este
     * handler, escaparia como 500 ERROR_INTERNO. Aca se traduce al mismo 409
     * RECURSO_DUPLICADO que habria dado el chequeo previo.
     *
     * El mensaje se afina segun el nombre de la constraint violada (van con
     * nombre explicito en las @Entity justamente para poder distinguirlas). Si
     * no se reconoce, cae en un texto generico: nunca se expone el SQL crudo.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarViolacionIntegridad(DataIntegrityViolationException ex) {
        Throwable causa = ex.getMostSpecificCause();
        String detalle = causa != null ? causa.getMessage() : null;
        log.warn("Violacion de integridad traducida a 409: {}", detalle);
        return construir("RECURSO_DUPLICADO", mensajeSegunConstraint(detalle), HttpStatus.CONFLICT);
    }

    private String mensajeSegunConstraint(String detalle) {
        if (detalle != null) {
            String d = detalle.toLowerCase();
            if (d.contains("uk_usuario_email"))      return "Ya existe una cuenta registrada con ese email";
            if (d.contains("uk_usuario_nickname"))   return "Ese nickname ya esta en uso";
            if (d.contains("uk_persona_fisica_dni")) return "Ya existe una cuenta registrada con ese DNI";
            if (d.contains("uk_persona_juridica_cuit")) return "Ya existe una organizacion registrada con ese CUIT";
        }
        return "Ya existe una cuenta con esos datos";
    }

    // ---------- Errores de dominio (Modulos 2, 4 y 7) ----------

    /** 404. Tambien cubre el recurso ajeno: ver el javadoc de la excepcion. */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return construir("RECURSO_NO_ENCONTRADO", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    /** 409. El dato es valido, pero el estado actual del recurso no admite la accion. */
    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ResponseEntity<ErrorResponse> manejarOperacionNoPermitida(OperacionNoPermitidaException ex) {
        return construir("OPERACION_NO_PERMITIDA", ex.getMessage(), HttpStatus.CONFLICT);
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
