-- Migración rota a propósito para MigracionesIntegrationTest: crea una tabla,
-- le inserta una fila y después falla. Como PostgreSQL tiene DDL
-- transaccional, no debe quedar nada de lo que hizo.
CREATE TABLE tabla_de_prueba (id integer PRIMARY KEY);

INSERT INTO tabla_de_prueba (id) VALUES (1);

SELECT 1 / 0;
