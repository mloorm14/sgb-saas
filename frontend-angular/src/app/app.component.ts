import { Component, HostListener } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { filter } from 'rxjs/operators';
import { ChatbotWidgetComponent } from './shared/chatbot-widget/chatbot-widget.component';
import { LoaderComponent } from './shared/loader/loader.component';
import { ConfirmDialogComponent } from './shared/confirm-dialog/confirm-dialog.component';
import { NavigationBlockingService } from './core/services/navigation-blocking.service';
import { FocusTrapDirective } from './shared/focus-trap.directive';

interface EnlaceNav {
  ruta: string;
  etiqueta: string;
  icono: string;
  futuro?: boolean;
  roles?: string[];
}

@Component({
  standalone: true,
    selector: 'app-root',
    imports: [RouterOutlet, RouterLink, RouterLinkActive, ChatbotWidgetComponent, LoaderComponent, ConfirmDialogComponent, FocusTrapDirective],
    templateUrl: './app.component.html',
    styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'Leibri';
  mostrarMenuUsuario = false;
  enRutaBibliotecario = false;
  enRutaAdmin = false;
  enRutaLector = false;
  enRutaPublica = true;
  enRutaCatalogoPublico = false;
  cargandoRuta = true;
  rutaAnnouncement = '';
  confirmacionVisible = false;
  confirmacionMensaje = '';
  confirmacionPendiente: (() => void) | null = null;

  constructor(
    private authService: AuthService,
    private router: Router,
    private navigationBlocking: NavigationBlockingService
  ) {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event) => {
      const url = (event as NavigationEnd).urlAfterRedirects || (event as NavigationEnd).url;
      this.enRutaBibliotecario = url.startsWith('/dashboard-bibliotecario');
      this.enRutaAdmin = url.startsWith('/dashboard-admin');
      this.enRutaLector = url.startsWith('/dashboard-lector');
      this.enRutaPublica = url.startsWith('/no-autorizado');
      this.enRutaCatalogoPublico = url === '/' || url.startsWith('/portal') || url.startsWith('/catalogo');
      this.cargandoRuta = false;
      this.rutaAnnouncement = this.obtenerNombreRuta(url);
    });
  }

  @HostListener('document:click', ['$event'])
  cerrarMenuFuera(event: Event): void {
    const target = event.target as HTMLElement;
    if (!target.closest('[data-menu-usuario]')) {
      this.mostrarMenuUsuario = false;
    }
  }

  // Rama B completada: /catalogo, /favoritos, /sugerencias y /notificaciones ya son reales
  // y salen de la lista de futuros. Mi Credencial (rama frontend/estudiante-cuenta, no integrada)
  // NO se lista: el LECTOR no debe ver enlaces a rutas inexistentes, ni siquiera con
  // opacity -- se agregan cuando la rama de cuenta se integre.
  enlacesLector: EnlaceNav[] = [
    // Entrada al panel con sidebar del LECTOR (mismo patron que Cajas).
    { ruta: '/dashboard-lector', etiqueta: 'Panel', icono: 'dashboard' },
    { ruta: '/prestamos', etiqueta: 'Mis Préstamos', icono: 'menu_book' },
    { ruta: '/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available' },
    { ruta: '/multas', etiqueta: 'Multas', icono: 'payments' },
    { ruta: '/catalogo', etiqueta: 'Catálogo', icono: 'search' },
    { ruta: '/favoritos', etiqueta: 'Favoritos', icono: 'favorite' },
    { ruta: '/sugerencias', etiqueta: 'Sugerencias', icono: 'lightbulb' },
    { ruta: '/notificaciones', etiqueta: 'Notificaciones', icono: 'notifications' }
  ];

  // Navbar compartido del staff (mockup 23: SGB · Staff). roles por enlace
  // con hasRole(), espejo de los roleGuard de app.routes y de los
  // @PreAuthorize reales de cada controller:
  // - /prestamos/gestion, /reservaciones, /multas: BIBLIOTECARIO/GERENTE
  //   (ADMIN no tiene endpoints en esos controllers).
  // - /reportes: GERENTE/ADMIN (PrestamoController).
  // - /sugerencias/gestion, /admin/usuarios: GERENTE/ADMIN.
  // - /auditoria: solo ADMIN.
  // - /admin/configuracion: solo ADMIN (ConfiguracionSistemaController,
  //   @PreAuthorize a nivel de clase) -- antes vivía como botón ad-hoc
  //   dentro de LibrosComponent (no había navbar compartido todavía);
  //   se movió acá al traer este navbar de otra rama, mismo criterio que
  //   el resto de enlaces de staff.
  // - /libros: todo el staff (inventario).
  // - /dashboard-gerente: Dashboard real del GERENTE (mockup 24, rama fix/sincronizar-despliegue-y-dashboard-gerente).
  //   Reemplaza al placeholder futuro:true de /dashboard, que prometía la
  //   pantalla a todo el staff sin ruta real detrás. BIBLIOTECARIO y ADMIN
  //   no la ven (tienen /reportes y el resto del panel respectivamente).
  enlacesStaff: EnlaceNav[] = [
    // Entrada al panel con sidebar GERENTE/ADMIN (mismo patron que Cajas).
    { ruta: '/dashboard-admin', etiqueta: 'Panel', icono: 'dashboard', roles: ['GERENTE', 'ADMIN'] },
    { ruta: '/libros', etiqueta: 'Libros', icono: 'inventory_2', roles: ['BIBLIOTECARIO', 'GERENTE', 'ADMIN'] },
    { ruta: '/prestamos/gestion', etiqueta: 'Préstamos', icono: 'assignment_return', roles: ['BIBLIOTECARIO', 'GERENTE'] },
    { ruta: '/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available', roles: ['BIBLIOTECARIO', 'GERENTE'] },
    { ruta: '/multas', etiqueta: 'Multas', icono: 'payments', roles: ['BIBLIOTECARIO', 'GERENTE'] },
    { ruta: '/dashboard-admin/reportes', etiqueta: 'Reportes', icono: 'bar_chart', roles: ['GERENTE', 'ADMIN'] },
    { ruta: '/sugerencias/gestion', etiqueta: 'Sugerencias', icono: 'lightbulb', roles: ['GERENTE', 'ADMIN'] },
    { ruta: '/dashboard-admin/admin/usuarios', etiqueta: 'Usuarios', icono: 'manage_accounts', roles: ['ADMIN'] },
    { ruta: '/dashboard-admin/auditoria', etiqueta: 'Auditoría', icono: 'receipt_long', roles: ['ADMIN'] },
    { ruta: '/dashboard-admin/admin/configuracion', etiqueta: 'Configuración', icono: 'settings', roles: ['ADMIN'] },
    { ruta: '/dashboard-gerente', etiqueta: 'Dashboard', icono: 'dashboard', roles: ['GERENTE'] }
  ];

  private obtenerNombreRuta(url: string): string {
    const ruta = url.split('?')[0].split('#')[0].replace(/\/+$/, '') || '/';
    const mapa: Record<string, string> = {
      '/': 'Inicio',
      '/catalogo': 'Catálogo de libros',
      '/favoritos': 'Mis favoritos',
      '/sugerencias': 'Sugerencias',
      '/notificaciones': 'Notificaciones',
      '/prestamos': 'Mis préstamos',
      '/prestamos/gestion': 'Gestión de préstamos',
      '/reservaciones': 'Reservaciones',
      '/multas': 'Multas',
      '/libros': 'Inventario de libros',
      '/reportes': 'Reportes',
      '/auditoria': 'Auditoría',
      '/login': 'Iniciar sesión',
    };
    if (mapa[ruta]) return mapa[ruta];
    if (ruta.startsWith('/dashboard-admin/admin/usuarios')) return 'Gestión de usuarios';
    if (ruta.startsWith('/dashboard-admin/admin/configuracion')) return 'Configuración del sistema';
    if (ruta.startsWith('/dashboard-admin')) return 'Panel de administración';
    if (ruta.startsWith('/dashboard-bibliotecario')) return 'Panel de bibliotecario';
    if (ruta.startsWith('/dashboard-gerente')) return 'Dashboard del gerente';
    if (ruta.startsWith('/dashboard-lector')) return 'Panel del lector';
    return 'Página';
  }

  estaLogueado(): boolean {
    return this.authService.isLoggedIn();
  }

  esLector(): boolean {
    return this.authService.hasRole('LECTOR');
  }

  esStaff(): boolean {
    return this.authService.hasRole('BIBLIOTECARIO', 'GERENTE', 'ADMIN');
  }

  permiteEnlace(enlace: EnlaceNav): boolean {
    return this.authService.hasRole(...(enlace.roles ?? []));
  }

  mostrarConfirmacion(mensaje: string, accion: () => void) {
    this.confirmacionMensaje = mensaje;
    this.confirmacionPendiente = accion;
    this.confirmacionVisible = true;
  }

  confirmarAccion() {
    this.confirmacionVisible = false;
    if (this.confirmacionPendiente) {
      this.confirmacionPendiente();
      this.confirmacionPendiente = null;
    }
  }

  cancelarConfirmacion() {
    this.confirmacionVisible = false;
    this.confirmacionPendiente = null;
  }

  cerrarSesion(): void {
    this.mostrarMenuUsuario = false;
    this.mostrarConfirmacion('¿Seguro que quieres cerrar sesión?', () => {
      this.authService.logout();
    });
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
}