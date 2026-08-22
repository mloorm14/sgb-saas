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

export interface MultaDetalle {
  id: number;
  prestamoId: number;
  libroTitulo: string;
  libroIsbn: string;
  observaciones: string;
  monto: number;
  montoPagado: number;
  saldo: number;
  estadoMultaId: number;
  estadoNombre: string;
  fechaGenerada: string;
  fechaPagada: string;
  fechaPrestamoInicio: string;
  fechaPrestamoFin: string;
  diasAtraso: number;
}

export interface PagoMultaRequest {
  montoPagado: number;
}

export interface PagoMultaResponse {
  o_multa_id: number;
  o_estado: string;
  o_saldo_restante: number;
  o_usuario_desbloqueado: boolean;
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