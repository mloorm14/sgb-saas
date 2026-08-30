import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { LoginComponent } from './auth/login/login.component';
import { RegistroComponent } from './auth/registro/registro.component';
import { LibrosComponent } from './libros/libros.component';
import { PrestamosLectorComponent } from './prestamos-lector/prestamos-lector.component';
import { PrestamosGestionComponent } from './prestamos-gestion/prestamos-gestion.component';
import { ReservacionesComponent } from './reservaciones/reservaciones.component';
import { MultasComponent } from './multas/multas.component';
import { ReportesComponent } from './reportes/reportes.component';
import { DashboardGerenteComponent } from './dashboard-gerente/dashboard-gerente.component';
import { DashboardGerenteAdminHomeComponent } from './dashboard-gerente-admin/dashboard-gerente-admin-home.component';
import { DashboardBibliotecarioComponent } from './dashboard/dashboard-bibliotecario.component';
import { DashboardBibliotecarioHomeComponent } from './dashboard/dashboard-bibliotecario-home.component';
import { NoAutorizadoComponent } from './shared/no-autorizado/no-autorizado.component';
import { CatalogoComponent } from './catalogo/catalogo.component';
import { LibroDetalleComponent } from './catalogo/libro-detalle/libro-detalle.component';
import { FavoritosComponent } from './favoritos/favoritos.component';
import { SugerenciasFormComponent } from './sugerencias/sugerencias-form/sugerencias-form.component';
import { MisSugerenciasComponent } from './sugerencias/mis-sugerencias/mis-sugerencias.component';
import { GestionSugerenciasComponent } from './sugerencias/gestion-sugerencias/gestion-sugerencias.component';
import { PortalPublicoComponent } from './portal-publico/portal-publico.component';
import { DetallePublicoComponent } from './portal-publico/detalle-publico/detalle-publico.component';
import { ConfiguracionSistemaComponent } from './configuracion-sistema/configuracion-sistema.component';
import { UsuariosComponent } from './admin/usuarios/usuarios.component';
import { NotificacionesComponent } from './notificaciones/notificaciones.component';
import { AuditoriaComponent } from './admin/auditoria/auditoria.component';
import { DashboardGerenteAdminComponent } from './dashboard-gerente-admin/dashboard-gerente-admin.component';
import { DashboardLectorComponent } from './dashboard-lector/dashboard-lector.component';
import { MiCredencialComponent } from './mi-credencial/mi-credencial.component';
import { DevolucionesComponent } from './devoluciones/devoluciones.component';
import { LibrosPendientesComponent } from './libros-pendientes/libros-pendientes.component';
import { catalogoResolver } from './core/resolvers/catalogo.resolver';
import { libroDetalleResolver } from './core/resolvers/libro-detalle.resolver';

