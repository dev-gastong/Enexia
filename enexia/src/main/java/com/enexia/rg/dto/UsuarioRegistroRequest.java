package com.enexia.rg.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos que llegan del formulario de registro de Persona Fisica (RF-1.1, Paso 2).
 *
 * Las anotaciones de este DTO son la PRIMERA barrera de validacion: se ejecutan
 * en el controller gracias a {@code @Valid}, antes de que el service vea nada.
 * Corresponden al paso 1.1.2 del DFD de registro ("Validar Datos Personales").
 *
 * Persona Juridica queda fuera de Sprint 1 (ver CLAUDE.md).
 */
@Getter
@Setter
@NoArgsConstructor
public class UsuarioRegistroRequest {

    // ---------- Credenciales de acceso ----------

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es valido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String email;

    @NotBlank(message = "El nickname es obligatorio")
    @Size(min = 3, max = 20, message = "El nickname debe tener entre 3 y 20 caracteres")
    @Pattern(
        regexp = "^[a-zA-Z0-9_]+$",
        message = "El nickname solo admite letras, numeros y guion bajo"
    )
    private String nickname;

    /**
     * Contrasena en claro. Solo existe en memoria durante el request: el service
     * la hashea con BCrypt antes de persistir y nunca se guarda ni se loguea.
     */
    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "La contrasena debe incluir al menos una minuscula, una mayuscula y un numero"
    )
    private String password;

    @NotBlank(message = "Debe repetir la contrasena")
    private String passwordConfirmacion;

    // ---------- Identidad civil (tabla persona_fisica) ----------

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    // \p{L} = cualquier letra Unicode, asi entran acentos y enie sin listarlos.
    @Pattern(
        regexp = "^[\\p{L} '-]+$",
        message = "El nombre solo admite letras, espacios, apostrofes y guiones"
    )
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
    @Pattern(
        regexp = "^[\\p{L} '-]+$",
        message = "El apellido solo admite letras, espacios, apostrofes y guiones"
    )
    private String apellido;

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "^\\d{7,8}$", message = "El DNI debe tener 7 u 8 digitos")
    private String dni;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    private LocalDate fechaNacimiento;

    // ---------- Perfil elegido (RF-1.1, Paso 1) ----------

    /**
     * PARTICIPANTE u ORGANIZADOR. Se valida contra el enum RolNombre en el
     * service; aca solo se exige que venga y que sea uno de los dos permitidos
     * (ADMINISTRADOR jamas puede auto-asignarse desde el registro publico).
     */
    @NotBlank(message = "Debe seleccionar un perfil")
    @Pattern(
        regexp = "^(PARTICIPANTE|ORGANIZADOR)$",
        message = "El perfil debe ser PARTICIPANTE u ORGANIZADOR"
    )
    private String perfil;
}
