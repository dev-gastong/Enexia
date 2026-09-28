package com.enexia.rg.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Alta de una inscripcion a un ticket de un cronograma (RF-3.1, RF-3.2).
 *
 * NO LLEVA {@code idUsuario}: el participante siempre es quien esta
 * autenticado (viene del JWT via {@code Principal}, igual que en el resto de
 * los endpoints protegidos). Aceptarlo como campo del body permitiria que
 * cualquiera inscribiera a otra persona con solo conocer su id.
 */
@Getter
@Setter
@NoArgsConstructor
public class InscripcionRequest {

    @NotNull(message = "Hay que indicar el ticket a reservar")
    private Long idCronogramaTicket;

    /**
     * Metodo de pago simulado ("tarjeta", "mercadopago", etc.), solo relevante
     * para tickets de pago (RF-3.2). Ignorado en tickets gratuitos.
     *
     * GANCHO DE PRUEBA: en modo simulado, un valor que contenga "rechazar"
     * hace que la pasarela rechace la operacion, igual que el marcador que
     * {@code CloudinaryService} usa para probar el camino de rechazo sin
     * credenciales reales. Ver {@link com.enexia.rg.service.PasarelaPagoSimuladaService}.
     */
    private String metodoPago;
}
