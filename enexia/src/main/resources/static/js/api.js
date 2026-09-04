/* =====================================================================
   api.js - Cliente HTTP de Enexia
   Envoltorio unico sobre fetch(). Todas las llamadas al backend pasan por
   aca, de modo que el manejo de errores, el token y el registro en la
   consola de respuesta se escriben una sola vez.
   ===================================================================== */

const API = {

    /** Origen del backend. El frontend se sirve en 8000 y la API en 8080. */
    BASE: 'http://localhost:8080',

    /**
     * Ejecuta una peticion y SIEMPRE devuelve un objeto con la misma forma,
     * exista o no error de red:
     *
     *   { ok, status, cuerpo, headers, ms, url, metodo, errorRed }
     *
     * No lanza excepciones: quien llama decide que hacer segun el status.
     * Esto es lo que permite que la pantalla muestre igual de bien un 201
     * que un 429, sin envolver cada llamada en un try/catch.
     */
    async peticion(metodo, ruta, cuerpoEnvio = null, conToken = false) {
        const url = this.BASE + ruta;
        const inicio = performance.now();

        const opciones = {
            method: metodo,
            headers: { 'Content-Type': 'application/json' }
        };

        if (conToken && Auth.token()) {
            opciones.headers['Authorization'] = 'Bearer ' + Auth.token();
        }

        if (cuerpoEnvio !== null) {
            opciones.body = JSON.stringify(cuerpoEnvio);
        }

        try {
            const respuesta = await fetch(url, opciones);
            const ms = Math.round(performance.now() - inicio);

            // 204 no trae cuerpo; el resto de la API responde JSON siempre.
            let cuerpo = null;
            const texto = await respuesta.text();
            if (texto) {
                try {
                    cuerpo = JSON.parse(texto);
                } catch {
                    cuerpo = texto;   // por si el servidor devolvio HTML de error
                }
            }

            // Si el backend devuelve 401, redirige al login
            if (respuesta.status === 401) {
                Auth.cerrarSesion();
                window.location.href = '/pages/auth/login-desktop-claro.html';
            }

            return {
                ok: respuesta.ok,
                status: respuesta.status,
                cuerpo,
                headers: respuesta.headers,
                ms,
                url,
                metodo,
                errorRed: false
            };

        } catch (error) {
            // Aca cae el backend apagado, el DNS caido o el bloqueo por CORS.
            // fetch() solo rechaza en esos casos; un 500 del servidor NO llega
            // a este catch, resuelve normalmente con ok=false.
            return {
                ok: false,
                status: 0,
                cuerpo: { mensaje: error.message },
                headers: new Headers(),
                ms: Math.round(performance.now() - inicio),
                url,
                metodo,
                errorRed: true
            };
        }
    },

    /** POST /api/auth/registro  ->  201 | 400 | 409 | 422 */
    registro(datos) {
        return this.peticion('POST', '/api/auth/registro', datos);
    },

    /** POST /api/auth/login  ->  200 | 400 | 401 | 403 | 429 */
    login(datos) {
        return this.peticion('POST', '/api/auth/login', datos);
    },

    /**
     * Sonda de disponibilidad del backend.
     *
     * Se pega a /api/auth/login con un cuerpo vacio a proposito: la respuesta
     * esperada es 400 (validacion fallida), y eso ya prueba que el servidor
     * esta arriba y que CORS deja pasar. Cualquier status sirve como senal de
     * vida; solo errorRed indica que no hay nadie del otro lado.
     */
    async ping() {
        const r = await this.peticion('POST', '/api/auth/login', {});
        return !r.errorRed;
    },

    /* ===== Metodos convenientes para requests autenticadas ===== */

    /** GET autenticado */
    get(ruta) {
        return this.peticion('GET', ruta, null, true);
    },

    /** POST autenticado */
    post(ruta, datos) {
        return this.peticion('POST', ruta, datos, true);
    },

    /** PUT autenticado */
    put(ruta, datos) {
        return this.peticion('PUT', ruta, datos, true);
    },

    /** DELETE autenticado */
    delete(ruta) {
        return this.peticion('DELETE', ruta, null, true);
    }
};
