package com.enexia.rg.controller;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.enexia.rg.dto.EventoCrearRequest;
import com.enexia.rg.dto.EventoEstadisticasResponse;
import com.enexia.rg.dto.EventoResponse;
import com.enexia.rg.exception.ReglaNegocioException;
import com.enexia.rg.service.EventoService;
import com.enexia.rg.service.ImagenPendiente;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Panel del organizador (Modulo 2: RF-2.1 a RF-2.10).
 *
 * Cuelga de {@code /api/organizador/**}, prefijo que SecurityConfig ya restringe
 * a {@code hasRole("ORGANIZADOR")}. Los {@code @PreAuthorize} son una segunda
 * capa deliberada: si manana se reorganizan las rutas, el endpoint sigue
 * protegido por si mismo.
 */
@RestController
@RequestMapping("/api/organizador/eventos")
@RequiredArgsConstructor
public class EventoOrganizadorController {

    /** Tamano de pagina por defecto del dashboard. */
    private static final int TAMANO_PAGINA_DEFECTO = 12;

    /** Techo duro: sin el, "?tamano=100000" seria una denegacion de servicio de una linea. */
    private static final int TAMANO_PAGINA_MAXIMO = 50;

    private final EventoService eventoService;

    /**
     * Crea un evento (RF-2.1 a RF-2.6).
     *
     * POR QUE MULTIPART Y NO JSON PURO
     * RF-2.2 pide que el organizador cargue "el formulario completo en una unica
     * peticion", imagenes incluidas. Multipart permite mandar el JSON de datos y
     * los archivos binarios juntos sin inflar nada; con Base64 dentro del JSON,
     * cada imagen creceria un 33% y habria que tenerla dos veces en memoria.
     *
     * RESPONDE 202 ACCEPTED, NO 201 CREATED. Es la diferencia que marca el DFD:
     * el evento quedo ACEPTADO y esta EN_PROCESO, pero todavia no esta publicado.
     * Un 201 le diria al cliente que el recurso ya esta completo y disponible, y
     * no lo esta: falta la moderacion asincrona.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ORGANIZADOR')")
    public ResponseEntity<EventoResponse> crear(
            @Valid @RequestPart("datos") EventoCrearRequest datos,
            @RequestPart(value = "imagenes", required = false) List<MultipartFile> imagenes,
            Principal principal,
            HttpServletRequest request) {

        EventoResponse respuesta = eventoService.crear(
                principal.getName(), datos, leerImagenes(imagenes), request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(respuesta);
    }

    /**
     * Copia los bytes de cada archivo ANTES de que termine la peticion.
     *
     * Es obligatorio: el pipeline de moderacion corre en otro hilo y para
     * entonces el contenedor ya borro los archivos temporales del multipart.
     * Leerlos ahi lanzaria IOException por un archivo inexistente. Ver
     * ImagenPendiente.
     */
    private List<ImagenPendiente> leerImagenes(List<MultipartFile> archivos) {
        List<ImagenPendiente> pendientes = new ArrayList<>();

        if (archivos == null) {
            return pendientes;
        }

        for (MultipartFile archivo : archivos) {
            if (archivo == null || archivo.isEmpty()) {
                continue;
            }
            try {
                pendientes.add(new ImagenPendiente(
                        archivo.getOriginalFilename(), archivo.getBytes()));

            } catch (IOException ex) {
                // Se traduce a una regla de negocio (400) y no se deja escapar
                // como 500: el archivo llego mal, no es una falla del servidor.
                throw new ReglaNegocioException(
                        "No se pudo leer la imagen '" + archivo.getOriginalFilename() + "'");
            }
        }
        return pendientes;
    }

    /**
     * Dashboard paginado con busqueda y filtros (RF-2.8).
     *
     * Ordena por fecha de creacion descendente: lo ultimo que cargo el
     * organizador es lo que mas probablemente venga a mirar.
     */
    @GetMapping
    @PreAuthorize("hasRole('ORGANIZADOR')")
    public ResponseEntity<Page<EventoResponse>> listar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long idCategoria,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + TAMANO_PAGINA_DEFECTO) int tamano,
            Principal principal) {

        PageRequest paginado = PageRequest.of(
                Math.max(0, pagina),
                Math.min(Math.max(1, tamano), TAMANO_PAGINA_MAXIMO),
                Sort.by(Sort.Direction.DESC, "fechaCreacion"));

        return ResponseEntity.ok(eventoService.listarDeOrganizador(
                principal.getName(), texto, idCategoria, estado, paginado));
    }

    /**
     * Baja logica (RF-2.9).
     *
     * DELETE por semantica HTTP, pero NO borra: muta el estado a DADO_DE_BAJA.
     * El verbo describe la intencion del cliente ("quiero que deje de estar"),
     * no la operacion de base de datos.
     */
    @DeleteMapping("/{idEvento}")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    public ResponseEntity<EventoResponse> darDeBaja(@PathVariable Long idEvento,
                                                    Principal principal,
                                                    HttpServletRequest request) {

        return ResponseEntity.ok(eventoService.darDeBaja(principal.getName(), idEvento, request));
    }

    /** Metricas de rendimiento del evento (RF-2.10). */
    @GetMapping("/{idEvento}/estadisticas")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    public ResponseEntity<EventoEstadisticasResponse> estadisticas(@PathVariable Long idEvento,
                                                                   Principal principal) {

        return ResponseEntity.ok(eventoService.estadisticas(principal.getName(), idEvento));
    }
}
