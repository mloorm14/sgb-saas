// Errores RFC 7807, no {success, data}.
// Los services lo usan en catchError para leer title/status/detail/errores.
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  errores?: Record<string, string>;
}