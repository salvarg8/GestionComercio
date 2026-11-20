-- =====================================================================
-- V2: anulación de ventas (COM26)
--
-- Una venta anulada guarda cuándo, quién y por qué se anuló. Los tres
-- datos van juntos y sólo en ventas ANULADAS.
-- =====================================================================

ALTER TABLE ventas
    ADD COLUMN fecha_anulacion      timestamp(6),
    ADD COLUMN motivo_anulacion     varchar(255),
    ADD COLUMN usuario_anulacion_id bigint;

ALTER TABLE ventas
    ADD CONSTRAINT fk_ventas_usuario_anulacion
        FOREIGN KEY (usuario_anulacion_id) REFERENCES usuarios (id),
    ADD CONSTRAINT ck_ventas_motivo_anulacion
        CHECK (motivo_anulacion IS NULL OR btrim(motivo_anulacion) <> ''),
    ADD CONSTRAINT ck_ventas_anulacion
        CHECK ((estado_venta = 'ANULADA') = (fecha_anulacion IS NOT NULL
                                              AND motivo_anulacion IS NOT NULL
                                              AND usuario_anulacion_id IS NOT NULL));
