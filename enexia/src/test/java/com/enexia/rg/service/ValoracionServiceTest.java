package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.enexia.rg.dto.ValoracionRequest;
import com.enexia.rg.dto.ValoracionResponse;
import com.enexia.rg.exception.ContenidoInapropiadoException;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoDuplicadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.model.Evento;
import com.enexia.rg.model.EventoCronograma;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.Valoracion;
import com.enexia.rg.repository.EventoCronogramaRepository;
import com.enexia.rg.repository.InscripcionRepository;
import com.enexia.rg.repository.UsuarioRepository;
import com.enexia.rg.repository.ValoracionRepository;

import jakarta.servlet.http.HttpServletRequest;

/** Pruebas de {@link ValoracionService}: elegibilidad, unicidad y moderacion (RF-3.4, RF-3.5). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ValoracionService - calificacion de cronogramas (RF-3.4, RF-3.5)")
class ValoracionServiceTest {

    @Mock private ValoracionRepository valoracionRepository;
    @Mock private InscripcionRepository inscripcionRepository;
    @Mock private EventoCronogramaRepository cronogramaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private ModeracionTextoService moderacionTextoService;
    @Mock private HttpServletRequest request;

    @InjectMocks private ValoracionService servicio;

    private static final String EMAIL = "participante@enexia.test";
    private static final Long ID_USUARIO = 1L;
    private static final Long ID_CRONOGRAMA = 30L;

    private Usuario participante;
    private EventoCronograma cronogramaFinalizado;
    private ValoracionRequest peticion;

    @BeforeEach
    void prepararEscenario() {
        participante = new Usuario();
        participante.setIdUsuario(ID_USUARIO);
        participante.setEmail(EMAIL);

        Evento evento = new Evento();
        evento.setIdEvento(20L);
        evento.setNombre("Festival de Musica");

        cronogramaFinalizado = new EventoCronograma();
        cronogramaFinalizado.setIdCronograma(ID_CRONOGRAMA);
        cronogramaFinalizado.setEvento(evento);
        cronogramaFinalizado.setFecha(LocalDate.now().minusDays(1));
        cronogramaFinalizado.setHoraInicio(LocalTime.of(20, 0));
        cronogramaFinalizado.setHoraFin(LocalTime.of(23, 0));

        peticion = new ValoracionRequest();
        peticion.setIdCronograma(ID_CRONOGRAMA);
        peticion.setValor(5);
        peticion.setComentario("Excelente organizacion");

        when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL)).thenReturn(Optional.of(participante));
        when(cronogramaRepository.findById(ID_CRONOGRAMA)).thenReturn(Optional.of(cronogramaFinalizado));
        when(inscripcionRepository.existeConfirmadaDeUsuarioEnCronograma(ID_USUARIO, ID_CRONOGRAMA))
                .thenReturn(true);
        when(valoracionRepository.existsByUsuarioIdUsuarioAndCronogramaIdCronograma(ID_USUARIO, ID_CRONOGRAMA))
                .thenReturn(false);
        when(moderacionTextoService.contieneLenguajeOfensivo(anyString())).thenReturn(false);
        when(valoracionRepository.save(any(Valoracion.class))).thenAnswer(inv -> {
            Valoracion v = inv.getArgument(0);
            v.setIdvaloracion(77L);
            return v;
        });
    }

    @Test
    @DisplayName("Camino feliz: persiste y devuelve la valoracion")
    void creaValoracion() {
        ValoracionResponse respuesta = servicio.crear(EMAIL, peticion, request);

        assertThat(respuesta.getIdValoracion()).isEqualTo(77L);
        assertThat(respuesta.getEventoNombre()).isEqualTo("Festival de Musica");
        verify(auditoriaService).registrar(eq(participante),
                eq(AuditoriaService.ACCION_VALORACION_CREADA), anyString(), eq(request));
    }

    @Test
    @DisplayName("Cronograma inexistente: 404")
    void rechazaCronogramaInexistente() {
        when(cronogramaRepository.findById(ID_CRONOGRAMA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(EMAIL, peticion, request))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Sin inscripcion confirmada: solo pueden valorar quienes asistieron")
    void rechazaSinAsistenciaConfirmada() {
        when(inscripcionRepository.existeConfirmadaDeUsuarioEnCronograma(ID_USUARIO, ID_CRONOGRAMA))
                .thenReturn(false);

        assertThatThrownBy(() -> servicio.crear(EMAIL, peticion, request))
                .isInstanceOf(OperacionNoPermitidaException.class);

        verify(valoracionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cronograma todavia no finalizo: no se puede valorar")
    void rechazaCronogramaSinFinalizar() {
        cronogramaFinalizado.setFecha(LocalDate.now().plusDays(5));

        assertThatThrownBy(() -> servicio.crear(EMAIL, peticion, request))
                .isInstanceOf(OperacionNoPermitidaException.class);

        verify(valoracionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Restriccion unica: no se puede valorar dos veces el mismo cronograma")
    void rechazaValoracionDuplicada() {
        when(valoracionRepository.existsByUsuarioIdUsuarioAndCronogramaIdCronograma(ID_USUARIO, ID_CRONOGRAMA))
                .thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(EMAIL, peticion, request))
                .isInstanceOf(RecursoDuplicadoException.class);

        verify(valoracionRepository, never()).save(any());
    }

    @Test
    @DisplayName("RF-3.5: comentario ofensivo -- se audita el intento y NO se persiste la valoracion")
    void rechazaComentarioOfensivo() {
        when(moderacionTextoService.contieneLenguajeOfensivo(anyString())).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(EMAIL, peticion, request))
                .isInstanceOf(ContenidoInapropiadoException.class);

        verify(valoracionRepository, never()).save(any());
        verify(auditoriaService).registrarAparte(eq(participante),
                eq(AuditoriaService.ACCION_VALORACION_RECHAZADA_MODERACION), anyString(), eq(request));
        verify(auditoriaService, never()).registrar(any(), anyString(), anyString(), any());
    }
}
