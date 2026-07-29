import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { LoginComponent } from './auth/login/login.component';
import { RegistroComponent } from './auth/registro/registro.component';
import { LibrosComponent } from './libros/libros.component';
import { PrestamosLectorComponent } from './prestamos-lector/prestamos-lector.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'registro', component: RegistroComponent },
  { path: 'libros', component: LibrosComponent, canActivate: [authGuard] },
  { path: 'prestamos', component: PrestamosLectorComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: 'login' }
];