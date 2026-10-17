package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoEstadoOrganizador;
import com.enexia.rg.model.EventoEstadoSistema;
import com.enexia.rg.model.HistorialEstadoEvento;
import com.enexia.rg.repository.EventoEstadoOrganizadorRepository;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.HistorialEstadoEventoRepository;

/**
 * Pruebas de {@link FinalizacionEventoService}: un evento cuya ultima funcion
 * ya termino pasa a FINALIZADO, sin pisar una decision tomada en paralelo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FinalizacionEventoService - finalizacion automatica de eventos")
class FinalizacionEventoServiceTest {

    @Mock private EventoRepository eventoRepository;
    @Mock private EventoEstadoOrganizadorRepository estadoOrganizadorRepository;
    @Mock private HistorialEstadoEventoRepository historialRepository;

    @Mock private AvisoInscriptosService avisoInscriptosService;

    @InjectMocks private FinalizacionEventoService servicio;

    private static final Long ID_EVENTO = 5L;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 11, 12, 30);

    private Evento evento;
    private EventoEstadoOrganizador finalizado;

    @BeforeEach
    void prepararEscenario() {
        EventoEstadoOrganizador publicado = new EventoEstadoOrganizador();
        publicado.setEstadoOrganizador("PUBLICADO");

        EventoEstadoSistema aprobado = new EventoEstadoSistema();
        aprobado.setEstadoSistema("APROBADO_SISTEMA");

        evento = new Evento();
        evento.setIdEvento(ID_EVENTO);
        evento.setEstadoOrganizador(publicado);
        evento.setEstadoSistema(aprobado);

        finalizado = new EventoEstadoOrganizador();
        finalizado.setEstadoOrganizador("FINALIZADO");

        when(eventoRepository.bloquearParaActualizar(ID_EVENTO)).thenReturn(Optional.of(evento));
        when(estadoOrganizadorRepository.findByEstadoOrganizadorIgnoreCase("FINALIZADO"))
                .thenReturn(Optional.of(finalizado));
    }

    private void siguePareciendoVencido(boolean vencido) {
        when(eventoRepository.buscarIdsFinalizables(
                eq("PUBLICADO"), any(), eq(ID_EVENTO), eq(AHORA.toLocalDate()), eq(AHORA.toLocalTime())))
                .thenReturn(vencido ? List.of(ID_EVENTO) : List.of());
    }

    @Test
    @DisplayName("Lista los ids vencidos con la fecha y la hora de la pasada")
    void listaIdsVencidos() {
        when(eventoRepository.buscarIdsFinalizables(
                eq("PUBLICADO"), eq(EventoRepository.ESTADOS_VISIBLES), eq(null),
                eq(AHORA.toLocalDate()), eq(AHORA.toLocalTime())))
                .thenReturn(List.of(1L, 2L));

        assertThat(servicio.idsVencidos(AHORA)).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("Un evento vencido pasa a FINALIZADO y queda en el historial sin usuario")
    void finalizaYRegistraHistorial() {
        siguePareciendoVencido(true);

        boolean resultado = servicio.finalizar(ID_EVENTO, AHORA);

        assertThat(resultado).isTrue();
        assertThat(evento.getEstadoOrganizador()).isSameAs(finalizado);
        verify(eventoRepository).save(evento);

        ArgumentCaptor<HistorialEstadoEvento> captor = ArgumentCaptor.forClass(HistorialEstadoEvento.class);
        verify(historialRepository).save(captor.capture());
        assertThat(captor.getValue().getUsuario()).isNull();
        assertThat(captor.getValue().getEstadoOrganizador()).isSameAs(finalizado);
        assertThat(captor.getValue().getFechaCambio()).isEqualTo(AHORA);
        // RF-3.4: al terminar, se invita a valorar a los inscriptos confirmados.
        verify(avisoInscriptosService).invitarAValorar(eq(ID_EVENTO), any());
    }

    @Test
    @DisplayName("Si entre el listado y el bloqueo dejo de corresponder (baja, suspension), no lo toca")
    void noPisaUnCambioEnParalelo() {
        siguePareciendoVencido(false);

        boolean resultado = servicio.finalizar(ID_EVENTO, AHORA);

        assertThat(resultado).isFalse();
        assertThat(evento.getEstadoOrganizador().getEstadoOrganizador()).isEqualTo("PUBLICADO");
        verify(eventoRepository, never()).save(any());
        verify(historialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un evento que ya no existe se ignora")
    void eventoInexistente() {
        when(eventoRepository.bloquearParaActualizar(ID_EVENTO)).thenReturn(Optional.empty());

        assertThat(servicio.finalizar(ID_EVENTO, AHORA)).isFalse();
        verify(historialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sin la fila FINALIZADO en el catalogo falla con un error claro y no cambia nada")
    void catalogoSinFinalizado() {
        siguePareciendoVencido(true);
        when(estadoOrganizadorRepository.findByEstadoOrganizadorIgnoreCase("FINALIZADO"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.finalizar(ID_EVENTO, AHORA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("FINALIZADO");
        verify(eventoRepository, never()).save(any());
    }
}
