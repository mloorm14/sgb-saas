import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { interval, Subscription } from 'rxjs';
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
export class RegistroComponent implements OnDestroy {
  paso: 'registro' | 'verificar' = 'registro';

  formRegistro: FormGroup;
  formVerificar: FormGroup;

  errorMsg: string = '';
  errorMsgVerificar: string = '';
  mensajeReenvio: string = '';
  cargando: boolean = false;
  mostrarPassword: boolean = false;
  mostrarPassword2: boolean = false;
  correoRegistrado: string = '';

  // F6: reenvío máximo 3 por correo, cooldown 180s entre cada uno.
  // Contador individual por correo en localStorage (no global).
  static readonly MAX_REENVIOS = 3;
  static readonly COOLDOWN_SEG = 180;
  reenviosRealizados = 0;
  segundosRestantes = 0;
  enviandoReenvio = false;
  private cooldownSub?: Subscription;

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
        this.iniciarEstadoReenvio();
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
        this.limpiarReenvio(this.correoRegistrado);
        this.router.navigate(['/login'], { queryParams: { verificado: '1' } });
      },
      error: (err) => {
        this.cargando = false;
        this.errorMsgVerificar = err.error?.detail || 'Código inválido o expirado. Es posible solicitar un nuevo envío.';
      }
    });
  }

  volverARegistro() {
    this.paso = 'registro';
    this.formRegistro.reset({ compliance: false });
  }

  ngOnDestroy(): void {
    this.cooldownSub?.unsubscribe();
  }

  // ── F6: reenvío con límite y cooldown por correo ──────────────
  private claveReenvio(correo: string): string {
    return `reenvio:${correo.trim().toLowerCase()}`;
  }

  private leerReenvio(correo: string): { count: number; ultimoTs: number } {
    try {
      const raw = localStorage.getItem(this.claveReenvio(correo));
      if (!raw) return { count: 0, ultimoTs: 0 };
      const parsed = JSON.parse(raw);
      return { count: Number(parsed.count) || 0, ultimoTs: Number(parsed.ultimoTs) || 0 };
    } catch {
      return { count: 0, ultimoTs: 0 };
    }
  }

  private guardarReenvio(correo: string, count: number, ultimoTs: number): void {
    try {
      localStorage.setItem(this.claveReenvio(correo), JSON.stringify({ count, ultimoTs }));
    } catch {}
  }

  private limpiarReenvio(correo: string): void {
    try { localStorage.removeItem(this.claveReenvio(correo)); } catch {}
  }

  // Llamar al entrar al paso verificar (tras registro exitoso).
  iniciarEstadoReenvio(): void {
    const { count, ultimoTs } = this.leerReenvio(this.correoRegistrado);
    this.reenviosRealizados = count;
    this.mensajeReenvio = '';
    this.errorMsgVerificar = '';
    const transcurrido = Math.floor((Date.now() - ultimoTs) / 1000);
    const restante = ultimoTs > 0 ? RegistroComponent.COOLDOWN_SEG - transcurrido : 0;
    if (count > 0 && restante > 0) {
      this.iniciarCooldown(restante);
    } else {
      this.segundosRestantes = 0;
    }
  }

  get puedeReenviar(): boolean {
    return this.reenviosRealizados < RegistroComponent.MAX_REENVIOS
      && this.segundosRestantes <= 0
      && !this.enviandoReenvio
      && this.correoRegistrado.trim().length > 0;
  }

  get textoCooldown(): string {
    const m = Math.floor(this.segundosRestantes / 60);
    const s = this.segundosRestantes % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  private iniciarCooldown(segundos: number): void {
    this.cooldownSub?.unsubscribe();
    this.segundosRestantes = segundos;
    this.cooldownSub = interval(1000).subscribe(() => {
      this.segundosRestantes -= 1;
      if (this.segundosRestantes <= 0) {
        this.segundosRestantes = 0;
        this.cooldownSub?.unsubscribe();
      }
    });
  }

  reenviarCodigo(): void {
    if (!this.puedeReenviar) return;
    this.enviandoReenvio = true;
    this.errorMsgVerificar = '';
    this.mensajeReenvio = '';
    this.authService.reenviarCodigo(this.correoRegistrado.trim()).subscribe({
      next: () => {
        this.enviandoReenvio = false;
        this.reenviosRealizados += 1;
        this.guardarReenvio(this.correoRegistrado, this.reenviosRealizados, Date.now());
        this.iniciarCooldown(RegistroComponent.COOLDOWN_SEG);
        this.mensajeReenvio = `Nuevo código enviado (${this.reenviosRealizados}/${RegistroComponent.MAX_REENVIOS}). Revisar la bandeja de entrada.`;
      },
      error: (err) => {
        this.enviandoReenvio = false;
        this.errorMsgVerificar = err?.error?.detail ?? 'No fue posible enviar un nuevo código. Intentar de nuevo.';
      }
    });
  }
}