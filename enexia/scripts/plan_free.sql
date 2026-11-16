DROP PROCEDURE IF EXISTS sp_asignar_plan_free_usuarios_existentes;
DELIMITER $$
CREATE PROCEDURE sp_asignar_plan_free_usuarios_existentes()
BEGIN
    -- 1. Insertar suscripciones FREE para los que no son administradores y no tienen suscripción
    INSERT INTO suscripcion (id_usuario, id_estado_suscripcion, tipo_plan, fecha_inicio, fecha_fin)
    SELECT 
        u.id_usuario, 
        se.id_estado_suscripcion, 
        'FREE', 
        CURDATE(), 
        NULL
    FROM usuario u
    INNER JOIN suscripcion_estado se ON UPPER(se.nombre_estado) = 'ACTIVA'
    WHERE NOT EXISTS (
        SELECT 1 
        FROM usuario_rol ur
        INNER JOIN rol r ON ur.id_rol = r.id_rol
        WHERE ur.id_usuario = u.id_usuario 
          AND UPPER(r.nombre_rol) = 'ADMINISTRADOR'
    )
    AND NOT EXISTS (
        SELECT 1 
        FROM suscripcion s 
        WHERE s.id_usuario = u.id_usuario
    );
    -- 2. Registrar en el historial de suscripciones
    INSERT INTO historial_estado_suscripcion (id_suscripcion, id_estado_suscripcion, fecha_cambio, motivo_cambio)
    SELECT 
        s.id_suscripcion, 
        s.id_estado_suscripcion, 
        NOW(), 
        'Asignación masiva inicial plan FREE'
    FROM suscripcion s
    LEFT JOIN historial_estado_suscripcion hes ON s.id_suscripcion = hes.id_suscripcion
    WHERE hes.id_historial IS NULL;
END$$
DELIMITER ;