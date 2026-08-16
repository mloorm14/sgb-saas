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
}