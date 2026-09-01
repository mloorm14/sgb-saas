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
  templateUrl: './dashboard-gerente-admin.component.html',
  styles: [`:host { display: block; height: 100%; overflow: hidden; }`]
})
export class DashboardGerenteAdminComponent implements OnInit {
  mostrarMenuUsuario = false;
  isCollapsed = signal(false);
  seccionesExpandidas = signal<Set<string>>(new Set());

  // Espejo de los @PreAuthorize reales verificados en backend-springboot:
  // - /libros: todo el staff opera el inventario (LibroController).
  // - /sugerencias/gestion: GERENTE/ADMIN. /auditoria: solo ADMIN (ahora oculto a GERENTE en sidebar).
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
        { ruta: '/dashboard-admin/proveedores', etiqueta: 'Proveedores', icono: 'local_shipping', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/sugerencias/gestion', etiqueta: 'Sugerencias', icono: 'lightbulb', roles: ['GERENTE', 'ADMIN'] },
        { ruta: '/dashboard-admin/admin/usuarios', etiqueta: 'Usuarios', icono: 'manage_accounts', roles: ['ADMIN'] },
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

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    // Restaurar estado del sidebar desde localStorage
    const saved = localStorage.getItem('sidebar:collapsed');
    if (saved !== null) {
      this.isCollapsed.set(saved === 'true');
    }
    // Restaurar secciones expandidas
    const savedSecciones = localStorage.getItem('sidebar:secciones-expandidas');
    if (savedSecciones) {
      try { this.seccionesExpandidas.set(new Set(JSON.parse(savedSecciones))); } catch {}
    }
    // Default: todas expandidas si no hay guardado
    if (this.seccionesExpandidas().size === 0) {
      this.seccionesExpandidas.set(new Set(this.secciones.map(s => s.titulo)));
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
    if (this.isCollapsed()) { this.mostrarMenuUsuario = false; }
  }

  onClickMenuUsuario(): void {
    if (this.isCollapsed()) { return; }
    this.mostrarMenuUsuario = !this.mostrarMenuUsuario;
  }

  toggleSeccion(titulo: string): void {
    this.seccionesExpandidas.update(set => {
      const nuevo = new Set(set);
      nuevo.has(titulo) ? nuevo.delete(titulo) : nuevo.add(titulo);
      localStorage.setItem('sidebar:secciones-expandidas', JSON.stringify([...nuevo]));
      return nuevo;
    });
  }

  estaExpandida(titulo: string): boolean {
    return this.seccionesExpandidas().has(titulo);
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
