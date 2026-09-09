-- =====================================================================
-- Enexia - Migracion Sprint 2  (2026-09-08)
-- =====================================================================
--
-- POR QUE HACE FALTA ESTE ARCHIVO
-- La aplicacion corre con `spring.jpa.hibernate.ddl-auto=update`. Ese modo solo
-- AGREGA: crea tablas y columnas nuevas a partir de las @Entity, pero NUNCA
-- borra ni modifica lo existente, justamente para no destruir datos por un
-- descuido en el codigo. Las columnas eliminadas y los UNIQUE nuevos hay que
-- aplicarlos a mano, y eso es lo que hace este script.
--
-- Las columnas AGREGADAS tampoco puede crearlas Hibernate en este entorno: la
-- MariaDB 10.4 de XAMPP rechaza la sintaxis que genera. El detalle esta en la
-- seccion 3. Por eso este script NO es opcional.
--
-- COMO APLICARLO
--   mysql -u root -p enexia < docs/diseño_bd/migraciones/2026-09-08_sprint2.sql
--
-- Es idempotente: se puede correr mas de una vez sin romper nada.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. Reparacion: columnas de persona_juridica que nunca llegaron a crearse
-- ---------------------------------------------------------------------
-- Estas cuatro columnas estan declaradas en la @Entity PersonaJuridica desde
-- Sprint 1 (2026-07-26), pero NO existian en la base. Es el mismo defecto que se
-- explica en la seccion 3: Hibernate intento agregarlas con
-- `ALTER TABLE IF EXISTS`, MariaDB 10.4 rechazo la sintaxis, el error quedo en
-- el log y el arranque siguio como si nada.
--
-- Nadie lo noto porque hasta ahora ninguna consulta tocaba persona_juridica.
-- La primera que lo hizo -- el catalogo publico, que la incluye para resolver la
-- firma del organizador (RF-7.4) -- fallo con
-- "Unknown column 'pj1_0.id_estado_persona_juridica' in 'field list'".
--
-- Es exactamente el motivo por el que este archivo existe.
ALTER TABLE persona_juridica ADD COLUMN IF NOT EXISTS nombre_fantasia varchar(255) NULL;
ALTER TABLE persona_juridica ADD COLUMN IF NOT EXISTS fecha_registro datetime(6) NULL;
ALTER TABLE persona_juridica
    ADD COLUMN IF NOT EXISTS id_estado_persona_juridica bigint(20) NULL;
ALTER TABLE persona_juridica
    ADD COLUMN IF NOT EXISTS id_estado_persona_juridica_sistema bigint(20) NULL;

DROP PROCEDURE IF EXISTS enexia_agregar_fks_pj;
DELIMITER //
CREATE PROCEDURE enexia_agregar_fks_pj()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'persona_juridica'
          AND CONSTRAINT_NAME = 'fk_pj_estado'
    ) THEN
        ALTER TABLE persona_juridica
            ADD CONSTRAINT fk_pj_estado
            FOREIGN KEY (id_estado_persona_juridica)
            REFERENCES persona_juridica_estado (id_estado_persona_juridica);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'persona_juridica'
          AND CONSTRAINT_NAME = 'fk_pj_estado_sistema'
    ) THEN
        ALTER TABLE persona_juridica
            ADD CONSTRAINT fk_pj_estado_sistema
            FOREIGN KEY (id_estado_persona_juridica_sistema)
            REFERENCES persona_juridica_estado_sistema (id_estado_persona_juridica_sistema);
    END IF;
END //
DELIMITER ;
CALL enexia_agregar_fks_pj();
DROP PROCEDURE IF EXISTS enexia_agregar_fks_pj;

-- ---------------------------------------------------------------------
-- 1. Persona: se elimina tipo_persona
-- ---------------------------------------------------------------------
-- La columna suponia una jerarquia Persona -> (Fisica | Juridica) que el modelo
-- nunca tuvo: el MER solo declara `Persona ||--|| Persona_Fisica`, y
-- Persona_Juridica es una entidad independiente, sin FK a persona. En la
-- practica la columna valia "FISICA" en el 100% de las filas -- no discriminaba
-- nada -- y admitir "JURIDICA" habria permitido crear una persona juridica sin
-- persona fisica asociada, rompiendo el invariante de que todo Usuario es una
-- persona humana.
ALTER TABLE persona DROP COLUMN IF EXISTS tipo_persona;

