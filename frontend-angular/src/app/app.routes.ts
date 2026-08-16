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

// Los roleGuard de abajo reflejan los @PreAuthorize reales de cada
// controller en backend-springboot (verificado en el codigo, no asumido):
// - /libros: GET todos los roles, POST/PUT/DELETE y portada exigen
//   BIBLIOTECARIO/GERENTE/ADMIN -> el minimo para operar el inventario.
// - /prestamos y /prestamos/gestion: crear/devolver es solo
//   BIBLIOTECARIO/GERENTE; el listado por usuario admite tambien LECTOR
//   pero ADMIN queda fuera en todos los endpoints del controller.
// - /reservaciones y /multas: LECTOR/BIBLIOTECARIO/GERENTE (ADMIN fuera).
export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'registro', component: RegistroComponent },
  { path: 'libros', component: LibrosComponent, canActivate: [authGuard, roleGuard(['BIBLIOTECARIO', 'GERENTE', 'ADMIN'])] },
  { path: 'prestamos', component: PrestamosLectorComponent, canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'prestamos/gestion', component: PrestamosGestionComponent, canActivate: [authGuard, roleGuard(['BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'reservaciones', component: ReservacionesComponent, canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'multas', component: MultasComponent, canActivate: [authGuard, roleGuard(['LECTOR', 'BIBLIOTECARIO', 'GERENTE'])] },
  { path: 'no-autorizado', component: NoAutorizadoComponent },
  { path: '**', redirectTo: 'login' }
];