// Contratos exactos de MultaController (/api/v1/multas), ver los DTOs en
// backend-springboot. Pagar y anular son POST, no PATCH/PUT.
export interface Multa {
  id: number;
  prestamoId: number;
  monto: number;
  estadoMultaId: number;
  fechaGenerada: string;
  fechaPagada: string;
  observaciones: string;
}

export interface AnulacionMultaRequest {
  motivo: string;
}

export interface MultaAccionResponse {
  multaId: number;
  usuarioDesbloqueado: boolean;
}

// GET /api/v1/multas/reportes/resumen-financiero (dashboard GERENTE/ADMIN).
export interface ResumenFinancieroMultas {
  totalRecaudado: number;
  totalPendiente: number;
}