import { Component, OnInit } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { LibroService } from '../core/services/libro.service';
import { Libro, LibroRequest } from '../core/models/libro.model';

@Component({
    selector: 'app-libros',
    imports: [ReactiveFormsModule],
    templateUrl: './libros.component.html'
})
export class LibrosComponent implements OnInit {
  libros: Libro[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';
  mostrarFormulario: boolean = false;
  modoEdicion: boolean = false;
  libroSeleccionadoId: number | null = null;
  form: FormGroup;

  constructor(
    private libroService: LibroService,
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      isbn: ['', [Validators.required]],
      titulo: ['', [Validators.required]],
      anioPublicacion: ['', [Validators.required]],
      stockTotal: ['', [Validators.required]],
      stockDisponible: ['', [Validators.required]],
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
    this.libroService.listar({
      page: this.currentPage,
      size: this.pageSize,
      sort: 'id,asc'
    }).subscribe({
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

  abrirFormularioEditar(libro: Libro): void {
    this.modoEdicion = true;
    this.libroSeleccionadoId = libro.id;
    this.form.patchValue({
      isbn: libro.isbn,
      titulo: libro.titulo,
      anioPublicacion: libro.anioPublicacion,
      stockTotal: libro.stockTotal,
      stockDisponible: libro.stockDisponible,
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
    // Los valores del form siguen viajando tal cual (el backend convierte
    // los strings a Integer); solo cambia el canal de transporte.
    const datos = this.form.value as LibroRequest;

    if (this.modoEdicion && this.libroSeleccionadoId) {
      this.libroService.actualizar(this.libroSeleccionadoId, datos).subscribe({
        next: () => {
          this.cerrarFormulario();
          this.cargarLibros();
        },
        error: () => { this.errorMsg = 'Error al actualizar el libro'; }
      });
    } else {
      this.libroService.crear(datos).subscribe({
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
    this.libroService.eliminar(id).subscribe({
      next: () => { this.cargarLibros(); },
      error: () => { this.errorMsg = 'Error al eliminar el libro'; }
    });
  }

  cerrarSesion(): void {
    this.authService.logout();
  }
}