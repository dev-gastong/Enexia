package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas de la pasarela de pago simulada (RF-3.2). */
class PasarelaPagoSimuladaServiceTest {

    private final PasarelaPagoSimuladaService servicio = new PasarelaPagoSimuladaService();

    @Test
    @DisplayName("Aprueba por defecto y genera un token de operacion")
    void apruebaPorDefecto() {
        PasarelaPagoSimuladaService.ResultadoPago resultado =
                servicio.procesar(new BigDecimal("1500.00"), "tarjeta");

        assertThat(resultado.aprobado()).isTrue();
        assertThat(resultado.tokenOperacion()).isNotBlank();
        assertThat(resultado.motivoRechazo()).isNull();
    }

    @Test
    @DisplayName("Gancho de prueba: un metodo de pago que contiene 'rechazar' fuerza el rechazo")
    void rechazaConElMarcadorDePrueba() {
        PasarelaPagoSimuladaService.ResultadoPago resultado =
                servicio.procesar(new BigDecimal("1500.00"), "tarjeta-a-RECHAZAR");

        assertThat(resultado.aprobado()).isFalse();
        assertThat(resultado.tokenOperacion()).isNotBlank();
        assertThat(resultado.motivoRechazo()).isNotBlank();
    }

    @Test
    @DisplayName("Metodo de pago null: no revienta, se trata como aprobado")
    void metodoPagoNuloSeAprueba() {
        PasarelaPagoSimuladaService.ResultadoPago resultado =
                servicio.procesar(new BigDecimal("100.00"), null);

        assertThat(resultado.aprobado()).isTrue();
    }
}
