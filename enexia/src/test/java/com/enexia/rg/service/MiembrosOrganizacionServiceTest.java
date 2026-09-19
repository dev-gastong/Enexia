package com.enexia.rg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

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

import com.enexia.rg.dto.MiembroAltaRequest;
import com.enexia.rg.dto.MiembroResponse;
import com.enexia.rg.exception.RecursoDuplicadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.model.MiembrosOrganizacion;
import com.enexia.rg.model.PersonaFisica;
import com.enexia.rg.model.PersonaJuridica;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.MiembrosOrganizacionRepository;
import com.enexia.rg.repository.PersonaJuridicaRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pruebas de "Mi Equipo": listado y gestion de miembros de una organizacion.
 *
 * No hay RF formal para este flujo (ver el javadoc de la clase bajo prueba),
 * asi que estos tests fijan el comportamiento como contrato: cualquier cambio
 * a una de estas reglas es una decision de producto, no un detalle interno.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MiembrosOrganizacionService - gestion de equipo")
class MiembrosOrganizacionServiceTest {

    @Mock private MiembrosOrganizacionRepository miembrosRepository;
    @Mock private PersonaJuridicaRepository personaJuridicaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private HttpServletRequest request;

    @InjectMocks private MiembrosOrganizacionService servicio;

    private static final Long ID_ORGANIZACION = 7L;
    private static final String EMAIL_ADMIN = "admin@enexia.test";
    private static final String EMAIL_MIEMBRO = "miembro@enexia.test";
    private static final String EMAIL_NUEVO = "nuevo@enexia.test";

    private Usuario admin;
    private Usuario miembroExistente;

