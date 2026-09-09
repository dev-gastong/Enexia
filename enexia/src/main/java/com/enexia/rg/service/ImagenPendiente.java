package com.enexia.rg.service;

/**
 * Una imagen recibida en el formulario, todavia sin moderar (RF-2.2 Fase 2).
 *
 * POR QUE LOS BYTES Y NO EL MultipartFile
 * El pipeline de moderacion corre en OTRO HILO y despues de que la peticion HTTP
 * termino. Para entonces el contenedor ya libero el {@code MultipartFile}: su
 * archivo temporal fue borrado y leerlo lanza excepcion. Copiar los bytes al
 * recibir la peticion es lo unico que permite procesarlos despues.
 *
 * COSTO ASUMIDO: como maximo 3 imagenes de 2MB = 6MB en memoria por evento en
 * curso, y el pool de AsyncConfig acota cuantos hay a la vez. Si el volumen
 * creciera, el reemplazo natural es guardar el archivo en disco temporal y pasar
 * la ruta en vez del contenido.
 *
 * @param nombreArchivo nombre original; de el sale la validacion de extension
 * @param contenido     bytes del archivo
 */
public record ImagenPendiente(String nombreArchivo, byte[] contenido) {

    public long tamanoBytes() {
        return contenido == null ? 0 : contenido.length;
    }
}
