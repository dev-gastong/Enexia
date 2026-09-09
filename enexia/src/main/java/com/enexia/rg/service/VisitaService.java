package com.enexia.rg.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.model.Evento;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.Visita;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.UsuarioRepository;
import com.enexia.rg.repository.VisitaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro pasivo de trafico de la ficha publica (RF-4.5, DFD 4.5).
 *
 * POR QUE ES UNA CLASE APARTE Y NO UN METODO DE CatalogoPublicoService
 * Es la misma leccion que ya esta documentada en IntentosLoginService, y aca
 * costo un error real antes de aplicarla.
 *
 * {@code @Transactional} funciona por PROXY: solo se aplica cuando la llamada
 * entra al bean desde afuera. Este metodo vivia dentro de CatalogoPublicoService
 * y se invocaba como {@code this.registrarVisita(...)} desde verFicha(), que es
 * {@code @Transactional(readOnly = true)}. Al no pasar por el proxy, el
 * REQUIRES_NEW quedaba SIN EFECTO y el INSERT terminaba dentro de la transaccion
 * de solo lectura. Resultado observado:
 *
 *     UnexpectedRollbackException: Transaction silently rolled back
 *     because it has been marked as rollback-only
 *
 * Y lo peor: el try/catch que envolvia el guardado NO alcanzaba a atraparlo,
 * porque el fallo no ocurre en el {@code save()} sino al confirmar, ya fuera del
 * metodo. La ficha entera respondia 500 por culpa de una metrica.
 *
 * Separarlo en otro bean hace que el REQUIRES_NEW se aplique de verdad: la
 * visita se escribe en su propia transaccion, independiente de la lectura de la
 * ficha, y un fallo suyo no puede tumbar la pantalla mas visitada del sitio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VisitaService {

    private final VisitaRepository visitaRepository;
    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Asienta una visita a la ficha de un evento.
     *
     * SE INSERTA UNA FILA POR VISITA, sin deduplicar. La unicidad se calcula al
     * LEER (COUNT DISTINCT, RF-2.10), tal como decide el DFD 4.4/4.5 para el MVP.
     * Deduplicar al escribir obligaria a consultar antes de cada insercion, en la
     * pagina mas cargada del sitio, y ademas perderia el dato de cuantas veces
     * volvio la misma persona.
     *
     * @param emailUsuario email del visitante si hay sesion; null si es anonimo
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long idEvento, String emailUsuario) {
        try {
            // getReferenceById devuelve un proxy sin consultar la base: alcanza
            // para poblar la clave foranea y evita un SELECT innecesario en cada
            // carga de ficha.
            Evento referencia = eventoRepository.getReferenceById(idEvento);

            Usuario visitante = null;
            if (emailUsuario != null && !emailUsuario.isBlank()) {
                visitante = usuarioRepository.buscarActivoPorEmailConRoles(emailUsuario).orElse(null);
            }

            Visita visita = new Visita();
            visita.setEvento(referencia);
            // null = visita anonima (DFD 4.5.3). Es un valor valido, no un error.
            visita.setUsuario(visitante);
            visita.setFechaVisita(LocalDateTime.now());
            visitaRepository.save(visita);

        } catch (RuntimeException ex) {
            // Una metrica perdida es mucho menos grave que un 500 en la ficha.
            // Ahora si sirve atrapar aca: como esta transaccion es propia, el
            // fallo se resuelve dentro de este metodo y no contamina la lectura.
            log.warn("No se pudo registrar la visita al evento {}", idEvento, ex);
        }
    }
}
