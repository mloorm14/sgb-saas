// Contratos de DevolucionController (/api/v1/devoluciones).
// DTOs en backend: DevolucionRequestDTO, DevolucionCompletaResponseDTO,
// DanoDetalleResponseDTO, DevolucionHistorialDTO, TipoDanoDTO.

export interface TipoDano {
  id: number;
  nombre: string;
  precio: number;
}

export interface DanoItem {
  tipoDanoId: number | null;
  nombreCustom: string | null;
  precioCobrado: number;
}

export interface DevolucionRequest {
  estadoDevolucion: string;
  descripcion: string | null;
  danos: DanoItem[] | null;
}

export interface DanoDetalleResponse {
  id: number | null;
  tipoDanoNombre: string;
  nombreCustom: string | null;
  precioCobrado: number;
}

export interface DevolucionCompletaResponse {
  prestamoId: number;
  registroDanoId: number | null;
  huboMultaAtraso: boolean;
  montoMultaAtraso: number | null;
  huboMultaDano: boolean;
  montoMultaDano: number;
  montoTotal: number;
  danosRegistrados: DanoDetalleResponse[];
}

export interface DevolucionHistorial {
  prestamoId: number;
  libroTitulo: string;
  libroIsbn: string;
  usuarioNombre: string;
  fechaPrestamo: string;
  fechaDevolucionEstimada: string;
  fechaDevolucionReal: string;
  estadoDevolucion: string;
  montoTotalMultas: number;
  bibliotecarioNombre: string;
  fechaRegistro: string;
}

export interface EvidenciaDanoResponse {
  id: number;
  registroDanoId: number;
  archivoNombre: string;
  archivoTipo: string;
  subidoEn: string;
}
