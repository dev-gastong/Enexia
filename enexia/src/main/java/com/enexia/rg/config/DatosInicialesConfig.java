package com.enexia.rg.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.enexia.rg.model.Categoria;
import com.enexia.rg.model.Ciudad;
import com.enexia.rg.model.EstadoEventoOrganizadorNombre;
import com.enexia.rg.model.EstadoEventoSistemaNombre;
import com.enexia.rg.model.EstadoPersonaJuridicaNombre;
import com.enexia.rg.model.EstadoPersonaJuridicaSistemaNombre;
import com.enexia.rg.model.EstadoUsuarioNombre;
import com.enexia.rg.model.EventoEstadoOrganizador;
import com.enexia.rg.model.EventoEstadoSistema;
import com.enexia.rg.model.MotivoModeracionEvento;
import com.enexia.rg.model.Pais;
import com.enexia.rg.model.PersonaJuridicaEstado;
import com.enexia.rg.model.PersonaJuridicaEstadoSistema;
import com.enexia.rg.model.Provincia;
import com.enexia.rg.model.Rol;
import com.enexia.rg.model.RolNombre;
import com.enexia.rg.model.TipoTicket;
import com.enexia.rg.model.UsuarioEstado;
import com.enexia.rg.repository.CategoriaRepository;
import com.enexia.rg.repository.CiudadRepository;
import com.enexia.rg.repository.EventoEstadoOrganizadorRepository;
import com.enexia.rg.repository.EventoEstadoSistemaRepository;
import com.enexia.rg.repository.PaisRepository;
import com.enexia.rg.repository.PersonaJuridicaEstadoRepository;
import com.enexia.rg.repository.PersonaJuridicaEstadoSistemaRepository;
import com.enexia.rg.repository.ProvinciaRepository;
import com.enexia.rg.repository.RolRepository;
import com.enexia.rg.repository.TipoTicketRepository;
import com.enexia.rg.repository.UsuarioEstadoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Precarga los catalogos que el sistema necesita para funcionar.
 *
 * POR QUE HACE FALTA
 * Roles, estados, categorias y geografia viven en tablas, no en enums de Java.
 * Con {@code ddl-auto=update} Hibernate crea las tablas VACIAS, asi que en una
 * base recien creada el primer registro fallaria: no existiria la fila ACTIVO
 * que asignarle al usuario, ni el rol PARTICIPANTE, ni el estado EN_PROCESO con
 * el que nace un evento.
 *
 * IDEMPOTENTE: antes de insertar cada fila verifica si ya esta. Puede correr en
 * cada arranque sin duplicar nada y sin pisar datos editados a mano (por ejemplo
 * una categoria que un administrador renombro).
 *
 * {@code CommandLineRunner} se ejecuta una vez, despues de que el contexto de
 * Spring termino de levantar y por lo tanto despues de que Hibernate creo o
 * actualizo el esquema.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatosInicialesConfig {

    /**
     * Categorias iniciales, tomadas del dominio que declara el proyecto:
     * eventos culturales, educativos, sociales, deportivos y de gaming.
     * Un administrador puede ampliarlas despues (RF-6.3).
     */
    private static final List<String> CATEGORIAS = List.of(
            "Cultural", "Educativo", "Social", "Deportivo", "Gaming",
            "Musica", "Gastronomia", "Tecnologia");

    /**
     * Tipos de ticket de arranque (RF-2.5). El catalogo crece solo: si un
     * organizador nombra un tipo que no existe, el service lo da de alta.
     */
    private static final List<String> TIPOS_TICKET = List.of(
            "Inscripcion General", "Pase VIP", "Preventa", "Entrada Libre", "Estudiante");

    /**
     * Geografia minima para que los selectores en cascada de RF-4.3 y el
     * domicilio fiscal de RF-7.2 tengan de donde elegir.
     *
     * Se siembra Tierra del Fuego completa (es el ambito del proyecto) y las
     * cabeceras de las provincias vecinas para no dejar el selector con una sola
     * opcion. No pretende ser un padron: es un piso operativo.
     */
    private static final Map<String, List<String>> GEOGRAFIA = Map.of(
            "Tierra del Fuego", List.of("Ushuaia", "Rio Grande", "Tolhuin"),
            "Santa Cruz", List.of("Rio Gallegos", "El Calafate", "Caleta Olivia"),
            "Chubut", List.of("Rawson", "Comodoro Rivadavia", "Puerto Madryn"),
            "Buenos Aires", List.of("La Plata", "Mar del Plata", "Bahia Blanca"),
            "Cordoba", List.of("Cordoba", "Villa Carlos Paz", "Rio Cuarto"));

    private static final String ID_PAIS_ARGENTINA = "AR";

    private final RolRepository rolRepository;
    private final UsuarioEstadoRepository usuarioEstadoRepository;
    private final PersonaJuridicaEstadoRepository personaJuridicaEstadoRepository;
    private final PersonaJuridicaEstadoSistemaRepository personaJuridicaEstadoSistemaRepository;
    private final EventoEstadoSistemaRepository eventoEstadoSistemaRepository;
    private final EventoEstadoOrganizadorRepository eventoEstadoOrganizadorRepository;
    private final CategoriaRepository categoriaRepository;
    private final TipoTicketRepository tipoTicketRepository;
    private final PaisRepository paisRepository;
    private final ProvinciaRepository provinciaRepository;
    private final CiudadRepository ciudadRepository;

    @Bean
    public CommandLineRunner cargarCatalogos() {
        return args -> {
            int creados = 0;

            creados += cargarRoles();
            creados += cargarEstadosDeUsuario();
            creados += cargarEstadosDeOrganizacion();
            creados += cargarEstadosDeEvento();
            creados += cargarCategorias();
            creados += cargarTiposDeTicket();
            creados += cargarGeografia();

            if (creados > 0) {
                log.info("Catalogos inicializados: {} filas nuevas.", creados);
            }
        };
    }

    // =====================================================================
    // Modulo 1 - Autenticacion
    // =====================================================================

    private int cargarRoles() {
        int creados = 0;
        for (RolNombre nombre : RolNombre.values()) {
            if (rolRepository.findByNombreRolIgnoreCase(nombre.name()).isEmpty()) {
                Rol rol = new Rol();
                rol.setNombreRol(nombre.name());
                rolRepository.save(rol);
                creados++;
            }
        }
        return creados;
    }

    private int cargarEstadosDeUsuario() {
        int creados = 0;
        for (EstadoUsuarioNombre nombre : EstadoUsuarioNombre.values()) {
            if (usuarioEstadoRepository.findByEstadoUsuarioIgnoreCase(nombre.name()).isEmpty()) {
                UsuarioEstado estado = new UsuarioEstado();
                estado.setEstadoUsuario(nombre.name());
                usuarioEstadoRepository.save(estado);
                creados++;
            }
        }
        return creados;
    }

    // =====================================================================
    // Modulo 7 - Organizaciones
    // =====================================================================

    /**
     * Estados de Persona Juridica.
     *
     * Son DOS catalogos independientes y ambos hacen falta desde el primer alta:
     * una organizacion nace {@code REVISION_PENDIENTE} a nivel sistema (la mira
     * un moderador) e {@code INACTIVO} a nivel propio (todavia no opera). Sin
     * estas filas, el registro de organizacion falla en el primer intento.
     */
    private int cargarEstadosDeOrganizacion() {
        int creados = 0;

        for (EstadoPersonaJuridicaSistemaNombre nombre : EstadoPersonaJuridicaSistemaNombre.values()) {
            if (personaJuridicaEstadoSistemaRepository
                    .findByEstadoPersonaJuridicaSistemaIgnoreCase(nombre.name()).isEmpty()) {
                PersonaJuridicaEstadoSistema estado = new PersonaJuridicaEstadoSistema();
                estado.setEstadoPersonaJuridicaSistema(nombre.name());
                personaJuridicaEstadoSistemaRepository.save(estado);
                creados++;
            }
        }

        for (EstadoPersonaJuridicaNombre nombre : EstadoPersonaJuridicaNombre.values()) {
            if (personaJuridicaEstadoRepository
                    .findByEstadoPersonaJuridicaIgnoreCase(nombre.name()).isEmpty()) {
                PersonaJuridicaEstado estado = new PersonaJuridicaEstado();
                estado.setEstadoPersonaJuridica(nombre.name());
                personaJuridicaEstadoRepository.save(estado);
                creados++;
            }
        }

        return creados;
    }

    // =====================================================================
    // Modulo 2 - Eventos
    // =====================================================================

    /**
     * Estados de evento.
     *
     * DETALLE IMPORTANTE DEL MODELO: el MER pone {@code motivo_codigo} en el
     * catalogo {@code evento_estado_sistema}, no en la tabla {@code evento}. La
     * consecuencia es que RECHAZADO_SISTEMA necesita UNA FILA POR MOTIVO
     * (texto, imagen, sin imagenes validas, error tecnico). El evento apunta a la
     * fila que corresponde y asi queda registrado el porque del rechazo sin
     * agregar columnas fuera del MER.
     *
     * Los estados que no tienen motivo (EN_PROCESO, APROBADO_*) llevan
     * {@code motivo_codigo = null}.
     */
    private int cargarEstadosDeEvento() {
        int creados = 0;

        for (EstadoEventoSistemaNombre nombre : EstadoEventoSistemaNombre.values()) {
            creados += crearEstadoSistemaSiFalta(nombre.name(), null);
        }

        // Una fila extra por cada motivo de rechazo automatico.
        for (MotivoModeracionEvento motivo : MotivoModeracionEvento.values()) {
            creados += crearEstadoSistemaSiFalta(
                    EstadoEventoSistemaNombre.RECHAZADO_SISTEMA.name(), motivo.name());
        }

        for (EstadoEventoOrganizadorNombre nombre : EstadoEventoOrganizadorNombre.values()) {
            if (eventoEstadoOrganizadorRepository
                    .findByEstadoOrganizadorIgnoreCase(nombre.name()).isEmpty()) {
                EventoEstadoOrganizador estado = new EventoEstadoOrganizador();
                estado.setEstadoOrganizador(nombre.name());
                eventoEstadoOrganizadorRepository.save(estado);
                creados++;
            }
        }

        return creados;
    }

    private int crearEstadoSistemaSiFalta(String estado, String motivo) {
        if (eventoEstadoSistemaRepository.buscarPorEstadoYMotivo(estado, motivo).isPresent()) {
            return 0;
        }
        EventoEstadoSistema fila = new EventoEstadoSistema();
        fila.setEstadoSistema(estado);
        fila.setMotivoCodigo(motivo);
        eventoEstadoSistemaRepository.save(fila);
        return 1;
    }

    private int cargarCategorias() {
        int creados = 0;
        for (String nombre : CATEGORIAS) {
            if (categoriaRepository.findByNombreCategoriaIgnoreCase(nombre).isEmpty()) {
                Categoria categoria = new Categoria();
                categoria.setNombreCategoria(nombre);
                categoriaRepository.save(categoria);
                creados++;
            }
        }
        return creados;
    }

    private int cargarTiposDeTicket() {
        int creados = 0;
        for (String nombre : TIPOS_TICKET) {
            if (tipoTicketRepository.findByNombreIgnoreCase(nombre).isEmpty()) {
                TipoTicket tipo = new TipoTicket();
                tipo.setNombre(nombre);
                tipoTicketRepository.save(tipo);
                creados++;
            }
        }
        return creados;
    }

    // =====================================================================
    // Modulo 4 - Geografia (filtros en cascada)
    // =====================================================================

    private int cargarGeografia() {
        int creados = 0;

        Pais argentina = paisRepository.findById(ID_PAIS_ARGENTINA).orElse(null);
        if (argentina == null) {
            argentina = new Pais();
            // Pais usa el codigo ISO como PK (String), no un autoincremental:
            // por eso se asigna a mano.
            argentina.setIdPais(ID_PAIS_ARGENTINA);
            argentina.setNombre("Argentina");
            argentina = paisRepository.save(argentina);
            creados++;
        }

        for (Map.Entry<String, List<String>> entrada : GEOGRAFIA.entrySet()) {
            Provincia provincia = provinciaRepository.findByNombreIgnoreCase(entrada.getKey())
                    .orElse(null);

            if (provincia == null) {
                provincia = new Provincia();
                provincia.setNombre(entrada.getKey());
                provincia.setPais(argentina);
                provincia = provinciaRepository.save(provincia);
                creados++;
            }

            for (String nombreCiudad : entrada.getValue()) {
                if (ciudadRepository.findByNombreIgnoreCase(nombreCiudad).isEmpty()) {
                    Ciudad ciudad = new Ciudad();
                    ciudad.setNombre(nombreCiudad);
                    ciudad.setProvincia(provincia);
                    ciudadRepository.save(ciudad);
                    creados++;
                }
            }
        }

        return creados;
    }
}
