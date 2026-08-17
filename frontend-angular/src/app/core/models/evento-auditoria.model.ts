// Contrato de AuditoriaController (/api/v1/auditoria),
// EventoAuditoriaResponseDTO en backend-springboot. `usuario` puede venir
// null (p.ej. LOGIN_FAIL antes de identificar al usuario) -- la vista lo
// muestra como "—", nunca como texto vacío.
export interface EventoAuditoria {
  id: number;
  usuario: string | null;
  accion: string;
  fechaHora: string;
  modulo: string;
  detalle: string;
}