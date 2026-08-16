// RFC 7807 (application/problem+json): el backend responde errores en este
// formato (hallazgo mu del reporte — NO es {success, data, ...}). Los
// services de dominio lo usan en su catchError para que los componentes
// lean title/status/detail/errores de forma consistente.
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  errores?: Record<string, string>;
}