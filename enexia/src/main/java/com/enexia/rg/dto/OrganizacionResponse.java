package com.enexia.rg.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vista publica de una organizacion (RF-7.2).
 *
 * NO expone el domicilio fiscal completo ni el id de ubicacion: son datos
 * legales de la empresa que no hacen falta para ninguna pantalla del catalogo.
 * El principio es el mismo de siempre: un DTO devuelve lo que la vista necesita,
 * no todo lo que la entidad tiene.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizacionResponse {

    private Long idPersonaJuridica;
    private String razonSocial;
    private String nombreFantasia;
    /** Formateado para mostrar (30-71659554-0), no los 11 digitos crudos. */
    private String cuit;
    private String emailCorporativo;
    private String telefonoContacto;
    /** REVISION_PENDIENTE / APROBADO / RECHAZADO. */
    private String estadoSistema;
    /** ACTIVO / INACTIVO. */
    private String estado;
    private String rolEnEmpresa;
    private LocalDateTime fechaRegistro;
    private String mensaje;
}
