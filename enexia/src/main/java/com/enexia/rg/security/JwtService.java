package com.enexia.rg.security;

import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Emision y verificacion de JSON Web Tokens (RF-1.2).
 *
 * COMO FUNCIONA UN JWT
 * Un JWT son tres partes separadas por puntos: {@code header.payload.firma}.
 * Header y payload son JSON codificados en Base64URL, o sea LEGIBLES por
 * cualquiera que tenga el token. La firma es un HMAC calculado sobre
 * header+payload usando una clave secreta que solo conoce el servidor.
 *
 * De ahi salen dos consecuencias que hay que tener presentes siempre:
 *
 *  1. El token NO esta cifrado, solo firmado. Nunca poner datos sensibles en el
 *     payload (contrasenas, DNI, telefonos): cualquiera puede leerlos.
 *  2. El token es INFALSIFICABLE sin la clave. Si alguien altera un byte del
 *     payload, la firma deja de coincidir y la verificacion falla.
 *
 * Eso es lo que permite que la autenticacion sea "stateless": el servidor no
 * guarda sesiones en memoria ni en base; le alcanza con verificar la firma del
 * token que trae el cliente en cada peticion.
 */
@Service
@Slf4j
public class JwtService {

    /** Nombre del claim donde viajan los roles. Debe coincidir con el que lee el filtro. */
    public static final String CLAIM_ROLES = "roles";

    private final SecretKey claveFirma;
    private final long vigenciaMs;
    private final String emisor;

    /**
     * Spring inyecta los valores de application.properties por constructor.
     *
     * La clave se decodifica UNA sola vez, al arrancar la aplicacion, y queda
     * como campo final. Hacerlo en cada llamada seria trabajo repetido e inutil.
     * Ademas, si la clave esta mal configurada la aplicacion falla al arrancar
     * en vez de fallar en el primer login, que es mucho mas dificil de detectar.
     */
    public JwtService(
            @Value("${enexia.security.jwt.secret-key}") String claveBase64,
            @Value("${enexia.security.jwt.expiration}") long vigenciaMs,
            @Value("${enexia.security.jwt.issuer}") String emisor) {

        byte[] bytesClave = Decoders.BASE64.decode(claveBase64);

        // El algoritmo NO se elige a mano: jjwt toma el HMAC mas fuerte que
        // soporte el largo de la clave.
        //   32 a 47 bytes -> HS256
        //   48 a 63 bytes -> HS384
        //   64 o mas      -> HS512
        // Con menos de 32 bytes lanza excepcion; se valida aca para dar un
        // mensaje entendible en vez de un error de libreria al primer login.
        if (bytesClave.length < 32) {
            throw new IllegalStateException(
                    "La clave JWT debe tener al menos 32 bytes (256 bits) una vez decodificada de Base64. "
                    + "Revise la propiedad enexia.security.jwt.secret-key o la variable JWT_SECRET_KEY.");
        }

        this.claveFirma = Keys.hmacShaKeyFor(bytesClave);
        this.vigenciaMs = vigenciaMs;
        this.emisor = emisor;
    }

    /**
     * Emite el token de un login exitoso (DFD Login 1.2.10).
     *
     * @param email identidad del usuario; va en el claim estandar "sub" (subject)
     * @param roles roles asignados, para que el filtro pueda autorizar sin ir a la base
     */
    public String generarToken(String email, List<String> roles) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + vigenciaMs);

        return Jwts.builder()
                .subject(email)              // sub: a quien identifica el token
                .claim(CLAIM_ROLES, roles)   // claim propio de Enexia
                .issuer(emisor)              // iss: quien lo emitio
                .issuedAt(ahora)             // iat: cuando se emitio
                .expiration(expiracion)      // exp: hasta cuando vale
                .signWith(claveFirma)        // calcula el HMAC y lo adjunta
                .compact();                  // arma el string final header.payload.firma
    }

    /**
     * Verifica firma, emisor y vigencia, y devuelve el contenido del token.
     *
     * {@code verifyWith} es el paso critico: recalcula el HMAC y lo compara con
     * la firma recibida. Si no coinciden, lanza excepcion. Un token manipulado
     * jamas llega a devolver claims.
     *
     * @throws JwtException si el token es invalido, fue alterado o expiro
     */
    public Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(claveFirma)
                .requireIssuer(emisor)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Email (claim "sub") del token. */
    public String extraerEmail(String token) {
        return extraerClaims(token).getSubject();
    }

    /**
     * Roles del token.
     *
     * El cast a List<String> es inevitable: el payload de un JWT es JSON sin
     * tipos, asi que jjwt entrega Object y el tipo concreto solo se conoce
     * porque lo pusimos nosotros en generarToken.
     */
    @SuppressWarnings("unchecked")
    public List<String> extraerRoles(String token) {
        Object roles = extraerClaims(token).get(CLAIM_ROLES);
        return roles instanceof List<?> lista ? (List<String>) lista : List.of();
    }

    /**
     * Verifica un token sin propagar la excepcion.
     *
     * Pensado para el filtro, donde un token invalido no es un error del sistema
     * sino simplemente una peticion que sigue como anonima.
     */
    public boolean esValido(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            // Nivel debug, no error: recibir tokens vencidos es normal y llenar
            // el log de errores por eso esconderia los problemas reales.
            log.debug("Token JWT rechazado: {}", ex.getMessage());
            return false;
        }
    }
}
