package com.enexia.rg.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidadorFechaFutura.class) // Apunta al validador
@Documented
public @interface FechaFuturaValida {

    // Mensaje por defecto si la validación falla
    String message() default "La fecha debe ser futura y válida";

    // Requeridos por la especificación Jakarta Validation
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}