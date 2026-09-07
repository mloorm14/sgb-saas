// Contratos de ReservacionesGestionController (/api/v1/reservaciones/gestion).
// Modelos de la gestión de reservaciones en ventanilla: búsqueda por correo e historial.

// UsuarioReservacionesGestionDTO: tarjeta de identificación del usuario
// encontrado. Incluye cantidad de reservas activas y el límite permitido
// para el badge "X/3 Reservas activas".
export interface UsuarioReservaciones {
  id: number;
  nombreCompleto: string;
  correo: string;
  estadoCuenta: string;
  cantidadReservasActivas: number;
  limiteReservas: number;
}

// HistorialReservacionDTO: tarjeta del historial de reservaciones.
// El título del libro viene resuelto para evitar llamadas N+1.
export interface HistorialReservacion {
  reservacionId: number;
  libroTitulo: string;
  estadoNombre: string;
  estadoId: number;
  fechaReserva: string;
  fechaLimiteRetiro: string;
}
