package com.enexia.rg.validation;


import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;

public class ValidadorFechaFutura implements ConstraintValidator<FechaFuturaValida, LocalDate> {

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        // Si el valor es nulo, dejamos que @NotNull se encargue si estuviera presente
        if (value == null) {
            return true;
        }

        // Lógica personalizada: Comprobar que sea posterior a la fecha actual
        return value.isAfter(LocalDate.now().minusDays(1));


    }
}
