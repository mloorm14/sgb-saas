// Modelo compartido del sidebar de los shells de dashboard.
// Centraliza las interfaces que antes estaban duplicadas en cada
// dashboard-*.component.ts (LECTOR, Bibliotecario y Gerente/Admin).
export interface EnlaceSidebar {
  ruta: string;
  etiqueta: string;
  icono: string;
  /** Roles que pueden ver el enlace. Sin definir = visible para todos. */
  roles?: string[];
}

export interface SeccionSidebar {
  titulo: string;
  enlaces: EnlaceSidebar[];
}
