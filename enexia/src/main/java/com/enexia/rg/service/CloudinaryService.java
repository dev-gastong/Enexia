package com.enexia.rg.service;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Subida y analisis de imagenes promocionales (RF-2.3, RF-5.2, RF-5.3).
 *
 * QUE HACE Y EN QUE ORDEN
 *   1. Valida el archivo ANTES de tocar la red: extension JPG/PNG y peso <= 2MB
 *      (RF-5.2). Un archivo que no pasa esto no se sube: seria gastar ancho de
 *      banda y cuota de la cuenta para que el servidor remoto lo rechace igual.
 *   2. Sube a Cloudinary pidiendo, si esta configurado, el complemento de
 *      moderacion automatica (RF-5.3).
 *   3. Devuelve un veredicto: aprobada (con su URL) o rechazada (con motivo).
 *
 * MODO SIMULADO
 * Sin {@code enexia.cloudinary.cloud-name} configurado, la clase NO llama a la
 * red: valida formato y peso igual, y aprueba el resto devolviendo una URL
 * ficticia. Es lo que permite desarrollar y correr la suite de pruebas sin
 * credenciales ni conexion, sin que el resto del pipeline se entere de la
 * diferencia. Para poder probar el camino de rechazo, un nombre de archivo que
 * contenga "rechazar" se rechaza en modo simulado (ver {@link #esRechazoSimulado}).
 *
 * SOBRE LA MODERACION AUTOMATICA REAL
 * Cloudinary no analiza contenido por si solo: hace falta un complemento
 * (aws_rek_moderation, google_vision, etc.) habilitado en la cuenta. Si la
 * propiedad {@code enexia.cloudinary.moderacion} viene vacia, la imagen se sube
 * SIN analisis y se aprueba. Eso no es un descuido silencioso: se avisa por log
 * al arrancar, porque una moderacion que no modera y nadie sabe que no modera es
 * peor que no tenerla.
 */
@Service
@Slf4j
public class CloudinaryService {

    /**
     * Veredicto de una imagen.
     *
     * Es un record y no tres campos sueltos porque las tres cosas se producen
     * juntas y no tiene sentido tener una sin las otras: una URL sin veredicto no
     * dice si se puede publicar, y un rechazo sin motivo no se puede explicar.
     *
     * @param aprobada si la imagen puede publicarse
     * @param url      URL segura entregada por Cloudinary; null si se rechazo
     * @param publicId identificador remoto, necesario para poder borrarla despues
     * @param motivo   por que se rechazo; null si se aprobo
     */
    public record ResultadoImagen(boolean aprobada, String url, String publicId, String motivo) {

        public static ResultadoImagen aprobada(String url, String publicId) {
            return new ResultadoImagen(true, url, publicId, null);
        }

        public static ResultadoImagen rechazada(String motivo) {
            return new ResultadoImagen(false, null, null, motivo);
        }
    }

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String carpeta;
    private final String complementoModeracion;
    private final long maxBytes;

    /** Null en modo simulado. Es lo unico que distingue un modo del otro. */
    private Cloudinary cliente;

    public CloudinaryService(
            @Value("${enexia.cloudinary.cloud-name:}") String cloudName,
            @Value("${enexia.cloudinary.api-key:}") String apiKey,
            @Value("${enexia.cloudinary.api-secret:}") String apiSecret,
            @Value("${enexia.cloudinary.carpeta:enexia/eventos}") String carpeta,
            @Value("${enexia.cloudinary.moderacion:}") String complementoModeracion,
            @Value("${enexia.multimedia.max-bytes:2097152}") long maxBytes) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.carpeta = carpeta;
        this.complementoModeracion = complementoModeracion;
        this.maxBytes = maxBytes;
    }

    @PostConstruct
    void inicializar() {
        if (cloudName == null || cloudName.isBlank()) {
            log.warn("Cloudinary sin configurar: las imagenes NO se suben y se aprueban "
                    + "automaticamente (modo simulado). Definir CLOUDINARY_CLOUD_NAME para activarlo.");
            return;
        }

        this.cliente = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));

        if (complementoModeracion == null || complementoModeracion.isBlank()) {
            log.warn("Cloudinary activo pero SIN complemento de moderacion: las imagenes se suben "
                    + "y se aprueban sin analisis de contenido (RF-5.3 parcial).");
        } else {
            log.info("Cloudinary activo con moderacion '{}'.", complementoModeracion);
        }
    }

    /** true si hay credenciales y las subidas son reales. */
    public boolean estaActivo() {
        return cliente != null;
    }

    // =====================================================================
    // VALIDACION LOCAL  (RF-5.2, DFD 5.2)
    // =====================================================================

    /**
     * Paso 5.2.2: extension permitida.
     *
     * Se mira el NOMBRE del archivo y tambien deberia mirarse el content-type;
     * ninguno de los dos es prueba de nada (renombrar un .exe a .jpg es trivial),
     * pero como Cloudinary rechaza lo que no sea imagen al procesarla, este
     * control es un filtro barato de primera linea, no la garantia final.
     */
    public boolean formatoPermitido(String nombreArchivo) {
        if (nombreArchivo == null) {
            return false;
        }
        String minusculas = nombreArchivo.toLowerCase();
        return minusculas.endsWith(".jpg")
                || minusculas.endsWith(".jpeg")
                || minusculas.endsWith(".png");
    }

    /** Paso 5.2.3: peso maximo, 2MB por defecto (RF-5.2). */
    public boolean pesoPermitido(long bytes) {
        return bytes > 0 && bytes <= maxBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    // =====================================================================
    // SUBIDA Y MODERACION  (RF-2.3, RF-5.3, DFD 5.3)
    // =====================================================================

    /**
     * Valida, sube y devuelve el veredicto de una imagen.
     *
     * NUNCA PROPAGA EXCEPCIONES. Un fallo de red o de la API se traduce en un
     * rechazo con motivo tecnico, porque quien llama es el pipeline asincrono
     * que procesa varias imagenes en fila: si la primera lanzara, las demas no
     * se procesarian y un problema puntual de red se convertiria en el rechazo
     * de un evento entero.
     *
     * @param contenido     bytes del archivo, ya leidos del multipart
     * @param nombreArchivo nombre original, solo para validar extension y trazar
     */
    @SuppressWarnings("unchecked")
    public ResultadoImagen subirYModerar(byte[] contenido, String nombreArchivo) {

        if (contenido == null || contenido.length == 0) {
            return ResultadoImagen.rechazada("Archivo vacio");
        }
        if (!formatoPermitido(nombreArchivo)) {
            return ResultadoImagen.rechazada("Formato no permitido: solo JPG o PNG");
        }
        if (!pesoPermitido(contenido.length)) {
            return ResultadoImagen.rechazada(
                    "La imagen supera el maximo de " + (maxBytes / 1024 / 1024) + "MB");
        }

        if (!estaActivo()) {
            return simular(nombreArchivo);
        }

        try {
            Map<String, Object> opciones = ObjectUtils.asMap(
                    "folder", carpeta,
                    // Cloudinary genera el identificador: dejar que lo elija el
                    // cliente permitiria sobrescribir la imagen de otro evento
                    // mandando un public_id ajeno.
                    "resource_type", "image",
                    "overwrite", false);

            if (complementoModeracion != null && !complementoModeracion.isBlank()) {
                opciones.put("moderation", complementoModeracion);
            }

            // El SDK de Cloudinary devuelve un Map crudo: la conversion sin
            // comprobar es inevitable y por eso el metodo lleva @SuppressWarnings.
            Map<String, Object> respuesta = cliente.uploader().upload(contenido, opciones);

            String url = Objects.toString(respuesta.get("secure_url"), null);
            String publicId = Objects.toString(respuesta.get("public_id"), null);

            if (fueRechazadaPorModeracion(respuesta)) {
                // La imagen quedo subida pero no se puede publicar: se borra para
                // no dejar contenido inapropiado accesible por URL directa.
                borrarSilencioso(publicId);
                return ResultadoImagen.rechazada("Contenido sensible detectado por moderacion automatica");
            }

            return ResultadoImagen.aprobada(url, publicId);

        } catch (Exception ex) {
            // Se atrapa Exception y no solo IOException: el SDK de Cloudinary
            // envuelve fallos de red y de parseo en tipos propios que no
            // comparten una raiz util.
            log.error("Fallo la subida a Cloudinary de '{}'", nombreArchivo, ex);
            return ResultadoImagen.rechazada("No se pudo procesar la imagen (error tecnico)");
        }
    }

    /**
     * Lee el veredicto del complemento de moderacion.
     *
     * La respuesta trae {@code moderation: [{status: "approved"|"rejected"|"pending"}]}.
     * "pending" significa analisis asincrono todavia sin resolver: se trata como
     * APROBADA provisional, porque bloquear el evento esperando un veredicto que
     * puede tardar minutos anularia el sentido del pipeline. El repaso de los
     * pendientes corresponde al panel de moderacion manual (RF-6.1).
     */
    @SuppressWarnings("unchecked")
    private boolean fueRechazadaPorModeracion(Map<String, Object> respuesta) {
        Object moderacion = respuesta.get("moderation");
        if (!(moderacion instanceof Iterable<?> entradas)) {
            return false;
        }
        for (Object entrada : entradas) {
            if (entrada instanceof Map<?, ?> mapa) {
                String estado = Objects.toString(((Map<String, Object>) mapa).get("status"), "");
                if ("rejected".equalsIgnoreCase(estado)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Borra una imagen remota. Los fallos solo se loguean: es limpieza, no flujo principal. */
    public void borrarSilencioso(String publicId) {
        if (!estaActivo() || publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cliente.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception ex) {
            log.warn("No se pudo borrar la imagen '{}' de Cloudinary", publicId, ex);
        }
    }

    // =====================================================================
    // MODO SIMULADO
    // =====================================================================

    private ResultadoImagen simular(String nombreArchivo) {
        if (esRechazoSimulado(nombreArchivo)) {
            return ResultadoImagen.rechazada("Contenido sensible detectado (simulado)");
        }
        String publicId = carpeta + "/simulado-" + Math.abs(nombreArchivo.hashCode());
        return ResultadoImagen.aprobada("https://simulado.enexia.local/" + publicId + ".jpg", publicId);
    }

    /**
     * Gancho para probar el camino de rechazo sin credenciales.
     *
     * Sin esto, en modo simulado toda imagen se aprueba y la rama "todas las
     * imagenes rechazadas -> evento RECHAZADO_SISTEMA" quedaria sin probar en
     * toda la suite, que es justo la rama donde se esconden los errores.
     */
    private boolean esRechazoSimulado(String nombreArchivo) {
        return nombreArchivo.toLowerCase().contains("rechazar");
    }
}
