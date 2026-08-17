// Contrato de UsuarioAdminController (/api/v1/admin/usuarios),
// UsuarioListadoResponseDTO en backend-springboot.
export interface UsuarioAdmin {
  id: number;
  nombre: string;
  apellido: string;
  correo: string;
  roles: string[];
  estado: string;
  multasPendientes: boolean;
}