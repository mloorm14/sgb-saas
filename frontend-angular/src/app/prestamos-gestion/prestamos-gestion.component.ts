import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-prestamos-gestion',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './prestamos-gestion.component.html'
})
export class PrestamosGestionComponent {
  // Formulario de creación
  formCrear: FormGroup;
  errorMsgCrear: string = '';

  // Búsqueda de préstamos por usuario (no existe endpoint de "listar todos")
  usuarioIdBusqueda: number | null = null;
  prestamos: any[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  private apiUrl = 'http://localhost:8080/api/v1';

  constructor(private http: HttpClient, private fb: FormBuilder) {
    this.formCrear = this.fb.group({
      usuarioId: ['', [Validators.required]],
      libroId: ['', [Validators.required]],
      dias: ['', [Validators.required, Validators.min(1)]]
    });
  }

  crearPrestamo(): void {
    if (this.formCrear.invalid) return;
    this.errorMsgCrear = '';
    this.http.post(`${this.apiUrl}/prestamos`, this.formCrear.value).subscribe({
      next: () => {
        this.formCrear.reset();
        // Si el usuario recien prestado es el mismo que se esta buscando, refrescamos
        if (this.usuarioIdBusqueda === Number(this.formCrear.value.usuarioId)) {
          this.buscarPrestamos();
        }
      },
      error: () => { this.errorMsgCrear = 'Error al crear el préstamo'; }
    });
  }

  buscarPrestamos(): void {
    if (!this.usuarioIdBusqueda) {
      this.errorMsg = 'Ingresá un ID de usuario para buscar';
      return;
    }
    this.cargando = true;
    this.errorMsg = '';
    this.currentPage = 0;
    this.cargarPagina();
  }

  private cargarPagina(): void {
    this.cargando = true;
    this.http.get<any>(
      `${this.apiUrl}/prestamos/usuario/${this.usuarioIdBusqueda}?page=${this.currentPage}&size=${this.pageSize}&sort=id,desc`
    ).subscribe({
      next: (data) => {
        this.prestamos = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al buscar los préstamos de ese usuario';
        this.cargando = false;
      }
    });
  }

  paginaAnterior(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.cargarPagina();
    }
  }

  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.cargarPagina();
    }
  }

  registrarDevolucion(prestamoId: number): void {
    if (!confirm('¿Confirmar la devolución de este préstamo?')) return;
    this.http.post(`${this.apiUrl}/prestamos/${prestamoId}/devolucion`, {}).subscribe({
      next: () => { this.cargarPagina(); },
      error: () => { this.errorMsg = 'Error al registrar la devolución'; }
    });
  }
}