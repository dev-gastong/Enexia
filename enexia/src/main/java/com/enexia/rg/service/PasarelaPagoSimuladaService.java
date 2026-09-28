package com.enexia.rg.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Simulacion del flujo de pago (RF-3.2, DFD 3.2.1/3.2.2).
 *
 * No hay pasarela real integrada: el proyecto no tiene alcance de cobro
 * efectivo, solo necesita ejercitar el flujo completo (aprobado / rechazado)
 * de forma reproducible. Misma logica que {@link CloudinaryService} en modo
 * simulado: aprueba por defecto, y expone un gancho de prueba para forzar el
 * rechazo sin depender de un tercero.
 */
@Service
@Slf4j
public class PasarelaPagoSimuladaService {

    /**
     * Veredicto de la pasarela.
     *
     * @param aprobado       si la operacion se confirmo
     * @param tokenOperacion identificador de la operacion simulada, se
     *                       persiste en {@code Pago.token_operacion} tanto si
     *                       aprueba como si rechaza, para poder correlacionar
     *                       el intento en los logs
     * @param motivoRechazo  null si aprobado
     */
    public record ResultadoPago(boolean aprobado, String tokenOperacion, String motivoRechazo) {

        public static ResultadoPago aprobado(String token) {
            return new ResultadoPago(true, token, null);
        }

        public static ResultadoPago rechazado(String token, String motivo) {
            return new ResultadoPago(false, token, motivo);
        }
    }

    /**
     * Procesa un pago simulado.
     *
     * @param monto      importe a cobrar (solo se usa para el log, no hay
     *                   verificacion de saldo real)
     * @param metodoPago metodo elegido; si contiene "rechazar" (sin importar
     *                   mayusculas) la operacion se rechaza, igual que el
     *                   marcador de {@code CloudinaryService.esRechazoSimulado}
     */
    public ResultadoPago procesar(BigDecimal monto, String metodoPago) {
        String token = UUID.randomUUID().toString();

        if (metodoPago != null && metodoPago.toLowerCase().contains("rechazar")) {
            log.info("Pasarela simulada RECHAZO una operacion de {} (token {})", monto, token);
            return ResultadoPago.rechazado(token, "La pasarela de pago rechazo la operacion");
        }

        log.info("Pasarela simulada APROBO una operacion de {} (token {})", monto, token);
        return ResultadoPago.aprobado(token);
    }
}
