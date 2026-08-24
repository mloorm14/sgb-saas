import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/services/auth.service';

interface EnlaceSidebar {
  ruta: string;
  etiqueta: string;
  icono: string;
}

interface SeccionSidebar {
  titulo: string;
  enlaces: EnlaceSidebar[];
}

@Component({
  selector: 'app-dashboard-bibliotecario',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './dashboard-bibliotecario.component.html'
})
export class DashboardBibliotecarioComponent {
  mostrarMenuUsuario = false;

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

  @HostListener('document:click', ['$event'])
  cerrarMenuFuera(event: Event): void {
    const target = event.target as HTMLElement;
    if (!target.closest('[data-menu-usuario-sidebar]')) {
      this.mostrarMenuUsuario = false;
    }
  }

  get correoUsuario(): string {
    return this.authService.getCorreo() ?? '';
  }

  get inicialesUsuario(): string {
    const correo = this.correoUsuario;
    if (!correo) return '??';
    const parte = correo.split('@')[0];
    return parte.substring(0, 2).toUpperCase();
  }

  get nombreRol(): string {
    if (this.authService.hasRole('ADMIN')) return 'Administrador';
    if (this.authService.hasRole('GERENTE')) return 'Gerente';
    if (this.authService.hasRole('BIBLIOTECARIO')) return 'Bibliotecario';
    return '';
  }

  cerrarSesion(): void {
    this.mostrarMenuUsuario = false;
    this.authService.logout();
  }
}
