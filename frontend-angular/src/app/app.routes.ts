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
import { NoAutorizadoComponent } from './shared/no-autorizado/no-autorizado.component';
import { CatalogoComponent } from './catalogo/catalogo.component';
import { LibroDetalleComponent } from './catalogo/libro-detalle/libro-detalle.component';
import { FavoritosComponent } from './favoritos/favoritos.component';
import { SugerenciasFormComponent } from './sugerencias/sugerencias-form/sugerencias-form.component';
import { MisSugerenciasComponent } from './sugerencias/mis-sugerencias/mis-sugerencias.component';
import { PortalPublicoComponent } from './portal-publico/portal-publico.component';
import { DetallePublicoComponent } from './portal-publico/detalle-publico/detalle-publico.component';

// Los roleGuard de abajo reflejan los @PreAuthorize reales de cada
// controller en backend-springboot (verificado en el codigo, no asumido):
// - /libros: GET todos los roles, POST/PUT/DELETE y portada exigen
//   BIBLIOTECARIO/GERENTE/ADMIN -> el minimo para operar el inventario.
// - /prestamos y /prestamos/gestion: crear/devolver es solo
//   BIBLIOTECARIO/GERENTE; el listado por usuario admite tambien LECTOR
//   pero ADMIN queda fuera en todos los endpoints del controller.
// - /reservaciones y /multas: LECTOR/BIBLIOTECARIO/GERENTE (ADMIN fuera).
// Rama C (portal público): la raiz de la app es el portal SIN sesion
// (/api/publico/libros, permitAll en SecurityConfig). Las rutas de sesion
// quedan en /login y /registro; las del resto de ramas no cambian. El
// wildcard ya no redirige a /login sino al portal (nadie esta obligado a
// iniciar sesion para navegar el catalogo).
export const routes: Routes = [
  { path: '', component: PortalPublicoComponent },
  { path: 'portal/:id', component: DetallePublicoComponent },
  { path: 'login', component: LoginComponent },
  { path: 'registro', component: RegistroComponent },
  { path: 'libros', component: LibrosComponent, canActivate: [authGuard, roleGuard(['BIBLIOTECARIO', 'GERENTE', 'ADMIN'])] },
  { path: 'prestamos', component: PrestamosLectorComponent, canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'prestamos/gestion', component: PrestamosGestionComponent, canActivate: [authGuard, roleGuard(['BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'reservaciones', component: ReservacionesComponent, canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'multas', component: MultasComponent, canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  // Rama B (frontend/estudiante-catalogo-social): las 5 rutas del
  // consumidor son 100% LECTOR — verificado en FavoritoController.java y
  // SugerenciaAdquisicionController.java (los endpoints de favoritos y de
  // sugerencias/adquisicion son @PreAuthorize hasRole('LECTOR'); las
  // categorias y autores admiten cualquier rol autenticado).
  { path: 'catalogo', component: CatalogoComponent, canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'catalogo/:id', component: LibroDetalleComponent, canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'favoritos', component: FavoritosComponent, canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'sugerencias', component: MisSugerenciasComponent, canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'sugerencias/nueva', component: SugerenciasFormComponent, canActivate: [authGuard, roleGuard(['LECTOR'])] },
  { path: 'no-autorizado', component: NoAutorizadoComponent },
  { path: '**', redirectTo: '' }
];