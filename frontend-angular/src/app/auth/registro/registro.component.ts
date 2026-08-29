import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

function passwordsIgualesValidator(control: AbstractControl): ValidationErrors | null {
  const pass = control.get('password')?.value;
  const pass2 = control.get('confirmarPassword')?.value;
  return pass && pass2 && pass !== pass2 ? { passwordsDistintas: true } : null;
}

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './registro.component.html',
  styles: [`:host { display: block; height: 100%; overflow-y: auto; }`]
})
export class RegistroComponent {
  paso: 'registro' | 'verificar' = 'registro';

  formRegistro: FormGroup;
  formVerificar: FormGroup;

  errorMsg: string = '';
  errorMsgVerificar: string = '';
  cargando: boolean = false;
  mostrarPassword: boolean = false;
  mostrarPassword2: boolean = false;
  correoRegistrado: string = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.formRegistro = this.fb.group({
      nombre: ['', [Validators.required, Validators.minLength(2)]],
      apellido: ['', [Validators.required, Validators.minLength(2)]],
      correo: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmarPassword: ['', [Validators.required]],
      compliance: [false, [Validators.requiredTrue]]
    }, { validators: passwordsIgualesValidator });

    this.formVerificar = this.fb.group({
      codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
    });
  }

  // Fuerza de contraseña, solo para feedback visual (no reemplaza la
  // validacion real del backend, que exige minimo 8 caracteres)
  get fuerzaPassword(): { porcentaje: number; etiqueta: string; color: string } {
    const val: string = this.formRegistro.get('password')?.value || '';
    let puntos = 0;
    if (val.length >= 8) puntos += 33;
    if (/[A-Z]/.test(val) && /[0-9]/.test(val)) puntos += 33;
    if (/[^A-Za-z0-9]/.test(val)) puntos += 34;

    if (puntos === 0) return { porcentaje: 0, etiqueta: 'Mínimo 8 caracteres', color: '#ba1a1a' };
    if (puntos < 60) return { porcentaje: puntos, etiqueta: 'Débil (agrega números y mayúsculas)', color: '#fec004' };
    if (puntos < 100) return { porcentaje: puntos, etiqueta: 'Buena', color: '#003694' };
    return { porcentaje: 100, etiqueta: 'Fuerte', color: '#006b5f' };
  }

  togglePassword() { this.mostrarPassword = !this.mostrarPassword; }
  togglePassword2() { this.mostrarPassword2 = !this.mostrarPassword2; }

  submitRegistro() {
    this.formRegistro.markAllAsTouched();
    if (this.formRegistro.invalid) return;
    this.cargando = true;
    this.errorMsg = '';

    const { nombre, apellido, correo, password } = this.formRegistro.value;

    this.authService.registro(nombre, apellido, correo, password).subscribe({
      next: () => {
        this.cargando = false;
        this.correoRegistrado = correo;
        this.paso = 'verificar';
      },
      error: (err) => {
        this.cargando = false;
        this.errorMsg = err.status === 409
          ? (err.error?.detail || 'Este correo ya está registrado')
          : (err.error?.detail || 'Error al registrarse, intente de nuevo');
      }
    });
  }

  submitVerificar() {
    if (this.formVerificar.invalid) return;
    this.cargando = true;
    this.errorMsgVerificar = '';

    const { codigo } = this.formVerificar.value;

    this.authService.verificarCorreo(this.correoRegistrado, codigo).subscribe({
      next: () => {
        this.cargando = false;
        this.router.navigate(['/login'], { queryParams: { verificado: '1' } });
      },
      error: (err) => {
        this.cargando = false;
        // El backend no tiene endpoint de reenvio de codigo: si expiro o es
        // invalido, el detail del ProblemDetail lo dice explicitamente y la
        // unica salida real es volver a /registro (ver roadmap, gap real).
        this.errorMsgVerificar = err.error?.detail || 'Código inválido o expirado';
      }
    });
  }

  volverARegistro() {
    this.paso = 'registro';
    this.formRegistro.reset({ compliance: false });
  }
}