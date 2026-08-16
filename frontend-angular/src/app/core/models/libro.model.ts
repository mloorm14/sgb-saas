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
  resumen?: string;
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