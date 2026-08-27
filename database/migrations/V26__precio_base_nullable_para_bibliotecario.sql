-- Permitir precio_base nulo para flujos BIBLIOTECARIO (solo GERENTE/ADMIN lo proveen)
ALTER TABLE libros ALTER COLUMN precio_base DROP NOT NULL;
