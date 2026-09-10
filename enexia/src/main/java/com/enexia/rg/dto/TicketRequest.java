package com.enexia.rg.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un tipo de entrada dentro de una fecha del cronograma (RF-2.5, RF-2.6).
 *
 * El precio es {@link BigDecimal} y no double: los binarios de punto flotante no
 * pueden representar 0.10 de forma exacta, y sumando importes el error se
 * acumula hasta dar diferencias visibles en la recaudacion. BigDecimal opera en
 * base 10, como el dinero.
 */
@Getter
@Setter
@NoArgsConstructor
public class TicketRequest {

    /**
     * Nombre del tipo: "Inscripcion General", "Pase VIP", "Preventa"...
     *
     * Viaja como texto y no como id porque el organizador puede inventar uno
     * nuevo. El service busca la fila en el catalogo Tipo_Ticket y la crea si no
     * existe, de modo que el catalogo crece con el uso sin exigir un ABM previo.
     *
     * Una UNICA anotacion a proposito. Bean Validation no garantiza el orden
     * en que se evaluan varias anotaciones sobre el mismo campo (sin usar
     * grupos, que el proyecto no adopta en ningun otro DTO); con @NotBlank y
     * dos @Pattern por separado, un valor vacio podia terminar mostrando "no
     * puede tener espacios" en lugar de "es obligatorio", segun el orden que
     * Hibernate Validator eligiera ese dia. El lookahead exige 3 a 20
     * caracteres en total; el resto exige que no haya espacio al inicio, al
     * final, ni dos seguidos (y de paso cubre el string vacio, que no
     * satisface ninguno de los dos).
     */
    @Pattern(regexp = "^(?=.{3,20}$)\\S+( \\S+)*$",
            message = "El tipo de ticket es obligatorio: entre 3 y 20 caracteres, "
                     + "sin espacios al inicio, al final ni dobles")
    private String tipoTicket;

    /**
     * Precio. Cero significa entrada gratuita (RF-2.6).
     *
     * {@code @Digits} acota a 10 enteros y 2 decimales: sin ese limite, un
     * numero con mas decimales de los que admite la columna DECIMAL de MySQL se
     * redondearia en silencio al guardar, y el precio cobrado no seria el
     * mostrado. El tope de 50 millones (2026-09-10, a pedido del formulario)
     * es una cota de sentido comun contra un error de tipeo (agregar un cero
     * de mas), no una restriccion de negocio real.
     */
    @NotNull(message = "El precio es obligatorio (0 para entrada gratuita)")
    @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
    @DecimalMax(value = "50000000", message = "El precio no puede superar los $50.000.000")
    @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 2 decimales")
    private BigDecimal precio;

    /**
     * Cupo maximo (RF-2.6).
     *
     * Minimo 1: un cupo de cero seria un ticket que nadie puede sacar, un estado
     * sin utilidad que ademas confunde en la ficha publica. El tope de 1 millon
     * es la misma cota de sentido comun que el precio.
     *
     * Al EDITAR un evento existente, EventoService ademas exige que este valor
     * no sea menor al cupo ya cargado en el ticket que se esta modificando
     * (ver EventoService.validarCupoYPrecio) -- esa comparacion necesita el
     * ticket existente y no se puede expresar como anotacion de este DTO.
     */
    @NotNull(message = "El cupo maximo es obligatorio")
    @Min(value = 1, message = "El cupo maximo debe ser al menos 1")
    @Max(value = 1_000_000, message = "El cupo maximo no puede superar 1.000.000")
    private Integer cupoMaximo;

    /**
     * Identidad del ticket que se esta editando (null = sector nuevo).
     *
     * Solo lo completa el frontend cuando reenvia el formulario de EDICION con
     * datos que ya vinieron de GET /api/organizador/eventos/{id}. En el alta
     * siempre viaja null. No lleva validacion propia: es una referencia interna,
     * no un dato que el usuario carga.
     */
    private Long idCronogramaTicket;
}
