package com.enexia.rg.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.dto.AdminCategoriaEstadoRequest;
import com.enexia.rg.dto.AdminCategoriaOperacionResponse;
import com.enexia.rg.dto.AdminCategoriaRequest;
import com.enexia.rg.dto.AdminCategoriaResponse;
import com.enexia.rg.dto.AdminCategoriasListadoResponse;
import com.enexia.rg.exception.OperacionNoPermitidaException;
import com.enexia.rg.exception.RecursoDuplicadoException;
import com.enexia.rg.exception.RecursoNoEncontradoException;
import com.enexia.rg.model.Categoria;
import com.enexia.rg.model.EstadoEventoOrganizadorNombre;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.CategoriaRepository;
import com.enexia.rg.repository.EventoRepository;
import com.enexia.rg.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Modulo 6: ABM de categorias (RF-6.3).
 *
 * LA "BAJA" ES LOGICA. El requisito pide evitar registros huerfanos al dar de
 * baja una categoria; borrar la fila con eventos colgando los dejaria sin
 * clasificacion, y negarse mientras tenga eventos la volveria imposible de
 * retirar (casi siempre tiene). Desactivarla resuelve ambas: deja de
 * ofrecerse para eventos nuevos y en los filtros del catalogo, y los eventos
 * existentes conservan su categoria. Es reversible (ACTIVAR).
 *
 * Reglas:
 *   - El nombre es unico sin distinguir mayusculas ni espacios de mas.
 *   - No se puede desactivar la ULTIMA categoria activa: los organizadores se
 *     quedarian sin ninguna opcion y no podrian crear eventos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCategoriaService {

    /** Texto de la confirmacion "Guardar categoria" (alta y edicion comparten modal). */
    static final String MENSAJE_GUARDADA =
            "Los cambios en la categoría han sido guardados correctamente.";

    private final CategoriaRepository categoriaRepository;
    private final EventoRepository eventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public AdminCategoriasListadoResponse listar() {
        Map<Long, Long> publicados = contar(true);
        Map<Long, Long> totales = contar(false);

        List<Categoria> categorias = categoriaRepository.findAllByOrderByNombreCategoriaAsc();

        List<AdminCategoriaResponse> filas = categorias.stream()
                .map(c -> aRespuesta(c, publicados, totales))
                .toList();

        long eventosTotales = totales.values().stream().mapToLong(Long::longValue).sum();
        long activas = categorias.stream().filter(Categoria::isActiva).count();

        return new AdminCategoriasListadoResponse(categorias.size(), activas, eventosTotales, filas);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminCategoriaOperacionResponse crear(String emailAdmin, AdminCategoriaRequest datos,
                                                 HttpServletRequest request) {
        Usuario administrador = administrador(emailAdmin);
        String nombre = normalizar(datos.getNombre());

        if (categoriaRepository.findByNombreCategoriaIgnoreCase(nombre).isPresent()) {
            throw new RecursoDuplicadoException("Ya existe una categoria con ese nombre");
        }

        Categoria categoria = new Categoria();
        categoria.setNombreCategoria(nombre);
        categoria.setActiva(true);
        categoria = categoriaRepository.save(categoria);

        auditoriaService.registrar(administrador, AuditoriaService.ACCION_CATEGORIA_CREADA,
                "Categoria " + categoria.getIdCategoria() + ": '" + nombre + "'", request);

        return new AdminCategoriaOperacionResponse(
                MENSAJE_GUARDADA,
                new AdminCategoriaResponse(categoria.getIdCategoria(), nombre, true, 0, 0));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminCategoriaOperacionResponse renombrar(String emailAdmin, Long idCategoria,
                                                     AdminCategoriaRequest datos,
                                                     HttpServletRequest request) {
        Usuario administrador = administrador(emailAdmin);
        Categoria categoria = categoriaRepository.findById(idCategoria)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la categoria"));

        String nombre = normalizar(datos.getNombre());

        if (categoriaRepository.existsByNombreCategoriaIgnoreCaseAndIdCategoriaNot(nombre, idCategoria)) {
            throw new RecursoDuplicadoException("Ya existe una categoria con ese nombre");
        }

        String anterior = categoria.getNombreCategoria();
        categoria.setNombreCategoria(nombre);
        categoriaRepository.save(categoria);

        auditoriaService.registrar(administrador, AuditoriaService.ACCION_CATEGORIA_RENOMBRADA,
                "Categoria " + idCategoria + ": '" + anterior + "' -> '" + nombre + "'", request);

        return new AdminCategoriaOperacionResponse(
                MENSAJE_GUARDADA, conConteos(categoria));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminCategoriaOperacionResponse cambiarEstado(String emailAdmin, Long idCategoria,
                                                         AdminCategoriaEstadoRequest datos,
                                                         HttpServletRequest request) {
        Usuario administrador = administrador(emailAdmin);
        Categoria categoria = categoriaRepository.findById(idCategoria)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la categoria"));

        boolean desactivar = "DESACTIVAR".equals(datos.getAccion());

        if (categoria.isActiva() == !desactivar) {
            throw new OperacionNoPermitidaException(desactivar
                    ? "La categoria ya esta desactivada"
                    : "La categoria ya esta activa");
        }

        if (desactivar && categoriaRepository.countByActivaTrue() <= 1) {
            throw new OperacionNoPermitidaException(
                    "No se puede desactivar la ultima categoria activa: los organizadores "
                    + "no tendrian donde clasificar sus eventos");
        }

        categoria.setActiva(!desactivar);
        categoriaRepository.save(categoria);

        auditoriaService.registrar(administrador,
                desactivar ? AuditoriaService.ACCION_CATEGORIA_DESACTIVADA
                           : AuditoriaService.ACCION_CATEGORIA_ACTIVADA,
                "Categoria " + idCategoria + " '" + categoria.getNombreCategoria() + "'", request);

        log.info("Categoria {} {} por el administrador {}", idCategoria,
                desactivar ? "desactivada" : "activada", administrador.getIdUsuario());

        return new AdminCategoriaOperacionResponse(
                desactivar ? "Categoría desactivada. Ya no se ofrece para nuevos eventos."
                           : "Categoría activada. Ya se ofrece para nuevos eventos.",
                conConteos(categoria));
    }

    // ------------------------------------------------------------------ util

    private Usuario administrador(String email) {
        return usuarioRepository.buscarActivoPorEmailConRoles(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la cuenta"));
    }

    /** Recorta y colapsa espacios: "  Arte   Joven " y "Arte Joven" son el mismo nombre. */
    private String normalizar(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ");
    }

    private Map<Long, Long> contar(boolean soloVisibles) {
        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] fila : eventoRepository.contarPorCategoria(
                soloVisibles,
                EstadoEventoOrganizadorNombre.DADO_DE_BAJA.name(),
                EstadoEventoOrganizadorNombre.PUBLICADO.name(),
                EventoRepository.ESTADOS_VISIBLES)) {
            mapa.put((Long) fila[0], (Long) fila[1]);
        }
        return mapa;
    }

    private AdminCategoriaResponse aRespuesta(Categoria c, Map<Long, Long> publicados, Map<Long, Long> totales) {
        return new AdminCategoriaResponse(c.getIdCategoria(), c.getNombreCategoria(), c.isActiva(),
                publicados.getOrDefault(c.getIdCategoria(), 0L),
                totales.getOrDefault(c.getIdCategoria(), 0L));
    }

    private AdminCategoriaResponse conConteos(Categoria c) {
        return aRespuesta(c, contar(true), contar(false));
    }
}
