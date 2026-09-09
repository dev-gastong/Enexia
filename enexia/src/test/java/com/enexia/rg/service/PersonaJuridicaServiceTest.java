package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

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

import com.enexia.rg.dto.OrganizacionRegistroRequest;
import com.enexia.rg.dto.OrganizacionResponse;
import com.enexia.rg.dto.UbicacionRequest;
import com.enexia.rg.exception.ContenidoInapropiadoException;
import com.enexia.rg.exception.RecursoDuplicadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.Ciudad;
import com.enexia.rg.model.EstadoPersonaJuridicaNombre;
import com.enexia.rg.model.EstadoPersonaJuridicaSistemaNombre;
import com.enexia.rg.model.MiembrosOrganizacion;
import com.enexia.rg.model.PersonaJuridica;
import com.enexia.rg.model.PersonaJuridicaEstado;
import com.enexia.rg.model.PersonaJuridicaEstadoSistema;
import com.enexia.rg.model.Rol;
import com.enexia.rg.model.Ubicacion;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.model.UsuarioRol;
import com.enexia.rg.repository.CiudadRepository;
import com.enexia.rg.repository.HistorialEstadoPersonaJuridicaRepository;
import com.enexia.rg.repository.MiembrosOrganizacionRepository;
import com.enexia.rg.repository.PersonaJuridicaEstadoRepository;
import com.enexia.rg.repository.PersonaJuridicaEstadoSistemaRepository;
import com.enexia.rg.repository.PersonaJuridicaRepository;
import com.enexia.rg.repository.UbicacionRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pruebas del alta de organizaciones (RF-7.2, RF-7.3; DFD 7.1/7.2 y 7.3).
 *
 * Cubre las cuatro reglas que hacen que este endpoint sea seguro dejarlo
 * abierto al publico, y una quinta que protege la autoria de los eventos:
 *
 *   1. Solo una cuenta ORGANIZADOR puede tener empresa.
 *   2. El CUIT tiene que ser aritmeticamente valido (RF-7.3).
 *   3. El CUIT es unico, normalizado a 11 digitos.
 *   4. La organizacion nace APROBADA + ACTIVA y habilitada para publicar
 *      (ADR-14): el modulo 11 es el unico control que resuelve el alta.
 *   5. Publicar bajo una organizacion exige ser miembro Y que este habilitada.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PersonaJuridicaService - alta de organizaciones (RF-7.2)")
class PersonaJuridicaServiceTest {

    @Mock private PersonaJuridicaRepository personaJuridicaRepository;
    @Mock private PersonaJuridicaEstadoRepository estadoRepository;
    @Mock private PersonaJuridicaEstadoSistemaRepository estadoSistemaRepository;
    @Mock private HistorialEstadoPersonaJuridicaRepository historialRepository;
    @Mock private MiembrosOrganizacionRepository miembrosRepository;
    @Mock private UbicacionRepository ubicacionRepository;
    @Mock private CiudadRepository ciudadRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @Mock private ModeracionTextoService moderacionService;
    @Mock private AuditoriaService auditoriaService;
    @Mock private EmailService emailService;

    @Mock private HttpServletRequest request;

    @InjectMocks private PersonaJuridicaService servicio;

    /** CUIT real y valido (suma 198, resto 0, verificador 0). */
    private static final String CUIT_VALIDO = "30-71659554-0";
    private static final String CUIT_NORMALIZADO = "30716595540";

    private OrganizacionRegistroRequest peticion;

