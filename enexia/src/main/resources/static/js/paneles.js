/* =====================================================================
   paneles.js - Selector de paneles para cuentas con mas de un rol

   Cada rol tiene su propio panel (participante, organizador, admin) y
   cada panel su propio sidebar. Una cuenta que reune varios roles
   (p. ej. ADMINISTRADOR + PARTICIPANTE) no deberia tener que escribir
   URLs a mano: este script agrega al sidebar de CUALQUIER pagina privada
   los accesos a los demas paneles que sus roles permiten.

   Solo es navegacion: quien decide si el acceso es real es el backend.

   Uso: <script src="../../js/paneles.js"></script> despues de auth.js.
   Las rutas se resuelven contra la ubicacion de este mismo archivo, asi
   que funciona igual desde pages/admin, pages/organizador, etc.
   ===================================================================== */
const Paneles = (function () {

    // Raiz estatica = carpeta que contiene /js/paneles.js
    const raiz = (function () {
        const el = document.currentScript;
        return el && el.src ? el.src.replace(/js\/paneles\.js(\?.*)?$/, '') : '/';
    })();

    const PANELES = [
        { rol: 'ADMINISTRADOR', carpeta: 'pages/admin/',        destino: 'pages/admin/moderacion-eventos.html',      texto: 'Panel Administrador' },
        { rol: 'ORGANIZADOR',   carpeta: 'pages/organizador/',  destino: 'pages/organizador/dashboard.html',         texto: 'Panel Organizador' },
        { rol: 'PARTICIPANTE',  carpeta: 'pages/participante/', destino: 'pages/participante/mis-inscripciones.html', texto: 'Panel Participante' }
    ];

    const ICONO = '<svg viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
        '<path d="M2 5.5h10M10 3l2.5 2.5L10 8M14 10.5H4M6 8l-2.5 2.5L6 13" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';

    /** Paneles a los que la cuenta puede ir, sin contar el que esta abierto. */
    function disponibles() {
        // Las cuentas de administracion son solo de administracion.
        if (Auth.hasRole('ADMINISTRADOR')) return [];
        const ruta = window.location.pathname;
        return PANELES.filter(p => Auth.hasRole(p.rol) && ruta.indexOf('/' + p.carpeta) === -1);
    }

    function pintarEnSidebar() {
        const nav = document.querySelector('.sidebar__nav');
        if (!nav || nav.querySelector('[data-paneles]') || !Auth.autenticado()) return;

        const otros = disponibles();
        if (!otros.length) return;

        const grupo = document.createElement('div');
        grupo.setAttribute('data-paneles', '');
        grupo.style.cssText = 'margin-top:14px;padding-top:14px;border-top:1px solid var(--borde);display:flex;flex-direction:column;gap:4px';

        const titulo = document.createElement('span');
        titulo.textContent = 'Cambiar de panel';
        titulo.style.cssText = 'padding:0 14px 6px;font-size:10.5px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--texto-secundario)';
        grupo.appendChild(titulo);

        otros.forEach(p => {
            const a = document.createElement('a');
            a.className = 'sidebar__item';
            a.href = raiz + p.destino;
            a.innerHTML = ICONO + p.texto;
            grupo.appendChild(a);
        });

        const catalogo = document.createElement('a');
        catalogo.className = 'sidebar__item';
        catalogo.href = raiz + 'index.html';
        catalogo.innerHTML = ICONO + 'Catálogo público';
        grupo.appendChild(catalogo);

        nav.appendChild(grupo);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', pintarEnSidebar);
    } else {
        pintarEnSidebar();
    }

    return { disponibles, raiz };
})();
