import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';

interface EnlaceNav {
  ruta: string;
  etiqueta: string;
  icono: string;
  futuro?: boolean;
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

  // TODO(frontend/estudiante-catalogo-social, frontend/estudiante-cuenta,
  // frontend/bibliotecario-operacion, frontend/gerente-panel-administrativo):
  // los enlaces con futuro=true apuntan a rutas que todavia no existen en
  // esta rama (catalogo, favoritos, mi-credencial, sugerencias,
  // notificaciones, dashboard, reportes, admin/usuarios, auditoria). Se
  // completan cuando se mergeen esas ramas; el clic hoy cae en el
  // comodin '**' -> /login.
  enlacesLector: EnlaceNav[] = [
    { ruta: '/prestamos', etiqueta: 'Mis Préstamos', icono: 'menu_book' },
    { ruta: '/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available' },
    { ruta: '/multas', etiqueta: 'Multas', icono: 'payments' },
    { ruta: '/catalogo', etiqueta: 'Catálogo', icono: 'search', futuro: true },
    { ruta: '/favoritos', etiqueta: 'Favoritos', icono: 'favorite', futuro: true },
    { ruta: '/mi-credencial', etiqueta: 'Mi Credencial', icono: 'qr_code_2', futuro: true },
    { ruta: '/sugerencias', etiqueta: 'Sugerencias', icono: 'lightbulb', futuro: true },
    { ruta: '/notificaciones', etiqueta: 'Notificaciones', icono: 'notifications', futuro: true }
  ];

  enlacesOperacion: EnlaceNav[] = [
    { ruta: '/prestamos/gestion', etiqueta: 'Préstamos', icono: 'assignment_return' },
    { ruta: '/reservaciones', etiqueta: 'Reservaciones', icono: 'event_available' },
    { ruta: '/multas', etiqueta: 'Multas', icono: 'payments' },
    { ruta: '/libros', etiqueta: 'Libros', icono: 'inventory_2' },
    { ruta: '/dashboard', etiqueta: 'Dashboard', icono: 'dashboard', futuro: true },
    { ruta: '/reportes', etiqueta: 'Reportes', icono: 'bar_chart', futuro: true }
  ];

  enlacesAdmin: EnlaceNav[] = [
    { ruta: '/libros', etiqueta: 'Libros', icono: 'inventory_2' },
    { ruta: '/dashboard', etiqueta: 'Dashboard', icono: 'dashboard', futuro: true },
    { ruta: '/reportes', etiqueta: 'Reportes', icono: 'bar_chart', futuro: true },
    { ruta: '/admin/usuarios', etiqueta: 'Usuarios', icono: 'manage_accounts', futuro: true },
    { ruta: '/auditoria', etiqueta: 'Auditoría', icono: 'receipt_long', futuro: true }
  ];

  estaLogueado(): boolean {
    return this.authService.isLoggedIn();
  }

  esLector(): boolean {
    return this.authService.hasRole('LECTOR');
  }

  esOperacion(): boolean {
    return this.authService.hasRole('BIBLIOTECARIO', 'GERENTE');
  }

  esAdmin(): boolean {
    return this.authService.hasRole('ADMIN');
  }

  cerrarSesion(): void {
    this.authService.logout();
  }
}