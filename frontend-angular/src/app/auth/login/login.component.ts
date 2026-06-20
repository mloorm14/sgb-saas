import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  form: FormGroup;
  errorMsg: string = '';
  cargando: boolean = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      correo: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  submit() {
    if (this.form.invalid) return;
    this.cargando = true;
    this.errorMsg = '';

    const { correo, password } = this.form.value;

    this.authService.login(correo, password).subscribe({
      next: () => {
        this.cargando = false;
        this.router.navigate(['/libros']);
      },
      error: (err) => {
        this.cargando = false;
        this.errorMsg = err.status === 401
          ? 'Correo o contraseña incorrectos'
          : 'Error al iniciar sesión, intente de nuevo';
      }
    });
  }
}