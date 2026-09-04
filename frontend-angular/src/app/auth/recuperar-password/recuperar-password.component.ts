import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

function passwordsIgualesValidator(control: AbstractControl): ValidationErrors | null {
  const pass = control.get('nuevaPassword')?.value;
  const pass2 = control.get('confirmarPassword')?.value;
  return pass && pass2 && pass !== pass2 ? { passwordsDistintas: true } : null;
}

// Recuperación de contraseña en 2 pasos (backend A5: POST /solicitar-reset
// genera código de 6 dígitos con TTL 15 min, POST /reset lo consume).
// Ruta pública /recuperar-password con redirectIfAuthenticatedGuard, mismo
// patrón de máquina de pasos que RegistroComponent. Copy en neutro.
@Component({
  selector: 'app-recuperar-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './recuperar-password.component.html',
  styles: [`:host { display: block; height: 100%; overflow-y: auto; }`]
})
export class RecuperarPasswordComponent {
  paso: 'solicitar' | 'restablecer' = 'solicitar';

  formSolicitar: FormGroup;
  formReset: FormGroup;

  errorMsg = '';
  mensajeInfo = '';
  cargando = false;
  mostrarPassword = false;
  mostrarPassword2 = false;
  correoFijo = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.formSolicitar = this.fb.group({
      correo: ['', [Validators.required, Validators.email]]
    });

    this.formReset = this.fb.group({
      codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
      nuevaPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmarPassword: ['', [Validators.required]]
    }, { validators: passwordsIgualesValidator });
  }

  togglePassword(): void { this.mostrarPassword = !this.mostrarPassword; }
  togglePassword2(): void { this.mostrarPassword2 = !this.mostrarPassword2; }

  solicitar(): void {
    this.formSolicitar.markAllAsTouched();
    if (this.formSolicitar.invalid) return;
    this.cargando = true;
    this.errorMsg = '';
    this.mensajeInfo = '';

    const { correo } = this.formSolicitar.value;
    this.authService.solicitarReset(correo.trim()).subscribe({
      next: () => {
        this.cargando = false;
        this.correoFijo = correo.trim();
        this.paso = 'restablecer';
        this.mensajeInfo = 'Se envió un código de 6 dígitos. Escribir el código junto con la nueva contraseña.';
      },
      error: (err) => {
        this.cargando = false;
        this.errorMsg = err?.error?.detail || 'No fue posible enviar el código. Verificar el correo e intentar de nuevo.';
      }
    });
  }

  restablecer(): void {
    this.formReset.markAllAsTouched();
    if (this.formReset.invalid) return;
    this.cargando = true;
    this.errorMsg = '';

    const { codigo, nuevaPassword } = this.formReset.value;
    this.authService.resetearPassword(this.correoFijo, codigo, nuevaPassword).subscribe({
      next: () => {
        this.cargando = false;
        this.router.navigate(['/login'], { queryParams: { reset: '1' } });
      },
      error: (err) => {
        this.cargando = false;
        this.errorMsg = err?.error?.detail || 'Código inválido o expirado. Es posible solicitar un nuevo envío.';
      }
    });
  }

  volverASolicitar(): void {
    this.paso = 'solicitar';
    this.errorMsg = '';
    this.mensajeInfo = '';
  }

  // Reenvío desde el paso 2 (usa el correo ya fijado, no el form del paso 1).
  reenviarCodigo(): void {
    if (!this.correoFijo || this.cargando) return;
    this.cargando = true;
    this.errorMsg = '';
    this.mensajeInfo = '';
    this.authService.solicitarReset(this.correoFijo).subscribe({
      next: () => {
        this.cargando = false;
        this.mensajeInfo = 'Se envió un nuevo código. Revisar la bandeja de entrada.';
      },
      error: (err) => {
        this.cargando = false;
        this.errorMsg = err?.error?.detail || 'No fue posible enviar un nuevo código. Intentar de nuevo.';
      }
    });
  }
}
