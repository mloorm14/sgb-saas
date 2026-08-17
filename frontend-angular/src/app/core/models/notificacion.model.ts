// NotificacionResponseDTO tal como la serializa el backend
// (com.uteq.backend.dto.NotificacionResponseDTO)
// Campos reales: id, prestamoId, tipoNotificacionId, mensaje, fechaEnvio, enviadoOk, creadoEn
export interface Notificacion {
  id: number;
  prestamoId: number;
  tipoNotificacionId: number;
  mensaje: string;
  fechaEnvio: string; // ISO string (OffsetDateTime serializado)
  enviadoOk: boolean;
  creadoEn: string; // ISO string (OffsetDateTime serializado)
}

export interface NotificacionListarParams {
  page?: number;
  size?: number;
}