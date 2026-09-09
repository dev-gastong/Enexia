package com.enexia.rg.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta del alta en un solo paso (cuenta personal + organizacion).
 *
 * Se devuelven los dos bloques por separado, y no aplanados, porque son dos
 * recursos con ciclos de vida distintos: la cuenta nace ACTIVA y usable, la
 * organizacion nace en revision. Mezclar sus campos en un unico objeto plano
 * haria ambiguo a que se refiere "estado".
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistroConOrganizacionResponse {

    private UsuarioRegistroResponse cuenta;

    private OrganizacionResponse organizacion;
}
