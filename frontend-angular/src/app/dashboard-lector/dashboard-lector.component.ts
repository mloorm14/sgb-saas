import { Component, HostListener } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/services/auth.service';

interface EnlaceSidebar {
  ruta: string;
  etiqueta: string;
  icono: string;
}

@Component({
  selector: 'app-dashboard-lector',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './dashboard-lector.component.html'
})
export class DashboardLectorComponent {
  mostrarMenuUsuario = false;

  // Rutas 100% LECTOR ya existentes (FavoritoController y
  // SugerenciaAdquisicionController exigen hasRole('LECTOR'); el resto
  // admite LECTOR). Espejo del navbar de lector de app.component.
  secciones = [
    {
      titulo: 'BIBLIOTECA',
      enlaces: [
        { ruta: '/dashboard-lector/catalogo', etiqueta: 'Catálogo', icono: 'search' },
        { ruta: '/dashboard-lector/prestamos', etiqueta: 'Mis Préstamos', icono: 'menu_book' },
        { ruta: '/dashboard-lector/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available' },
        { ruta: '/dashboard-lector/multas', etiqueta: 'Multas', icono: 'payments' },
      ] as EnlaceSidebar[]
    },
    {
      titulo: 'MI CUENTA',
      enlaces: [
        { ruta: '/dashboard-lector/favoritos', etiqueta: 'Favoritos', icono: 'favorite' },
        { ruta: '/dashboard-lector/sugerencias', etiqueta: 'Sugerencias', icono: 'lightbulb' },
        { ruta: '/dashboard-lector/notificaciones', etiqueta: 'Notificaciones', icono: 'notifications' },
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
    if (this.authService.hasRole('LECTOR')) return 'Lector';
    return '';
  }

  cerrarSesion(): void {
    this.mostrarMenuUsuario = false;
    if (confirm('¿Seguro que querés cerrar sesión?')) {
      this.authService.logout();
    }
  }
}
