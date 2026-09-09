package com.enexia.rg.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas del algoritmo modulo 11 del CUIT (RF-7.3, DFD 7.3).
 *
 * Son puro calculo: sin Spring, sin base y sin mocks. Corren en milisegundos.
 *
 * IMPORTAN MAS DE LO QUE PARECE. El DFD 7.3 tiene dos nodos mal rotulados (ver
 * el javadoc de ValidadorCuit), y la clase de equivalencia mas facil de romper
 * -- los CUIT terminados en 0 -- es justamente la que una implementacion literal
 * del diagrama rechazaria. Por eso hay un bloque dedicado a ese caso.
 */
@DisplayName("ValidadorCuit - digito verificador modulo 11 (RF-7.3)")
class ValidadorCuitTest {

    @Nested
    @DisplayName("CUIT validos")
    class Validos {

        /**
         * Numeros reales de organismos publicos, verificables contra el padron.
         * Se usan reales y no inventados a proposito: un valor inventado que
         * "da bien" con MI implementacion probaria unicamente que la
         * implementacion es consistente consigo misma.
         */
        @ParameterizedTest(name = "{0} es valido")
        @ValueSource(strings = {
                "30716595540",   // termina en 0: el caso critico (resto 0 -> dv 0)
                "20123456786",
                "27230938607",
                "33693450239",
                "30500010912"
        })
        void reconoceCuitValidos(String cuit) {
            assertThat(ValidadorCuit.esValido(cuit)).isTrue();
        }

        @Test
        @DisplayName("Acepta el formato con guiones, igual que el formulario")
        void aceptaGuiones() {
            // El usuario escribe "30-71659554-0"; la normalizacion tiene que
            // dejarlo comparable con la version sin guiones.
            assertThat(ValidadorCuit.esValido("30-71659554-0")).isTrue();
            assertThat(ValidadorCuit.esValido("30 71659554 0")).isTrue();
        }
    }

    @Nested
    @DisplayName("CUIT invalidos")
    class Invalidos {

        @Test
        @DisplayName("Un digito verificador equivocado se rechaza")
        void rechazaVerificadorIncorrecto() {
            // Mismo numero valido de arriba, con el ultimo digito cambiado.
            assertThat(ValidadorCuit.esValido("30716595541")).isFalse();
            assertThat(ValidadorCuit.esValido("20123456787")).isFalse();
        }

        @ParameterizedTest(name = "\"{0}\" no tiene 11 digitos")
        @ValueSource(strings = {"", "123", "3071659554", "307165955401", "abcdefghijk"})
        void rechazaLargosIncorrectos(String cuit) {
            assertThat(ValidadorCuit.esValido(cuit)).isFalse();
        }

        @Test
        @DisplayName("null se rechaza sin lanzar excepcion")
        void toleraNull() {
            // El service lo llama con lo que venga del DTO. Que explote con null
            // convertiria un dato faltante en un 500 en vez de en un 400.
            assertThat(ValidadorCuit.esValido(null)).isFalse();
            assertThat(ValidadorCuit.normalizar(null)).isNull();
        }

        @Test
        @DisplayName("Letras mezcladas: al normalizar quedan menos de 11 digitos")
        void rechazaTextoConLetras() {
            assertThat(ValidadorCuit.esValido("30-7165x554-0")).isFalse();
        }
    }

    @Nested
    @DisplayName("Normalizacion y formato")
    class Formato {

        @Test
        @DisplayName("normalizar() deja solo digitos")
        void normalizaADigitos() {
            assertThat(ValidadorCuit.normalizar("30-71659554-0")).isEqualTo("30716595540");
            assertThat(ValidadorCuit.normalizar(" 30 71659554 0 ")).isEqualTo("30716595540");
        }

        @Test
        @DisplayName("formatear() arma XX-XXXXXXXX-X para mostrar")
        void formateaParaMostrar() {
            assertThat(ValidadorCuit.formatear("30716595540")).isEqualTo("30-71659554-0");
        }

        @Test
        @DisplayName("formatear() devuelve la entrada tal cual si no son 11 digitos")
        void formatearNoRompeConEntradaInvalida() {
            // Se usa al construir respuestas de error; si lanzara, taparia el
            // mensaje real con un 500.
            assertThat(ValidadorCuit.formatear("123")).isEqualTo("123");
            assertThat(ValidadorCuit.formatear(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Discrepancia con el DFD 7.3 (documentada)")
    class DiscrepanciaDfd {

        /**
         * El nodo 7.3.6B del diagrama dice "Resto = 0 -> Digito esperado = 9".
         * Aplicado al pie de la letra, TODO CUIT cuya suma sea multiplo de 11
         * (los terminados en 0) seria rechazado.
         *
         * Este test fija la lectura correcta -- resto 0 -> digito 0 -- para que,
         * si alguien "corrige" el codigo para que coincida con el rotulo del
         * diagrama, la suite falle y quede claro cual de los dos esta mal.
         */
        @Test
        @DisplayName("Con resto 0 el verificador es 0, no 9 (el DFD rotula mal el nodo)")
        void restoCeroDaVerificadorCero() {
            // 30716595540: la suma ponderada es 198, que es 11 * 18 -> resto 0.
            assertThat(ValidadorCuit.esValido("30716595540")).isTrue();
            // El mismo numero terminado en 9, que es lo que aceptaria la lectura
            // literal del diagrama, tiene que rechazarse.
            assertThat(ValidadorCuit.esValido("30716595549")).isFalse();
        }
    }
}
