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
import { AdminUsuariosComponent } from './admin-usuarios/admin-usuarios.component';
import { ConfiguracionSistemaComponent } from './configuracion-sistema/configuracion-sistema.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'registro', component: RegistroComponent },
  { path: 'libros', component: LibrosComponent, canActivate: [authGuard] },
  { path: 'prestamos', component: PrestamosLectorComponent, canActivate: [authGuard] },
  { path: 'prestamos/gestion', component: PrestamosGestionComponent, canActivate: [authGuard] },
  { path: 'reservaciones', component: ReservacionesComponent, canActivate: [authGuard] },
  { path: 'multas', component: MultasComponent, canActivate: [authGuard] },
  // Listado accesible a ADMIN y GERENTE (igual que el backend, ver
  // UsuarioAdminController#listar); cambiar rol/estado queda gateado
  // dentro del propio componente porque ahí sí es solo ADMIN.
  { path: 'admin/usuarios', component: AdminUsuariosComponent, canActivate: [authGuard, roleGuard('ADMIN', 'GERENTE')] },
  // Solo ADMIN (ver ConfiguracionSistemaController, @PreAuthorize a nivel
  // de clase) -- roleGuard evita que un GERENTE/BIBLIOTECARIO/LECTOR
  // navegue acá por URL y vea un componente vacío condenado a fallar.
  { path: 'admin/configuracion', component: ConfiguracionSistemaComponent, canActivate: [authGuard, roleGuard('ADMIN')] },
  { path: '**', redirectTo: 'login' }
];