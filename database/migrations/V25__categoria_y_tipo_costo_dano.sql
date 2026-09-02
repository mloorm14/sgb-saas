-- Categorías de daño y tipo de costo FIJO/PORCENTAJE ligado a precio_base
CREATE TABLE IF NOT EXISTS categorias_dano (
  id SERIAL PRIMARY KEY,
  nombre VARCHAR(50) NOT NULL UNIQUE,
  activo BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO categorias_dano (nombre) VALUES ('Leve'),('Moderado'),('Grave'),('Pérdida total')
ON CONFLICT (nombre) DO NOTHING;

-- precio_base obligatorio (backfill antes de NOT NULL)
UPDATE libros SET precio_base = 15.00 WHERE precio_base IS NULL;
ALTER TABLE libros ALTER COLUMN precio_base SET NOT NULL;

-- tipos_dano: añadir categoria + tipo_costo + valor, migrar precio
ALTER TABLE tipos_dano ADD COLUMN IF NOT EXISTS categoria_id INT REFERENCES categorias_dano(id);
ALTER TABLE tipos_dano ADD COLUMN IF NOT EXISTS tipo_costo VARCHAR(10) CHECK (tipo_costo IN ('FIJO','PORCENTAJE'));
ALTER TABLE tipos_dano ADD COLUMN IF NOT EXISTS valor NUMERIC(10,2) CHECK (valor >= 0);
-- migrar datos existentes: precio -> valor, FIJO
UPDATE tipos_dano SET tipo_costo = 'FIJO' WHERE tipo_costo IS NULL;
UPDATE tipos_dano SET valor = precio WHERE valor IS NULL AND precio IS NOT NULL;
-- asignar categoría por defecto a existentes
UPDATE tipos_dano SET categoria_id = (SELECT id FROM categorias_dano WHERE nombre='Grave') WHERE categoria_id IS NULL;

ALTER TABLE tipos_dano ALTER COLUMN tipo_costo SET NOT NULL;
ALTER TABLE tipos_dano ALTER COLUMN valor SET NOT NULL;
ALTER TABLE tipos_dano ALTER COLUMN categoria_id SET NOT NULL;
-- chequeo porcentaje 0-100
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='chk_tipos_dano_porcentaje') THEN
    ALTER TABLE tipos_dano ADD CONSTRAINT chk_tipos_dano_porcentaje CHECK (tipo_costo='FIJO' OR (valor BETWEEN 0 AND 100));
  END IF;
END $$;
-- ahora se puede dropear precio si existe
ALTER TABLE tipos_dano DROP COLUMN IF EXISTS precio;

-- inserts paso 2 (upsert por nombre)
INSERT INTO tipos_dano (nombre, categoria_id, tipo_costo, valor) VALUES
  ('Manchado de portada', (SELECT id FROM categorias_dano WHERE nombre='Leve'), 'FIJO', 10),
  ('Esquinas dobladas', (SELECT id FROM categorias_dano WHERE nombre='Leve'), 'FIJO', 5),
  ('Páginas sueltas (1-5)', (SELECT id FROM categorias_dano WHERE nombre='Moderado'), 'PORCENTAJE', 20),
  ('Rayones en el interior', (SELECT id FROM categorias_dano WHERE nombre='Moderado'), 'FIJO', 8),
  ('Páginas rotas/faltantes', (SELECT id FROM categorias_dano WHERE nombre='Grave'), 'PORCENTAJE', 50),
  ('Libro no recuperable', (SELECT id FROM categorias_dano WHERE nombre='Pérdida total'), 'PORCENTAJE', 100)
ON CONFLICT (nombre) DO UPDATE SET categoria_id=EXCLUDED.categoria_id, tipo_costo=EXCLUDED.tipo_costo, valor=EXCLUDED.valor;
