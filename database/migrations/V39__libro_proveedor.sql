-- V39: vincular libro con proveedor opcional (S/P si null).
ALTER TABLE libros
    ADD COLUMN IF NOT EXISTS proveedor_id INTEGER REFERENCES proveedores(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_libros_proveedor ON libros (proveedor_id);
