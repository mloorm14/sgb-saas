// Contratos exactos de LibroController (/api/v1/libros), ver
// LibroResponseDTO y LibroRequestDTO en backend-springboot.
export interface Libro {
  id: number;
  titulo: string;
  isbn: string;
  resumen: string;
  portadaUrl: string;
  tienePortada: boolean;
  portadaNombre: string;
  portadaTipo: string;
  anioPublicacion: number;
  numeroPaginas?: number | null;
  precioBase?: number | null;
  editorialId: number;
  editorial: string;
  idiomaId: number;
  idioma: string;
  estadoId: number;
  estado: string;
  stockTotal: number;
  stockDisponible: number;
  ubicacionFisica: string;
  fechaRegistro: string;
  categorias: string[];
  autores: string[];
}

export interface LibroRequest {
  titulo: string;
  isbn: string;
  anioPublicacion: number;
  numeroPaginas?: number | null;
  precioBase?: number | null;
  resumen?: string;
  ubicacionFisica?: string;
  portadaUrl?: string;
  editorialId: number;
  idiomaId: number;
  estadoId: number;
  stockTotal: number;
  stockDisponible: number;
  categoriaIds?: number[];
  autorIds?: number[];
}

// Respuesta de GET /v1/libros/sugerencias?texto= (LibroSugerenciaDTO).
export interface LibroSugerencia {
  id: number;
  titulo: string;
  disponible: boolean;
}

// Respuesta de GET /v1/libros/lookup-isbn?isbn= (LibroIsbnLookupDTO,
// Google Books). anioPublicacion puede venir null si no hay fecha; la
// portada NO viaja acá (se descarga aparte con portadaPorIsbn()).
export interface LibroIsbnLookup {
  titulo: string;
  autor: string;
  resumen: string;
  anioPublicacion: number | null;
  portadaDisponible: boolean;
  editorial: string | null;
}