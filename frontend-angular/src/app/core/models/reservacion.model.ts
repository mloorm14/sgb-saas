// Contratos exactos de ReservacionController (/api/v1/reservaciones), ver
// ReservacionResponseDTO y ReservacionRequestDTO en backend-springboot.
export interface Reservacion {
  id: number;
  usuarioId: number;
  libroId: number;
  estadoReservacionId: number;
  fechaReserva: string;
  fechaLimiteRetiro: string;
}

export interface ReservacionRequest {
  usuarioId: number;
  libroId: number;
  fechaRetiro?: string;
}

// Body de PATCH /api/v1/reservaciones/{id}/estado (CambioEstadoReservacionRequestDTO).
// El patrón del backend restringe a LISTA_PARA_RETIRO (aceptar) o CANCELADA (rechazar).
export interface CambioEstadoReservacionRequest {
  nuevoEstado: 'LISTA_PARA_RETIRO' | 'CANCELADA';
}