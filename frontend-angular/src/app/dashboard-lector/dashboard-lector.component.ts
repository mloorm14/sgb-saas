import { Component, HostListener, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { ChatbotWidgetComponent } from '../shared/chatbot-widget/chatbot-widget.component';

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
  selector: 'app-dashboard-lector',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, ChatbotWidgetComponent],
  templateUrl: './dashboard-lector.component.html'
})
export class DashboardLectorComponent implements OnInit {
  mostrarMenuUsuario = false;
  isCollapsed = signal(false);
  seccionesExpandidas = signal<Set<string>>(new Set());

  // Rutas 100% LECTOR ya existentes (FavoritoController y
  // SugerenciaAdquisicionController exigen hasRole('LECTOR'); el resto
  // admite LECTOR). Espejo del navbar de lector de app.component.
  secciones: SeccionSidebar[] = [
    {
      titulo: 'BIBLIOTECA',
      enlaces: [
        { ruta: '/dashboard-lector/catalogo', etiqueta: 'Catálogo', icono: 'search' },
        { ruta: '/dashboard-lector/prestamos', etiqueta: 'Mis Préstamos', icono: 'menu_book' },
        { ruta: '/dashboard-lector/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available' },
        { ruta: '/dashboard-lector/multas', etiqueta: 'Multas', icono: 'payments' },
      ]
    },
    {
      titulo: 'MI CUENTA',
      enlaces: [
        { ruta: '/dashboard-lector/favoritos', etiqueta: 'Favoritos', icono: 'favorite' },
        { ruta: '/dashboard-lector/sugerencias', etiqueta: 'Sugerencias', icono: 'lightbulb' },
        { ruta: '/dashboard-lector/notificaciones', etiqueta: 'Notificaciones', icono: 'notifications' },
        { ruta: '/dashboard-lector/mi-credencial', etiqueta: 'Mi Credencial', icono: 'qr_code_2' },
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

  @HostListener('document:click', ['$event'])
  cerrarMenuFuera(event: Event): void {
    const target = event.target as HTMLElement;
    if (!target.closest('[data-menu-usuario-sidebar]')) {
      this.mostrarMenuUsuario = false;
    }
  }

  toggleSidebar(): void {
    this.isCollapsed.update(v => !v);
    localStorage.setItem('sidebar:collapsed', String(this.isCollapsed()));
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
