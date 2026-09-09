package com.enexia.rg.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
     */
    @NotBlank(message = "El tipo de ticket es obligatorio")
    @Size(min = 3, max = 60, message = "El tipo de ticket debe tener entre 3 y 60 caracteres")
    private String tipoTicket;

    /**
     * Precio. Cero significa entrada gratuita (RF-2.6).
     *
     * {@code @Digits} acota a 10 enteros y 2 decimales: sin ese limite, un
     * numero con mas decimales de los que admite la columna DECIMAL de MySQL se
     * redondearia en silencio al guardar, y el precio cobrado no seria el
     * mostrado.
     */
    @NotNull(message = "El precio es obligatorio (0 para entrada gratuita)")
    @DecimalMin(value = "0.0", message = "El precio no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 2 decimales")
    private BigDecimal precio;

    /**
     * Cupo maximo (RF-2.6).
     *
     * Minimo 1: un cupo de cero seria un ticket que nadie puede sacar, un estado
     * sin utilidad que ademas confunde en la ficha publica.
     */
    @NotNull(message = "El cupo maximo es obligatorio")
    @Min(value = 1, message = "El cupo maximo debe ser al menos 1")
    private Integer cupoMaximo;
}
