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

            // Si una peticion CON token vuelve 401, la sesion murio (token
            // vencido o invalido): recien ahi corresponde cerrar sesion y
            // mandar al login. Login/Registro nunca mandan token y devuelven
            // 401 como respuesta normal ante credenciales invalidas; tratar
            // ese caso igual redirigiria al login estando ya en el login,
            // pisando el cartel de error apenas se pinta.
            if (conToken && respuesta.status === 401) {
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
    },

    /**
     * POST/PUT multipart autenticado (creacion/edicion de evento).
     *
     * No puede pasar por peticion(): esa siempre serializa a JSON y fija
     * Content-Type: application/json, y un multipart necesita exactamente lo
     * contrario -- el boundary lo calcula el navegador solo con tal de que
     * Content-Type NO se toque a mano.
     *
     * @param formData FormData con la parte "datos" (Blob JSON) y, opcional,
     *                 una o mas partes "imagenes"
     */
    async multipart(metodo, ruta, formData) {
        const url = this.BASE + ruta;
        const inicio = performance.now();

        const opciones = { method: metodo, headers: {} };
        if (Auth.token()) {
            opciones.headers['Authorization'] = 'Bearer ' + Auth.token();
        }
        opciones.body = formData;

        try {
            const respuesta = await fetch(url, opciones);
            const ms = Math.round(performance.now() - inicio);

            let cuerpo = null;
            const texto = await respuesta.text();
            if (texto) {
                try { cuerpo = JSON.parse(texto); } catch { cuerpo = texto; }
            }

            if (respuesta.status === 401) {
                Auth.cerrarSesion();
                window.location.href = '/pages/auth/login-desktop-claro.html';
            }

            return { ok: respuesta.ok, status: respuesta.status, cuerpo, headers: respuesta.headers,
                      ms, url, metodo, errorRed: false };

        } catch (error) {
            return { ok: false, status: 0, cuerpo: { mensaje: error.message }, headers: new Headers(),
                      ms: Math.round(performance.now() - inicio), url, metodo, errorRed: true };
        }
    },

    /** Arma la parte "datos" como Blob JSON, tal como espera @RequestPart. */
    parteJson(objeto) {
        return new Blob([JSON.stringify(objeto)], { type: 'application/json' });
    },

    /* ===== Modulo 2: eventos del organizador ===== */

    /** GET /api/organizador/eventos?... -> dashboard paginado (RF-2.8) */
    listarEventos(parametros = {}) {
        const query = new URLSearchParams(
            Object.entries(parametros).filter(([, v]) => v !== null && v !== undefined && v !== '')
        ).toString();
        return this.get('/api/organizador/eventos' + (query ? '?' + query : ''));
    },

    /** GET /api/organizador/eventos/{id} -> detalle propio, para editar (RF-2.7) */
    obtenerEventoParaEditar(idEvento) {
        return this.get('/api/organizador/eventos/' + idEvento);
    },

    /** POST /api/organizador/eventos (multipart) -> crea, 202 Accepted (RF-2.1 a RF-2.6) */
    crearEvento(datos, imagenes) {
        const formData = new FormData();
        formData.append('datos', this.parteJson(datos));
        (imagenes || []).forEach(archivo => formData.append('imagenes', archivo));
        return this.multipart('POST', '/api/organizador/eventos', formData);
    },

    /** PUT /api/organizador/eventos/{id} (multipart) -> edita, 200 OK (RF-2.7) */
    editarEvento(idEvento, datos, imagenes) {
        const formData = new FormData();
        formData.append('datos', this.parteJson(datos));
        (imagenes || []).forEach(archivo => formData.append('imagenes', archivo));
        return this.multipart('PUT', '/api/organizador/eventos/' + idEvento, formData);
    },

    /** DELETE /api/organizador/eventos/{id} -> baja logica (RF-2.9) */
    darDeBajaEvento(idEvento) {
        return this.delete('/api/organizador/eventos/' + idEvento);
    },

    /* ===== Modulo 1: perfil propio ===== */

    /** GET /api/usuario/perfil -> datos de identidad del usuario autenticado */
    obtenerPerfil() {
        return this.get('/api/usuario/perfil');
    },

    /** PUT /api/usuario/perfil -> actualiza nombre/apellido/fecha de nacimiento */
    actualizarPerfil(datos) {
        return this.put('/api/usuario/perfil', datos);
    },

    /* ===== Modulo 7: organizaciones propias ===== */

    /** GET /api/organizador/organizaciones -> organizaciones donde el usuario es miembro */
    misOrganizaciones() {
        return this.get('/api/organizador/organizaciones');
    },

    /* ===== Catalogos publicos (sin token) ===== */

    async publico(ruta) {
        return this.peticion('GET', ruta, null, false);
    },

    categorias() {
        return this.publico('/api/publico/categorias');
    },

    provincias() {
        return this.publico('/api/publico/provincias');
    },

    ciudadesDeProvincia(idProvincia) {
        return this.publico('/api/publico/provincias/' + idProvincia + '/ciudades');
    }
};