    @BeforeEach
    void prepararEscenario() {
        admin = usuario(1L, EMAIL_ADMIN);
        miembroExistente = usuario(2L, EMAIL_MIEMBRO);

        when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL_ADMIN)).thenReturn(Optional.of(admin));
        when(miembrosRepository.buscarMembresia(1L, ID_ORGANIZACION))
                .thenReturn(Optional.of(membresia(admin, "ADMINISTRADOR")));
    }

    // =====================================================================
    // Listar
    // =====================================================================

    @Nested
    @DisplayName("Listar")
    class Listar {

        @Test
        @DisplayName("Cualquier miembro (no solo el administrador) puede ver el equipo")
        void miembroCualquieraPuedeListar() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL_MIEMBRO))
                    .thenReturn(Optional.of(miembroExistente));
            when(miembrosRepository.buscarMembresia(2L, ID_ORGANIZACION))
                    .thenReturn(Optional.of(membresia(miembroExistente, "MIEMBRO")));
            when(miembrosRepository.listarPorOrganizacion(ID_ORGANIZACION))
                    .thenReturn(List.of(membresia(admin, "ADMINISTRADOR"), membresia(miembroExistente, "MIEMBRO")));

            List<MiembroResponse> equipo = servicio.listar(EMAIL_MIEMBRO, ID_ORGANIZACION);

            assertThat(equipo).hasSize(2);
        }

        @Test
        @DisplayName("Quien no es miembro de la organizacion recibe 404, no 403")
        void noMiembroRecibeNoEncontrado() {
            when(miembrosRepository.buscarMembresia(1L, ID_ORGANIZACION)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.listar(EMAIL_ADMIN, ID_ORGANIZACION))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    // =====================================================================
    // Agregar
    // =====================================================================

    @Nested
    @DisplayName("Agregar miembro")
    class Agregar {

        private MiembroAltaRequest peticion;

        @BeforeEach
        void prepararPeticion() {
            peticion = new MiembroAltaRequest();
            peticion.setEmail(EMAIL_NUEVO);
        }

        @Test
        @DisplayName("Solo un ADMINISTRADOR de la organizacion puede agregar miembros")
        void soloAdministradorPuedeAgregar() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL_MIEMBRO))
                    .thenReturn(Optional.of(miembroExistente));
            when(miembrosRepository.buscarMembresia(2L, ID_ORGANIZACION))
                    .thenReturn(Optional.of(membresia(miembroExistente, "MIEMBRO")));

            assertThatThrownBy(() -> servicio.agregar(EMAIL_MIEMBRO, ID_ORGANIZACION, peticion, request))
                    .isInstanceOf(ReglaNegocioException.class);

            verify(miembrosRepository, never()).save(any());
        }

        @Test
        @DisplayName("El email tiene que corresponder a una cuenta activa existente")
        void rechazaEmailSinCuenta() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL_NUEVO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.agregar(EMAIL_ADMIN, ID_ORGANIZACION, peticion, request))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verify(miembrosRepository, never()).save(any());
        }

        @Test
        @DisplayName("No se puede agregar a alguien que ya es miembro")
        void rechazaMiembroDuplicado() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL_NUEVO))
                    .thenReturn(Optional.of(miembroExistente));
            when(miembrosRepository.buscarMembresia(2L, ID_ORGANIZACION))
                    .thenReturn(Optional.of(membresia(miembroExistente, "MIEMBRO")));

            assertThatThrownBy(() -> servicio.agregar(EMAIL_ADMIN, ID_ORGANIZACION, peticion, request))
                    .isInstanceOf(RecursoDuplicadoException.class);

            verify(miembrosRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sin rol explicito, el nuevo miembro entra como MIEMBRO (el de menos privilegios)")
        void rolPorDefectoEsMiembro() {
            Usuario nuevo = usuario(3L, EMAIL_NUEVO);
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL_NUEVO)).thenReturn(Optional.of(nuevo));
            when(miembrosRepository.buscarMembresia(3L, ID_ORGANIZACION)).thenReturn(Optional.empty());

            servicio.agregar(EMAIL_ADMIN, ID_ORGANIZACION, peticion, request);

            ArgumentCaptor<MiembrosOrganizacion> captor = ArgumentCaptor.forClass(MiembrosOrganizacion.class);
            verify(miembrosRepository).save(captor.capture());
            assertThat(captor.getValue().getRolEnEmpresa()).isEqualTo("MIEMBRO");
        }

        @Test
        @DisplayName("Con rol explicito ADMINISTRADOR, se respeta lo pedido")
        void respetaRolExplicito() {
            peticion.setRolEnEmpresa("ADMINISTRADOR");
            Usuario nuevo = usuario(3L, EMAIL_NUEVO);
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL_NUEVO)).thenReturn(Optional.of(nuevo));
            when(miembrosRepository.buscarMembresia(3L, ID_ORGANIZACION)).thenReturn(Optional.empty());

            servicio.agregar(EMAIL_ADMIN, ID_ORGANIZACION, peticion, request);

            ArgumentCaptor<MiembrosOrganizacion> captor = ArgumentCaptor.forClass(MiembrosOrganizacion.class);
            verify(miembrosRepository).save(captor.capture());
            assertThat(captor.getValue().getRolEnEmpresa()).isEqualTo("ADMINISTRADOR");
        }
    }

    // =====================================================================
    // Quitar
    // =====================================================================

    @Nested
    @DisplayName("Quitar miembro")
    class Quitar {

        @Test
        @DisplayName("No se puede quitar al unico administrador de la organizacion")
        void noQuitaUnicoAdministrador() {
            when(miembrosRepository.buscarMembresia(1L, ID_ORGANIZACION))
                    .thenReturn(Optional.of(membresia(admin, "ADMINISTRADOR")));
            when(miembrosRepository.contarAdministradores(ID_ORGANIZACION)).thenReturn(1L);

            assertThatThrownBy(() -> servicio.quitar(EMAIL_ADMIN, ID_ORGANIZACION, 1L, request))
                    .isInstanceOf(ReglaNegocioException.class);

            verify(miembrosRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Si hay otro administrador, se puede quitar a uno de los dos (incluso a si mismo)")
        void quitaAdministradorConReemplazo() {
            when(miembrosRepository.buscarMembresia(1L, ID_ORGANIZACION))
                    .thenReturn(Optional.of(membresia(admin, "ADMINISTRADOR")));
            when(miembrosRepository.contarAdministradores(ID_ORGANIZACION)).thenReturn(2L);

            servicio.quitar(EMAIL_ADMIN, ID_ORGANIZACION, 1L, request);

            verify(miembrosRepository).delete(any());
        }

        @Test
        @DisplayName("Quitar a un MIEMBRO comun nunca choca con la regla del ultimo administrador")
        void quitaMiembroComunSinRestriccion() {
            when(miembrosRepository.buscarMembresia(2L, ID_ORGANIZACION))
                    .thenReturn(Optional.of(membresia(miembroExistente, "MIEMBRO")));

            servicio.quitar(EMAIL_ADMIN, ID_ORGANIZACION, 2L, request);

            verify(miembrosRepository).delete(any());
            verify(miembrosRepository, never()).contarAdministradores(any());
        }

        @Test
        @DisplayName("Solo un ADMINISTRADOR de la organizacion puede quitar miembros")
        void soloAdministradorPuedeQuitar() {
            when(usuarioRepository.buscarActivoPorEmailConRoles(EMAIL_MIEMBRO))
                    .thenReturn(Optional.of(miembroExistente));
            when(miembrosRepository.buscarMembresia(2L, ID_ORGANIZACION))
                    .thenReturn(Optional.of(membresia(miembroExistente, "MIEMBRO")));

            assertThatThrownBy(() -> servicio.quitar(EMAIL_MIEMBRO, ID_ORGANIZACION, 1L, request))
                    .isInstanceOf(ReglaNegocioException.class);

            verify(miembrosRepository, never()).delete(any());
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private Usuario usuario(Long id, String email) {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setEmail(email);
        usuario.setNickname(email.split("@")[0]);

        PersonaFisica pf = new PersonaFisica();
        pf.setNombre("Nombre");
        pf.setApellido("Apellido");
        usuario.setPersonaFisica(pf);

        return usuario;
    }

    private MiembrosOrganizacion membresia(Usuario usuario, String rol) {
        PersonaJuridica organizacion = new PersonaJuridica();
        organizacion.setIdPersonaJuridica(ID_ORGANIZACION);
        organizacion.setRazonSocial("Cultural Fueguina SRL");

        MiembrosOrganizacion membresia = new MiembrosOrganizacion();
        membresia.setUsuario(usuario);
        membresia.setPersonaJuridica(organizacion);
        membresia.setRolEnEmpresa(rol);
        return membresia;
    }
}
