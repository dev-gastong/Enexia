/* =====================================================================
   utils.js - Helpers de interfaz
   Consola de respuesta, pintado de errores por campo, avisos y chips.
   ===================================================================== */

const UI = {

    /* ---------------------------------------------------- Consola HTTP --- */

    /**
     * Vuelca el resultado de API.peticion() en la consola de la derecha.
     * Es la pieza que permite mostrar en la defensa que el backend devuelve
     * el status y el JSON correctos en cada escenario.
     */
    mostrarRespuesta(resultado) {
        const consola = document.getElementById('consola');
        if (!consola) return;

        const clase = this.claseEstado(resultado);
        const etiqueta = resultado.errorRed
            ? 'SIN CONEXION'
            : resultado.status + ' ' + this.textoEstado(resultado.status);

        // Cabecera especial del cooldown: el backend informa cuando se libera
        // la cuenta en una cabecera propia, no en el cuerpo.
        const reintentar = resultado.headers.get('X-Reintentar-Despues');

        consola.innerHTML = `
            <div class="consola__cabecera">
                <p class="consola__titulo">Respuesta del backend</p>
                <span class="estado ${clase}">${etiqueta}</span>
                <span class="consola__meta">${resultado.ms} ms</span>
            </div>
            <div class="consola__peticion">${resultado.metodo} ${resultado.url}</div>
            ${reintentar ? `<div class="consola__peticion">X-Reintentar-Despues: ${reintentar}</div>` : ''}
            <pre class="consola__cuerpo">${this.colorearJson(resultado.cuerpo)}</pre>
        `;
    },

    limpiarConsola(mensaje = 'Todavia no se envio ninguna peticion.') {
        const consola = document.getElementById('consola');
        if (!consola) return;
        consola.innerHTML = `
            <div class="consola__cabecera">
                <p class="consola__titulo">Respuesta del backend</p>
            </div>
            <div class="consola__vacia">${mensaje}</div>
        `;
    },

    claseEstado(r) {
        if (r.errorRed) return 'estado--red';
        if (r.status >= 500) return 'estado--5xx';
        if (r.status === 401 || r.status === 403) return 'estado--401';
        if (r.status >= 400) return 'estado--4xx';
        return 'estado--2xx';
    },

    textoEstado(status) {
        const mapa = {
            200: 'OK',
            201: 'Created',
            400: 'Bad Request',
            401: 'Unauthorized',
            403: 'Forbidden',
            409: 'Conflict',
            422: 'Unprocessable Content',
            429: 'Too Many Requests',
            500: 'Internal Server Error'
        };
        return mapa[status] || '';
    },

    /** Colorea el JSON para que se lea de lejos en un proyector. */
    colorearJson(valor) {
        if (valor === null || valor === undefined) return '<span class="j-null">(sin cuerpo)</span>';

        const texto = typeof valor === 'string'
            ? valor
            : JSON.stringify(valor, null, 2);

        const escapado = texto
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        return escapado.replace(
            /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false)\b|\bnull\b|-?\d+(\.\d*)?([eE][+-]?\d+)?)/g,
            coincidencia => {
                let clase = 'j-num';
                if (/^"/.test(coincidencia)) {
                    clase = /:$/.test(coincidencia) ? 'j-clave' : 'j-texto';
                } else if (/true|false/.test(coincidencia)) {
                    clase = 'j-bool';
                } else if (/null/.test(coincidencia)) {
                    clase = 'j-null';
                }
                return `<span class="${clase}">${coincidencia}</span>`;
            }
        );
    },

    /* ------------------------------------------------ Errores por campo --- */

    /**
     * Marca en rojo los inputs que el backend rechazo.
     *
     * El handler global devuelve, en los 400 de validacion, un mapa
     * { campo: motivo } con el mismo nombre de propiedad que tiene el DTO.
     * Si el id del input coincide con esa clave, el error se pinta solo.
     */
    pintarErrores(errores) {
        this.limpiarErrores();
        if (!errores) return;

        Object.entries(errores).forEach(([campo, motivo]) => {
            const input = document.getElementById(campo);
            if (!input) return;

            const contenedor = input.closest('.campo');
            if (!contenedor) return;

            contenedor.classList.add('campo--invalido');
            const span = contenedor.querySelector('.campo__error');
            if (span) span.textContent = motivo;
        });
    },

    limpiarErrores() {
        document.querySelectorAll('.campo--invalido').forEach(c => {
            c.classList.remove('campo--invalido');
            const span = c.querySelector('.campo__error');
            if (span) span.textContent = '';
        });
    },

    /* --------------------------------------------------------- Avisos --- */

    aviso(id, tipo, mensaje) {
        const caja = document.getElementById(id);
        if (!caja) return;
        caja.className = 'aviso aviso--visible aviso--' + tipo;
        caja.innerHTML = mensaje;
    },

    ocultarAviso(id) {
        const caja = document.getElementById(id);
        if (caja) caja.className = 'aviso';
    },

    /**
     * Traduce el resultado HTTP a un mensaje para el usuario final.
     * El switch va sobre el campo `error` del ErrorResponse, que es una
     * etiqueta estable pensada justamente para esto.
     */
    mensajeDeError(resultado) {
        if (resultado.errorRed) {
            return '<strong>No se pudo contactar al backend.</strong> '
                 + 'Verifica que la aplicacion Spring Boot este corriendo en '
                 + '<code class="inline">localhost:8080</code>.';
        }

        const cuerpo = resultado.cuerpo || {};

        switch (cuerpo.error) {
            case 'VALIDACION_FALLIDA':
                return 'Hay campos con errores. Revisa el detalle marcado en el formulario.';
            case 'CREDENCIALES_INVALIDAS':
                return 'Email o contrasena incorrectos.';
            case 'CUENTA_BLOQUEADA':
                return '<strong>Cuenta bloqueada.</strong> ' + (cuerpo.mensaje || '');
            case 'CUENTA_EN_COOLDOWN':
                return '<strong>Cuenta penalizada temporalmente.</strong> ' + (cuerpo.mensaje || '');
            case 'RATE_LIMIT_EXCEDIDO':
                return '<strong>Demasiados intentos desde esta IP.</strong> ' + (cuerpo.mensaje || '');
            case 'RECURSO_DUPLICADO':
                return cuerpo.mensaje || 'Ese dato ya esta registrado.';
            case 'CONTENIDO_INAPROPIADO':
                return '<strong>Contenido rechazado por moderacion.</strong> ' + (cuerpo.mensaje || '');
            case 'REGLA_NEGOCIO':
                return cuerpo.mensaje || 'La operacion no cumple una regla de negocio.';
            default:
                return cuerpo.mensaje || 'Ocurrio un error inesperado.';
        }
    },

    /* ---------------------------------------------------------- Chips --- */

    chipRol(rol) {
        const clase = {
            PARTICIPANTE: 'chip--participante',
            ORGANIZADOR: 'chip--organizador',
            ADMINISTRADOR: 'chip--administrador'
        }[rol] || 'chip--neutro';

        return `<span class="chip ${clase}">${rol}</span>`;
    },

    /* ------------------------------------------------ Sonda de backend --- */

    /** Pinta el indicador de "backend arriba / caido" en la barra superior. */
    async sondearApi(idElemento = 'pulso') {
        const el = document.getElementById(idElemento);
        if (!el) return;

        const vivo = await API.ping();
        el.innerHTML = vivo
            ? '<span class="pulso__punto pulso__punto--ok"></span> API conectada'
            : '<span class="pulso__punto pulso__punto--mal"></span> API sin responder';
    },

    /* --------------------------------------------------------- Varios --- */

    /** Vuelca un objeto de datos en los inputs con el id correspondiente. */
    rellenarFormulario(datos) {
        Object.entries(datos).forEach(([campo, valor]) => {
            const input = document.getElementById(campo);
            if (input) input.value = valor;
        });
    },

    /** Sufijo aleatorio para generar emails y nicknames unicos en las pruebas. */
    sufijo() {
        return Math.random().toString(36).slice(2, 7);
    },

    fechaLegible(fecha) {
        if (!fecha) return '-';
        return fecha.toLocaleString('es-AR', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit', second: '2-digit'
        });
    }
};
