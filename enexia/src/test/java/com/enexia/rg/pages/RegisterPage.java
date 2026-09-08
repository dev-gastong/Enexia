package pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Page Object del formulario de registro (pages/auth/register-paso2.html).
 *
 * Sigue el mismo patron que {@link LoginPage}: los selectores viven aca y solo
 * aca. Si manana cambia un id del HTML, se corrige en este archivo y ningun
 * test se entera. Los tests hablan de intenciones ("completar como
 * participante"), no de selectores CSS.
 */
public class RegisterPage {

    public static final String URL =
            "http://localhost:8080/pages/auth/register-paso2.html";

    private final Page page;

    // --- Toggle de tipo de cuenta
    private final Locator tipoPersonaFisica;
    private final Locator tipoPersonaJuridica;

    // --- Perfil
    private final Locator perfilParticipante;
    private final Locator perfilOrganizador;

    // --- Credenciales
    private final Locator nickname;
    private final Locator email;
    private final Locator password;
    private final Locator passwordConfirmacion;

    // --- Identidad civil
    private final Locator nombre;
    private final Locator apellido;
    private final Locator dni;
    private final Locator fechaNacimiento;

    // --- Acciones y respuesta
    private final Locator botonEnviar;
    private final Locator aviso;

    /*
     * Los radios de tipo de cuenta y de perfil estan ocultos por CSS
     * (opacity:0 y pointer-events:none): el control visible es su <label>.
     * Por eso se localiza la etiqueta y no el input. Hacer click sobre el
     * input fallaria con "the label intercepts pointer events", y ademas
     * seria una interaccion que ningun usuario real puede realizar.
     */
    public RegisterPage(Page page) {
        this.page = page;

        this.tipoPersonaFisica    = page.locator("label[for='tipo-pf']");
        this.tipoPersonaJuridica  = page.locator("label[for='tipo-pj']");

        this.perfilParticipante   = page.locator("label[for='perfil-participante']");
        this.perfilOrganizador    = page.locator("label[for='perfil-organizador']");

        this.nickname             = page.locator("#nickname");
        this.email                = page.locator("#email");
        this.password             = page.locator("#password");
        this.passwordConfirmacion = page.locator("#passwordConfirmacion");

        this.nombre               = page.locator("#nombre");
        this.apellido             = page.locator("#apellido");
        this.dni                  = page.locator("#dni");
        this.fechaNacimiento      = page.locator("#fechaNacimiento");

        this.botonEnviar          = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Finalizar Registro"));
        this.aviso                = page.locator("#avisoRegistro");
    }

    /* ------------------------------------------------------ Navegacion --- */

    public void navegar() {
        page.navigate(URL);
    }

    /* --------------------------------------------------------- Acciones --- */

    /**
     * Completa el formulario con una identidad de Persona Fisica.
     *
     * El tipo de cuenta y el perfil se eligen haciendo click en la etiqueta
     * visible, igual que un usuario: el radio real esta oculto por CSS. Como
     * son radios y no checkboxes, un segundo click no los desmarca, asi que
     * llamar al metodo dos veces es inocuo.
     */
    public void completarPersonaFisica(DatosRegistro datos) {
        tipoPersonaFisica.click();

        if ("ORGANIZADOR".equals(datos.perfil)) {
            perfilOrganizador.click();
        } else {
            perfilParticipante.click();
        }

        nickname.fill(datos.nickname);
        email.fill(datos.email);
        password.fill(datos.password);
        passwordConfirmacion.fill(datos.passwordConfirmacion);

        nombre.fill(datos.nombre);
        apellido.fill(datos.apellido);
        dni.fill(datos.dni);
        fechaNacimiento.fill(datos.fechaNacimiento);
    }

    public void enviar() {
        botonEnviar.click();
    }

    public void elegirPersonaJuridica() {
        tipoPersonaJuridica.click();
    }

    /* ----------------------------------------------------- Comprobacion --- */

    /** Aviso general del formulario (duplicados, moderacion, backend caido). */
    public Locator aviso() {
        return aviso;
    }

    public Locator botonEnviar() {
        return botonEnviar;
    }

    /** Mensaje de error pintado bajo un campo concreto (400 de validacion). */
    public Locator errorDeCampo(String idCampo) {
        return page.locator("#" + idCampo)
                   .locator("xpath=ancestor::div[contains(@class,'campo')][1]")
                   .locator(".campo__error");
    }

    /** El contenedor .campo se marca con la clase de invalido. */
    public Locator contenedorDeCampo(String idCampo) {
        return page.locator("#" + idCampo)
                   .locator("xpath=ancestor::div[contains(@class,'campo')][1]");
    }

    /**
     * Bloque de domicilio personal. Se quito del formulario porque el MER no
     * modela una Ubicacion para Persona_Fisica; este locator existe para poder
     * afirmar que efectivamente ya no esta.
     */
    public Locator seccionDomicilioPersonal() {
        return page.getByText("Domicilio personal");
    }

    /* --------------------------------------------- Datos de una prueba --- */

    /**
     * Bolsa de datos del formulario. Es una clase y no nueve parametros
     * sueltos para que en el test se lea que valor va en que campo.
     */
    public static class DatosRegistro {
        public String nickname;
        public String email;
        public String password = "Segura123";
        public String passwordConfirmacion = "Segura123";
        public String nombre = "Maria";
        public String apellido = "Gonzalez";
        public String dni;
        public String fechaNacimiento = "1998-05-14";
        public String perfil = "PARTICIPANTE";

        /**
         * Identidad nueva en cada corrida.
         *
         * El registro es irreversible por diseno (borrado logico, sin DELETE),
         * asi que una prueba que reutilizara el mismo email pasaria la primera
         * vez y fallaria con 409 en todas las siguientes.
         */
        public static DatosRegistro unica() {
            // Los ultimos 8 digitos del reloj: alcanzan para un DNI valido
            // (@Pattern admite 7 u 8 digitos) y no se repiten entre corridas.
            String marca = String.valueOf(System.currentTimeMillis());
            String sufijo = marca.substring(marca.length() - 8);

            DatosRegistro datos = new DatosRegistro();
            datos.nickname = "e2e_" + sufijo;
            datos.email = "e2e." + sufijo + "@enexia.test";
            datos.dni = sufijo;
            return datos;
        }
    }
}
