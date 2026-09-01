import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './login.component.html',
  styles: [`:host { display: block; height: 100%; overflow-y: auto; }`]
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
    private route: ActivatedRoute,
    public themeService: ThemeService
  ) {
    this.form = this.fb.group({
      correo: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      compliance: [false, [Validators.requiredTrue]]
    });
  }

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('verificado') === '1') {
      this.exitoMsg = 'Cuenta verificada correctamente. Ya puedes iniciar sesión.';
    }
  }

  togglePassword() {
    this.mostrarPassword = !this.mostrarPassword;
  }

  // Redirección post-login hacia los paneles con sidebar:
  // - ADMIN/GERENTE → /dashboard-admin (panel con sidebar).
  // - LECTOR → /dashboard-lector (panel consumidor con sidebar).
  // - BIBLIOTECARIO → /dashboard-bibliotecario (panel Cajas con sidebar).
  private redirigirSegunRol(): void {
    if (this.authService.hasRole('ADMIN') || this.authService.hasRole('GERENTE')) {
      this.router.navigate(['/dashboard-admin']);
    } else if (this.authService.hasRole('LECTOR')) {
      this.router.navigate(['/dashboard-lector']);
    } else if (this.authService.hasRole('BIBLIOTECARIO')) {
      this.router.navigate(['/dashboard-bibliotecario']);
    } else {
      this.router.navigate(['/login']);
    }
  }

  submit() {
    this.form.markAllAsTouched();
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
            this.errorMsg = detail || 'Demasiados intentos. Espera un momento antes de volver a intentar';
            break;
          default:
            this.errorMsg = detail || 'Error al iniciar sesión, intente de nuevo';
        }
      }
    });
  }
}