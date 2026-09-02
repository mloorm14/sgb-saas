import { Component, OnDestroy, OnInit } from '@angular/core';
import { CredencialQrService } from '../core/services/credencial-qr.service';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-mi-credencial',
  standalone: true,
  imports: [],
  templateUrl: './mi-credencial.component.html'
})
export class MiCredencialComponent implements OnInit, OnDestroy {
  objectUrl: string | null = null;
  cargando = false;
  error = '';

  constructor(
    private credencialService: CredencialQrService,
    private authService: AuthService
  ) {}

  get correoUsuario(): string {
    return this.authService.getCorreo() ?? '';
  }

  get iniciales(): string {
    const correo = this.correoUsuario;
    if (!correo) return '??';
    const parte = correo.split('@')[0];
    return parte.substring(0, 2).toUpperCase();
  }

  ngOnInit(): void {
    this.cargando = true;
    this.credencialService.obtenerImagen().subscribe({
      next: (blob) => {
        this.objectUrl = URL.createObjectURL(blob);
        this.cargando = false;
      },
      error: () => {
        this.error = 'No se pudo cargar la credencial. Intenta nuevamente.';
        this.cargando = false;
      }
    });
  }

  ngOnDestroy(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
    }
  }
}
