-- Ajuste biblioteca: numero_paginas, precio_base, estado PENDIENTE
ALTER TABLE libros ADD COLUMN IF NOT EXISTS numero_paginas SMALLINT CHECK (numero_paginas > 0);
ALTER TABLE libros ADD COLUMN IF NOT EXISTS precio_base NUMERIC(10,2) CHECK (precio_base >= 0);

INSERT INTO estados_libro (nombre) VALUES ('PENDIENTE') ON CONFLICT (nombre) DO NOTHING;
