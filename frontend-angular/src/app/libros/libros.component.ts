import { Component, OnInit } from '@angular/core';

import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { LibroService } from '../core/services/libro.service';
import { CategoriaService } from '../core/services/categoria.service';
import { AutorService } from '../core/services/autor.service';
import { EditorialService } from '../core/services/editorial.service';
import { IdiomaService } from '../core/services/idioma.service';
import { EstadoLibroService } from '../core/services/estado-libro.service';
import { PortadaLibroComponent } from '../shared/portada-libro/portada-libro.component';
import { Categoria } from '../core/models/categoria.model';
import { Autor } from '../core/models/autor.model';
import { Editorial } from '../core/models/editorial.model';
import { Idioma } from '../core/models/idioma.model';
import { EstadoLibro } from '../core/models/estado-libro.model';
import { Libro, LibroRequest, LibroIsbnLookup } from '../core/models/libro.model';

@Component({
    selector: 'app-libros',
    imports: [ReactiveFormsModule, FormsModule, PortadaLibroComponent],
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
  categorias: Categoria[] = [];
  autores: Autor[] = [];
  // FIX 3: catálogos editorial/idioma/estado para los <select> del
  // formulario (GET /api/v1/editoriales, /api/v1/idiomas,
  // /api/v1/estados-libro — antes eran inputs de ID a mano).
  editoriales: Editorial[] = [];
  idiomas: Idioma[] = [];
  estados: EstadoLibro[] = [];
  categoriaFiltro: string = '';
  errorCatalogo: string = '';

  get paginasVisibles(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  constructor(
    private libroService: LibroService,
    private categoriaService: CategoriaService,
    private autorService: AutorService,
    private editorialService: EditorialService,
    private idiomaService: IdiomaService,
    private estadoLibroService: EstadoLibroService,
    private fb: FormBuilder
  ) {
    this.form = this.fb.group({
      isbn: ['', [Validators.required]],
      titulo: ['', [Validators.required]],
      resumen: [''],
      ubicacionFisica: [''],
      anioPublicacion: ['', [Validators.required]],
      stockTotal: ['', [Validators.required]],
      stockDisponible: ['', [Validators.required]],
      editorialId: ['', [Validators.required]],
      idiomaId: ['', [Validators.required]],
      estadoId: ['', [Validators.required]],
      // <select multiple> nativo: el control lleva el array de ids de las
      // opciones seleccionadas (null o vacío es válido -- el backend acepta
      // un libro sin categoría/autor, ver LibroRequestDTO.categoriaIds).
      categoriaIds: [[]],
      autorIds: [[]]
    });
  }

  ngOnInit(): void {
    this.cargarLibros();
    this.cargarCatalogo();
  }

  private cargarCatalogo(): void {
    this.categoriaService.listar().subscribe({
      next: (categorias) => { this.categorias = categorias; },
      error: () => { this.errorCatalogo = 'No se pudieron cargar las categorías'; }
    });
    this.autorService.listar().subscribe({
      next: (autores) => { this.autores = autores; },
      error: () => { this.errorCatalogo = 'No se pudieron cargar los autores'; }
    });
    this.editorialService.listar().subscribe({
      next: (editoriales) => { this.editoriales = editoriales; },
      error: () => { this.errorCatalogo = 'No se pudieron cargar las editoriales'; }
    });
    this.idiomaService.listar().subscribe({
      next: (idiomas) => { this.idiomas = idiomas; },
      error: () => { this.errorCatalogo = 'No se pudieron cargar los idiomas'; }
    });
    this.estadoLibroService.listar().subscribe({
      next: (estados) => { this.estados = estados; },
      error: () => { this.errorCatalogo = 'No se pudieron cargar los estados del libro'; }
    });
  }

  cargarLibros(): void {
    this.cargando = true;
    this.libroService.listar({
      page: this.currentPage,
      size: this.pageSize,
      sort: 'id,asc',
      ...(this.categoriaFiltro ? { categoriaId: Number(this.categoriaFiltro) } : {})
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

  filtrarPorCategoria(): void {
    this.currentPage = 0;
    this.cargarLibros();
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
    this.form.reset({ categoriaIds: [], autorIds: [] });
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
      ubicacionFisica: libro.ubicacionFisica,
      anioPublicacion: libro.anioPublicacion,
      stockTotal: libro.stockTotal,
      stockDisponible: libro.stockDisponible,
      editorialId: libro.editorialId,
      idiomaId: libro.idiomaId,
      estadoId: libro.estadoId,
      // El DTO trae solo NOMBRES (LibroResponseDTO.categorias/autores);
      // se resuelven a los ids del catálogo cargado para preseleccionar
      // el <select multiple>.
      categoriaIds: this.idsDeNombres(this.categorias, libro.categorias),
      autorIds: this.idsDeNombres(this.autores, libro.autores)
    });
    this.mostrarFormulario = true;
  }

  // match por nombre (único en el catálogo sembrado): el libro que no
  // matchee ninguna categoría/autor cargado se deja sin selección en vez
  // de inventar un id.
  private idsDeNombres(catalogo: { id: number; nombre: string }[], nombres: string[]): number[] {
    if (!nombres?.length) return [];
    return nombres
      .map(nombre => catalogo.find(item => item.nombre === nombre)?.id)
      .filter((id): id is number => id !== undefined);
  }

  cerrarFormulario(): void {
    this.mostrarFormulario = false;
    this.form.reset({ categoriaIds: [], autorIds: [] });
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

  // Subida manual de portada (no solo por ISBN): misma whitelist de tipos
  // y límite que valida el backend (V13: max_tamano_portada_mb = 2 en
  // configuracion_sistema, tope duro de servlet 5MB). El preview reusa
  // portadaPreviewUrl/portadaPreviewBlob -- el mismo canal que usa el
  // autocompletar, para que guardarPortadaPendiente() no distinga origen.
  onArchivoPortadaSeleccionado(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) return;

    const tiposPermitidos = ['image/png', 'image/jpeg', 'image/webp'];
    if (!tiposPermitidos.includes(archivo.type)) {
      this.lookupError = 'Formato no permitido. Usá PNG, JPEG o WEBP.';
      return;
    }
    if (archivo.size > 2 * 1024 * 1024) {
      this.lookupError = 'La imagen supera los 2MB permitidos.';
      return;
    }

    if (this.portadaPreviewUrl) {
      URL.revokeObjectURL(this.portadaPreviewUrl);
    }
    this.portadaPreviewBlob = archivo;
    this.portadaPreviewUrl = URL.createObjectURL(archivo);
    this.lookupError = '';
  }

  guardarLibro(): void {
    if (this.form.invalid) return;
    // Los valores del form siguen viajando tal cual (el backend convierte
    // los strings a Integer); solo cambia el canal de transporte.
    const datos = this.form.value as LibroRequest;

    if (this.modoEdicion && this.libroSeleccionadoId) {
      this.libroService.actualizar(this.libroSeleccionadoId, datos).subscribe({
        next: (libro) => {
          this.guardarPortadaPendiente(libro.id);
          this.cerrarFormulario();
          this.cargarLibros();
        },
        error: () => { this.errorMsg = 'Error al actualizar el libro'; }
      });
    } else {
      this.libroService.crear(datos).subscribe({
        next: (libro) => {
          this.guardarPortadaPendiente(libro.id);
          this.cerrarFormulario();
          this.cargarLibros();
        },
        error: () => { this.errorMsg = 'Error al crear el libro'; }
      });
    }
  }

  // La portada pendiente (Google Books o selección manual, ambas dejan el
  // Blob en portadaPreviewBlob) se sube como archivo al libro recién
  // guardado; el backend la persiste como PortadaImagen (V13).
  private guardarPortadaPendiente(libroId: number): void {
    if (!this.portadaPreviewBlob) return;
    const tipo = this.portadaPreviewBlob.type.split('/')[1] ?? 'jpg';
    const archivo = new File([this.portadaPreviewBlob], `portada.${tipo}`);
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

}
