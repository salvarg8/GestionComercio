-- =====================================================================
-- V4: ancho del ticket (COM27)
--
-- Ancho del rollo de la impresora térmica donde se imprimen los tickets,
-- configurable en los datos de la empresa. Por defecto, 80 mm.
-- =====================================================================

ALTER TABLE empresas
    ADD COLUMN ancho_ticket varchar(255) NOT NULL DEFAULT 'MM_80',
    ADD CONSTRAINT ck_empresas_ancho_ticket CHECK (ancho_ticket IN ('MM_58', 'MM_80'));
