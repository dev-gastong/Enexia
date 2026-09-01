/* =====================================================================
   Enexia - Auditoria de contraste WCAG 2.1 AA
   ---------------------------------------------------------------------
   Herramienta de desarrollo. No se enlaza desde ninguna pagina del
   producto: se carga a mano desde la consola.

       const s = document.createElement('script');
       s.src = '/js/auditoria-contraste.js';
       document.head.appendChild(s);
       s.onload = async () => console.table(await auditarContraste());

   POR QUE EXISTE
   Medir el contraste sobre los valores del CSS no alcanza cuando el
   texto vive sobre una fotografia con filtros, velos y halos encima:
   el fondo real es la composicion de todas esas capas y varia pixel a
   pixel. Esto recompone esas capas en un canvas y busca el PEOR pixel
   debajo de cada bloque de texto.

   TRES TRAMPAS, TODAS APRENDIDAS A LOS GOLPES
   1. El canvas debe tener el tamano de la CAJA del elemento contenedor,
      no del viewport. Si el contenedor es mas alto que la ventana,
      getImageData devuelve ceros para lo que queda debajo del pliegue,
      esos ceros se leen como negro y aparecen fallos que no existen.
   2. Los inset negativos EXPANDEN. El borde izquierdo de una capa con
      inset:-110px es c.left + (-110), y su ancho es c.width - (-110) -
      (-140). Invertir el signo da un rectangulo mas chico y corrido.
   3. Un elemento con fondo OPACO propio (un chip, un boton) no se mide
      contra la foto: la foto no lo atraviesa. Se mide contra su propio
      backgroundColor.
   ===================================================================== */

