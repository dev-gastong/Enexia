package com.enexia.rg.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.enexia.rg.model.EstadoUsuarioNombre;
import com.enexia.rg.model.Rol;
import com.enexia.rg.model.RolNombre;
import com.enexia.rg.model.UsuarioEstado;
import com.enexia.rg.repository.RolRepository;
import com.enexia.rg.repository.UsuarioEstadoRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Precarga los catalogos que el modulo de autenticacion necesita para funcionar.
 *
 * POR QUE HACE FALTA
 * Roles y estados viven en tablas, no en enums de Java. Con
 * {@code ddl-auto=update} Hibernate crea las tablas vacias, asi que en una base
 * recien creada el primer registro fallaria: no existiria la fila ACTIVO que
 * hay que asignarle al usuario, ni el rol PARTICIPANTE.
 *
 * Es idempotente: antes de insertar cada fila verifica si ya esta. Puede correr
 * en cada arranque sin duplicar nada.
 *
 * {@code CommandLineRunner} se ejecuta una vez, despues de que el contexto de
 * Spring termino de levantar (y por lo tanto despues de que Hibernate creo o
 * actualizo el esquema).
 */
@Configuration
@Slf4j
public class DatosInicialesConfig {

    @Bean
    public CommandLineRunner cargarCatalogosDeAutenticacion(
            RolRepository rolRepository,
            UsuarioEstadoRepository usuarioEstadoRepository) {

        return args -> {
            int rolesCreados = 0;
            for (RolNombre nombre : RolNombre.values()) {
                if (rolRepository.findByNombreRolIgnoreCase(nombre.name()).isEmpty()) {
                    Rol rol = new Rol();
                    rol.setNombreRol(nombre.name());
                    rolRepository.save(rol);
                    rolesCreados++;
                }
            }

            int estadosCreados = 0;
            for (EstadoUsuarioNombre nombre : EstadoUsuarioNombre.values()) {
                if (usuarioEstadoRepository.findByEstadoUsuarioIgnoreCase(nombre.name()).isEmpty()) {
                    UsuarioEstado estado = new UsuarioEstado();
                    estado.setEstadoUsuario(nombre.name());
                    usuarioEstadoRepository.save(estado);
                    estadosCreados++;
                }
            }

            if (rolesCreados > 0 || estadosCreados > 0) {
                log.info("Catalogos inicializados: {} roles y {} estados de usuario nuevos.",
                        rolesCreados, estadosCreados);
            }
        };
    }
}
