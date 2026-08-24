package com.enexia.rg.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.enexia.rg.exception.ContenidoInapropiadoException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Filtro de lenguaje ofensivo para campos de texto libre (DFD Registro 1.1.1A).
 *
 * NOTA SOBRE LA LIBRERIA
 * CLAUDE.md menciona "better-profanity" como libreria Java. En realidad
 * better-profanity es un paquete de Python y no tiene equivalente publicado
 * para Java, asi que el filtro se implementa aca. Cumple igual los criterios
 * que pedia el documento: gratuito, liviano y offline (sin llamadas de red,
 * asi no agrega latencia ni un tercero del que depender).
 *
 * COMO EVITA LAS EVASIONES MAS COMUNES
 * Comparar el texto tal cual llega es inutil: "PUTO", "puto", "putO" y "pu7o"
 * pasarian todos. Por eso el texto se normaliza antes de comparar:
 *
 *   "P.U.T.0"  ->  minusculas   ->  "p.u.t.0"
 *              ->  sin acentos  ->  "p.u.t.0"
 *              ->  sin l33t     ->  "p.u.t.o"
 *              ->  solo letras  ->  "puto"      -> coincide
 *
 * LIMITACION CONOCIDA (problema Scunthorpe)
 * La busqueda por subcadena produce falsos positivos: un apellido legitimo
 * puede contener un termino de la lista. Se mitiga comparando primero palabras
 * completas y recurriendo a la subcadena solo en campos sin espacios como el
 * nickname, donde "xXpu7oXx" seria indetectable de otro modo.
 */
@Service
@Slf4j
public class ModeracionTextoService {

    private static final String ARCHIVO_TERMINOS = "moderacion/terminos-bloqueados.txt";

    /**
     * Sustituciones l33t: cada caracter de la izquierda se reemplaza por el de
     * la derecha antes de comparar. Los dos arreglos deben mantenerse alineados.
     */
    private static final char[] LEET_ORIGEN  = {'0', '1', '3', '4', '5', '7', '@', '$', '!', '|'};
    private static final char[] LEET_DESTINO = {'o', 'i', 'e', 'a', 's', 't', 'a', 's', 'i', 'i'};

    /**
     * Terminos ya normalizados. Es un HashSet y no una List porque la busqueda
     * pasa a ser O(1) en vez de O(n), y este metodo corre en cada registro.
     */
    private Set<String> terminosBloqueados = Set.of();

    /**
     * Carga la lista al arrancar la aplicacion.
     *
     * {@code @PostConstruct} corre una sola vez, despues de que Spring construye
     * el bean. Leer el archivo en cada validacion seria un acceso a disco por
     * request sin ningun beneficio.
     */
    @PostConstruct
    public void cargarTerminos() {
        Set<String> terminos = new HashSet<>();

        try (InputStream entrada = new ClassPathResource(ARCHIVO_TERMINOS).getInputStream();
             BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {

            String linea;
            while ((linea = lector.readLine()) != null) {
                String limpia = linea.trim();
                if (limpia.isEmpty() || limpia.startsWith("#")) {
                    continue;
                }
                // Se normaliza tambien la lista: si el archivo y el texto de
                // entrada no pasan por el mismo proceso, nunca coincidirian.
                String normalizado = normalizar(limpia);
                if (!normalizado.isEmpty()) {
                    terminos.add(normalizado);
                }
            }

        } catch (IOException ex) {
            // Fallar el arranque seria peor que arrancar sin filtro, pero dejarlo
            // pasar en silencio significaria creer que se esta moderando cuando no.
            // Por eso: se arranca, pero con un log de error bien visible.
            log.error("No se pudo leer {}. La moderacion de texto queda DESACTIVADA.", ARCHIVO_TERMINOS, ex);
        }

        this.terminosBloqueados = Set.copyOf(terminos);
        log.info("Moderacion de texto inicializada con {} terminos.", terminosBloqueados.size());
    }

    /**
     * Valida un campo y corta el flujo si detecta lenguaje ofensivo.
     *
     * @param texto  contenido a revisar; null o vacio se consideran validos
     *               (que el campo sea obligatorio lo decide {@code @NotBlank})
     * @param campo  nombre del campo, solo para el mensaje de error
     * @throws ContenidoInapropiadoException si el texto contiene un termino bloqueado
     */
    public void validar(String texto, String campo) {
        if (contieneLenguajeOfensivo(texto)) {
            // Se registra el campo, nunca el valor: guardar en el log el texto
            // ofensivo lo replica dentro del sistema sin ninguna utilidad.
            log.warn("Texto rechazado por moderacion en el campo '{}'", campo);
            throw new ContenidoInapropiadoException(campo);
        }
    }

    /**
     * Version que devuelve un booleano en lugar de lanzar excepcion.
     * Util cuando hay que revisar varios campos y reportarlos juntos.
     */
    public boolean contieneLenguajeOfensivo(String texto) {
        if (texto == null || texto.isBlank() || terminosBloqueados.isEmpty()) {
            return false;
        }

        // 1) Palabras completas: separa por cualquier cosa que no sea letra o
        //    digito, para que "sos un boludo" se detecte sin marcar "boludismo".
        String[] palabras = texto.toLowerCase().split("[^\\p{L}\\p{N}]+");
        for (String palabra : palabras) {
            String normalizada = normalizar(palabra);
            if (!normalizada.isEmpty() && terminosBloqueados.contains(normalizada)) {
                return true;
            }
        }

        // 2) Cadena compacta: cubre nicknames sin separadores tipo "xXpu7oXx".
        //    Es la parte que puede dar falsos positivos, de ahi que se aplique
        //    despues y solo sobre textos cortos.
        String compacto = normalizar(texto);
        if (compacto.length() <= 40) {
            for (String termino : terminosBloqueados) {
                if (compacto.contains(termino)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Lleva el texto a una forma canonica comparable.
     *
     * Pasos: minusculas -> quitar acentos -> deshacer l33t -> descartar todo
     * lo que no sea letra.
     */
    private String normalizar(String texto) {
        // Normalizer.Form.NFD separa cada letra acentuada en letra base +
        // marca diacritica; despues se descartan las marcas (bloque
        // InCombiningDiacriticalMarks). Asi una "a" con tilde queda en "a".
        String sinAcentos = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        StringBuilder resultado = new StringBuilder(sinAcentos.length());
        for (char caracter : sinAcentos.toCharArray()) {
            char convertido = aplicarLeet(caracter);
            if (Character.isLetter(convertido)) {
                resultado.append(convertido);
            }
            // Todo lo demas (espacios, puntos, guiones, digitos sobrantes) se
            // descarta: son justamente los caracteres que se usan para evadir.
        }
        return resultado.toString();
    }

    /** Traduce un caracter l33t a su letra, o lo devuelve sin cambios. */
    private char aplicarLeet(char caracter) {
        int posicion = indiceDe(LEET_ORIGEN, caracter);
        return posicion >= 0 ? LEET_DESTINO[posicion] : caracter;
    }

    private int indiceDe(char[] arreglo, char buscado) {
        for (int i = 0; i < arreglo.length; i++) {
            if (arreglo[i] == buscado) {
                return i;
            }
        }
        return -1;
    }

    /** Cantidad de terminos cargados. Pensado para tests y diagnostico. */
    public int cantidadTerminos() {
        return terminosBloqueados.size();
    }
}
