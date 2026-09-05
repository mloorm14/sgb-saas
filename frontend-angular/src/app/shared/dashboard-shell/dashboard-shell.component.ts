import { Component, HostListener, input, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ChatbotWidgetComponent } from '../chatbot-widget/chatbot-widget.component';
import { EnlaceSidebar, SeccionSidebar } from './seccion-sidebar.model';

// Shell visual compartido por los dashboards por rol (LECTOR,
// Bibliotecario y Gerente/Admin). Cada shell conserva su componente,
// su ruta y sus guards; solo el cromo (sidebar + main + outlet) es común.
// Las rutas hijas y los roleGuard de app.routes.ts quedan intactos.
@Component({
  selector: 'app-dashboard-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, ChatbotWidgetComponent],
  templateUrl: './dashboard-shell.component.html',
  styles: [`:host { display: block; height: 100%; overflow: hidden; }`]
})
export class DashboardShellComponent implements OnInit {
  /** Secciones a renderizar (cada shell aporta las de su rol). */
  readonly secciones = input.required<SeccionSidebar[]>();
  /** Solo el shell del LECTOR muestra el widget flotante del chatbot. */
  readonly mostrarChatbot = input(false);

  mostrarMenuUsuario = false;
  isCollapsed = signal(false);
  seccionesExpandidas = signal<Set<string>>(new Set());

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
      this.seccionesExpandidas.set(new Set(this.secciones().map(s => s.titulo).filter(t => t)));
    }
  }

  /** Enlaces visibles según roles (sin roles = visible para todos). */
  enlacesVisibles(seccion: SeccionSidebar): EnlaceSidebar[] {
    return seccion.enlaces.filter(enlace => !enlace.roles || this.authService.hasRole(...enlace.roles));
  }

  isMobile(): boolean {
    return window.innerWidth < 1024;
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
    if (this.authService.hasRole('BIBLIOTECARIO')) return 'Bibliotecario';
    return '';
  }

  cerrarSesion(): void {
    this.mostrarMenuUsuario = false;
    this.authService.logout();
  }
}
