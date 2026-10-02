package com.enexia.rg.pages;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

/**
 * Page Object del formulario de alta/edicion de evento
 * (pages/organizador/evento-form.html, Modulo 2, RF-2.1 a RF-2.6).
 *
 * Sigue el mismo patron que {@link LoginPage} y {@link RegisterPage}: los
 * selectores viven aca y solo aca. Solo cubre la PRIMERA instancia del
 * cronograma con su primer sector de ticket (cronogramas[0].tickets[0]):
 * es la que ya viene precargada al entrar (ver evento-form.html, que llama a
 * agregarInstancia() apenas carga la pagina en modo alta), asi que alcanza
 * para completar el formulario entero sin tocar los botones "Anadir".
 */
public class EventoFormPage {

    public static final String URL = "http://localhost:8080/pages/organizador/evento-form.html";

    private final Page page;

    // --- Informacion basica
    private final Locator nombre;
    private final Locator descripcion;
    private final Locator idCategoria;

    // --- Ubicacion
    private final Locator calle;
    private final Locator numeroExterior;
    private final Locator numeroInterior;
    private final Locator idCiudad;
    private final Locator latitud;
    private final Locator longitud;

    // --- Cronograma (primera instancia)
    private final Locator fecha;
    private final Locator horaInicio;
    private final Locator horaFin;

    // --- Tickets (primer sector de la primera instancia)
    private final Locator tipoTicket;
    private final Locator precio;
    private final Locator cupoMaximo;

    // --- Galeria y envio
    private final Locator inputPortada;
    private final Locator btnPublicar;
    private final Locator aviso;

    /*
     * Los campos de ubicacion y de cronograma/tickets tienen un punto o
     * corchetes en su id ("ubicacion.calle", "cronogramas[0].fecha", ...).
     * Un selector CSS de id (#ubicacion.calle) interpretaria el punto como
     * separador de clase y encontraria el elemento equivocado (o ninguno), asi
     * que en vez de eso se busca por el atributo name, que en este formulario
     * siempre coincide con el id (ver evento-form.html, reindexar()).
     */
    public EventoFormPage(Page page) {
        this.page = page;

        this.nombre = page.locator("[name='nombre']");
        this.descripcion = page.locator("[name='descripcion']");
        this.idCategoria = page.locator("[name='idCategoria']");

        this.calle = page.locator("[name='ubicacion.calle']");
        this.numeroExterior = page.locator("[name='ubicacion.numeroExterior']");
        this.numeroInterior = page.locator("[name='ubicacion.numeroInterior']");
        this.idCiudad = page.locator("[name='ubicacion.idCiudad']");
        this.latitud = page.locator("[name='ubicacion.latitud']");
        this.longitud = page.locator("[name='ubicacion.longitud']");

        this.fecha = page.locator("[name='cronogramas\\[0\\]\\.fecha']");
        this.horaInicio = page.locator("[name='cronogramas\\[0\\]\\.horaInicio']");
        this.horaFin = page.locator("[name='cronogramas\\[0\\]\\.horaFin']");

        this.tipoTicket = page.locator("[name='cronogramas\\[0\\]\\.tickets\\[0\\]\\.tipoTicket']");
        this.precio = page.locator("[name='cronogramas\\[0\\]\\.tickets\\[0\\]\\.precio']");
        this.cupoMaximo = page.locator("[name='cronogramas\\[0\\]\\.tickets\\[0\\]\\.cupoMaximo']");

        this.inputPortada = page.locator("#inputPortada");
        this.btnPublicar = page.locator("#btnPublicar");
        this.aviso = page.locator("#avisoEvento");
    }

    /* ------------------------------------------------------ Navegacion --- */

    public void navegar() {
        page.navigate(URL);
    }

    /* --------------------------------------------------------- Acciones --- */

    /**
     * Completa el formulario entero: informacion basica, ubicacion, la
     * primera fecha del cronograma con su primer sector de ticket, y la
     * imagen de portada.
     *
     * Categoria y ciudad se cargan de forma asincronica (GET /api/publico/...)
     * despues de que la pagina ya esta en el DOM, asi que antes de elegir una
     * opcion hay que esperar a que el <select> deje de tener solo el
     * placeholder ("Seleccionar..."). Sin esa espera, selectOption() podria
     * correr contra un <select> todavia vacio.
     */
    public void completar(DatosEvento datos, Path imagenPortada) {
        nombre.fill(datos.nombre);
        descripcion.fill(datos.descripcion);

        esperarOpciones(idCategoria);
        seleccionarPorEtiquetaOPrimera(idCategoria, datos.categoria);

        calle.fill(datos.calle);
        numeroExterior.fill(datos.numeroExterior);
        if (datos.numeroInterior != null && !datos.numeroInterior.isBlank()) {
            numeroInterior.fill(datos.numeroInterior);
        }

        esperarOpciones(idCiudad);
        seleccionarPorEtiquetaOPrimera(idCiudad, datos.ciudad);

        if (datos.latitud != null && !datos.latitud.isBlank()) {
            latitud.fill(datos.latitud);
        }
        if (datos.longitud != null && !datos.longitud.isBlank()) {
            longitud.fill(datos.longitud);
        }

        fecha.fill(datos.fecha);
        horaInicio.fill(datos.horaInicio);
        horaFin.fill(datos.horaFin);

        tipoTicket.fill(datos.tipoTicket);
        precio.fill(datos.precio);
        cupoMaximo.fill(datos.cupoMaximo);

        // El input esta oculto por CSS (input[type="file"] { display:none }) y
        // se abre por JS al hacer click en el dropzone; setInputFiles no
        // necesita que sea visible, dispara el mismo evento "change" que
        // escucha evento-form.html.
        inputPortada.setInputFiles(imagenPortada);
    }

