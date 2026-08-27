import { Component, HostListener, OnInit, effect, signal } from '@angular/core';
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
export class DashboardBibliotecarioComponent implements OnInit {
  mostrarMenuUsuario = false;
  isCollapsed = signal(false);

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

  ngOnInit(): void {
    // Restaurar estado del sidebar desde localStorage
    const saved = localStorage.getItem('sidebar:collapsed');
    if (saved !== null) {
      this.isCollapsed.set(saved === 'true');
    }
  }

  get seccionesVisibles(): SeccionSidebar[] {
    if (this.authService.hasRole('BIBLIOTECARIO')) {
      return this.secciones.filter(s => s.titulo !== 'SISTEMA');
    }
    return this.secciones;
  }

  isMobile(): boolean {
    return window.innerWidth < 1024;
  }

  toggleSidebar(): void {
    this.isCollapsed.update(v => !v);
    localStorage.setItem('sidebar:collapsed', String(this.isCollapsed()));
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
