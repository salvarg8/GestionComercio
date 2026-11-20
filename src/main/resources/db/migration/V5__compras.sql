-- =====================================================================
-- V5: registro de compras (COM28.1)
--
-- Una compra guarda su forma de pago y, si se pagó en efectivo, la caja
-- de donde salió el dinero. Como las ventas, una compra anulada guarda
-- cuándo, quién y por qué se anuló.
--
-- Las compras que ya existieran no tienen forma de pago conocida: quedan
-- en cuenta corriente, que no mueve caja al anularlas.
-- =====================================================================

ALTER TABLE compras
    ADD COLUMN forma_pago           varchar(255) NOT NULL DEFAULT 'CUENTA_CORRIENTE',
    ADD COLUMN caja_id              bigint,
    ADD COLUMN fecha_anulacion      timestamp(6),
    ADD COLUMN motivo_anulacion     varchar(255),
    ADD COLUMN usuario_anulacion_id bigint;

ALTER TABLE compras
    ALTER COLUMN forma_pago DROP DEFAULT;

ALTER TABLE compras
    ADD CONSTRAINT fk_compras_caja
        FOREIGN KEY (caja_id) REFERENCES cajas (id),
    ADD CONSTRAINT fk_compras_usuario_anulacion
        FOREIGN KEY (usuario_anulacion_id) REFERENCES usuarios (id),
    ADD CONSTRAINT ck_compras_caja
        CHECK (caja_id IS NULL OR forma_pago = 'EFECTIVO'),
    ADD CONSTRAINT ck_compras_numero_factura
        CHECK (numero_factura IS NULL OR btrim(numero_factura) <> ''),
    ADD CONSTRAINT ck_compras_motivo_anulacion
        CHECK (motivo_anulacion IS NULL OR btrim(motivo_anulacion) <> ''),
    ADD CONSTRAINT ck_compras_anulacion
        CHECK ((estado_compra = 'ANULADA') = (fecha_anulacion IS NOT NULL
                                               AND motivo_anulacion IS NOT NULL
                                               AND usuario_anulacion_id IS NOT NULL));

-- Una misma factura de un proveedor se registra una sola vez (sin distinguir
-- mayúsculas). Anular la compra libera el número para volver a cargarla.
CREATE UNIQUE INDEX uk_compras_factura_proveedor
    ON compras (proveedor_id, upper(numero_factura))
    WHERE numero_factura IS NOT NULL AND estado_compra <> 'ANULADA';

CREATE INDEX idx_compras_caja ON compras (caja_id);
