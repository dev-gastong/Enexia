/* =====================================================================
   auth.js - Manejo de sesion en el cliente
   Guarda el JWT, lo lee, lo decodifica y expone los chequeos de rol que
   usan las paginas para renderizar segun el perfil (RF-1.3, lado frontend).
   ===================================================================== */

const Auth = {

    CLAVE_TOKEN: 'enexia_token',
    CLAVE_USUARIO: 'enexia_usuario',

    /**
     * Donde vive el token.
     *
     * sessionStorage: se borra al cerrar la pestana. Es lo mas seguro de las
     * dos opciones de Web Storage porque acota la ventana de exposicion.
     * localStorage sobrevive al cierre del navegador ("mantener sesion
     * iniciada"), pero deja el token disponible indefinidamente.
     *
     * Ninguno de los dos protege contra XSS: cualquier script inyectado en la
     * pagina puede leerlos. La defensa real contra robo de token es una cookie
     * HttpOnly, que JavaScript no puede leer; queda anotado para Sprint 2.
     */
    almacen() {
        return sessionStorage;
    },

    guardarSesion(respuestaLogin) {
        this.almacen().setItem(this.CLAVE_TOKEN, respuestaLogin.token);
        this.almacen().setItem(this.CLAVE_USUARIO, JSON.stringify({
            idUsuario: respuestaLogin.idUsuario,
            email: respuestaLogin.email,
            roles: respuestaLogin.roles || []
        }));
    },

    token() {
        return this.almacen().getItem(this.CLAVE_TOKEN);
    },

    usuario() {
        const crudo = this.almacen().getItem(this.CLAVE_USUARIO);
        return crudo ? JSON.parse(crudo) : null;
    },

    autenticado() {
        return this.token() !== null && !this.expirado();
    },

    cerrarSesion() {
        this.almacen().removeItem(this.CLAVE_TOKEN);
        this.almacen().removeItem(this.CLAVE_USUARIO);
    },

    /**
     * Decodifica la carga util del JWT.
     *
     * Un JWT son tres partes separadas por punto: cabecera.payload.firma, cada
     * una en Base64Url. Decodificar el payload NO es validarlo: la firma es lo
     * unico que prueba que el token no fue alterado, y esa verificacion la hace
     * el backend con la clave secreta. Aca se lee solo para mostrar datos en
     * pantalla; nunca para decidir permisos reales.
     */
    payload() {
        const token = this.token();
        if (!token) return null;

        try {
            const partes = token.split('.');
            if (partes.length !== 3) return null;

            // Base64Url usa - y _ donde Base64 usa + y /; hay que revertirlo
            // antes de pasarselo a atob().
            const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
            const relleno = base64 + '='.repeat((4 - base64.length % 4) % 4);

            // decodeURIComponent + escape recupera correctamente los acentos:
            // atob() devuelve bytes y sin este paso se rompen los no ASCII.
            const json = decodeURIComponent(
                Array.from(atob(relleno))
                    .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                    .join('')
            );

            return JSON.parse(json);
        } catch {
            return null;
        }
    },

    /** Roles del token; si no hay token, cae a los guardados del login. */
    roles() {
        const p = this.payload();
        if (p && Array.isArray(p.roles)) return p.roles;

        const u = this.usuario();
        return u ? u.roles : [];
    },

    hasRole(rol) {
        return this.roles().includes(rol);
    },

    hasAnyRole(...roles) {
        return roles.some(r => this.hasRole(r));
    },

    /** Momento de expiracion. El claim exp del JWT viene en SEGUNDOS Unix. */
    expiraEn() {
        const p = this.payload();
        return p && p.exp ? new Date(p.exp * 1000) : null;
    },

    expirado() {
        const exp = this.expiraEn();
        return exp !== null && exp.getTime() < Date.now();
    },

    /** Segundos restantes de validez (0 si ya vencio o no hay token). */
    segundosRestantes() {
        const exp = this.expiraEn();
        if (!exp) return 0;
        return Math.max(0, Math.floor((exp.getTime() - Date.now()) / 1000));
    },

    /** Redirige al login si no hay sesion valida. Se llama al abrir una pagina privada. */
    exigirSesion(rutaLogin = 'auth/login.html') {
        if (!this.autenticado()) {
            window.location.href = rutaLogin;
            return false;
        }
        return true;
    }
};
