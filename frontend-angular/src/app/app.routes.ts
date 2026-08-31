import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { LoginComponent } from './auth/login/login.component';
import { RegistroComponent } from './auth/registro/registro.component';
import { DashboardGerenteComponent } from './dashboard-gerente/dashboard-gerente.component';
import { DashboardGerenteAdminHomeComponent } from './dashboard-gerente-admin/dashboard-gerente-admin-home.component';
import { DashboardBibliotecarioComponent } from './dashboard/dashboard-bibliotecario.component';
import { DashboardBibliotecarioHomeComponent } from './dashboard/dashboard-bibliotecario-home.component';
import { NoAutorizadoComponent } from './shared/no-autorizado/no-autorizado.component';
import { PortalPublicoComponent } from './portal-publico/portal-publico.component';
import { DetallePublicoComponent } from './portal-publico/detalle-publico/detalle-publico.component';
import { DashboardGerenteAdminComponent } from './dashboard-gerente-admin/dashboard-gerente-admin.component';
import { DashboardLectorComponent } from './dashboard-lector/dashboard-lector.component';
import { catalogoResolver } from './core/resolvers/catalogo.resolver';
import { libroDetalleResolver } from './core/resolvers/libro-detalle.resolver';

export const routes: Routes = [
  { path: '', component: PortalPublicoComponent },
  { path: 'portal/:id', component: DetallePublicoComponent, resolve: { data: libroDetalleResolver } },
  { path: 'login', component: LoginComponent },
  { path: 'registro', component: RegistroComponent },
  { path: 'libros', loadComponent: () => import('./libros/libros.component').then(m => m.LibrosComponent), canActivate: [authGuard, roleGuard(['BIBLIOTECARIO', 'GERENTE', 'ADMIN'])] },
  { path: 'prestamos', loadComponent: () => import('./prestamos-lector/prestamos-lector.component').then(m => m.PrestamosLectorComponent), canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'prestamos/gestion', loadComponent: () => import('./prestamos-gestion/prestamos-gestion.component').then(m => m.PrestamosGestionComponent), canActivate: [authGuard, roleGuard(['BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'reservaciones', loadComponent: () => import('./reservaciones/reservaciones.component').then(m => m.ReservacionesComponent), canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'multas', loadComponent: () => import('./multas/multas.component').then(m => m.MultasComponent), canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'admin/configuracion', loadComponent: () => import('./configuracion-sistema/configuracion-sistema.component').then(m => m.ConfiguracionSistemaComponent), canActivate: [authGuard, roleGuard(['ADMIN'])] },
  { path: 'reportes', loadComponent: () => import('./reportes/reportes.component').then(m => m.ReportesComponent), canActivate: [authGuard, roleGuard(['GERENTE', 'ADMIN'])] },
  { path: 'dashboard-gerente', component: DashboardGerenteComponent, canActivate: [authGuard, roleGuard(['GERENTE'])] },
  {
    path: 'dashboard-bibliotecario',
    component: DashboardBibliotecarioComponent,
    canActivate: [authGuard, roleGuard(['BIBLIOTECARIO'])],
    children: [
      { path: '', component: DashboardBibliotecarioHomeComponent },
      { path: 'libros', loadComponent: () => import('./libros/libros.component').then(m => m.LibrosComponent) },
      { path: 'libros-pendientes', loadComponent: () => import('./libros-pendientes/libros-pendientes.component').then(m => m.LibrosPendientesComponent) },
      { path: 'prestamos/gestion', loadComponent: () => import('./prestamos-gestion/prestamos-gestion.component').then(m => m.PrestamosGestionComponent) },
      { path: 'reservaciones', loadComponent: () => import('./reservaciones/reservaciones.component').then(m => m.ReservacionesComponent) },
      { path: 'devoluciones', loadComponent: () => import('./devoluciones/devoluciones.component').then(m => m.DevolucionesComponent) },
      { path: 'multas', loadComponent: () => import('./multas/multas.component').then(m => m.MultasComponent) },
    ]
  },
  {
    path: 'dashboard-admin',
    component: DashboardGerenteAdminComponent,
    canActivate: [authGuard, roleGuard(['GERENTE', 'ADMIN'])],
    children: [
      { path: '', component: DashboardGerenteAdminHomeComponent },
      { path: 'libros', loadComponent: () => import('./libros/libros.component').then(m => m.LibrosComponent) },
      { path: 'libros-pendientes', loadComponent: () => import('./libros-pendientes/libros-pendientes.component').then(m => m.LibrosPendientesComponent) },
      { path: 'prestamos/gestion', loadComponent: () => import('./prestamos-gestion/prestamos-gestion.component').then(m => m.PrestamosGestionComponent) },
      { path: 'reservaciones', loadComponent: () => import('./reservaciones/reservaciones.component').then(m => m.ReservacionesComponent) },
      { path: 'multas', loadComponent: () => import('./multas/multas.component').then(m => m.MultasComponent) },
      { path: 'sugerencias/gestion', loadComponent: () => import('./sugerencias/gestion-sugerencias/gestion-sugerencias.component').then(m => m.GestionSugerenciasComponent) },
      { path: 'admin/usuarios', loadComponent: () => import('./admin/usuarios/usuarios.component').then(m => m.UsuariosComponent) },
      { path: 'auditoria', loadComponent: () => import('./admin/auditoria/auditoria.component').then(m => m.AuditoriaComponent) },
      { path: 'reportes', loadComponent: () => import('./reportes/reportes.component').then(m => m.ReportesComponent), canActivate: [roleGuard(['GERENTE', 'ADMIN'])] },
      { path: 'admin/configuracion', loadComponent: () => import('./configuracion-sistema/configuracion-sistema.component').then(m => m.ConfiguracionSistemaComponent), canActivate: [roleGuard(['ADMIN'])] },
    ]
  },
  {
    path: 'dashboard-lector',
    component: DashboardLectorComponent,
    canActivate: [authGuard, roleGuard(['LECTOR'])],
    children: [
      { path: '', redirectTo: 'catalogo', pathMatch: 'full' },
      { path: 'catalogo', loadComponent: () => import('./catalogo/catalogo.component').then(m => m.CatalogoComponent), resolve: { data: catalogoResolver } },
      { path: 'catalogo/:id', loadComponent: () => import('./catalogo/libro-detalle/libro-detalle.component').then(m => m.LibroDetalleComponent), resolve: { data: libroDetalleResolver } },
      { path: 'prestamos', loadComponent: () => import('./prestamos-lector/prestamos-lector.component').then(m => m.PrestamosLectorComponent) },
      { path: 'reservaciones', loadComponent: () => import('./reservaciones/reservaciones.component').then(m => m.ReservacionesComponent) },
      { path: 'multas', loadComponent: () => import('./multas/multas.component').then(m => m.MultasComponent) },
      { path: 'favoritos', loadComponent: () => import('./favoritos/favoritos.component').then(m => m.FavoritosComponent) },
      { path: 'sugerencias', loadComponent: () => import('./sugerencias/mis-sugerencias/mis-sugerencias.component').then(m => m.MisSugerenciasComponent) },
      { path: 'sugerencias/nueva', loadComponent: () => import('./sugerencias/sugerencias-form/sugerencias-form.component').then(m => m.SugerenciasFormComponent) },
      { path: 'notificaciones', loadComponent: () => import('./notificaciones/notificaciones.component').then(m => m.NotificacionesComponent) },
      { path: 'mi-credencial', loadComponent: () => import('./mi-credencial/mi-credencial.component').then(m => m.MiCredencialComponent) },
    ]
  },
  { path: 'catalogo', loadComponent: () => import('./catalogo/catalogo.component').then(m => m.CatalogoComponent), canActivate: [authGuard, roleGuard(['LECTOR'])], resolve: { data: catalogoResolver } },
  { path: 'catalogo/:id', loadComponent: () => import('./catalogo/libro-detalle/libro-detalle.component').then(m => m.LibroDetalleComponent), canActivate: [authGuard, roleGuard(['LECTOR'])], resolve: { data: libroDetalleResolver } },
  { path: 'favoritos', loadComponent: () => import('./favoritos/favoritos.component').then(m => m.FavoritosComponent), canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'sugerencias', loadComponent: () => import('./sugerencias/mis-sugerencias/mis-sugerencias.component').then(m => m.MisSugerenciasComponent), canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'sugerencias/nueva', loadComponent: () => import('./sugerencias/sugerencias-form/sugerencias-form.component').then(m => m.SugerenciasFormComponent), canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'sugerencias/gestion', loadComponent: () => import('./sugerencias/gestion-sugerencias/gestion-sugerencias.component').then(m => m.GestionSugerenciasComponent), canActivate: [authGuard, roleGuard(['GERENTE', 'ADMIN'])] },
  { path: 'admin/usuarios', loadComponent: () => import('./admin/usuarios/usuarios.component').then(m => m.UsuariosComponent), canActivate: [authGuard, roleGuard(['ADMIN', 'GERENTE'])] },
  { path: 'auditoria', loadComponent: () => import('./admin/auditoria/auditoria.component').then(m => m.AuditoriaComponent), canActivate: [authGuard, roleGuard(['ADMIN'])] },
  { path: 'admin/backups', loadComponent: () => import('./admin/backups/backups.component').then(m => m.BackupsComponent), canActivate: [authGuard, roleGuard(['ADMIN'])] },
  { path: 'notificaciones', loadComponent: () => import('./notificaciones/notificaciones.component').then(m => m.NotificacionesComponent), canActivate: [authGuard] },
  { path: 'no-autorizado', component: NoAutorizadoComponent },
  { path: '**', redirectTo: '' }
];