(function () {
    'use strict';

    /* --- Luminancia relativa y ratio (WCAG 2.1) --------------------- */

    function canalLineal(c) {
        c /= 255;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    function luminancia(r, g, b) {
        return 0.2126 * canalLineal(r) + 0.7152 * canalLineal(g) + 0.0722 * canalLineal(b);
    }

    /** Acepta "rgb(a,b,c)", "rgba(...)" o "#rrggbb". */
    function luminanciaDe(color) {
        if (color[0] === '#') {
            const h = color.slice(1);
            const n = h.length === 3
                ? h.split('').map(c => parseInt(c + c, 16))
                : [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16));
            return luminancia(n[0], n[1], n[2]);
        }
        const v = color.match(/[\d.]+/g);
        return luminancia(+v[0], +v[1], +v[2]);
    }

    function ratio(lumA, lumB) {
        const alto = Math.max(lumA, lumB), bajo = Math.min(lumA, lumB);
        return (alto + 0.05) / (bajo + 0.05);
    }

    /* --- Lectura de gradientes desde tokens CSS --------------------- */

    function paradas(valor) {
        return [...valor.matchAll(/(rgba?\([^)]*\))\s*([\d.]+)%/g)]
            .map(m => [m[1], +m[2] / 100]);
    }

    /* --- Recomposicion del fondo ------------------------------------ */

    /**
     * Dibuja en un canvas las capas de fondo que hay detras del texto.
     * `capas` describe la pila; ver auditarContraste() para el uso.
     */
    async function componerFondo(contenedor, capas) {
        const caja = contenedor.getBoundingClientRect();
        // TRAMPA 1: el canvas mide la caja, no el viewport.
        const W = Math.round(caja.width), H = Math.round(caja.height);
        const cv = document.createElement('canvas');
        cv.width = W; cv.height = H;
        const ctx = cv.getContext('2d', { willReadFrequently: true });

        for (const capa of capas) {
            const el = document.querySelector(capa.sel);
            if (!el) continue;
            const r = el.getBoundingClientRect();
            const x = r.left - caja.left, y = r.top - caja.top;

            if (capa.tipo === 'imagen') {
                const img = new Image();
                img.src = capa.src;
                // NO usar img.decode(): en una pestana en segundo plano la
                // decodificacion puede quedarse colgada sin resolver nunca.
                // El evento load si dispara.
                await new Promise((listo, error) => {
                    if (img.complete && img.naturalWidth) return listo();
                    img.onload = listo;
                    img.onerror = () => error(new Error('no se pudo cargar ' + capa.src));
                });
                ctx.filter = getComputedStyle(el).filter || 'none';
                ctx.drawImage(img, x, y, r.width, r.height);
                ctx.filter = 'none';

            } else if (capa.tipo === 'lineal') {
                const ang = (capa.grados != null ? capa.grados : 180) * Math.PI / 180;
                const dx = Math.sin(ang), dy = -Math.cos(ang);
                const L = Math.abs(r.width * dx) + Math.abs(r.height * dy);
                const cx = x + r.width / 2, cy = y + r.height / 2;
                const g = ctx.createLinearGradient(
                    cx - L / 2 * dx, cy - L / 2 * dy, cx + L / 2 * dx, cy + L / 2 * dy);
                capa.paradas.forEach(([c, o]) => g.addColorStop(o, c));
                ctx.fillStyle = g;
                ctx.fillRect(x, y, r.width, r.height);

            } else if (capa.tipo === 'radial') {
                const cx = x + r.width / 2, cy = y + r.height / 2;
                const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r.width / 2);
                capa.paradas.forEach(([c, o]) => g.addColorStop(o, c));
                ctx.fillStyle = g;
                ctx.fillRect(x, y, r.width, r.height);
            }
        }
        return { ctx, caja, W, H };
    }

    /** Luminancia del pixel mas oscuro bajo el elemento. */
    function peorFondo(ctx, caja, W, H, el) {
        const b = el.getBoundingClientRect();
        const x = Math.max(0, Math.round(b.left - caja.left));
        const y = Math.max(0, Math.round(b.top - caja.top));
        const w = Math.min(Math.max(1, Math.round(b.width)), W - x);
        const h = Math.min(Math.max(1, Math.round(b.height)), H - y);
        if (w <= 0 || h <= 0) return null;
        const d = ctx.getImageData(x, y, w, h).data;
        let min = 1;
        for (let i = 0; i < d.length; i += 4) {
            const l = luminancia(d[i], d[i + 1], d[i + 2]);
            if (l < min) min = l;
        }
        return min;
    }

    /** Un color es opaco si no es transparente ni rgba con alfa < 1. */
    function esOpaco(color) {
        if (!color || color === 'transparent') return false;
        const m = color.match(/rgba\([^)]*,\s*([\d.]+)\s*\)/);
        return !m || +m[1] >= 1;
    }

    /**
     * Sube por la cadena de ancestros buscando el primer fondo opaco.
     * Variante fina de la trampa 3: un enlace dentro de una tarjeta
     * blanca no tiene fondo propio, pero tampoco lo atraviesa la foto.
     *
     * TRAMPA 4: la caminata se corta en `limite` (el contenedor cuyas
     * capas se recompusieron). Todo lo que este en o por encima de el
     * -- body incluido -- se pinta DEBAJO de la foto, asi que su color
     * de fondo es irrelevante. Sin este corte, el texto del hero
     * "heredaria" el gris del body y daria ratios inflados.
     */
    function fondoOpacoHeredado(el, limite) {
        for (let n = el; n && n !== limite; n = n.parentElement) {
            const bg = getComputedStyle(n).backgroundColor;
            if (esOpaco(bg)) return bg;
        }
        return null;
    }

    /* --- API -------------------------------------------------------- */

    /**
     * @param {Array} casos  [{ nombre, sel, minimo }]
     *   minimo: 4.5 texto normal, 3.0 texto grande (>=24px o >=18.7px bold)
     *           y componentes no textuales.
     */
    window.auditarContraste = async function (casos) {
        const cs = getComputedStyle(document.documentElement);
        const tok = n => cs.getPropertyValue(n).trim();

        // Pila de capas del login. Los gradientes salen de los tokens de
        // la paleta activa, asi que esto sirve para cualquiera de ellas.
        const veloPrincipal = tok('--p-velo') || tok('--velo');
        const grados = +(veloPrincipal.match(/(\d+)deg/) || [0, 118])[1];

        const capas = [
            { sel: '.pantalla__foto', tipo: 'imagen', src: '/assets/login-bg.png' },
            { sel: '.pantalla__velo', tipo: 'lineal', grados: grados,
              paradas: paradas(veloPrincipal) },
            { sel: '.halo--sup-izq', tipo: 'radial', paradas: paradas(tok('--p-halo-1')) },
            { sel: '.halo--inf-der', tipo: 'radial', paradas: paradas(tok('--p-halo-2')) },
            { sel: '.velo-hero', tipo: 'lineal', grados: 180,
              paradas: paradas(tok('--p-velo-hero')) }
        ];

        const cont = document.querySelector('.pantalla');
        const { ctx, caja, W, H } = await componerFondo(cont, capas);

        const blanco = luminancia(255, 255, 255);
        let fallan = 0;
        const filas = [];

        for (const caso of casos) {
            const el = document.querySelector(caso.sel);
            if (!el) { filas.push({ elemento: caso.nombre, ratio: '(no existe)' }); continue; }

            const estilo = getComputedStyle(el);
            const tinta = luminanciaDe(estilo.color);

            // TRAMPA 3: si el elemento (o un ancestro) tiene fondo opaco,
            // se mide contra el; la foto no lo atraviesa.
            let fondo, origen;
            const propio = esOpaco(estilo.backgroundColor) ? estilo.backgroundColor : null;
            const heredado = propio || fondoOpacoHeredado(el, cont);
            if (heredado) {
                fondo = luminanciaDe(heredado);
                origen = propio ? 'fondo propio' : 'fondo heredado';
            } else {
                const p = peorFondo(ctx, caja, W, H, el);
                fondo = p === null ? blanco : p;
                origen = 'peor pixel';
            }

            const r = ratio(tinta, fondo);
            const ok = r >= caso.minimo;
            if (!ok) fallan++;
            filas.push({
                elemento: caso.nombre,
                ratio: r.toFixed(2),
                minimo: caso.minimo.toFixed(1),
                origen: origen,
                estado: ok ? 'OK' : 'FALLA'
            });
        }

        return { paleta: tok('--p-nombre') || '(sin nombre)', ancho: innerWidth, filas, fallan };
    };

    /** Los elementos del login, con su umbral correspondiente. */
    window.CASOS_LOGIN = [
        { nombre: 'Titulo hero 48px',     sel: '.presentacion__titulo',    minimo: 3.0 },
        { nombre: 'Remate del titulo',    sel: '.presentacion__titulo em', minimo: 3.0 },
        { nombre: 'Bajada hero 16px',     sel: '.presentacion__bajada',    minimo: 4.5 },
        { nombre: 'Footer 12px',          sel: '.presentacion__pie',       minimo: 4.5 },
        { nombre: 'Chip de ciudad',       sel: '.ciudad',                  minimo: 4.5 },
        { nombre: 'Titulo tarjeta 24px',  sel: '.tarjeta__titulo',         minimo: 3.0 },
        { nombre: 'Subtitulo tarjeta',    sel: '.tarjeta__sub',            minimo: 4.5 },
        { nombre: 'Etiqueta de campo',    sel: '.campo__etiqueta',         minimo: 4.5 },
        { nombre: 'Enlace contrasena',    sel: '.campo__enlace',           minimo: 4.5 },
        { nombre: 'Icono de campo',       sel: '.entrada__icono',          minimo: 3.0 },
        { nombre: 'Boton principal',      sel: '.boton',                   minimo: 4.5 },
        { nombre: 'Boton secundario',     sel: '.boton--secundario',       minimo: 4.5 },
        { nombre: 'Nota al pie tarjeta',  sel: '.tarjeta__nota',           minimo: 4.5 }
    ];

    window.auditarLogin = () => window.auditarContraste(window.CASOS_LOGIN);
})();
