import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { PrestamoService } from '../core/services/prestamo.service';
import { Prestamo, PrestamoRequest } from '../core/models/prestamo.model';

@Component({
    selector: 'app-prestamos-gestion',
    imports: [CommonModule, ReactiveFormsModule, FormsModule],
    templateUrl: './prestamos-gestion.component.html'
})
export class PrestamosGestionComponent {
  // Formulario de creación
  formCrear: FormGroup;
  errorMsgCrear: string = '';

  // Búsqueda de préstamos por usuario (no existe endpoint de "listar todos")
  usuarioIdBusqueda: number | null = null;
  prestamos: Prestamo[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  constructor(private prestamoService: PrestamoService, private fb: FormBuilder) {
    this.formCrear = this.fb.group({
      usuarioId: ['', [Validators.required]],
      libroId: ['', [Validators.required]],
      diasPrestamo: ['', [Validators.required, Validators.min(1)]]
    });
  }

  crearPrestamo(): void {
    if (this.formCrear.invalid) return;
    this.errorMsgCrear = '';
    // Los valores del form viajan tal cual (el backend convierte strings);
    // solo cambia el canal de transporte.
    this.prestamoService.crear(this.formCrear.value as PrestamoRequest).subscribe({
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
    this.prestamoService.listarPorUsuario(this.usuarioIdBusqueda!, {
      page: this.currentPage,
      size: this.pageSize,
      sort: 'id,desc'
    }).subscribe({
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
    this.prestamoService.devolver(prestamoId).subscribe({
      next: () => { this.cargarPagina(); },
      error: () => { this.errorMsg = 'Error al registrar la devolución'; }
    });
  }
}