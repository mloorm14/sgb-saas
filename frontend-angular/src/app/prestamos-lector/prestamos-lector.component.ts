import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../core/services/auth.service';
import { PrestamoService } from '../core/services/prestamo.service';
import { Prestamo } from '../core/models/prestamo.model';

@Component({
    selector: 'app-prestamos-lector',
    imports: [CommonModule],
    templateUrl: './prestamos-lector.component.html'
})
export class PrestamosLectorComponent implements OnInit {
  prestamos: Prestamo[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  constructor(
    private prestamoService: PrestamoService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.cargarPrestamos();
  }

  cargarPrestamos(): void {
    const miId = this.authService.getUserId();
    if (!miId) {
      this.errorMsg = 'No se pudo identificar al usuario logueado';
      return;
    }
    this.cargando = true;
    this.prestamoService.listarPorUsuario(miId, {
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
        this.errorMsg = 'Error al cargar tus préstamos';
        this.cargando = false;
      }
    });
  }

  paginaAnterior(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.cargarPrestamos();
    }
  }

  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.cargarPrestamos();
    }
  }
}