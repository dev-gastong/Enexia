package com.enexia.rg.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Alta en un solo paso: cuenta personal + organizacion (DFD 7.1/7.2).
 *
 * POR QUE EXISTE ESTE DTO ADEMAS DEL ENDPOINT AUTENTICADO
 * La documentacion describe DOS caminos y ninguno invalida al otro:
 *
 *   - RF-7.2 dice que una Persona Fisica con rol ORGANIZADOR crea la
 *     organizacion "a traves de un flujo separado (no como parte del registro
 *     inicial)" -> POST /api/organizador/organizaciones, autenticado.
 *   - El DFD 7.1/7.2 muestra la bifurcacion Fisica/Juridica DENTRO del
 *     formulario de registro, y termina en "Continuar a Credenciales de
 *     Usuario" -> este DTO, publico.
 *
 * Se implementaron los dos sobre el MISMO metodo de servicio
 * (PersonaJuridicaService.crearOrganizacion), asi que no hay dos reglas de
 * negocio que puedan divergir: cambia el punto de entrada, no la logica.
 *
 * INVARIANTE QUE SE RESPETA EN AMBOS CAMINOS: la cuenta que se crea es SIEMPRE
 * de la persona humana. La organizacion nunca tiene credenciales propias.
 * El campo heredado {@code perfil} debe venir como ORGANIZADOR: el service lo
 * exige explicitamente en vez de corregirlo en silencio, porque recibir
 * PARTICIPANTE junto con datos de empresa significa que el cliente esta armando
 * mal la peticion, y ocultarselo solo retrasa el diagnostico.
 */
@Getter
@Setter
@NoArgsConstructor
public class RegistroConOrganizacionRequest extends UsuarioRegistroRequest {

    @NotNull(message = "Los datos de la organizacion son obligatorios")
    @Valid
    private OrganizacionRegistroRequest organizacion;
}
