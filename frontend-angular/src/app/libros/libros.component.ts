import { Component, OnInit } from '@angular/core';

import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { LibroService } from '../core/services/libro.service';
import { Libro, LibroRequest, LibroIsbnLookup } from '../core/models/libro.model';

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
  buscandoIsbn: boolean = false;
  lookupMensaje: string = '';
  lookupError: string = '';
  portadaPreviewUrl: string | null = null;
  portadaPreviewBlob: Blob | null = null;
  autocompletarAutor: string = '';

  // Libros es la pantalla a la que se llega tras el login (ver
  // LoginComponent#submit): no hay un layout/nav compartido en el
  // proyecto todavía, así que los accesos a las pantallas ADMIN se
  // exponen acá, gateados por rol igual que el resto de la UI.
  verAdminUsuarios: boolean = false;
  verAdminConfiguracion: boolean = false;

  constructor(
    private libroService: LibroService,
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      isbn: ['', [Validators.required]],
      titulo: ['', [Validators.required]],
      resumen: [''],
      anioPublicacion: ['', [Validators.required]],
      stockTotal: ['', [Validators.required]],
      stockDisponible: ['', [Validators.required]],
      editorialId: ['', [Validators.required]],
      idiomaId: ['', [Validators.required]],
      estadoId: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.verAdminUsuarios = this.authService.hasRole('ADMIN', 'GERENTE');
    this.verAdminConfiguracion = this.authService.hasRole('ADMIN');
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
    this.limpiarAutocompletar();
    this.mostrarFormulario = true;
  }

  abrirFormularioEditar(libro: Libro): void {
    this.modoEdicion = true;
    this.libroSeleccionadoId = libro.id;
    this.limpiarAutocompletar();
    this.form.patchValue({
      isbn: libro.isbn,
      titulo: libro.titulo,
      resumen: libro.resumen,
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
    this.limpiarAutocompletar();
  }

  // ── Autocompletar por ISBN (mockup 14, LibroIsbnLookupService) ──

  // El botón se habilita solo cuando el ISBN tiene 10-13 dígitos (se
  // ignoran guiones/espacios); coincide con la validación del backend.
  esIsbnAutocompletable(): boolean {
    const digitos = this.form.get('isbn')?.value?.replace(/\D/g, '') ?? '';
    return digitos.length >= 10 && digitos.length <= 13;
  }

  autocompletar(): void {
    if (!this.esIsbnAutocompletable()) return;
    const isbn = this.form.get('isbn')!.value as string;
    this.buscandoIsbn = true;
    this.lookupMensaje = '';
    this.lookupError = '';

    this.libroService.buscarPorIsbn(isbn).subscribe({
      next: (info) => this.aplicarAutocompletar(info, isbn),
      error: (err) => {
        this.buscandoIsbn = false;
        if ((err as { status?: number })?.status === 404) {
          this.lookupError = 'No se encontró información para ese ISBN, completá los campos manualmente';
        } else {
          this.lookupError = 'Error al consultar Google Books, intentá de nuevo';
        }
      }
    });
  }

  private aplicarAutocompletar(info: LibroIsbnLookup, isbn: string): void {
    this.form.patchValue({
      titulo: info.titulo ?? '',
      resumen: info.resumen ?? '',
      anioPublicacion: info.anioPublicacion ?? ''
    });
    this.autocompletarAutor = info.autor ?? '';
    this.buscandoIsbn = false;
    this.lookupMensaje = 'Encontrado en Google Books — campos rellenados abajo, revisalos antes de guardar';

    if (info.portadaDisponible) {
      this.libroService.portadaPorIsbn(isbn).subscribe({
        next: (blob) => {
          this.portadaPreviewBlob = blob;
          this.portadaPreviewUrl = URL.createObjectURL(blob);
        },
        error: () => {
          // El thumbnail puede faltar aunque el flag diga que existe:
          // se muestra el preview solo si la descarga funcionó.
          this.portadaPreviewBlob = null;
          this.portadaPreviewUrl = null;
        }
      });
    }
  }

  private limpiarAutocompletar(): void {
    if (this.portadaPreviewUrl) {
      URL.revokeObjectURL(this.portadaPreviewUrl);
    }
    this.portadaPreviewUrl = null;
    this.portadaPreviewBlob = null;
    this.autocompletarAutor = '';
    this.lookupMensaje = '';
    this.lookupError = '';
    this.buscandoIsbn = false;
  }

  guardarLibro(): void {
    if (this.form.invalid) return;
    // Los valores del form siguen viajando tal cual (el backend convierte
    // los strings a Integer); solo cambia el canal de transporte.
    const datos = this.form.value as LibroRequest;

    if (this.modoEdicion && this.libroSeleccionadoId) {
      this.libroService.actualizar(this.libroSeleccionadoId, datos).subscribe({
        next: (libro) => {
          this.guardarPortadaAutocompletada(libro.id);
          this.cerrarFormulario();
          this.cargarLibros();
        },
        error: () => { this.errorMsg = 'Error al actualizar el libro'; }
      });
    } else {
      this.libroService.crear(datos).subscribe({
        next: (libro) => {
          this.guardarPortadaAutocompletada(libro.id);
          this.cerrarFormulario();
          this.cargarLibros();
        },
        error: () => { this.errorMsg = 'Error al crear el libro'; }
      });
    }
  }

  // La portada bajada de Google Books se sube como archivo al libro recién
  // guardado (Blob -> File; el backend la guarda como PortadaImagen).
  private guardarPortadaAutocompletada(libroId: number): void {
    if (!this.portadaPreviewBlob) return;
    const archivo = new File([this.portadaPreviewBlob], 'portada-google-books.jpg');
    this.libroService.subirPortada(libroId, archivo).subscribe({
      error: () => { this.errorMsg = 'El libro se guardó, pero hubo un error al subir su portada'; }
    });
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

  irAUsuarios(): void {
    this.router.navigate(['/admin/usuarios']);
  }

  irAConfiguracion(): void {
    this.router.navigate(['/admin/configuracion']);
  }
}
