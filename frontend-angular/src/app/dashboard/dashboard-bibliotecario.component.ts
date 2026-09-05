import { Component } from '@angular/core';
import { AuthService } from '../core/services/auth.service';
import { DashboardShellComponent } from '../shared/dashboard-shell/dashboard-shell.component';
import { SeccionSidebar } from '../shared/dashboard-shell/seccion-sidebar.model';

// Shell del rol BIBLIOTECARIO. El cromo visual vive en
// DashboardShellComponent; acá solo quedan los datos del menú
// y el filtro de secciones de este rol. Ruta y guards intactos.
@Component({
  selector: 'app-dashboard-bibliotecario',
  standalone: true,
  imports: [DashboardShellComponent],
  templateUrl: './dashboard-bibliotecario.component.html',
  styles: [`:host { display: block; height: 100%; overflow: hidden; }`]
})
export class DashboardBibliotecarioComponent {
  private secciones: SeccionSidebar[] = [
    {
      titulo: '',
      enlaces: [
        { ruta: '/dashboard-bibliotecario', etiqueta: 'Inicio', icono: 'home' },
      ]
    },
    {
      titulo: 'GESTIÓN',
      enlaces: [
        { ruta: '/dashboard-bibliotecario/libros', etiqueta: 'Libros', icono: 'inventory_2' },
        { ruta: '/dashboard-bibliotecario/libros-pendientes', etiqueta: 'Pendientes', icono: 'pending_actions' },
        { ruta: '/dashboard-bibliotecario/prestamos/gestion', etiqueta: 'Préstamos', icono: 'assignment_return' },
        { ruta: '/dashboard-bibliotecario/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available' },
        { ruta: '/dashboard-bibliotecario/devoluciones', etiqueta: 'Devoluciones', icono: 'assignment_return' },
        { ruta: '/dashboard-bibliotecario/multas', etiqueta: 'Multas', icono: 'payments' },
      ]
    },
    {
      titulo: 'SISTEMA',
      enlaces: []
    }
  ];

  constructor(private authService: AuthService) {}

  get seccionesVisibles(): SeccionSidebar[] {
    if (this.authService.hasRole('BIBLIOTECARIO')) {
      return this.secciones.filter(s => s.titulo !== 'SISTEMA');
    }
    return this.secciones;
  }
}
