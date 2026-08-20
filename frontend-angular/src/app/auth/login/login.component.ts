import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './login.component.html'
})
export class LoginComponent implements OnInit {
  form: FormGroup;
  errorMsg: string = '';
  exitoMsg: string = '';
  cargando: boolean = false;
  mostrarPassword: boolean = false;
  correoPendienteVerificar: string = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.form = this.fb.group({
      correo: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      compliance: [false, [Validators.requiredTrue]]
    });
  }

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('verificado') === '1') {
      this.exitoMsg = 'Cuenta verificada correctamente. Ya podés iniciar sesión.';
    }
  }

  togglePassword() {
    this.mostrarPassword = !this.mostrarPassword;
  }

  // Hallazgo lambda: la redireccion post-login ya no es un /libros fijo,
  // depende del rol real (misma tabla que roleGuard en app.routes.ts).
  // Rama B: LECTOR -> /catalogo (la pantalla inicial del consumidor).
  // GERENTE -> /dashboard-gerente (rama fix/sincronizar-despliegue-y-dashboard-gerente);
  // BIBLIOTECARIO -> /prestamos/gestion (fallback hasta que exista su dashboard).
  // TODO(frontend/gerente-panel-administrativo): ADMIN -> /admin cuando
  // exista la rama F; hoy /libros (el backend excluye a ADMIN de la
  // operacion diaria en prestamos/reservaciones/multas).
  private redirigirSegunRol(): void {
    if (this.authService.hasRole('GERENTE')) {
      this.router.navigate(['/dashboard-gerente']);
    } else if (this.authService.hasRole('LECTOR')) {
      this.router.navigate(['/catalogo']);
    } else if (this.authService.hasRole('BIBLIOTECARIO')) {
      this.router.navigate(['/dashboard-bibliotecario']);
    } else {
      this.router.navigate(['/libros']);
    }
  }

  submit() {
    if (this.form.invalid) return;
    this.cargando = true;
    this.errorMsg = '';
    this.exitoMsg = '';
    this.correoPendienteVerificar = '';

    const { correo, password } = this.form.value;

    this.authService.login(correo, password).subscribe({
      next: () => {
        this.cargando = false;
        this.redirigirSegunRol();
      },
      error: (err) => {
        this.cargando = false;
        const detail: string | undefined = err.error?.detail;

        switch (err.status) {
          case 401:
            this.errorMsg = detail || 'Correo o contraseña incorrectos';
            break;
          case 403:
            // DisabledException del backend: cuenta inactiva o correo sin
            // verificar (hallazgo eta). No hay forma de distinguir cual de
            // las dos sin parsear el texto, asi que ademas del mensaje
            // ofrecemos el link a verificar-correo por si aplica.
            this.errorMsg = detail || 'Tu cuenta no está activa';
            this.correoPendienteVerificar = correo;
            break;
          case 423:
            this.errorMsg = detail || 'Tu cuenta está bloqueada por una multa pendiente';
            break;
          case 429:
            this.errorMsg = detail || 'Demasiados intentos. Esperá un momento antes de volver a intentar';
            break;
          default:
            this.errorMsg = detail || 'Error al iniciar sesión, intente de nuevo';
        }
      }
    });
  }
}