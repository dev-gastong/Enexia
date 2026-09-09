package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.enexia.rg.dto.EventoCrearRequest;
import com.enexia.rg.model.MotivoModeracionEvento;

/**
 * Pruebas del pipeline asincrono de moderacion (RF-2.2, RF-2.3; DFD 2.5 y 5.x).
 *
 * Se invoca {@code procesar(...)} directamente, sin Spring: el metodo es publico
 * y sin anotaciones justamente para poder probarlo asi. Lo que se verifica no es
 * "que modere", sino las CUATRO DECISIONES del pipeline, que son donde estan las
 * reglas de negocio:
 *
 *   1. Texto rechazado -> las imagenes NI SIQUIERA se procesan (RF-2.2).
 *   2. Todas las imagenes rechazadas -> evento rechazado, con el motivo correcto.
 *   3. Al menos una imagen aprobada -> se publica (RF-2.3, criterio permisivo).
 *   4. Fallo tecnico -> ERROR_PIPELINE, nunca un evento colgado en EN_PROCESO.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ModeracionEventoService - pipeline de dos fases (RF-2.2)")
class ModeracionEventoServiceTest {

    @Mock private ModeracionTextoService moderacionTextoService;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private PublicacionEventoService publicacionService;
    @Mock private EmailService emailService;

    @InjectMocks private ModeracionEventoService servicio;

    private static final Long ID_EVENTO = 42L;
    private static final String EMAIL = "organizador@enexia.test";

    private EventoCrearRequest datos;
    private ImagenPendiente imagen;

    @BeforeEach
    void prepararEscenario() {
        datos = new EventoCrearRequest();
        datos.setNombre("Festival de Invierno Fueguino");
        datos.setDescripcion("Tres jornadas de musica en vivo y gastronomia regional en Ushuaia.");

        imagen = new ImagenPendiente("portada.png", new byte[]{1, 2, 3});

        when(moderacionTextoService.contieneLenguajeOfensivo(anyString())).thenReturn(false);
    }

    // =====================================================================
    // FASE 1 - Texto
    // =====================================================================

    @Nested
    @DisplayName("Fase 1: moderacion de texto")
    class FaseTexto {

        @Test
        @DisplayName("Titulo ofensivo: rechaza con MODERACION_TEXTO")
        void tituloOfensivo() {
            when(moderacionTextoService.contieneLenguajeOfensivo(datos.getNombre())).thenReturn(true);

            servicio.procesar(ID_EVENTO, datos, List.of(imagen), EMAIL);

            verify(publicacionService).rechazar(ID_EVENTO, MotivoModeracionEvento.MODERACION_TEXTO);
            verify(publicacionService, never()).aprobarYPersistir(any(), any(), any());
        }

        @Test
        @DisplayName("Descripcion ofensiva: tambien rechaza")
        void descripcionOfensiva() {
            when(moderacionTextoService.contieneLenguajeOfensivo(datos.getDescripcion())).thenReturn(true);

            servicio.procesar(ID_EVENTO, datos, List.of(imagen), EMAIL);

            verify(publicacionService).rechazar(ID_EVENTO, MotivoModeracionEvento.MODERACION_TEXTO);
        }

        @Test
        @DisplayName("Texto rechazado: las imagenes se DESCARTAN SIN PROCESAR (RF-2.2)")
        void noSubeImagenesSiElTextoFalla() {
            when(moderacionTextoService.contieneLenguajeOfensivo(datos.getNombre())).thenReturn(true);

            servicio.procesar(ID_EVENTO, datos, List.of(imagen, imagen, imagen), EMAIL);

            // Es una optimizacion que RF-2.2 pide de forma explicita: subir y
            // analizar hasta 6MB para un evento que YA esta rechazado gasta
            // ancho de banda, cuota de Cloudinary y tiempo de un hilo del pool
            // en un resultado que no se va a usar.
            verifyNoInteractions(cloudinaryService);
        }

        @Test
        @DisplayName("Se notifica al organizador con el motivo del rechazo")
        void notificaRechazo() {
            when(moderacionTextoService.contieneLenguajeOfensivo(datos.getNombre())).thenReturn(true);

            servicio.procesar(ID_EVENTO, datos, List.of(imagen), EMAIL);

            verify(emailService).enviarResultadoModeracionEvento(
                    eq(EMAIL), eq(datos.getNombre()), eq(false), anyString());
        }
    }

    // =====================================================================
    // FASE 2 - Imagenes
    // =====================================================================

    @Nested
    @DisplayName("Fase 2: moderacion de multimedia")
    class FaseImagenes {

        @Test
        @DisplayName("Todas rechazadas por contenido -> MODERACION_IMAGEN")
        void todasRechazadas() {
            when(cloudinaryService.subirYModerar(any(), anyString()))
                    .thenReturn(CloudinaryService.ResultadoImagen.rechazada("Contenido sensible"));

            servicio.procesar(ID_EVENTO, datos, List.of(imagen, imagen), EMAIL);

            verify(publicacionService).rechazar(ID_EVENTO, MotivoModeracionEvento.MODERACION_IMAGEN);
        }

        @Test
        @DisplayName("Sin imagenes -> SIN_IMAGENES_VALIDAS, motivo distinto del anterior")
        void sinImagenes() {
            servicio.procesar(ID_EVENTO, datos, List.of(), EMAIL);

            // Los dos motivos se distinguen a proposito: "las rechazaron" es un
            // problema de contenido del organizador; "no habia ninguna" es un
            // problema de carga. El panel de administracion los trata distinto.
            verify(publicacionService).rechazar(ID_EVENTO, MotivoModeracionEvento.SIN_IMAGENES_VALIDAS);
        }

        @Test
        @DisplayName("Una aprobada de tres: el evento SE PUBLICA (RF-2.3)")
        void alcanzaConUnaAprobada() {
            when(cloudinaryService.subirYModerar(any(), eq("mala1.png")))
                    .thenReturn(CloudinaryService.ResultadoImagen.rechazada("Contenido sensible"));
            when(cloudinaryService.subirYModerar(any(), eq("mala2.png")))
                    .thenReturn(CloudinaryService.ResultadoImagen.rechazada("Peso excedido"));
            when(cloudinaryService.subirYModerar(any(), eq("buena.png")))
                    .thenReturn(CloudinaryService.ResultadoImagen.aprobada(
                            "https://cdn.test/buena.jpg", "enexia/buena"));

            servicio.procesar(ID_EVENTO, datos, List.of(
                    new ImagenPendiente("mala1.png", new byte[]{1}),
                    new ImagenPendiente("buena.png", new byte[]{2}),
                    new ImagenPendiente("mala2.png", new byte[]{3})), EMAIL);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(publicacionService).aprobarYPersistir(eq(ID_EVENTO), eq(datos), captor.capture());

            // Criterio deliberadamente permisivo: castigar el evento entero
            // porque dos de tres imagenes fallaron penalizaria al organizador
            // por un error menor y facil de corregir.
            assertThat(captor.getValue()).containsExactly("https://cdn.test/buena.jpg");
            verify(publicacionService, never()).rechazar(any(), any());
        }

        @Test
        @DisplayName("Se procesan TODAS las imagenes, no se corta en la primera que falla")
        void procesaTodas() {
            when(cloudinaryService.subirYModerar(any(), anyString()))
                    .thenReturn(CloudinaryService.ResultadoImagen.rechazada("x"));

            servicio.procesar(ID_EVENTO, datos, List.of(imagen, imagen, imagen), EMAIL);

            verify(cloudinaryService, times(3)).subirYModerar(any(), anyString());
        }

        @Test
        @DisplayName("Aprobado: se avisa al organizador que su evento ya es visible")
        void notificaAprobacion() {
            when(cloudinaryService.subirYModerar(any(), anyString()))
                    .thenReturn(CloudinaryService.ResultadoImagen.aprobada("https://cdn.test/a.jpg", "a"));

            servicio.procesar(ID_EVENTO, datos, List.of(imagen), EMAIL);

            verify(emailService).enviarResultadoModeracionEvento(
                    eq(EMAIL), eq(datos.getNombre()), eq(true), any());
        }
    }

    // =====================================================================
    // Contencion de fallos
    // =====================================================================

    @Nested
    @DisplayName("Red de contencion")
    class Contencion {

        @Test
        @DisplayName("Si la publicacion falla, el evento queda RECHAZADO por ERROR_PIPELINE")
        void falloTecnico() {
            when(cloudinaryService.subirYModerar(any(), anyString()))
                    .thenReturn(CloudinaryService.ResultadoImagen.aprobada("https://cdn.test/a.jpg", "a"));
            doThrow(new RuntimeException("base caida"))
                    .when(publicacionService).aprobarYPersistir(any(), any(), any());

            servicio.procesar(ID_EVENTO, datos, List.of(imagen), EMAIL);

            // Lo importante no es el motivo, es que el evento NO quede colgado
            // en EN_PROCESO: en un metodo asincrono void, una excepcion no llega
            // a nadie y el organizador se quedaria esperando para siempre.
            verify(publicacionService).rechazar(ID_EVENTO, MotivoModeracionEvento.ERROR_PIPELINE);
        }

        @Test
        @DisplayName("El pipeline nunca propaga excepciones hacia el hilo del pool")
        void nuncaPropaga() {
            when(cloudinaryService.subirYModerar(any(), anyString()))
                    .thenThrow(new RuntimeException("timeout de Cloudinary"));

            // Si esto lanzara, el hilo del pool registraria el error y nadie mas
            // se enteraria. La llamada tiene que volver normalmente.
            servicio.procesar(ID_EVENTO, datos, List.of(imagen), EMAIL);

            verify(publicacionService).rechazar(ID_EVENTO, MotivoModeracionEvento.ERROR_PIPELINE);
        }

        @Test
        @DisplayName("Si tampoco se puede marcar el rechazo, no lanza: solo queda el log")
        void falloDobleNoExplota() {
            when(cloudinaryService.subirYModerar(any(), anyString()))
                    .thenThrow(new RuntimeException("timeout"));
            doThrow(new RuntimeException("base caida")).when(publicacionService).rechazar(any(), any());

            servicio.procesar(ID_EVENTO, datos, List.of(imagen), EMAIL);

            // Una segunda excepcion aca ocultaria la primera, que es la que
            // explica el problema real.
            verify(emailService, never()).enviarResultadoModeracionEvento(
                    anyString(), anyString(), anyBoolean(), anyString());
        }
    }
}
