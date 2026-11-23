-- COM37: licencia del comercio. Una sola fila (id = 1) compartida por todas
-- las PC: la licencia lista los códigos de las PC habilitadas.
--
-- clave             clave de licencia firmada (null: período de prueba)
-- inicio_prueba     inicio del período de prueba (la instalación)
-- ultima_fecha      fecha más reciente en que se usó el sistema, para detectar
--                   un reloj atrasado a propósito para estirar el vencimiento
-- fecha_activacion  cuándo se activó la licencia actual
CREATE TABLE licencia (
    id               bigint       NOT NULL PRIMARY KEY,
    clave            text,
    inicio_prueba    date         NOT NULL,
    ultima_fecha     date         NOT NULL,
    fecha_activacion timestamp(6),
    CONSTRAINT ck_licencia_unica CHECK (id = 1)
);

INSERT INTO licencia (id, inicio_prueba, ultima_fecha) VALUES (1, current_date, current_date);
