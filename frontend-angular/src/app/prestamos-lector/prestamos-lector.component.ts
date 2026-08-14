import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-prestamos-lector',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './prestamos-lector.component.html'
})
export class PrestamosLectorComponent implements OnInit {
  prestamos: any[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  private apiUrl = 'https://sgb-backend-b058.onrender.com/api/v1';

  constructor(
    private http: HttpClient,
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
    this.http.get<any>(
      `${this.apiUrl}/prestamos/usuario/${miId}?page=${this.currentPage}&size=${this.pageSize}&sort=id,desc`
    ).subscribe({
      next: (data) => {
        // Nota: nombres de campo (usuarioId, libroId, estadoPrestamoId...)
        // siguen la entidad Prestamo.java tal cual hoy. Ajustar aqui si
        // PrestamoResponseDTO expone nombres distintos cuando Cajas
        // termine el backend.
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