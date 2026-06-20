import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './registro.component.html'
})
export class RegistroComponent {
  form: FormGroup;
  errorMsg: string = '';
  exitoMsg: string = '';
  cargando: boolean = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      nombre: ['', [Validators.required, Validators.minLength(3)]],
      correo: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  submit() {
    if (this.form.invalid) return;
    this.cargando = true;
    this.errorMsg = '';
    this.exitoMsg = '';

    const { nombre, correo, password } = this.form.value;

    this.authService.registro(nombre, correo, password).subscribe({
      next: () => {
        this.cargando = false;
        this.exitoMsg = 'Usuario registrado correctamente. Redirigiendo...';
        setTimeout(() => this.router.navigate(['/login']), 2000);
      },
      error: (err) => {
        this.cargando = false;
        this.errorMsg = err.status === 409
          ? 'Este correo ya está registrado'
          : 'Error al registrarse, intente de nuevo';
      }
    });
  }
}