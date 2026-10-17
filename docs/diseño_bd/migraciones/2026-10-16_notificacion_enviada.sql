-- =====================================================================
-- Enexia - Registro de correos enviados (2026-10-16)
-- =====================================================================
--
-- POR QUE HACE FALTA ESTE ARCHIVO
-- Subsistema de notificaciones por correo (docs/diagrams/notificaciones/).
-- La tabla cumple dos funciones:
--   1. IDEMPOTENCIA: el UNIQUE (tipo, id_usuario, id_referencia) impide mandar
--      dos veces el mismo aviso a la misma persona por el mismo motivo. Es lo
--      que deja correr el scheduler del recordatorio cada minuto sin duplicar.
--   2. LIMITE POR DESTINATARIO: contar filas recientes de un tipo para un
--      usuario frena el "email bombing" de la recuperacion de contrasena sin
--      depender de la IP.
--
-- id_referencia es NULL en los avisos que se limitan por conteo y no por
-- unicidad (RECUPERACION, PASSWORD_CAMBIADA): MariaDB trata los NULL como
-- distintos dentro de un UNIQUE, asi que esas filas no chocan entre si.
--
-- COMO APLICARLO
--   mysql -u root enexia < "docs/diseño_bd/migraciones/2026-10-16_notificacion_enviada.sql"
--
-- Es idempotente: si la tabla ya existe (porque ddl-auto=update la creo al
-- arrancar la aplicacion), no hace nada.
-- =====================================================================

CREATE TABLE IF NOT EXISTS notificacion_enviada (
    id_notificacion BIGINT NOT NULL AUTO_INCREMENT,
    tipo            VARCHAR(40) NOT NULL,
    id_usuario      BIGINT NOT NULL,
    id_referencia   BIGINT NULL,
    estado          VARCHAR(12) NOT NULL,
    fecha           DATETIME(6) NOT NULL,
    PRIMARY KEY (id_notificacion),
    UNIQUE KEY uk_notificacion_tipo_usuario_ref (tipo, id_usuario, id_referencia),
    KEY idx_notificacion_tipo_usuario_fecha (tipo, id_usuario, fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
