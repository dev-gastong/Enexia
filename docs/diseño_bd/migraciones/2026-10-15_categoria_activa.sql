-- RF-6.3: baja logica de categorias.
-- Una categoria inactiva no se ofrece para eventos nuevos ni en los filtros del
-- catalogo, pero los eventos que ya la tienen la conservan (sin huerfanos).
--
-- El DEFAULT 1 es OBLIGATORIO: sin el, al agregar la columna a una tabla con
-- filas, MariaDB las deja en 0 (inactivas) y el catalogo se queda sin categorias.
-- Con ddl-auto=update Hibernate ya la crea con ese default (columnDefinition en
-- Categoria.java); este script es para entornos con ddl-auto=validate.

ALTER TABLE categoria
    ADD COLUMN IF NOT EXISTS activa BIT(1) NOT NULL DEFAULT 1;
