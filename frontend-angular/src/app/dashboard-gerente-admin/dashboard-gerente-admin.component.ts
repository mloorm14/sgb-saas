import { Component, HostListener, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/services/auth.service';

interface EnlaceSidebar {
  ruta: string;
  etiqueta: string;
  icono: string;
  roles: string[];
}

interface SeccionSidebar {
  titulo: string;
  enlaces: EnlaceSidebar[];
}

@Component({
  selector: 'app-dashboard-gerente-admin',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './dashboard-gerente-admin.component.html'
})
export class DashboardGerenteAdminComponent implements OnInit {
  mostrarMenuUsuario = false;
  isCollapsed = signal(false);

  // Espejo de los @PreAuthorize reales verificados en backend-springboot:
  // - /libros: todo el staff opera el inventario (LibroController).
  // - /sugerencias/gestion y /auditoria: GERENTE/ADMIN.
  // - /admin/usuarios: listado GERENTE/ADMIN (cambios solo ADMIN dentro).
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
        { ruta: '/dashboard-admin/sugerencias/gestion', etiqueta: 'Sugerencias', icono: 'lightbulb', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/admin/usuarios', etiqueta: 'Usuarios', icono: 'manage_accounts', roles: ['ADMIN'] },
      ]
    },
    {
      titulo: 'SISTEMA',
      enlaces: [
        { ruta: '/dashboard-admin/auditoria', etiqueta: 'Auditoría', icono: 'receipt_long', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/reportes', etiqueta: 'Reportes', icono: 'bar_chart', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/admin/configuracion', etiqueta: 'Configuración', icono: 'settings', roles: ['ADMIN'] },
      ]
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

  isMobile(): boolean {
    return window.innerWidth < 1024;
  }

  visibles(seccion: SeccionSidebar): EnlaceSidebar[] {
    return seccion.enlaces.filter(enlace => this.authService.hasRole(...enlace.roles));
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
    return '';
  }

  cerrarSesion(): void {
    this.mostrarMenuUsuario = false;
    this.authService.logout();
  }
}
