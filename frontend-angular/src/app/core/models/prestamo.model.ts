// Contratos exactos de PrestamoController (/api/v1/prestamos), ver los
// DTOs en backend-springboot. Nota: PrestamoResponseDTO NO trae el titulo
// del libro (solo libroId); PrestamoActivoResponseDTO SI lo trae.
export interface Prestamo {
  id: number;
  usuarioId: number;
  libroId: number;
  bibliotecarioId: number;
  reservacionId: number;
  fechaPrestamo: string;
  fechaDevolucionEstimada: string;
  fechaDevolucionReal: string;
  renovacionesRealizadas: number;
  estadoPrestamoId: number;
}

export interface PrestamoActivo {
  prestamoId: number;
  libroTitulo: string;
  libroIsbn: string;
  fechaPrestamo: string;
  fechaDevolucionEstimada: string;
  diasRestantes: number;
  estadoNombre: string;
}

// PrestamoRequestDTO: exactamente uno de usuarioId o credencialQrToken.
export interface PrestamoRequest {
  usuarioId?: number;
  credencialQrToken?: string;
  libroId: number;
  diasPrestamo: number;
}

export interface RenovacionResponse {
  prestamoId: number;
  nuevaFechaDevolucionEstimada: string;
  renovacionesRealizadas: number;
  renovacionesRestantes: number;
}

export interface DevolucionResponse {
  prestamoId: number;
  huboMulta: boolean;
  montoMulta: number;
}

// Reportes (Rama F los consume): GET /v1/prestamos/reportes/*
export interface LibroMasPrestado {
  libroId: number;
  titulo: string;
  isbn: string;
  totalPrestamos: number;
}

export interface ReporteMorosidad {
  usuarioId: number;
  nombre: string;
  apellido: string;
  correo: string;
  montoTotalAdeudado: number;
  cantidadMultasPendientes: number;
  diasAtrasoPromedio: number;
}

export interface ReporteUsoPorPeriodo {
  periodo: string;
  totalPrestamos: number;
  totalDevoluciones: number;
}