package com.enexia.rg.event;

import java.util.List;

import com.enexia.rg.dto.EventoCrearRequest;
import com.enexia.rg.service.ImagenPendiente;

/**
 * Aviso de que un evento quedo creado en estado EN_PROCESO y hay que moderarlo
 * (RF-2.2, DFD 2.5).
 *
 * POR QUE UN EVENTO DE SPRING Y NO UNA LLAMADA DIRECTA
 * Esto no es indireccion por gusto: resuelve una condicion de carrera concreta y
 * ya observada en la practica.
 *
 * EventoService.crear() es {@code @Transactional}. Si al final del metodo se
 * llamara directo al pipeline asincrono, la tarea arrancaria en otro hilo
 * mientras la transaccion de creacion TODAVIA NO SE CONFIRMO. Ese otro hilo abre
 * su propia transaccion, busca el evento por id... y no lo encuentra, porque la
 * fila sigue sin confirmar y es invisible fuera de la transaccion que la creo.
 * El sintoma exacto que se vio antes de este cambio:
 *
 *     RecursoNoEncontradoException: El evento 1 ya no existe
 *     -> el evento terminaba en RECHAZADO_SISTEMA / ERROR_PIPELINE
 *
 * Y no siempre: el resultado dependia de cual hilo ganara la carrera, que es la
 * peor clase de error posible -- funciona en las pruebas y falla en produccion,
 * o al reves.
 *
 * Publicando un evento de aplicacion, quien escucha lo hace con
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: Spring garantiza
 * que la tarea no arranca hasta que la transaccion se confirmo. Para cuando el
 * pipeline busca el evento, la fila esta visible para todos.
 *
 * @param idEvento         id del skeleton recien creado
 * @param datos            formulario completo, todavia sin persistir (RF-2.2)
 * @param imagenes         bytes ya leidos del multipart
 * @param emailOrganizador destinatario del aviso de resultado
 */
public record EventoCreadoEvent(Long idEvento,
                                EventoCrearRequest datos,
                                List<ImagenPendiente> imagenes,
                                String emailOrganizador) {
}