    @BeforeEach
    void prepararEscenario() {
        peticion = new OrganizacionRegistroRequest();
        peticion.setRazonSocial("  Cultural Fueguina SRL  ");
        peticion.setNombreFantasia("Fueguina Eventos");
        peticion.setCuit(CUIT_VALIDO);
        peticion.setEmailCorporativo("  CONTACTO@Fueguina.TEST ");
        peticion.setTelefonoContacto(" +54 2901 123456 ");

        UbicacionRequest domicilio = new UbicacionRequest();
        domicilio.setCalle(" San Martin ");
        domicilio.setNumeroExterior(" 1234 ");
        domicilio.setIdCiudad(13L);
        peticion.setDomicilioFiscal(domicilio);

        when(personaJuridicaRepository.existsByCuit(anyString())).thenReturn(false);
        when(ciudadRepository.findById(13L)).thenReturn(Optional.of(new Ciudad()));
        when(ubicacionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(personaJuridicaRepository.save(any())).thenAnswer(i -> {
            PersonaJuridica pj = i.getArgument(0);
            pj.setIdPersonaJuridica(7L);
            return pj;
        });
        when(estadoSistemaRepository.findByEstadoPersonaJuridicaSistemaIgnoreCase(
                EstadoPersonaJuridicaSistemaNombre.APROBADO.name()))
                .thenReturn(Optional.of(estadoSistema(EstadoPersonaJuridicaSistemaNombre.APROBADO)));
        when(estadoRepository.findByEstadoPersonaJuridicaIgnoreCase(
                EstadoPersonaJuridicaNombre.ACTIVO.name()))
                .thenReturn(Optional.of(estadoPropio(EstadoPersonaJuridicaNombre.ACTIVO)));
    }

    // =====================================================================
    // Camino feliz
    // =====================================================================

    @Nested
    @DisplayName("Alta correcta")
    class AltaCorrecta {

        @Test
        @DisplayName("La organizacion nace APROBADA + ACTIVA, habilitada para publicar")
        void naceHabilitada() {
            OrganizacionResponse respuesta =
                    servicio.crearOrganizacion(organizador(), peticion, request);

            // ADR-14: el alta se resuelve en el acto. El estado de revision se
            // elimino porque ningun proceso lo cerraba (el Modulo 6 quedo fuera
            // de alcance), y con el freno puesto NINGUNA organizacion podia
            // publicar jamas: RF-7.4 era inalcanzable.
            assertThat(respuesta.getEstadoSistema())
                    .isEqualTo(EstadoPersonaJuridicaSistemaNombre.APROBADO.name());
            assertThat(respuesta.getEstado())
                    .isEqualTo(EstadoPersonaJuridicaNombre.ACTIVO.name());
        }

        @Test
        @DisplayName("La entidad persistida queda con el par que exige estaHabilitada()")
        void persistidaConParHabilitante() {
            servicio.crearOrganizacion(organizador(), peticion, request);

            // Regresion del bug que ADR-14 corrige: el alta y el control de
            // publicacion se contradecian entre si. Que la respuesta diga
            // APROBADO/ACTIVO no alcanza; lo que resolverOrganizacionHabilitada()
            // lee es la fila persistida, asi que se verifica sobre esa.
            ArgumentCaptor<PersonaJuridica> captor = ArgumentCaptor.forClass(PersonaJuridica.class);
            verify(personaJuridicaRepository).save(captor.capture());
            PersonaJuridica guardada = captor.getValue();

            assertThat(guardada.getEstadoPersonaJuridicaSistema().getEstadoPersonaJuridicaSistema())
                    .isEqualTo(EstadoPersonaJuridicaSistemaNombre.APROBADO.name());
            assertThat(guardada.getEstadoPersonaJuridica().getEstadoPersonaJuridica())
                    .isEqualTo(EstadoPersonaJuridicaNombre.ACTIVO.name());
        }

        @Test
        @DisplayName("El CUIT se guarda normalizado y se devuelve formateado")
        void normalizaYFormateaCuit() {
            ArgumentCaptor<PersonaJuridica> captor = ArgumentCaptor.forClass(PersonaJuridica.class);

            OrganizacionResponse respuesta =
                    servicio.crearOrganizacion(organizador(), peticion, request);

            verify(personaJuridicaRepository).save(captor.capture());
            // Sin normalizar, "30-71659554-0" y "30716595540" serian dos filas
            // distintas y el UNIQUE de la base no impediria el duplicado.
            assertThat(captor.getValue().getCuit()).isEqualTo(CUIT_NORMALIZADO);
            // Hacia afuera se muestra legible.
            assertThat(respuesta.getCuit()).isEqualTo(CUIT_VALIDO);
        }

        @Test
        @DisplayName("Los textos se recortan y el email corporativo se pasa a minusculas")
        void normalizaTextos() {
            ArgumentCaptor<PersonaJuridica> captor = ArgumentCaptor.forClass(PersonaJuridica.class);

            servicio.crearOrganizacion(organizador(), peticion, request);

            verify(personaJuridicaRepository).save(captor.capture());
            PersonaJuridica guardada = captor.getValue();
            assertThat(guardada.getRazonSocial()).isEqualTo("Cultural Fueguina SRL");
            assertThat(guardada.getEmailCorporativo()).isEqualTo("contacto@fueguina.test");
            assertThat(guardada.getTelefonoContacto()).isEqualTo("+54 2901 123456");
        }

        @Test
        @DisplayName("El fundador queda como ADMINISTRADOR de la organizacion")
        void fundadorEsAdministrador() {
            ArgumentCaptor<MiembrosOrganizacion> captor =
                    ArgumentCaptor.forClass(MiembrosOrganizacion.class);

            servicio.crearOrganizacion(organizador(), peticion, request);

            verify(miembrosRepository).save(captor.capture());
            // Sin esta fila la empresa quedaria sin ningun miembro: nadie podria
            // administrarla ni darla de baja, y su CUIT quedaria tomado para
            // siempre.
            assertThat(captor.getValue().getRolEnEmpresa())
                    .isEqualTo(PersonaJuridicaService.ROL_ADMINISTRADOR);
        }

        @Test
        @DisplayName("Se asienta el estado inicial en el historial y se audita el alta")
        void dejaTraza() {
            servicio.crearOrganizacion(organizador(), peticion, request);

            // El MER exige historial de cada cambio de estado; el alta es el
            // primero. Sin esta fila el historial empezaria en el segundo cambio
            // y una suspension posterior no tendria con que compararse.
            verify(historialRepository).save(any());
            verify(auditoriaService).registrar(any(),
                    org.mockito.ArgumentMatchers.eq(AuditoriaService.ACCION_ALTA_ORGANIZACION),
                    anyString(), any());
        }

        @Test
        @DisplayName("No se envia ningun correo por el alta")
        void noEnviaCorreo() {
            servicio.crearOrganizacion(organizador(), peticion, request);

            // ADR-14: el alta se resuelve en el acto y la respuesta HTTP ya lo
            // confirma. No queda nada pendiente que avisar.
            verifyNoInteractions(emailService);
        }
    }

    // =====================================================================
    // Rechazos
    // =====================================================================

    @Nested
    @DisplayName("Controles previos a cualquier escritura")
    class Rechazos {

        @Test
        @DisplayName("Un PARTICIPANTE no puede registrar organizacion")
        void soloOrganizador() {
            Usuario participante = usuarioConRol("PARTICIPANTE");

            assertThatThrownBy(() -> servicio.crearOrganizacion(participante, peticion, request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("ORGANIZADOR");

            // Es el control mas barato de todos y por eso va primero: corta
            // antes de calcular el CUIT y antes de consultar la base.
            verifyNoInteractions(personaJuridicaRepository, ubicacionRepository, miembrosRepository);
        }

        @Test
        @DisplayName("CUIT con digito verificador invalido -> 400, sin tocar la base")
        void cuitInvalido() {
            peticion.setCuit("30-71659554-1");

            assertThatThrownBy(() -> servicio.crearOrganizacion(organizador(), peticion, request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("digito verificador");

            verify(personaJuridicaRepository, never()).save(any());
        }

        @Test
        @DisplayName("CUIT repetido -> 409, y la consulta usa el numero normalizado")
        void cuitDuplicado() {
            when(personaJuridicaRepository.existsByCuit(CUIT_NORMALIZADO)).thenReturn(true);

            assertThatThrownBy(() -> servicio.crearOrganizacion(organizador(), peticion, request))
                    .isInstanceOf(RecursoDuplicadoException.class)
                    .hasMessageContaining("CUIT");

            verify(personaJuridicaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Razon social ofensiva: se audita el rechazo y no se persiste nada")
        void razonSocialModerada() {
            doThrow(new ContenidoInapropiadoException("razonSocial"))
                    .when(moderacionService).validar(anyString(),
                            org.mockito.ArgumentMatchers.eq("razonSocial"));

            assertThatThrownBy(() -> servicio.crearOrganizacion(organizador(), peticion, request))
                    .isInstanceOf(ContenidoInapropiadoException.class);

            // Orden verificado: la moderacion corre ANTES de la primera
            // escritura. Si corriera despues, el texto rechazado quedaria en la
            // base aunque el alta se revierta.
            verify(personaJuridicaRepository, never()).save(any());
            verify(ubicacionRepository, never()).save(any());
            verify(auditoriaService).registrarAparte(any(),
                    org.mockito.ArgumentMatchers.eq(AuditoriaService.ACCION_ALTA_ORGANIZACION_RECHAZADA),
                    anyString(), any());
        }

        @Test
        @DisplayName("Ciudad inexistente -> mensaje accionable, no NoSuchElementException")
        void ciudadInexistente() {
            when(ciudadRepository.findById(13L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crearOrganizacion(organizador(), peticion, request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("ciudad");
        }

        @Test
        @DisplayName("Catalogo de estados sin cargar -> error explicito")
        void catalogoSinInicializar() {
            when(estadoSistemaRepository.findByEstadoPersonaJuridicaSistemaIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.crearOrganizacion(organizador(), peticion, request))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no esta inicializado");
        }
    }

    // =====================================================================
    // Resolucion de autoria (RF-2.1)
    // =====================================================================

    @Nested
    @DisplayName("resolverOrganizacionHabilitada() - control de autoria de eventos")
    class Autoria {

        @Test
        @DisplayName("Sin id de organizacion devuelve null: publicar a titulo personal es lo normal")
        void sinOrganizacion() {
            assertThat(servicio.resolverOrganizacionHabilitada(organizador(), null)).isNull();
        }

        @Test
        @DisplayName("No ser miembro -> 404, no 403")
        void noEsMiembro() {
            when(miembrosRepository.buscarMembresia(1L, 99L)).thenReturn(Optional.empty());

            // 404 y no 403: un 403 confirmaria que esa organizacion existe, y
            // iterando ids se podria mapear el padron de empresas registradas.
            assertThatThrownBy(() -> servicio.resolverOrganizacionHabilitada(organizador(), 99L))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        @DisplayName("Organizacion en revision -> no habilita publicar a nombre corporativo")
        void organizacionEnRevision() {
            when(miembrosRepository.buscarMembresia(1L, 7L))
                    .thenReturn(Optional.of(membresia(
                            EstadoPersonaJuridicaSistemaNombre.REVISION_PENDIENTE,
                            EstadoPersonaJuridicaNombre.INACTIVO)));

            assertThatThrownBy(() -> servicio.resolverOrganizacionHabilitada(organizador(), 7L))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("revision");
        }

        @Test
        @DisplayName("Aprobada por el sistema pero INACTIVA por sus miembros -> tampoco habilita")
        void aprobadaPeroInactiva() {
            when(miembrosRepository.buscarMembresia(1L, 7L))
                    .thenReturn(Optional.of(membresia(
                            EstadoPersonaJuridicaSistemaNombre.APROBADO,
                            EstadoPersonaJuridicaNombre.INACTIVO)));

            // Los dos ejes de estado son independientes y hacen falta AMBOS:
            // uno lo mueve el moderador, el otro los propios miembros.
            assertThatThrownBy(() -> servicio.resolverOrganizacionHabilitada(organizador(), 7L))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("APROBADO + ACTIVO -> habilita")
        void habilitada() {
            when(miembrosRepository.buscarMembresia(1L, 7L))
                    .thenReturn(Optional.of(membresia(
                            EstadoPersonaJuridicaSistemaNombre.APROBADO,
                            EstadoPersonaJuridicaNombre.ACTIVO)));

            PersonaJuridica resuelta = servicio.resolverOrganizacionHabilitada(organizador(), 7L);

            assertThat(resuelta).isNotNull();
            assertThat(resuelta.getIdPersonaJuridica()).isEqualTo(7L);
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private Usuario organizador() {
        return usuarioConRol("ORGANIZADOR");
    }

    private Usuario usuarioConRol(String nombreRol) {
        Rol rol = new Rol();
        rol.setNombreRol(nombreRol);

        Usuario usuario = new Usuario();
        usuario.setIdUsuario(1L);
        usuario.setEmail("ana@enexia.test");

        UsuarioRol usuarioRol = new UsuarioRol();
        usuarioRol.setUsuario(usuario);
        usuarioRol.setRol(rol);
        usuario.setUsuarioRoles(Set.of(usuarioRol));

        return usuario;
    }

    private PersonaJuridicaEstadoSistema estadoSistema(EstadoPersonaJuridicaSistemaNombre nombre) {
        PersonaJuridicaEstadoSistema estado = new PersonaJuridicaEstadoSistema();
        estado.setEstadoPersonaJuridicaSistema(nombre.name());
        return estado;
    }

    private PersonaJuridicaEstado estadoPropio(EstadoPersonaJuridicaNombre nombre) {
        PersonaJuridicaEstado estado = new PersonaJuridicaEstado();
        estado.setEstadoPersonaJuridica(nombre.name());
        return estado;
    }

    private MiembrosOrganizacion membresia(EstadoPersonaJuridicaSistemaNombre sistema,
                                           EstadoPersonaJuridicaNombre propio) {
        PersonaJuridica organizacion = new PersonaJuridica();
        organizacion.setIdPersonaJuridica(7L);
        organizacion.setRazonSocial("Cultural Fueguina SRL");
        organizacion.setUbicacion(new Ubicacion());
        organizacion.setEstadoPersonaJuridicaSistema(estadoSistema(sistema));
        organizacion.setEstadoPersonaJuridica(estadoPropio(propio));

        MiembrosOrganizacion membresia = new MiembrosOrganizacion();
        membresia.setPersonaJuridica(organizacion);
        return membresia;
    }
}
