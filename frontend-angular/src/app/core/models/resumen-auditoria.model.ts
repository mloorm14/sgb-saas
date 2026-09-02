export interface ResumenCategoriaAuditoria {
  tablaAfectada: string;
  totalEventos: number;
  eventosHoy: number;
  ultimoEvento: string | null;
  requiereRevision: boolean;
}