    public void publicar() {
        btnPublicar.click();
    }

    /* ----------------------------------------------------- Comprobacion --- */

    /** Aviso general del formulario (errores de validacion, backend caido). */
    public Locator aviso() {
        return aviso;
    }

    public Locator botonPublicar() {
        return btnPublicar;
    }

    /* --------------------------------------------------------- Helpers --- */

    private void esperarOpciones(Locator select) {
        select.page().waitForFunction(
                "select => select.options.length > 1",
                select.elementHandle());
    }

    /**
     * Selecciona la opcion cuyo texto visible coincide con {@code etiqueta}.
     * Si el catalogo de este entorno no tiene esa etiqueta exacta (por
     * ejemplo, una base de datos sembrada con otro nombre), cae a la primera
     * opcion real: el indice 0 es siempre el placeholder ("Seleccionar...",
     * ver evento-form.html), asi que el indice 1 ya es una opcion valida.
     */
    private void seleccionarPorEtiquetaOPrimera(Locator select, String etiqueta) {
        try {
            select.selectOption(new SelectOption().setLabel(etiqueta));
        } catch (RuntimeException noEncontrada) {
            select.selectOption(new SelectOption().setIndex(1));
        }
    }

    /* --------------------------------------------- Datos de una prueba --- */

    /**
     * Bolsa de datos del formulario completo. Es una clase y no una decena de
     * parametros sueltos para que en el test se lea que valor va en que campo
     * (mismo criterio que {@link RegisterPage.DatosRegistro}).
     */
    public static class DatosEvento {
        public String nombre;
        public String descripcion =
                "Evento de prueba generado automaticamente para validar de punta a punta "
                + "el alta de eventos con Playwright (Modulo 2).";
        public String categoria = "Deportivo";

        public String calle = "Av. Malvinas Argentinas";
        public String numeroExterior = "1234";
        public String numeroInterior = null;
        public String ciudad = "Ushuaia";
        public String latitud = "-54.8019";
        public String longitud = "-68.3030";

        /**
         * Hoy, con la funcion arrancando en 2 minutos y durando otros 2. No se
         * hardcodea una fecha fija ({@code @FechaFuturaValida} solo exige
         * "hoy o despues", ver ValidadorFechaFutura): un turno tan corto deja
         * ver en minutos, sin esperar dias, como el evento pasa de "no
         * iniciado" a "en curso" a "finalizado" -- justo los tres estados que
         * gobiernan el boton de inscribirse (EventoCronogramaResponse.iniciado
         * / .finalizado).
         */
        public String fecha;
        public String horaInicio;
        public String horaFin;

        public String tipoTicket = "General";
        public String precio = "0";
        public String cupoMaximo = "100";

        /**
         * Nombre e fecha unicos en cada corrida: el nombre para no chocar con
         * un evento de una corrida anterior que haya quedado publicado, la
         * fecha para que siga siendo futura sin importar cuando se ejecute.
         */
        public static DatosEvento conValoresPorDefecto() {
            String sufijo = String.valueOf(System.currentTimeMillis());
            DateTimeFormatter horaFormato = DateTimeFormatter.ofPattern("HH:mm");

            // Se calcula con LocalDateTime (no LocalDate + LocalTime por
            // separado) para que, si la corrida cae a menos de 2 minutos de
            // medianoche, "fecha" ruede sola al dia siguiente junto con la
            // hora -- sin este cuidado, horaInicio podria envolver a "00:01"
            // mientras fecha se quedaba en "hoy", y la funcion nacia
            // "finalizada" antes de que el formulario llegara a enviarse.
            LocalDateTime inicio = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(2);
            LocalDateTime fin = inicio.plusMinutes(2);

            DatosEvento datos = new DatosEvento();
            datos.nombre = "Evento Playwright " + sufijo.substring(sufijo.length() - 8);
            datos.fecha = inicio.toLocalDate().toString();
            datos.horaInicio = inicio.toLocalTime().format(horaFormato);
            datos.horaFin = fin.toLocalTime().format(horaFormato);
            return datos;
        }
    }
}
