package com.enexia.rg.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.enexia.rg.model.Ubicacion;

/**
 * Domicilios (RF-7.2 domicilio fiscal, RF-2.1 lugar del evento).
 *
 * No se deduplican: dos eventos en la misma direccion generan dos filas. Es
 * deliberado: compartir la fila haria que editar la direccion de un evento
 * cambiara silenciosamente la del otro.
 */
@Repository
public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {
}
