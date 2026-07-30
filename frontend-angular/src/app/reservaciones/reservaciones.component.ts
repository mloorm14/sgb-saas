import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-reservaciones',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './reservaciones.component.html'
})
export class ReservacionesComponent implements OnInit {
  esLector: boolean = false;

  formCrear: FormGroup;
  errorMsgCrear: string = '';

  usuarioIdBusqueda: number | null = null;
  reservaciones: any[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  private apiUrl = 'http://localhost:8080/api/v1';

  constructor(
    private http: HttpClient,
    private fb: FormBuilder,
    private authService: AuthService
  ) {
    this.formCrear = this.fb.group({
      usuarioId: ['', [Validators.required]],
      libroId: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.esLector = this.authService.hasRole('LECTOR') && !this.authService.hasRole('BIBLIOTECARIO', 'GERENTE', 'ADMIN');

    if (this.esLector) {
      // El lector reserva y ve solo lo propio: no necesita elegir usuarioId
      const miId = this.authService.getUserId();
      this.formCrear.patchValue({ usuarioId: miId });
      this.usuarioIdBusqueda = miId;
      this.buscarReservaciones();
    }
  }

  crearReservacion(): void {
    if (this.formCrear.invalid) return;
    this.errorMsgCrear = '';
    this.http.post(`${this.apiUrl}/reservaciones`, this.formCrear.value).subscribe({
      next: () => {
        this.formCrear.patchValue({ libroId: '' });
        if (this.usuarioIdBusqueda === Number(this.formCrear.value.usuarioId)) {
          this.cargarPagina();
        }
      },
      error: () => { this.errorMsgCrear = 'Error al crear la reservación'; }
    });
  }

  buscarReservaciones(): void {
    if (!this.usuarioIdBusqueda) {
      this.errorMsg = 'Ingresá un ID de usuario para buscar';
      return;
    }
    this.errorMsg = '';
    this.currentPage = 0;
    this.cargarPagina();
  }

  private cargarPagina(): void {
    this.cargando = true;
    this.http.get<any>(
      `${this.apiUrl}/reservaciones/usuario/${this.usuarioIdBusqueda}?page=${this.currentPage}&size=${this.pageSize}&sort=id,desc`
    ).subscribe({
      next: (data) => {
        this.reservaciones = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al buscar las reservaciones';
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
}