export const routes: Routes = [
  { path: '', component: PortalPublicoComponent },
  { path: 'portal/:id', component: DetallePublicoComponent, resolve: { data: libroDetalleResolver } },
  { path: 'login', component: LoginComponent },
  { path: 'registro', component: RegistroComponent },
  { path: 'libros', component: LibrosComponent, canActivate: [authGuard, roleGuard(['BIBLIOTECARIO', 'GERENTE', 'ADMIN'])] },
  { path: 'prestamos', component: PrestamosLectorComponent, canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'prestamos/gestion', component: PrestamosGestionComponent, canActivate: [authGuard, roleGuard(['BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'reservaciones', component: ReservacionesComponent, canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'multas', component: MultasComponent, canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'admin/configuracion', component: ConfiguracionSistemaComponent, canActivate: [authGuard, roleGuard(['ADMIN'])] },
  { path: 'reportes', component: ReportesComponent, canActivate: [authGuard, roleGuard(['GERENTE', 'ADMIN'])] },
  { path: 'dashboard-gerente', component: DashboardGerenteComponent, canActivate: [authGuard, roleGuard(['GERENTE'])] },
  {
    path: 'dashboard-bibliotecario',
    component: DashboardBibliotecarioComponent,
    canActivate: [authGuard, roleGuard(['BIBLIOTECARIO'])],
    children: [
      { path: '', component: DashboardBibliotecarioHomeComponent },
      { path: 'libros', component: LibrosComponent },
      { path: 'libros-pendientes', component: LibrosPendientesComponent },
      { path: 'prestamos/gestion', component: PrestamosGestionComponent },
      { path: 'reservaciones', component: ReservacionesComponent },
      { path: 'devoluciones', component: DevolucionesComponent },
      { path: 'multas', component: MultasComponent },
    ]
  },
  {
    path: 'dashboard-admin',
    component: DashboardGerenteAdminComponent,
    canActivate: [authGuard, roleGuard(['GERENTE', 'ADMIN'])],
    children: [
      { path: '', component: DashboardGerenteAdminHomeComponent },
      { path: 'libros', component: LibrosComponent },
      { path: 'libros-pendientes', component: LibrosPendientesComponent },
      { path: 'prestamos/gestion', component: PrestamosGestionComponent },
      { path: 'reservaciones', component: ReservacionesComponent },
      { path: 'multas', component: MultasComponent },
      { path: 'sugerencias/gestion', component: GestionSugerenciasComponent },
      { path: 'admin/usuarios', component: UsuariosComponent },
      { path: 'auditoria', component: AuditoriaComponent },
      { path: 'reportes', component: ReportesComponent, canActivate: [roleGuard(['GERENTE', 'ADMIN'])] },
      { path: 'admin/configuracion', component: ConfiguracionSistemaComponent, canActivate: [roleGuard(['ADMIN'])] },
    ]
  },
  {
    path: 'dashboard-lector',
    component: DashboardLectorComponent,
    canActivate: [authGuard, roleGuard(['LECTOR'])],
    children: [
      { path: '', redirectTo: 'catalogo', pathMatch: 'full' },
      { path: 'catalogo', component: CatalogoComponent, resolve: { data: catalogoResolver } },
      { path: 'catalogo/:id', component: LibroDetalleComponent, resolve: { data: libroDetalleResolver } },
      { path: 'prestamos', component: PrestamosLectorComponent },
      { path: 'reservaciones', component: ReservacionesComponent },
      { path: 'multas', component: MultasComponent },
      { path: 'favoritos', component: FavoritosComponent },
      { path: 'sugerencias', component: MisSugerenciasComponent },
      { path: 'sugerencias/nueva', component: SugerenciasFormComponent },
      { path: 'notificaciones', component: NotificacionesComponent },
      { path: 'mi-credencial', component: MiCredencialComponent },
    ]
  },
  { path: 'catalogo', component: CatalogoComponent, canActivate: [authGuard, roleGuard(['LECTOR'])], resolve: { data: catalogoResolver } },
  { path: 'catalogo/:id', component: LibroDetalleComponent, canActivate: [authGuard, roleGuard(['LECTOR'])], resolve: { data: libroDetalleResolver } },
  { path: 'favoritos', component: FavoritosComponent, canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'sugerencias', component: MisSugerenciasComponent, canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'sugerencias/nueva', component: SugerenciasFormComponent, canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'sugerencias/gestion', component: GestionSugerenciasComponent, canActivate: [authGuard, roleGuard(['GERENTE', 'ADMIN'])] },
  { path: 'admin/usuarios', component: UsuariosComponent, canActivate: [authGuard, roleGuard(['ADMIN', 'GERENTE'])] },
  { path: 'auditoria', component: AuditoriaComponent, canActivate: [authGuard, roleGuard(['ADMIN'])] },
  { path: 'notificaciones', component: NotificacionesComponent, canActivate: [authGuard] },
  { path: 'no-autorizado', component: NoAutorizadoComponent },
  { path: '**', redirectTo: '' }
];
