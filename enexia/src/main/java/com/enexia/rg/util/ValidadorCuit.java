package com.enexia.rg.util;

/**
 * Validacion del CUIT por digito verificador, modulo 11 (RF-7.3, DFD 7.3).
 *
 * QUE VERIFICA Y QUE NO
 * Comprueba que el numero sea ARITMETICAMENTE valido: que su ultimo digito se
 * corresponda con los diez anteriores. Eso descarta tipeos y numeros inventados
 * al azar, que es el 99% de los errores reales. NO comprueba que el CUIT exista
 * de verdad ni que pertenezca a quien lo carga: para eso hace falta consultar el
 * padron de ARCA/AFIP, que queda fuera del alcance del MVP (CLAUDE.md lo deja
 * anotado como "Real AFIP validation for later"). Por eso el alta ademas queda
 * en REVISION_PENDIENTE para que un moderador la mire.
 *
 * EL ALGORITMO, PASO A PASO
 *   1. El CUIT son 11 digitos: 2 de tipo + 8 del DNI + 1 verificador.
 *   2. Se multiplican los 10 primeros por la secuencia fija 5,4,3,2,7,6,5,4,3,2.
 *   3. Se suman los productos y se toma el resto de dividir por 11.
 *   4. El verificador esperado es 11 - resto, con dos ajustes:
 *        - si da 11 -> 0
 *        - si da 10 -> 9
 *   5. Se compara contra el ultimo digito recibido.
 *
 * DISCREPANCIA DETECTADA CON EL DFD 7.3 (documentada el 2026-09-08)
 * Los nodos 7.3.6A y 7.3.6B del diagrama dicen literalmente:
 *      "Resto = 11 -> Digito esperado 0"   y   "Resto = 0 -> Digito esperado 9"
 * Ninguna de las dos puede leerse al pie de la letra:
 *   - Un resto modulo 11 nunca puede valer 11, asi que esa rama seria codigo
 *     muerto.
 *   - Y con resto 0 el verificador correcto es 0, no 9. Implementarlo tal cual
 *     rechazaria TODO CUIT terminado en 0 (por ejemplo 30-71659554-0, valido) y
 *     aceptaria numeros invalidos terminados en 9.
 * Lo que el diagrama describe en realidad es la variable intermedia (11 - resto),
 * no el resto: con 11 - resto = 11 el digito es 0, y con 11 - resto = 10 es 9.
 * Se implemento esa lectura, que es el algoritmo estandar. El DFD deberia
 * corregir el rotulo de esos dos nodos.
 */
public final class ValidadorCuit {

    /** Multiplicadores fijos del modulo 11 para CUIT/CUIL argentino. */
    private static final int[] MULTIPLICADORES = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private static final int LARGO_CUIT = 11;

    private ValidadorCuit() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Deja el CUIT en su forma canonica de 11 digitos.
     *
     * Los formularios reales reciben "30-71659554-0", "30 71659554 0" o
     * "30716595540". Normalizar aca evita que la misma organizacion se registre
     * dos veces con dos formatos distintos del mismo numero, que es lo que
     * pasaria si el UNIQUE de la base comparara las cadenas tal como llegan.
     *
     * @return solo los digitos; null si la entrada era null
     */
    public static String normalizar(String cuit) {
        if (cuit == null) {
            return null;
        }
        return cuit.replaceAll("[^0-9]", "");
    }

    /**
     * Paso 7.3.1 a 7.3.7 del DFD.
     *
     * @param cuit con o sin guiones; se normaliza internamente
     * @return true si el digito verificador coincide
     */
    public static boolean esValido(String cuit) {
        String digitos = normalizar(cuit);

        // --- 7.3.1: formato. Once digitos exactos, ni uno mas ni uno menos.
        if (digitos == null || digitos.length() != LARGO_CUIT) {
            return false;
        }

        // --- 7.3.2 y 7.3.3: descomponer y multiplicar.
        int suma = 0;
        for (int i = 0; i < MULTIPLICADORES.length; i++) {
            // Character.getNumericValue seria mas expresivo, pero acepta digitos
            // Unicode de otros alfabetos (por ejemplo el arabigo-indico) que
            // pasarian este bucle y romperian mas adelante. La resta de '0'
            // solo funciona con ASCII, que es exactamente lo que se quiere.
            suma += (digitos.charAt(i) - '0') * MULTIPLICADORES[i];
        }

        // --- 7.3.4 y 7.3.5: resto de la division por 11.
        int resto = suma % 11;

        // --- 7.3.6: digito esperado (ver la nota de discrepancia en el javadoc).
        int esperado = 11 - resto;
        if (esperado == 11) {
            esperado = 0;
        } else if (esperado == 10) {
            esperado = 9;
        }

        // --- 7.3.7: comparacion final contra el digito recibido.
        int recibido = digitos.charAt(LARGO_CUIT - 1) - '0';
        return esperado == recibido;
    }

    /**
     * Formatea para mostrar: 30716595540 -> 30-71659554-0.
     *
     * Solo presentacion. Lo que se persiste y compara son los 11 digitos crudos.
     */
    public static String formatear(String cuit) {
        String digitos = normalizar(cuit);
        if (digitos == null || digitos.length() != LARGO_CUIT) {
            return cuit;
        }
        return digitos.substring(0, 2) + "-" + digitos.substring(2, 10) + "-" + digitos.substring(10);
    }
}
