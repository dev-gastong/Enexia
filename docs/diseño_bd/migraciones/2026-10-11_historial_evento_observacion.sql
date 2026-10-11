-- =====================================================================
-- Enexia - Observacion de la suspension administrativa (2026-10-11)
-- =====================================================================
--
-- POR QUE HACE FALTA ESTE ARCHIVO
-- RF-6.1(b): al suspender un evento ya publicado, el administrador elige un
-- motivo codificado (DENUNCIA_FUNDADA, FRAUDE_DETECTADO,
-- INFRACCION_POST_PUBLICACION) y ademas deja una observacion de texto libre
-- con el caso concreto. El codigo sirve para filtrar; la observacion es la
-- constancia. Se guarda en el historial del estado del evento, junto al
-- usuario que decidio y la fecha, y no en la fila del catalogo
-- evento_estado_sistema (que es un catalogo: una fila por estado/motivo, no
-- por caso).
--
-- La columna es nullable a proposito: toda transicion que no sea una
-- suspension (el pipeline automatico, aprobar, revertir) la deja en NULL.
--
-- COMO APLICARLO
--   mysql -u root enexia < "docs/diseño_bd/migraciones/2026-10-11_historial_evento_observacion.sql"
--
-- Es idempotente: si la columna ya existe (porque ddl-auto=update la creo al
-- arrancar la aplicacion), no hace nada.
-- =====================================================================

SET @columna_existe := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'historial_estado_evento'
      AND COLUMN_NAME = 'observacion'
);

SET @sql := IF(@columna_existe = 0,
    'ALTER TABLE historial_estado_evento ADD COLUMN observacion VARCHAR(500) NULL',
    'SELECT "observacion ya existe, nada que hacer" AS resultado');

PREPARE instruccion FROM @sql;
EXECUTE instruccion;
DEALLOCATE PREPARE instruccion;
