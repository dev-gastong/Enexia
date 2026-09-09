package com.enexia.rg.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos fiscales y corporativos de una organizacion (RF-7.2, DFD 7.2.1).
 *
 * QUE ES OBLIGATORIO Y POR QUE
 * RF-7.2 fija el conjunto minimo: Razon Social, CUIT, Telefono de Contacto y
 * Correo Corporativo. El Nombre de Fantasia queda opcional porque muchas
 * organizaciones no tienen uno distinto de su razon social; cuando existe, es el
 * que se muestra en la firma publica del evento (RF-7.4).
 *
 * El CUIT se valida en DOS niveles: el @Pattern de aca solo comprueba la FORMA
 * (11 digitos, con o sin guiones); el digito verificador lo calcula el service
 * con ValidadorCuit, porque una expresion regular no puede hacer aritmetica
 * modulo 11.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrganizacionRegistroRequest {

    @NotBlank(message = "La razon social es obligatoria")
    @Size(min = 3, max = 150, message = "La razon social debe tener entre 3 y 150 caracteres")
    private String razonSocial;

    /** Opcional (RF-7.2). Si viene, prevalece sobre la razon social en la firma publica. */
    @Size(max = 150, message = "El nombre de fantasia no puede superar los 150 caracteres")
    private String nombreFantasia;

    @NotBlank(message = "El CUIT es obligatorio")
    @Pattern(
        regexp = "^\\d{2}-?\\d{8}-?\\d$",
        message = "El CUIT debe tener 11 digitos, con o sin guiones"
    )
    private String cuit;

    @NotBlank(message = "El correo corporativo es obligatorio")
    @Email(message = "El formato del correo corporativo no es valido")
    @Size(max = 150, message = "El correo corporativo no puede superar los 150 caracteres")
    private String emailCorporativo;

    @NotBlank(message = "El telefono de contacto es obligatorio")
    @Pattern(
        regexp = "^[0-9+()\\s-]{7,20}$",
        message = "El telefono admite digitos, espacios, parentesis, guiones y el signo +"
    )
    private String telefonoContacto;

    /**
     * Domicilio fiscal legal (DFD 7.2.2).
     *
     * {@code @Valid} en cascada: sin esta anotacion, las validaciones de
     * UbicacionRequest NO se ejecutan y una ciudad nula llegaria al service.
     */
    @NotNull(message = "El domicilio fiscal es obligatorio")
    @Valid
    private UbicacionRequest domicilioFiscal;
}
