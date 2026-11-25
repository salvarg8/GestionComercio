-- =====================================================================
-- V10: control del efectivo en caja (COM42)
--
-- empresas.monto_maximo_caja          saldo de caja a partir del cual hay
--                                     que retirar dinero; null = sin control
-- empresas.clientes_antes_de_bloqueo  ventas que se permiten una vez
--                                     alcanzado el monto; null = nunca se
--                                     bloquea. Sólo con monto máximo.
-- empresas.retiro_requiere_confirmacion  los retiros de caja los confirma
--                                     un usuario con CAJA_CONFIRMAR_RETIRO
-- movimientos_caja.usuario_autorizacion_id  quién confirmó el retiro
--
-- El permiso CAJA_CONFIRMAR_RETIRO lo crea DataInitializer a partir de
-- PermisoCodigo y lo asigna al rol ADMIN.
-- =====================================================================

ALTER TABLE empresas
    ADD COLUMN monto_maximo_caja            numeric(12, 2),
    ADD COLUMN clientes_antes_de_bloqueo    integer,
    ADD COLUMN retiro_requiere_confirmacion boolean NOT NULL DEFAULT false;

ALTER TABLE empresas
    ADD CONSTRAINT ck_empresas_monto_maximo_caja
        CHECK (monto_maximo_caja IS NULL OR monto_maximo_caja > 0),
    ADD CONSTRAINT ck_empresas_clientes_antes_de_bloqueo
        CHECK (clientes_antes_de_bloqueo IS NULL
            OR (clientes_antes_de_bloqueo >= 0 AND monto_maximo_caja IS NOT NULL));

ALTER TABLE movimientos_caja
    ADD COLUMN usuario_autorizacion_id bigint;

ALTER TABLE movimientos_caja
    ADD CONSTRAINT fk_movimientos_caja_usuario_autorizacion
        FOREIGN KEY (usuario_autorizacion_id) REFERENCES usuarios (id);

CREATE INDEX idx_movimientos_caja_usuario_autorizacion ON movimientos_caja (usuario_autorizacion_id);
