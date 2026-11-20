-- =====================================================================
-- V3: comprobante de venta (COM27)
--
-- Cada venta tiene un tipo de comprobante y un número correlativo propio
-- de ese tipo. Las ventas anteriores quedan como TICKET, numeradas en el
-- orden en que se registraron.
--
-- numeradores_comprobante guarda el último número usado de cada tipo. Se
-- incrementa con un UPDATE que bloquea la fila hasta que termina la
-- transacción: dos ventas simultáneas del mismo tipo obtienen números
-- distintos, y si la venta falla el número no se consume.
-- =====================================================================

ALTER TABLE ventas
    ADD COLUMN tipo_comprobante   varchar(255),
    ADD COLUMN numero_comprobante bigint;

UPDATE ventas v
SET tipo_comprobante   = 'TICKET',
    numero_comprobante = n.numero
FROM (SELECT id, row_number() OVER (ORDER BY id) AS numero FROM ventas) n
WHERE v.id = n.id;

ALTER TABLE ventas
    ALTER COLUMN tipo_comprobante SET NOT NULL,
    ALTER COLUMN numero_comprobante SET NOT NULL,
    ADD CONSTRAINT uk_ventas_comprobante UNIQUE (tipo_comprobante, numero_comprobante),
    ADD CONSTRAINT ck_ventas_numero_comprobante CHECK (numero_comprobante > 0);

CREATE TABLE numeradores_comprobante (
    tipo_comprobante varchar(255) PRIMARY KEY,
    ultimo_numero    bigint NOT NULL,
    CONSTRAINT ck_numeradores_comprobante_ultimo_numero CHECK (ultimo_numero >= 0)
);

INSERT INTO numeradores_comprobante (tipo_comprobante, ultimo_numero)
SELECT 'TICKET', coalesce(max(numero_comprobante), 0)
FROM ventas;
