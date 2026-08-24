package com.enexia.rg.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.enexia.rg.model.HistorialInteracciones;
import com.enexia.rg.model.Usuario;
import com.enexia.rg.repository.HistorialInteraccionesRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bitacora de acciones sobre la tabla {@code historial_interacciones}.
 *
 * Cumple dos funciones a la vez: deja rastro para investigar incidentes, y le
 * da datos al rate limiting por IP (DFD Login 1.2.1) para decidir a quien frenar.
 *
 * DOS METODOS CON PROPAGACION DISTINTA: POR QUE
 * Este es el punto mas delicado de la clase, y la eleccion NO es libre: cada
 * situacion admite una sola opcion correcta.
 *
 * 1) {@link #registrarAparte} usa REQUIRES_NEW.
 *    Al fallar un login hay que registrar el intento y despues lanzar la
 *    excepcion que devuelve el 401. Spring, por defecto, revierte la
 *    transaccion cuando una RuntimeException escapa de un metodo transaccional.
 *    Si la auditoria compartiera esa transaccion, la reversion se llevaria
 *    puesto el registro del intento fallido y los ataques de fuerza bruta no
 *    quedarian asentados en ningun lado. REQUIRES_NEW suspende la transaccion
 *    en curso y abre una propia que se confirma sola, asi el registro sobrevive.
 *
 * 2) {@link #registrar} usa la propagacion por defecto (REQUIRED) y se suma a
 *    la transaccion de quien lo llama.
 *    Hace falta cuando la accion auditada apunta a una fila creada por esa
 *    misma transaccion y todavia sin confirmar, como el usuario recien dado de
 *    alta en el registro. Con REQUIRES_NEW ahi se produce un INTERBLOQUEO real:
 *    la transaccion nueva necesita un lock compartido sobre la fila usuario
 *    para validar la clave foranea, pero esa fila la tiene bloqueada la
 *    transaccion suspendida, que no va a liberarla hasta que la nueva termine.
 *    Ninguna de las dos avanza y MySQL corta con "Lock wait timeout exceeded".
 *
 *    Ademas es lo semanticamente correcto: si el alta se revierte, el registro
 *    de "REGISTRO_EXITOSO" tiene que revertirse con ella. Un alta que nunca
 *    ocurrio no puede quedar auditada como exitosa.
 *
 * REGLA PRACTICA: si la auditoria referencia una fila que la transaccion en
 * curso todavia no confirmo, usar {@link #registrar}. Si tiene que sobrevivir
 * a una excepcion, usar {@link #registrarAparte}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaService {

    // Vocabulario de acciones. Constantes y no strings sueltos, porque el rate
    // limiting consulta por estos valores: un typo lo dejaria sin efecto en
    // silencio, sin ningun error visible.
    public static final String ACCION_LOGIN_EXITOSO = "LOGIN_EXITOSO";
    public static final String ACCION_LOGIN_FALLIDO = "LOGIN_FALLIDO";
    public static final String ACCION_CUENTA_BLOQUEADA = "CUENTA_BLOQUEADA";
    public static final String ACCION_REGISTRO_EXITOSO = "REGISTRO_EXITOSO";
    public static final String ACCION_REGISTRO_RECHAZADO_MODERACION = "REGISTRO_RECHAZADO_MODERACION";

    private static final String MODULO_AUTENTICACION = "AUTENTICACION";

    /** Limite de la columna user_agent; los navegadores mandan cadenas larguisimas. */
    private static final int MAX_USER_AGENT = 255;

    private final HistorialInteraccionesRepository historialRepository;

    /**
     * Asienta una accion sumandose a la transaccion de quien llama.
     *
     * Es la variante que hay que usar cuando la accion auditada referencia una
     * fila creada por esa misma transaccion y aun sin confirmar (ver el detalle
     * en la documentacion de la clase).
     *
     * Como comparte transaccion, un fallo al guardar SI arrastra la operacion
     * principal. Es intencional: no tiene sentido confirmar un alta cuya
     * auditoria no pudo escribirse.
     *
     * @param usuario  usuario involucrado, o null si todavia no se identifico
     * @param accion   una de las constantes ACCION_* de esta clase
     * @param detalles contexto adicional. NUNCA incluir contrasenas ni tokens
     * @param request  peticion HTTP, de donde salen IP y user-agent
     */
    @Transactional
    public void registrar(Usuario usuario, String accion, String detalles, HttpServletRequest request) {
        historialRepository.save(construir(usuario, accion, detalles, request));
    }

    /**
     * Asienta una accion en una transaccion propia e independiente.
     *
     * Sobrevive a la reversion de la operacion que la origino, que es lo que
     * hace falta para dejar constancia de intentos fallidos y rechazos.
     *
     * Solo debe usarse cuando la accion NO referencia filas sin confirmar de la
     * transaccion en curso: hacerlo produce un interbloqueo por clave foranea.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAparte(Usuario usuario, String accion, String detalles, HttpServletRequest request) {
        try {
            historialRepository.save(construir(usuario, accion, detalles, request));

        } catch (RuntimeException ex) {
            // Que falle la auditoria no puede tumbar el flujo principal.
            // LIMITE CONOCIDO: esto atrapa los fallos al ejecutar el INSERT,
            // pero no los que ocurren al confirmar la transaccion, porque el
            // commit sucede en el proxy de Spring, ya fuera de este metodo.
            log.error("No se pudo registrar la accion '{}' en la auditoria", accion, ex);
        }
    }

    /** Arma el registro de bitacora a partir de la accion y la peticion HTTP. */
    private HistorialInteracciones construir(Usuario usuario, String accion,
                                             String detalles, HttpServletRequest request) {
        HistorialInteracciones registro = new HistorialInteracciones();
        registro.setUsuario(usuario);
        registro.setAccion(accion);
        registro.setModulo(MODULO_AUTENTICACION);
        registro.setDetalles(detalles);
        registro.setFechaInteraccion(LocalDateTime.now());

        if (request != null) {
            registro.setEndpoint(request.getRequestURI());
            registro.setMetodoHttp(request.getMethod());
            registro.setIpOrigen(obtenerIpCliente(request));
            registro.setUserAgent(truncar(request.getHeader("User-Agent")));
        }
        return registro;
    }

    /**
     * Obtiene la IP real del cliente.
     *
     * {@code getRemoteAddr()} devuelve la IP de quien abrio la conexion TCP.
     * Detras de un proxy inverso (Nginx, como plantea el plan de despliegue de
     * Enexia) esa IP es siempre la del proxy, y el rate limiting terminaria
     * contando todo el trafico como si viniera de un unico cliente.
     *
     * La IP original viaja en {@code X-Forwarded-For}, cuyo primer valor es el
     * cliente y los siguientes los proxies intermedios.
     *
     * ADVERTENCIA: esa cabecera la puede falsificar cualquiera, asi que solo es
     * confiable si el proxy la reescribe (en Nginx,
     * {@code proxy_set_header X-Forwarded-For $remote_addr}). Sin esa
     * configuracion, un atacante evade el rate limiting mandando una cabecera
     * distinta en cada peticion.
     */
    private String obtenerIpCliente(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncar(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= MAX_USER_AGENT ? valor : valor.substring(0, MAX_USER_AGENT);
    }

    /** Expone la resolucion de IP para que otros servicios usen el mismo criterio. */
    public String ipDe(HttpServletRequest request) {
        return request == null ? "desconocida" : obtenerIpCliente(request);
    }
}
