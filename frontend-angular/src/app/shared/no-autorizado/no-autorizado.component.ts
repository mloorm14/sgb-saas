import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-no-autorizado',
  imports: [RouterModule],
  templateUrl: './no-autorizado.component.html'
})
export class NoAutorizadoComponent {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  // Link de vuelta al home del rol, segun la tabla de rutas real:
  // LECTOR -> /prestamos, BIBLIOTECARIO/GERENTE -> /prestamos/gestion,
  // ADMIN -> /libros. Sin sesion, a /login.
  volverAlHome(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    if (this.authService.hasRole('LECTOR')) {
      this.router.navigate(['/prestamos']);
    } else if (this.authService.hasRole('BIBLIOTECARIO', 'GERENTE')) {
      this.router.navigate(['/prestamos/gestion']);
    } else {
      this.router.navigate(['/libros']);
    }
  }
}