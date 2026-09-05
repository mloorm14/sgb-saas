import { Component } from '@angular/core';
import { DashboardShellComponent } from '../shared/dashboard-shell/dashboard-shell.component';
import { SeccionSidebar } from '../shared/dashboard-shell/seccion-sidebar.model';

// Shell del rol LECTOR. Único con widget flotante del chatbot
// ([mostrarChatbot]). Ruta y guards intactos.
@Component({
  selector: 'app-dashboard-lector',
  standalone: true,
  imports: [DashboardShellComponent],
  templateUrl: './dashboard-lector.component.html',
  styles: [`:host { display: block; height: 100%; overflow: hidden; }`]
})
export class DashboardLectorComponent {
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
}
