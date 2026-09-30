-- =====================================================================
-- Enexia - Correccion de columna id_puntuacion -> id_valoracion (2026-09-29)
-- =====================================================================
--
-- POR QUE HACE FALTA ESTE ARCHIVO
-- La @Entity Valoracion.java siempre mapeo su clave primaria como
-- id_valoracion (asi la declara tambien el MER), pero la tabla `valoracion`
-- de esta base tiene esa columna con el nombre id_puntuacion -- un resto de
-- cuando la entidad se llamaba "Puntuacion" (el DER todavia usa ese nombre
-- en las etiquetas de relacion: R_Us_Pun, R_EC_Pun), de antes de que se
-- renombrara a "Valoracion" en la documentacion. `ddl-auto=update` nunca
-- renombra columnas, solo agrega las que faltan, asi que el desajuste quedo
-- en silencio.
--
-- Nadie lo habia notado porque, hasta este sprint, ninguna consulta SELECT
-- leia Valoracion contra la base real (los tests de ValoracionService son
-- todos con mocks; la unica escritura previa via API no necesita releer la
-- fila). Se detecto al construir el listado publico de valoraciones (RF-4.4)
-- y probarlo en vivo: Hibernate arranca sin abortar (queda en el log un
-- intento fallido de "ALTER TABLE ... ADD COLUMN id_valoracion", que tampoco
-- puede completar porque la tabla ya tiene una columna auto_increment), pero
-- cualquier SELECT explota con
-- "Unknown column 'v1_0.id_valoracion' in 'field list'".
--
-- COMO APLICARLO
--   mysql -u root enexia < "docs/diseño_bd/migraciones/2026-09-29_valoracion_columna.sql"
--
-- Es idempotente: si id_puntuacion ya no existe (porque el script ya corrio),
-- no hace nada.
-- =====================================================================

SET @columna_vieja_existe := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'valoracion' AND COLUMN_NAME = 'id_puntuacion'
);

SET @sql := IF(@columna_vieja_existe > 0,
    'ALTER TABLE valoracion CHANGE COLUMN id_puntuacion id_valoracion bigint(20) NOT NULL AUTO_INCREMENT',
    'SELECT "id_puntuacion ya no existe, nada que hacer" AS resultado');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. comentario: la columna quedo en varchar(255) (el default de Hibernate
--    para un @Column sin `length`), pero ValoracionRequest.comentario valida
--    hasta 1000 caracteres (RF-3.4: "un campo de texto para comentarios y
--    reseñas cualitativas"). Con sql_mode=STRICT_TRANS_TABLES, un comentario
--    de entre 256 y 1000 caracteres pasaba la validacion del DTO y explotaba
--    recien en el INSERT con "Data too long for column 'comentario'" -- el
--    mismo genero de error que el 409 indescifrable que se corrigio para
--    Evento_Cronograma/Cronograma_Ticket al editar un evento (ver
--    PublicacionEventoService), ahora en Valoracion.
-- ---------------------------------------------------------------------
ALTER TABLE valoracion MODIFY COLUMN comentario varchar(1000) NULL;
