import { Component } from '@angular/core';
import { DashboardShellComponent } from '../shared/dashboard-shell/dashboard-shell.component';
import { SeccionSidebar } from '../shared/dashboard-shell/seccion-sidebar.model';

// Shell compartido GERENTE/ADMIN. El filtrado por roles vive en los
// enlaces (campo roles) y lo aplica el shell. Ruta y guards intactos.
@Component({
  selector: 'app-dashboard-gerente-admin',
  standalone: true,
  imports: [DashboardShellComponent],
  templateUrl: './dashboard-gerente-admin.component.html',
  styles: [`:host { display: block; height: 100%; overflow: hidden; }`]
})
export class DashboardGerenteAdminComponent {
  // Espejo de los @PreAuthorize reales verificados en backend-springboot:
  // - /libros: todo el staff opera el inventario (LibroController).
  // - /sugerencias/gestion: GERENTE/ADMIN. /auditoria: solo ADMIN.
  // - /admin/usuarios: ADMIN ve todo; GERENTE filtra por sus creados (?mios).
  // - /admin/mis-usuarios: apartado propio de GERENTE (F8/V38, soloMios).
  // - /admin/configuracion: SOLO ADMIN (ConfiguracionSistemaController).
  // - /reportes: BIBLIOTECARIO/GERENTE (PrestamoController) -- el ADMIN
  //   no tiene endpoints ahi, por eso no se le muestra el enlace.
  secciones: SeccionSidebar[] = [
    {
      titulo: 'INICIO',
      enlaces: [
        { ruta: '/dashboard-admin', etiqueta: 'Inicio', icono: 'home', roles: ['GERENTE', 'ADMIN'] },
      ]
    },
    {
       titulo: 'GESTIÓN',
      enlaces: [
        { ruta: '/dashboard-admin/libros', etiqueta: 'Libros', icono: 'inventory_2', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/libros-pendientes', etiqueta: 'Pendientes', icono: 'pending_actions', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/prestamos/gestion', etiqueta: 'Préstamos', icono: 'menu_book', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/multas', etiqueta: 'Multas', icono: 'payments', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/proveedores', etiqueta: 'Proveedores', icono: 'local_shipping', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/sugerencias/gestion', etiqueta: 'Sugerencias', icono: 'lightbulb', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/admin/usuarios', etiqueta: 'Usuarios', icono: 'manage_accounts', roles: ['ADMIN'] },
        { ruta: '/dashboard-admin/admin/mis-usuarios', etiqueta: 'Mis usuarios', icono: 'group', roles: ['GERENTE'] },
      ]
    },
    {
      titulo: 'SISTEMA',
      enlaces: [
        { ruta: '/dashboard-admin/auditoria', etiqueta: 'Auditoría', icono: 'receipt_long', roles: ['ADMIN'] },
        { ruta: '/dashboard-admin/reportes', etiqueta: 'Reportes', icono: 'bar_chart', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/admin/configuracion', etiqueta: 'Configuración', icono: 'settings', roles: ['ADMIN'] },
      ]
    }
  ];
}
