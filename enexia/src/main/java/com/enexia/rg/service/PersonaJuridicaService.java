package com.enexia.rg.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.enexia.rg.model.HistorialEstadoPersonaJuridica;
import com.enexia.rg.model.MiembrosOrganizacion;
import com.enexia.rg.model.PersonaJuridica;
import com.enexia.rg.model.PersonaJuridicaEstado;
import com.enexia.rg.model.PersonaJuridicaEstadoSistema;
import com.enexia.rg.model.RolNombre;
import com.enexia.rg.model.Ubicacion;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.CiudadRepository;
import com.enexia.rg.repository.HistorialEstadoPersonaJuridicaRepository;
import com.enexia.rg.repository.MiembrosOrganizacionRepository;
import com.enexia.rg.repository.PersonaJuridicaEstadoRepository;
import com.enexia.rg.repository.PersonaJuridicaEstadoSistemaRepository;
import com.enexia.rg.repository.PersonaJuridicaRepository;
import com.enexia.rg.repository.UbicacionRepository;
import com.enexia.rg.repository.UsuarioRepository;
import com.enexia.rg.util.ValidadorCuit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Alta y consulta de organizaciones (RF-7.2, RF-7.3; DFD 7.1/7.2 y 7.3).
 *
 * EL PRINCIPIO QUE ORDENA TODA ESTA CLASE
 * Una Persona Juridica NO es una identidad de acceso: es un contenedor
 * administrativo. No tiene usuario, no tiene contrasena, no se inscribe a
 * eventos. Existe unicamente para que un evento pueda publicarse a nombre
 * corporativo (RF-7.4) mientras la responsabilidad sigue siendo de una persona
 * humana concreta, la que figura en {@code miembros_organizacion}.
 *
 * Por eso este servicio SIEMPRE recibe un {@link Usuario} ya existente: no crea
 * cuentas. Los dos puntos de entrada (registro en un paso y alta autenticada)
 * difieren solo en de donde sale ese usuario.
 *
 * POR QUE NACE APROBADA Y ACTIVA  (ADR-14, 2026-09-09)
 * Hasta esta version la organizacion nacia en REVISION_PENDIENTE + INACTIVO, a
 * la espera de que un administrador verificara el CUIT. Ese freno se elimino, y
 * no por comodidad: NINGUN codigo escribia nunca APROBADO. La unica via era el
 * Modulo 6, que quedo fuera de alcance. El resultado era que
 * resolverOrganizacionHabilitada() rechazaba SIEMPRE, ninguna organizacion podia
 * publicar jamas, y la firma corporativa de RF-7.4 era inalcanzable: una maquina
 * de estados sin transicion de salida.
 *
 * La alternativa habria sido verificar la existencia real contra el padron de
 * AFIP/ARCA, que exige clave fiscal y certificado digital: no hay servicio
 * publico, gratuito y estable. Entre sostener una puerta sin llave y resolver
 * con el unico control disponible, se opto por lo segundo.
 *
 * LIMITACION ASUMIDA Y DOCUMENTADA (RF-7.2 y RF-7.3)
 * El modulo 11 es ARITMETICO, NO PROBATORIO: acredita que el CUIT esta bien
 * formado, no que exista una entidad juridica detras ni que pertenezca a quien
 * lo carga. Un CUIT inventado cuyo digito verificador cierre sera aceptado. La
 * verificacion de existencia real queda diferida a una futura integracion con
 * el padron, o a la revision manual del Modulo 6 cuando exista.
 *
 * LOS ESTADOS NO SE BORRARON
 * REVISION_PENDIENTE y RECHAZADO siguen en el catalogo, y el historial de
 * estados se sigue escribiendo desde el alta. No se usan aca, pero son la via
 * por la que el Modulo 6 podra suspender o dar de baja una organizacion, y
 * estado_persona_juridica (ACTIVO/INACTIVO) es la que usaran sus propios
 * miembros para desactivarla. Sin la fila inicial de historial, esos cambios
 * futuros no tendrian punto de partida.
 *
 * IMPORTANTE: estos estados son de la ORGANIZACION, no del usuario fundador.
 * La cuenta personal queda ACTIVA y operativa desde el primer momento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonaJuridicaService {

    /** Rol del fundador dentro de la organizacion (CLAUDE.md, tabla Miembros_Organizacion). */
    public static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";

    private final PersonaJuridicaRepository personaJuridicaRepository;
    private final PersonaJuridicaEstadoRepository estadoRepository;
    private final PersonaJuridicaEstadoSistemaRepository estadoSistemaRepository;
    private final HistorialEstadoPersonaJuridicaRepository historialRepository;
    private final MiembrosOrganizacionRepository miembrosRepository;
    private final UbicacionRepository ubicacionRepository;
    private final CiudadRepository ciudadRepository;
    private final UsuarioRepository usuarioRepository;

    private final ModeracionTextoService moderacionService;
    private final AuditoriaService auditoriaService;
    private final EmailService emailService;

    // =====================================================================
    // ALTA  (DFD 7.2)
    // =====================================================================

    /**
     * Crea la organizacion y deja al usuario como su ADMINISTRADOR.
     *
     * {@code @Transactional} es obligatorio: se escribe en cuatro tablas
     * encadenadas (ubicacion -> persona_juridica -> historial_estado ->
     * miembros_organizacion). Si fallara la ultima sin transaccion, quedaria una
     * empresa registrada SIN NINGUN MIEMBRO: nadie podria administrarla ni
     * darla de baja, y su CUIT quedaria tomado para siempre.
     *
     * ORDEN DE LOS CONTROLES (importa)
     *   1. Rol: debe ser ORGANIZADOR.        -> barato, corta antes que nada
     *   2. CUIT: formato + digito verificador -> puro calculo, sin base
     *   3. CUIT: unicidad                     -> una consulta
     *   4. Moderacion de razon social         -> antes de persistir NADA
     *   5. Recien entonces, escrituras.
     * Es el mismo criterio del registro de Persona Fisica: lo barato primero, y
     * la moderacion siempre antes de la primera escritura, para que un texto
     * rechazado no llegue nunca a la base.
     */
    @Transactional
    public OrganizacionResponse crearOrganizacion(Usuario usuario,
                                                  OrganizacionRegistroRequest peticion,
                                                  HttpServletRequest request) {

        // --- 1. Solo un ORGANIZADOR puede tener empresa.
        // Un PARTICIPANTE no puede publicar eventos, asi que darle una
        // organizacion crearia una entidad que no podria usar para nada.
        if (!usuario.getRoles().contains(RolNombre.ORGANIZADOR.name())) {
            throw new ReglaNegocioException(
                    "Solo una cuenta con perfil ORGANIZADOR puede registrar una organizacion");
        }

        // --- 2. Paso 7.3: validacion algoritmica del CUIT.
        String cuit = ValidadorCuit.normalizar(peticion.getCuit());
        if (!ValidadorCuit.esValido(cuit)) {
            throw new ReglaNegocioException(
                    "El CUIT ingresado no es valido: el digito verificador no coincide");
        }

        // --- 3. Paso 7.2.3: unicidad fiscal.
        if (personaJuridicaRepository.existsByCuit(cuit)) {
            throw new RecursoDuplicadoException("Ya existe una organizacion registrada con ese CUIT");
        }

        String razonSocial = peticion.getRazonSocial().trim();
        String nombreFantasia = peticion.getNombreFantasia() == null
                ? null
                : peticion.getNombreFantasia().trim();

        // --- 4. Moderacion (RF-5.1). Ambos campos son visibles para terceros:
        // son la firma que aparece en la ficha publica del evento (RF-7.4).
        moderarNombres(razonSocial, nombreFantasia, usuario, request);

        // --- 5. Paso 7.2.2: domicilio fiscal.
        Ubicacion domicilio = persistirUbicacion(peticion.getDomicilioFiscal());

        // --- 5b. Paso 7.2.3: la organizacion queda aprobada y activa (ADR-14).
        // El CUIT ya paso el modulo 11 en el paso 2; ese es el unico control que
        // resuelve el alta. Ver el javadoc de la clase para el alcance real de
        // esa validacion y por que se elimino el estado de revision.
        PersonaJuridicaEstadoSistema aprobada = estadoSistemaRepository
                .findByEstadoPersonaJuridicaSistemaIgnoreCase(
                        EstadoPersonaJuridicaSistemaNombre.APROBADO.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo de estados de sistema de organizacion no esta inicializado"));

        PersonaJuridicaEstado activa = estadoRepository
                .findByEstadoPersonaJuridicaIgnoreCase(EstadoPersonaJuridicaNombre.ACTIVO.name())
                .orElseThrow(() -> new ReglaNegocioException(
                        "El catalogo de estados de organizacion no esta inicializado"));

        PersonaJuridica organizacion = new PersonaJuridica();
        organizacion.setRazonSocial(razonSocial);
        organizacion.setNombreFantasia(nombreFantasia);
        // Se persiste normalizado (solo digitos). Ver el javadoc de la entidad.
        organizacion.setCuit(cuit);
        organizacion.setEmailCorporativo(peticion.getEmailCorporativo().trim().toLowerCase());
        organizacion.setTelefonoContacto(peticion.getTelefonoContacto().trim());
        organizacion.setUbicacion(domicilio);
        organizacion.setEstadoPersonaJuridicaSistema(aprobada);
        organizacion.setEstadoPersonaJuridica(activa);
        organizacion.setFechaRegistro(LocalDateTime.now());
        organizacion = personaJuridicaRepository.save(organizacion);

        // --- 5c. Trazabilidad del estado inicial.
        // El MER exige historial de cada cambio de estado. El alta cuenta como
        // el primer cambio: sin esta fila, el historial empezaria en el segundo
        // y no habria con que comparar una suspension posterior.
        HistorialEstadoPersonaJuridica historial = new HistorialEstadoPersonaJuridica();
        historial.setPersonaJuridica(organizacion);
        historial.setEstadoPersonaJuridicaSistema(aprobada);
        historial.setEstadoPersonaJuridica(activa);
        historial.setFechaCambio(LocalDateTime.now());
        historialRepository.save(historial);

        // --- 5d. El fundador queda como ADMINISTRADOR (RF-7.2).
        MiembrosOrganizacion membresia = new MiembrosOrganizacion();
        membresia.setUsuario(usuario);
        membresia.setPersonaJuridica(organizacion);
        membresia.setRolEnEmpresa(ROL_ADMINISTRADOR);
        miembrosRepository.save(membresia);

        auditoriaService.registrar(usuario, AuditoriaService.ACCION_ALTA_ORGANIZACION,
                "Alta de organizacion " + razonSocial + " (CUIT " + ValidadorCuit.formatear(cuit) + ")",
                request);

        // Sin correo de alta: el alta se resuelve en el acto y la respuesta HTTP
        // ya lo confirma. Un mail que solo dijera "se creo" no aporta nada que el
        // usuario no este viendo en pantalla (decision del usuario, ADR-14).

        log.info("Organizacion {} creada por el usuario {}, APROBADA y ACTIVA",
                organizacion.getIdPersonaJuridica(), usuario.getIdUsuario());

        return construirRespuesta(organizacion, ROL_ADMINISTRADOR,
                "Organizacion registrada y habilitada para publicar eventos.");
    }

    /**
     * Variante por email, para el camino publico de registro en un paso.
     *
     * Recibe el email en vez del objeto porque el usuario acaba de crearse en la
     * misma transaccion y quien llama tiene a mano su identificador, no la
     * entidad con los roles cargados.
     */
    @Transactional
    public OrganizacionResponse crearOrganizacionPara(String email,
                                                      OrganizacionRegistroRequest peticion,
                                                      HttpServletRequest request) {
        Usuario usuario = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        return crearOrganizacion(usuario, peticion, request);
    }

    // =====================================================================
    // CONSULTA
    // =====================================================================

    /** Organizaciones donde el usuario es miembro. */
    @Transactional(readOnly = true)
    public List<OrganizacionResponse> listarDeUsuario(String email) {
        Usuario usuario = usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));

        return personaJuridicaRepository.buscarPorMiembro(usuario.getIdUsuario()).stream()
                .map(pj -> construirRespuesta(pj, ROL_ADMINISTRADOR, null))
                .toList();
    }

    /**
     * Resuelve la organizacion bajo la que un usuario quiere publicar (RF-2.1).
     *
     * Devuelve null si {@code idPersonaJuridica} es null: publicar a titulo
     * personal es el caso normal y no es un error.
     *
     * DOS CONTROLES QUE NO SE PUEDEN SALTEAR
     *   - Membresia: sin ella, cualquiera podria publicar a nombre de una
     *     empresa ajena con solo mandar su id en el JSON.
     *   - Habilitacion: una organizacion en revision o rechazada todavia no
     *     probo ser quien dice; publicar con su nombre seria exactamente el
     *     fraude que la revision existe para evitar.
     */
    @Transactional(readOnly = true)
    public PersonaJuridica resolverOrganizacionHabilitada(Usuario usuario, Long idPersonaJuridica) {
        if (idPersonaJuridica == null) {
            return null;
        }

        MiembrosOrganizacion membresia = miembrosRepository
                .buscarMembresia(usuario.getIdUsuario(), idPersonaJuridica)
                // 404 y no 403: ver el javadoc de RecursoNoEncontradoException.
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la organizacion indicada entre las tuyas"));

        PersonaJuridica organizacion = membresia.getPersonaJuridica();

        if (!estaHabilitada(organizacion)) {
            throw new ReglaNegocioException(
                    "La organizacion todavia no esta habilitada para publicar eventos. "
                    + "Su alta esta en revision.");
        }

        return organizacion;
    }

    /** Habilitada = aprobada por moderacion Y activa segun sus propios miembros. */
    private boolean estaHabilitada(PersonaJuridica organizacion) {
        String sistema = organizacion.getEstadoPersonaJuridicaSistema() == null
                ? null
                : organizacion.getEstadoPersonaJuridicaSistema().getEstadoPersonaJuridicaSistema();

        String propio = organizacion.getEstadoPersonaJuridica() == null
                ? null
                : organizacion.getEstadoPersonaJuridica().getEstadoPersonaJuridica();

        return EstadoPersonaJuridicaSistemaNombre.APROBADO.name().equalsIgnoreCase(sistema)
                && EstadoPersonaJuridicaNombre.ACTIVO.name().equalsIgnoreCase(propio);
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private void moderarNombres(String razonSocial, String nombreFantasia,
                                Usuario usuario, HttpServletRequest request) {
        try {
            moderacionService.validar(razonSocial, "razonSocial");
            moderacionService.validar(nombreFantasia, "nombreFantasia");

        } catch (ContenidoInapropiadoException ex) {
            // En transaccion aparte: el alta se revierte al propagarse la
            // excepcion, y el rechazo tiene que quedar asentado igual.
            auditoriaService.registrarAparte(null,
                    AuditoriaService.ACCION_ALTA_ORGANIZACION_RECHAZADA,
                    "Alta de organizacion rechazada por moderacion de texto", request);
            throw ex;
        }
    }

    private Ubicacion persistirUbicacion(UbicacionRequest peticion) {
        Ciudad ciudad = ciudadRepository.findById(peticion.getIdCiudad())
                .orElseThrow(() -> new ReglaNegocioException(
                        "La ciudad indicada no existe en el catalogo"));

        Ubicacion ubicacion = new Ubicacion();
        ubicacion.setCalle(peticion.getCalle().trim());
        ubicacion.setNumeroExterior(peticion.getNumeroExterior().trim());
        ubicacion.setNumeroInterior(peticion.getNumeroInterior() == null
                ? null
                : peticion.getNumeroInterior().trim());
        ubicacion.setCiudad(ciudad);
        ubicacion.setLatitud(peticion.getLatitud());
        ubicacion.setLongitud(peticion.getLongitud());

        return ubicacionRepository.save(ubicacion);
    }

    private OrganizacionResponse construirRespuesta(PersonaJuridica organizacion,
                                                    String rolEnEmpresa, String mensaje) {
        return OrganizacionResponse.builder()
                .idPersonaJuridica(organizacion.getIdPersonaJuridica())
                .razonSocial(organizacion.getRazonSocial())
                .nombreFantasia(organizacion.getNombreFantasia())
                // Se guarda crudo y se muestra formateado.
                .cuit(ValidadorCuit.formatear(organizacion.getCuit()))
                .emailCorporativo(organizacion.getEmailCorporativo())
                .telefonoContacto(organizacion.getTelefonoContacto())
                .estadoSistema(organizacion.getEstadoPersonaJuridicaSistema() == null ? null
                        : organizacion.getEstadoPersonaJuridicaSistema().getEstadoPersonaJuridicaSistema())
                .estado(organizacion.getEstadoPersonaJuridica() == null ? null
                        : organizacion.getEstadoPersonaJuridica().getEstadoPersonaJuridica())
                .rolEnEmpresa(rolEnEmpresa)
                .fechaRegistro(organizacion.getFechaRegistro())
                .mensaje(mensaje)
                .build();
    }
}
