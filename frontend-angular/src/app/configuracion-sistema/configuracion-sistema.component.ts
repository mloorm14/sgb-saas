import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/services/auth.service';
import { environment } from '../../environments/environment';

const VALOR_MAX_LENGTH = 200;

@Component({
  selector: 'app-configuracion-sistema',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './configuracion-sistema.component.html'
})
export class ConfiguracionSistemaComponent implements OnInit {
  valorMaxLength = VALOR_MAX_LENGTH;

  // El route guard (roleGuard('ADMIN')) ya bloquea la navegación directa
  // por URL, pero este chequeo queda igual acá como defensa en profundidad
  // por si el componente se instancia por otra vía (o el guard cambia).
  esAdmin: boolean = false;

  configuraciones: any[] = [];
  cargando: boolean = false;
  errorMsg: string = '';

  claveEditando: string | null = null;
  valorEditando: string = '';

  private apiUrl = environment.apiUrl + '/v1';

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.esAdmin = this.authService.hasRole('ADMIN');
    if (this.esAdmin) {
      this.cargarConfiguraciones();
    }
  }

  private cargarConfiguraciones(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.http.get<any[]>(`${this.apiUrl}/configuracion`).subscribe({
      next: (data) => {
        this.configuraciones = data;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar la configuración del sistema';
        this.cargando = false;
      }
    });
  }

  iniciarEdicion(config: any): void {
    this.claveEditando = config.clave;
    this.valorEditando = config.valor;
  }

  cancelarEdicion(): void {
    this.claveEditando = null;
    this.valorEditando = '';
  }

  guardarValor(): void {
    if (!this.claveEditando) return;
    const valor = this.valorEditando.trim();
    if (!valor || valor.length > VALOR_MAX_LENGTH) return;

    this.http.put(`${this.apiUrl}/configuracion/${this.claveEditando}`, { valor }).subscribe({
      next: () => {
        this.cancelarEdicion();
        this.cargarConfiguraciones();
      },
      error: (err) => {
        this.errorMsg = err.status === 403
          ? 'No tenés permisos para modificar la configuración del sistema'
          : 'Error al actualizar el valor de configuración';
      }
    });
  }
}