-- ---------------------------------------------------------------------
-- 2. Persona_Juridica: UNIQUE sobre el CUIT
-- ---------------------------------------------------------------------
-- El existsByCuit del service da el mensaje amigable, pero es este indice el que
-- garantiza la unicidad cuando dos altas con el mismo CUIT corren en paralelo:
-- ambas pasan el chequeo previo (ninguna ve la fila sin confirmar de la otra) y
-- solo el indice, atomico, frena a la segunda.
--
-- El CUIT se persiste NORMALIZADO (solo los 11 digitos). Sin esa normalizacion,
-- "30-71659554-0" y "30716595540" serian dos cadenas distintas y el UNIQUE no
-- impediria el duplicado.
--
-- Se usa un procedimiento porque MariaDB no soporta
-- "ADD CONSTRAINT IF NOT EXISTS" para indices UNIQUE.
DROP PROCEDURE IF EXISTS enexia_agregar_uk_cuit;
DELIMITER //
CREATE PROCEDURE enexia_agregar_uk_cuit()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'persona_juridica'
          AND INDEX_NAME = 'uk_persona_juridica_cuit'
    ) THEN
        ALTER TABLE persona_juridica
            ADD CONSTRAINT uk_persona_juridica_cuit UNIQUE (cuit);
    END IF;
END //
DELIMITER ;
CALL enexia_agregar_uk_cuit();
DROP PROCEDURE IF EXISTS enexia_agregar_uk_cuit;

-- ---------------------------------------------------------------------
-- 3. Evento: columnas nuevas
-- ---------------------------------------------------------------------
-- fecha_creacion      -> la exige RF-2.2 como parte del registro "skeleton".
-- id_persona_juridica -> RF-2.1 Sprint 2 y RF-7.4: bajo que organizacion se
--                        publica el evento. NULLABLE: null = a titulo personal.
--
-- *** HALLAZGO IMPORTANTE (2026-09-08) ***
-- Estas dos columnas TIENEN que agregarse desde aca: `ddl-auto=update` NO puede
-- crearlas en este entorno.
--
-- Motivo: Hibernate genera `ALTER TABLE IF EXISTS evento ADD COLUMN ...`, y la
-- version de MariaDB que trae XAMPP (10.4.32) NO soporta `IF EXISTS` en un
-- ALTER TABLE: responde error 1064 de sintaxis. Hibernate registra el fallo en
-- el log pero NO detiene el arranque, asi que la aplicacion levanta con normalidad
-- y la columna simplemente no existe. El sintoma aparece despues, en tiempo de
-- ejecucion, como "Unknown column 'e1_0.fecha_creacion' in 'field list'".
--
-- CONSECUENCIA PARA EL EQUIPO: toda columna que se agregue a una @Entity de
-- ahora en mas hay que sumarla tambien a un script de migracion como este. La
-- alternativa de fondo es actualizar MariaDB a 10.6+ (minimo que soporta Hibernate 7) o pasar a Flyway/Liquibase,
-- que es lo que corresponde antes de produccion (con `ddl-auto=validate`).
ALTER TABLE evento ADD COLUMN IF NOT EXISTS fecha_creacion datetime(6) NULL;
ALTER TABLE evento ADD COLUMN IF NOT EXISTS id_persona_juridica bigint(20) NULL;

DROP PROCEDURE IF EXISTS enexia_agregar_fk_evento_pj;
DELIMITER //
CREATE PROCEDURE enexia_agregar_fk_evento_pj()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'evento'
          AND CONSTRAINT_NAME = 'fk_evento_persona_juridica'
    ) THEN
        ALTER TABLE evento
            ADD CONSTRAINT fk_evento_persona_juridica
            FOREIGN KEY (id_persona_juridica)
            REFERENCES persona_juridica (id_persona_juridica);
    END IF;
END //
DELIMITER ;
CALL enexia_agregar_fk_evento_pj();
DROP PROCEDURE IF EXISTS enexia_agregar_fk_evento_pj;

-- ---------------------------------------------------------------------
-- 4. Indices de apoyo al catalogo publico (RF-4.1 a RF-4.3)
-- ---------------------------------------------------------------------
-- La consulta del catalogo filtra siempre por los dos estados y muy seguido por
-- categoria. Sin indices, cada busqueda es un recorrido completo de la tabla;
-- con pocos eventos no se nota, pero es exactamente el tipo de cosa que deja de
-- cumplir el requisito no funcional de "2 segundos para lecturas" cuando la
-- tabla crece.
DROP PROCEDURE IF EXISTS enexia_agregar_indices_catalogo;
DELIMITER //
CREATE PROCEDURE enexia_agregar_indices_catalogo()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'evento'
          AND INDEX_NAME = 'ix_evento_estados'
    ) THEN
        ALTER TABLE evento
            ADD INDEX ix_evento_estados (id_estado_sistema, id_estado_organizador);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'visita'
          AND INDEX_NAME = 'ix_visita_evento'
    ) THEN
        ALTER TABLE visita ADD INDEX ix_visita_evento (id_evento, id_usuario);
    END IF;
END //
DELIMITER ;
CALL enexia_agregar_indices_catalogo();
DROP PROCEDURE IF EXISTS enexia_agregar_indices_catalogo;

-- =====================================================================
-- Fin de la migracion
-- =====================================================================
