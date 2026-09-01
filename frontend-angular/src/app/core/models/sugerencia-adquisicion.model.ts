// Contrato de SugerenciaAdquisicionController (/api/v1/sugerencias-adquisicion),
// ver los DTOs en backend-springboot. El request replica las validaciones
// exactas de SugerenciaAdquisicionRequestDTO.
export interface SugerenciaAdquisicion {
  id: number;
  usuarioId: number;
  titulo: string;
  autor: string;
  isbn: string;
  justificacion: string;
  estado: string;
  revisadoPor: number;
  proveedorId?: number | null;
  creadoEn: string;
}

export interface SugerenciaAdquisicionRequest {
  titulo: string;
  autor?: string;
  isbn?: string;
  justificacion?: string;
}