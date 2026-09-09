package com.enexia.rg.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.enexia.rg.dto.EventoDetalleResponse;
import com.enexia.rg.dto.EventoResponse;
import com.enexia.rg.repository.CategoriaRepository;
import com.enexia.rg.repository.CiudadRepository;
import com.enexia.rg.repository.ProvinciaRepository;
import com.enexia.rg.service.CatalogoPublicoService;

import lombok.RequiredArgsConstructor;

/**
 * Interfaz publica de Enexia (Modulo 4: RF-4.1 a RF-4.5).
 *
 * ES EL UNICO GRUPO DE ENDPOINTS QUE NO EXIGE TOKEN. SecurityConfig declara
 * {@code GET /api/publico/** permitAll()}, porque RF-4.1 pide un catalogo
 * "indexable y accesible de forma anonima": si exigiera autenticacion, ningun
 * buscador podria indexarlo y nadie veria un evento antes de registrarse, que es
 * justo al reves de lo que necesita una plataforma de difusion.
 *
 * EL JWT, SI VIENE, SE APROVECHA. El filtro de autenticacion corre igual sobre
 * estas rutas, asi que {@code Principal} llega poblado cuando hay sesion valida
 * y null cuando no. Eso alcanza para atribuir la visita (RF-4.5) sin bloquear al
 * visitante anonimo.
 *
 * Solo lecturas: no hay un solo POST aca. Todo lo que escribe pasa por endpoints
 * autenticados.
 */
@RestController
@RequestMapping("/api/publico")
@RequiredArgsConstructor
public class CatalogoPublicoController {

    private static final int TAMANO_PAGINA_DEFECTO = 12;
    private static final int TAMANO_PAGINA_MAXIMO = 50;

    private final CatalogoPublicoService catalogoService;
    private final CategoriaRepository categoriaRepository;
    private final ProvinciaRepository provinciaRepository;
    private final CiudadRepository ciudadRepository;

    /**
     * Catalogo con busqueda y filtros combinables (RF-4.1, RF-4.2, RF-4.3).
     *
     * Todos los parametros son opcionales: sin ninguno devuelve la primera
     * pagina del catalogo completo.
     *
     * El orden por defecto es {@code fechaCreacion} descendente y no por fecha
     * del evento, porque la fecha del evento vive en evento_cronograma y
     * ordenar por una tabla hija exigiria un JOIN con agregacion que rompe la
     * paginacion. La proxima fecha SI viaja en cada tarjeta, asi que el frontend
     * puede reordenar la pagina si le conviene.
     */
    @GetMapping("/eventos")
    public ResponseEntity<Page<EventoResponse>> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long idCategoria,
            @RequestParam(required = false) Long idProvincia,
            @RequestParam(required = false) Long idCiudad,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + TAMANO_PAGINA_DEFECTO) int tamano) {

        PageRequest paginado = PageRequest.of(
                Math.max(0, pagina),
                // Sin este techo, "?tamano=1000000" traeria la tabla entera a
                // memoria: una denegacion de servicio de un solo parametro sobre
                // el endpoint mas expuesto del sistema.
                Math.min(Math.max(1, tamano), TAMANO_PAGINA_MAXIMO),
                Sort.by(Sort.Direction.DESC, "fechaCreacion"));

        return ResponseEntity.ok(catalogoService.buscar(
                texto, idCategoria, idProvincia, idCiudad, desde, hasta, paginado));
    }

    /**
     * Ficha tecnica de un evento (RF-4.4) y registro de la visita (RF-4.5).
     *
     * {@code Principal} es null para un visitante anonimo: Spring lo inyecta asi
     * cuando no hay autenticacion, y ese null es informacion valida (visita
     * anonima), no un error.
     */
    @GetMapping("/eventos/{idEvento}")
    public ResponseEntity<EventoDetalleResponse> verFicha(@PathVariable Long idEvento,
                                                          Principal principal) {

        String email = principal == null ? null : principal.getName();
        return ResponseEntity.ok(catalogoService.verFicha(idEvento, email));
    }

    /**
     * Categorias para el panel de filtros (RF-4.3).
     *
     * Devuelve un DTO y NO la entidad {@code Categoria}, por la misma regla que
     * rige todo el proyecto: una entidad JPA en la respuesta acopla el contrato
     * publico de la API al esquema de la base (renombrar una columna rompe a los
     * clientes), expone cualquier campo que se agregue despues sin decidirlo, y
     * puede disparar carga perezosa en plena serializacion.
     */
    @GetMapping("/categorias")
    public ResponseEntity<List<OpcionCatalogo>> categorias() {
        List<OpcionCatalogo> opciones = categoriaRepository.findAllByOrderByNombreCategoriaAsc()
                .stream()
                .map(c -> new OpcionCatalogo(c.getIdCategoria(), c.getNombreCategoria()))
                .toList();

        return ResponseEntity.ok(opciones);
    }

    /**
     * Provincias, primer nivel del selector en cascada (RF-4.3).
     *
     * Devuelve un DTO minimo y no la entidad: Provincia arrastra la relacion con
     * Pais, y serializar la entidad tal cual expondria la estructura interna y
     * podria disparar carga perezosa en plena serializacion.
     */
    @GetMapping("/provincias")
    public ResponseEntity<List<OpcionCatalogo>> provincias() {
        List<OpcionCatalogo> opciones = provinciaRepository.listarConPais().stream()
                .map(p -> new OpcionCatalogo(p.getIdProvincia(), p.getNombre()))
                .toList();

        return ResponseEntity.ok(opciones);
    }

    /** Ciudades de una provincia, segundo nivel de la cascada (RF-4.3). */
    @GetMapping("/provincias/{idProvincia}/ciudades")
    public ResponseEntity<List<OpcionCatalogo>> ciudades(@PathVariable Long idProvincia) {
        List<OpcionCatalogo> opciones = ciudadRepository.buscarPorProvincia(idProvincia).stream()
                .map(c -> new OpcionCatalogo(c.getIdCiudad(), c.getNombre()))
                .toList();

        return ResponseEntity.ok(opciones);
    }

    /**
     * Par id/nombre para poblar un {@code <select>}.
     *
     * Lo comparten categorias, provincias y ciudades: los tres selectores del
     * panel de filtros necesitan exactamente lo mismo, y tres DTOs identicos con
     * nombres distintos serian ruido.
     *
     * Es un record anidado y no una clase en dto/ porque no se usa fuera de este
     * controller: sacarlo de aca solo agregaria un archivo que hay que ir a buscar.
     */
    public record OpcionCatalogo(Long id, String nombre) {
    }
}
