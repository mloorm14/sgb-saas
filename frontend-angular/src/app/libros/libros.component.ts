import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-libros',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './libros.component.html'
})
export class LibrosComponent implements OnInit {
  libros: any[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';
  mostrarFormulario: boolean = false;
  modoEdicion: boolean = false;
  libroSeleccionadoId: number | null = null;
  form: FormGroup;

  private apiUrl = 'http://localhost:8080/api';

  constructor(
    private http: HttpClient,
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      isbn: ['', [Validators.required]],
      titulo: ['', [Validators.required]],
      anioPublicacion: ['', [Validators.required]],
      stockTotal: ['', [Validators.required]],
      editorialId: ['', [Validators.required]],
      idiomaId: ['', [Validators.required]],
      estadoId: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.cargarLibros();
  }

  cargarLibros(): void {
    this.cargando = true;
    this.http.get<any>(
      `${this.apiUrl}/libros?page=${this.currentPage}&size=${this.pageSize}&sort=id,asc`
    ).subscribe({
      next: (data) => {
        this.libros = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar los libros';
        this.cargando = false;
      }
    });
  }

  paginaAnterior(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.cargarLibros();
    }
  }

  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.cargarLibros();
    }
  }

  abrirFormularioCrear(): void {
    this.modoEdicion = false;
    this.libroSeleccionadoId = null;
    this.form.reset();
    this.mostrarFormulario = true;
  }

  abrirFormularioEditar(libro: any): void {
    this.modoEdicion = true;
    this.libroSeleccionadoId = libro.id;
    this.form.patchValue({
      isbn: libro.isbn,
      titulo: libro.titulo,
      anioPublicacion: libro.anioPublicacion,
      stockTotal: libro.stockTotal,
      editorialId: libro.editorialId,
      idiomaId: libro.idiomaId,
      estadoId: libro.estadoId
    });
    this.mostrarFormulario = true;
  }

  cerrarFormulario(): void {
    this.mostrarFormulario = false;
    this.form.reset();
  }

  guardarLibro(): void {
    if (this.form.invalid) return;
    const datos = this.form.value;

    if (this.modoEdicion && this.libroSeleccionadoId) {
      this.http.put(`${this.apiUrl}/libros/${this.libroSeleccionadoId}`, datos).subscribe({
        next: () => {
          this.cerrarFormulario();
          this.cargarLibros();
        },
        error: () => { this.errorMsg = 'Error al actualizar el libro'; }
      });
    } else {
      this.http.post(`${this.apiUrl}/libros`, datos).subscribe({
        next: () => {
          this.cerrarFormulario();
          this.cargarLibros();
        },
        error: () => { this.errorMsg = 'Error al crear el libro'; }
      });
    }
  }

  eliminarLibro(id: number): void {
    if (!confirm('¿Está seguro de eliminar este libro?')) return;
    this.http.delete(`${this.apiUrl}/libros/${id}`).subscribe({
      next: () => { this.cargarLibros(); },
      error: () => { this.errorMsg = 'Error al eliminar el libro'; }
    });
  }

  cerrarSesion(): void {
    this.authService.logout();
  }
}