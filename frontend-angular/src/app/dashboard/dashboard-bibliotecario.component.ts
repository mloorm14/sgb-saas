import { Component, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/services/auth.service';

interface EnlaceSidebar {
  ruta: string;
  etiqueta: string;
  icono: string;
}

@Component({
  selector: 'app-dashboard-bibliotecario',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './dashboard-bibliotecario.component.html'
})
export class DashboardBibliotecarioComponent {
  mostrarMenuUsuario = false;

  secciones = [
    {
      titulo: 'GESTIÓN',
      enlaces: [
        { ruta: '/dashboard-bibliotecario/libros', etiqueta: 'Libros', icono: 'inventory_2' },
        { ruta: '/dashboard-bibliotecario/prestamos/gestion', etiqueta: 'Préstamos', icono: 'assignment_return' },
        { ruta: '/dashboard-bibliotecario/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available' },
        { ruta: '/dashboard-bibliotecario/multas', etiqueta: 'Multas', icono: 'payments' },
      ] as EnlaceSidebar[]
    },
    {
      titulo: 'SISTEMA',
      enlaces: [
        { ruta: '/dashboard-bibliotecario/reportes', etiqueta: 'Reportes', icono: 'bar_chart' },
      ] as EnlaceSidebar[]
    }
  ];

  constructor(private authService: AuthService) {}

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
