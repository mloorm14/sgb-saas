export interface Proveedor {
  id: number;
  nombre: string;
  ruc?: string;
  direccion?: string;
  telefono?: string;
  email?: string;
  personaContacto?: string;
  activo: boolean;
}
