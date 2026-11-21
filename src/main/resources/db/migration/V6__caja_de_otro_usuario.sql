-- =====================================================================
-- V6: abrir y cerrar la caja de otro usuario (COM41)
--
-- usuario_id sigue siendo el responsable de la caja (quien vende con
-- ella). Se agregan, para la auditoría, el usuario que la abrió y el que
-- la cerró, que pueden ser distintos del responsable: por ejemplo, un
-- encargado que abre la caja de un cajero al empezar el turno.
--
-- Los permisos CAJA_ABRIR_OTRO_USUARIO y CAJA_CERRAR_OTRO_USUARIO los crea
-- DataInitializer a partir de PermisoCodigo y los asigna al rol ADMIN.
--
-- En las cajas que ya existían, el responsable fue quien las abrió y, si
-- están cerradas, quien las cerró.
-- =====================================================================

ALTER TABLE cajas
    ADD COLUMN usuario_apertura_id bigint,
    ADD COLUMN usuario_cierre_id   bigint;

UPDATE cajas
SET usuario_apertura_id = usuario_id,
    usuario_cierre_id   = CASE WHEN estado_caja = 'CERRADA' THEN usuario_id END;

ALTER TABLE cajas
    ALTER COLUMN usuario_apertura_id SET NOT NULL;

ALTER TABLE cajas
    ADD CONSTRAINT fk_cajas_usuario_apertura
        FOREIGN KEY (usuario_apertura_id) REFERENCES usuarios (id),
    ADD CONSTRAINT fk_cajas_usuario_cierre
        FOREIGN KEY (usuario_cierre_id) REFERENCES usuarios (id),
    ADD CONSTRAINT ck_cajas_usuario_cierre
        CHECK ((estado_caja = 'CERRADA') = (usuario_cierre_id IS NOT NULL));

CREATE INDEX idx_cajas_usuario_apertura ON cajas (usuario_apertura_id);
CREATE INDEX idx_cajas_usuario_cierre ON cajas (usuario_cierre_id);
