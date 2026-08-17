import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';

interface EnlaceNav {
  ruta: string;
  etiqueta: string;
  icono: string;
  futuro?: boolean;
  roles?: string[];
}

@Component({
    selector: 'app-root',
    imports: [RouterOutlet, RouterLink, RouterLinkActive],
    templateUrl: './app.component.html',
    styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'frontend-angular';

  constructor(private authService: AuthService) {}

  // Rama B completada: /catalogo, /favoritos y /sugerencias ya son reales
  // y salen de la lista de futuros. Mi Credencial y Notificaciones
  // (rama frontend/estudiante-cuenta, no integrada) NO se listan: el
  // LECTOR no debe ver enlaces a rutas inexistentes, ni siquiera con
  // opacity -- se agregan cuando la rama de cuenta se integre.
  enlacesLector: EnlaceNav[] = [
    { ruta: '/prestamos', etiqueta: 'Mis Préstamos', icono: 'menu_book' },
    { ruta: '/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available' },
    { ruta: '/multas', etiqueta: 'Multas', icono: 'payments' },
    { ruta: '/catalogo', etiqueta: 'Catálogo', icono: 'search' },
    { ruta: '/favoritos', etiqueta: 'Favoritos', icono: 'favorite' },
    { ruta: '/sugerencias', etiqueta: 'Sugerencias', icono: 'lightbulb' }
  ];

  // Navbar compartido del staff (mockup 23: SGB · Staff). roles por enlace
  // con hasRole(), espejo de los roleGuard de app.routes y de los
  // @PreAuthorize reales de cada controller:
  // - /prestamos/gestion, /reservaciones, /multas: BIBLIOTECARIO/GERENTE
  //   (ADMIN no tiene endpoints en esos controllers).
  // - /reportes: BIBLIOTECARIO/GERENTE (el ADMIN no entra).
  // - /sugerencias/gestion, /admin/usuarios, /auditoria: GERENTE/ADMIN.
  // - /admin/configuracion: solo ADMIN (ConfiguracionSistemaController,
  //   @PreAuthorize a nivel de clase) -- antes vivía como botón ad-hoc
  //   dentro de LibrosComponent (no había navbar compartido todavía);
  //   se movió acá al traer este navbar de otra rama, mismo criterio que
  //   el resto de enlaces de staff.
  // - /libros: todo el staff (inventario).
  enlacesStaff: EnlaceNav[] = [
    { ruta: '/libros', etiqueta: 'Libros', icono: 'inventory_2', roles: ['BIBLIOTECARIO', 'GERENTE', 'ADMIN'] },
    { ruta: '/prestamos/gestion', etiqueta: 'Préstamos', icono: 'assignment_return', roles: ['BIBLIOTECARIO', 'GERENTE'] },
    { ruta: '/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available', roles: ['BIBLIOTECARIO', 'GERENTE'] },
    { ruta: '/multas', etiqueta: 'Multas', icono: 'payments', roles: ['BIBLIOTECARIO', 'GERENTE'] },
    { ruta: '/reportes', etiqueta: 'Reportes', icono: 'bar_chart', roles: ['BIBLIOTECARIO', 'GERENTE'] },
    { ruta: '/sugerencias/gestion', etiqueta: 'Sugerencias', icono: 'lightbulb', roles: ['GERENTE', 'ADMIN'] },
    { ruta: '/admin/usuarios', etiqueta: 'Usuarios', icono: 'manage_accounts', roles: ['GERENTE', 'ADMIN'] },
    { ruta: '/auditoria', etiqueta: 'Auditoría', icono: 'receipt_long', roles: ['GERENTE', 'ADMIN'] },
    { ruta: '/admin/configuracion', etiqueta: 'Configuración', icono: 'settings', roles: ['ADMIN'] },
    { ruta: '/dashboard', etiqueta: 'Dashboard', icono: 'dashboard', futuro: true, roles: ['BIBLIOTECARIO', 'GERENTE', 'ADMIN'] }
  ];

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

  cerrarSesion(): void {
    this.authService.logout();
  }
}