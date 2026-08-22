import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';

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
import { Libro, LibroRequest } from '../core/models/libro.model';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';

@Component({
    selector: 'app-libros',
    imports: [ReactiveFormsModule, FormsModule, PortadaLibroComponent],
    templateUrl: './libros.component.html'
})
export class LibrosComponent implements OnInit, OnDestroy {
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
  lookupError: string = '';
  portadaPreviewUrl: string | null = null;
  portadaPreviewBlob: Blob | null = null;
  portadaPreviewTipo: string | null = null;
  portadaModalVisible: boolean = false;
  portadaModalUrl: string | null = null;
  portadaModalCargando: boolean = false;
  categorias: Categoria[] = [];
  autores: Autor[] = [];
  editoriales: Editorial[] = [];
  idiomas: Idioma[] = [];
  estados: EstadoLibro[] = [];
  categoriaFiltro: string = '';
  errorCatalogo: string = '';

  textoAutor: string = '';
  textoCategoria: string = '';

  sugerenciasAutor: Autor[] = [];
  sugerenciasCategoria: Categoria[] = [];
  mostrarSugerenciasAutor: boolean = false;
  mostrarSugerenciasCategoria: boolean = false;
  indiceAutor: number = -1;
  indiceCategoria: number = -1;

  private autorBusqueda$ = new Subject<string>();
  private categoriaBusqueda$ = new Subject<string>();
  private destroy$ = new Subject<void>();

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
      editorialId: [null, [Validators.required]],
      idiomaId: [null, [Validators.required]],
      estadoId: [null, [Validators.required]],
      categoriaIds: [[]],
      autorIds: [[]]
    });
  }

  ngOnInit(): void {
    this.cargarLibros();
    this.cargarCatalogo();

    this.autorBusqueda$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(texto => {
      if (!texto.trim()) { this.sugerenciasAutor = []; return; }
      this.autorService.buscar(texto).subscribe({
        next: (result) => { this.sugerenciasAutor = result; this.indiceAutor = -1; },
        error: () => { this.sugerenciasAutor = []; }
      });
    });

    this.categoriaBusqueda$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(texto => {
      if (!texto.trim()) { this.sugerenciasCategoria = []; return; }
      this.categoriaService.buscar(texto).subscribe({
        next: (result) => { this.sugerenciasCategoria = result; this.indiceCategoria = -1; },
        error: () => { this.sugerenciasCategoria = []; }
      });
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
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
    this.form.reset({ categoriaIds: [], autorIds: [], editorialId: null, idiomaId: null, estadoId: null });
    this.limpiarPortada();
    this.textoAutor = '';
    this.textoCategoria = '';
    this.sugerenciasAutor = [];
    this.sugerenciasCategoria = [];
    this.mostrarFormulario = true;
  }

  abrirFormularioEditar(libro: Libro): void {
    this.modoEdicion = true;
    this.libroSeleccionadoId = libro.id;
    this.limpiarPortada();
    this.textoAutor = '';
    this.textoCategoria = '';
    this.sugerenciasAutor = [];
    this.sugerenciasCategoria = [];
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
      categoriaIds: this.idsDeNombres(this.categorias, libro.categorias),
      autorIds: this.idsDeNombres(this.autores, libro.autores)
    });
    this.mostrarFormulario = true;
  }

  private idsDeNombres(catalogo: { id: number; nombre: string }[], nombres: string[]): number[] {
    if (!nombres?.length) return [];
    return nombres
      .map(nombre => catalogo.find(item => item.nombre === nombre)?.id)
      .filter((id): id is number => id !== undefined);
  }

  cerrarFormulario(): void {
    this.mostrarFormulario = false;
    this.form.reset({ categoriaIds: [], autorIds: [], editorialId: null, idiomaId: null, estadoId: null });
    this.limpiarPortada();
    this.textoAutor = '';
    this.textoCategoria = '';
    this.sugerenciasAutor = [];
    this.sugerenciasCategoria = [];
  }

  // ── Autocomplete: Autores ──

  onBusquedaAutor(texto: string): void {
    this.textoAutor = texto;
    this.autorBusqueda$.next(texto);
    this.mostrarSugerenciasAutor = texto.trim().length >= 1;
  }

  seleccionarAutor(autor: Autor): void {
    const ids = this.form.get('autorIds')?.value as number[];
    if (!ids.includes(autor.id)) {
      this.form.patchValue({ autorIds: [...ids, autor.id] });
      if (!this.autores.find(a => a.id === autor.id)) {
        this.autores = [...this.autores, autor];
      }
    }
    this.textoAutor = '';
    this.sugerenciasAutor = [];
    this.mostrarSugerenciasAutor = false;
  }

  agregarAutor(event: Event): void {
    event.preventDefault();
    const texto = this.textoAutor.trim();
    if (!texto) return;

    const existente = this.autores.find(a => a.nombre.toLowerCase() === texto.toLowerCase());
    if (existente) {
      this.seleccionarAutor(existente);
      return;
    }

    this.autorService.crear(texto).subscribe({
      next: (nuevo) => {
        this.autores = [...this.autores, nuevo];
        this.textoAutor = '';
        this.sugerenciasAutor = [];
        this.mostrarSugerenciasAutor = false;
        const ids = this.form.get('autorIds')?.value as number[];
        if (!ids.includes(nuevo.id)) {
          this.form.patchValue({ autorIds: [...ids, nuevo.id] });
        }
      },
      error: () => { this.errorMsg = 'No se pudo crear el autor'; }
    });
  }

  quitarAutor(id: number): void {
    const ids = (this.form.get('autorIds')?.value as number[]).filter(i => i !== id);
    this.form.patchValue({ autorIds: ids });
  }

  nombreAutor(id: number): string {
    return this.autores.find(a => a.id === id)?.nombre ?? `#${id}`;
  }

  // ── Autocomplete: Categorías ──

  onBusquedaCategoria(texto: string): void {
    this.textoCategoria = texto;
    this.categoriaBusqueda$.next(texto);
    this.mostrarSugerenciasCategoria = texto.trim().length >= 1;
  }

  seleccionarCategoria(cat: Categoria): void {
    const ids = this.form.get('categoriaIds')?.value as number[];
    if (!ids.includes(cat.id)) {
      this.form.patchValue({ categoriaIds: [...ids, cat.id] });
      if (!this.categorias.find(c => c.id === cat.id)) {
        this.categorias = [...this.categorias, cat];
      }
    }
    this.textoCategoria = '';
    this.sugerenciasCategoria = [];
    this.mostrarSugerenciasCategoria = false;
  }

  agregarCategoria(event: Event): void {
    event.preventDefault();
    const texto = this.textoCategoria.trim();
    if (!texto) return;

    const existente = this.categorias.find(c => c.nombre.toLowerCase() === texto.toLowerCase());
    if (existente) {
      this.seleccionarCategoria(existente);
      return;
    }

    this.categoriaService.crear(texto).subscribe({
      next: (nueva) => {
        this.categorias = [...this.categorias, nueva];
        this.textoCategoria = '';
        this.sugerenciasCategoria = [];
        this.mostrarSugerenciasCategoria = false;
        const ids = this.form.get('categoriaIds')?.value as number[];
        if (!ids.includes(nueva.id)) {
          this.form.patchValue({ categoriaIds: [...ids, nueva.id] });
        }
      },
      error: () => { this.errorMsg = 'No se pudo crear la categoría'; }
    });
  }

  quitarCategoria(id: number): void {
    const ids = (this.form.get('categoriaIds')?.value as number[]).filter(i => i !== id);
    this.form.patchValue({ categoriaIds: ids });
  }

  nombreCategoria(id: number): string {
    return this.categorias.find(c => c.id === id)?.nombre ?? `#${id}`;
  }

  // ── Navegación con teclado (autocomplete) ──

  onKeydownAutor(event: KeyboardEvent): void {
    const sugerencias = this.sugerenciasAutor;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.indiceAutor = Math.min(this.indiceAutor + 1, sugerencias.length - 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.indiceAutor = Math.max(this.indiceAutor - 1, -1);
        break;
      case 'Enter':
        event.preventDefault();
        if (this.indiceAutor >= 0 && this.indiceAutor < sugerencias.length) {
          this.seleccionarAutor(sugerencias[this.indiceAutor]);
        } else {
          this.agregarAutor(event);
        }
        break;
      case 'Escape':
        this.mostrarSugerenciasAutor = false;
        break;
    }
  }

  onKeydownCategoria(event: KeyboardEvent): void {
    const sugerencias = this.sugerenciasCategoria;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.indiceCategoria = Math.min(this.indiceCategoria + 1, sugerencias.length - 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.indiceCategoria = Math.max(this.indiceCategoria - 1, -1);
        break;
      case 'Enter':
        event.preventDefault();
        if (this.indiceCategoria >= 0 && this.indiceCategoria < sugerencias.length) {
          this.seleccionarCategoria(sugerencias[this.indiceCategoria]);
        } else {
          this.agregarCategoria(event);
        }
        break;
      case 'Escape':
        this.mostrarSugerenciasCategoria = false;
        break;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('[data-autocomplete-autores]')) {
      this.mostrarSugerenciasAutor = false;
    }
    if (!target.closest('[data-autocomplete-categorias]')) {
      this.mostrarSugerenciasCategoria = false;
    }
  }

  // ── Portada: cleanup ──

  private limpiarPortada(): void {
    if (this.portadaPreviewUrl) {
      URL.revokeObjectURL(this.portadaPreviewUrl);
    }
    this.portadaPreviewUrl = null;
    this.portadaPreviewBlob = null;
    this.portadaPreviewTipo = null;
    this.lookupError = '';
  }

  // ── Portada: preview en tiempo real ──

  private static readonly TIPOS_PORTADA_PERMITIDOS = ['image/png', 'image/jpeg', 'image/webp', 'image/avif'];

  private esTipoPortadaPermitido(tipo: string | null | undefined): boolean {
    return !!tipo && LibrosComponent.TIPOS_PORTADA_PERMITIDOS.includes(tipo);
  }

  private async sniffMimePortada(archivo: Blob): Promise<string | null> {
    if (this.esTipoPortadaPermitido(archivo.type)) {
      return archivo.type;
    }
    try {
      const buf = new Uint8Array(await archivo.slice(0, 16).arrayBuffer());
      const ascii = (inicio: number, fin: number): string =>
        String.fromCharCode(...buf.subarray(inicio, fin));
      if (buf.length >= 8 && ascii(0, 8) === '\x89PNG\r\n\x1a\n') return 'image/png';
      if (buf.length >= 3 && buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'image/jpeg';
      if (buf.length >= 12 && ascii(0, 4) === 'RIFF' && ascii(8, 12) === 'WEBP') return 'image/webp';
      if (buf.length >= 12 && ascii(4, 8) === 'ftyp' && (ascii(8, 12) === 'avif' || ascii(8, 12) === 'avis')) return 'image/avif';
    } catch {
    }
    return null;
  }

  async onArchivoPortadaSeleccionado(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) return;

    const tipo = await this.sniffMimePortada(archivo);
    if (!tipo) {
      this.lookupError = 'Formato no permitido. Usá JPG, JPEG, PNG, WebP o AVIF.';
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
    this.portadaPreviewTipo = tipo;
    this.portadaPreviewUrl = URL.createObjectURL(archivo);
    this.lookupError = '';
  }

  // ── Guardar ──

  guardarLibro(): void {
    if (this.form.invalid) return;
    const datos = this.form.value as LibroRequest;

    const accion = this.modoEdicion && this.libroSeleccionadoId
      ? this.libroService.actualizar(this.libroSeleccionadoId, datos)
      : this.libroService.crear(datos);

    accion.subscribe({
      next: (libro) => {
        this.guardarPortadaPendiente(libro.id);
        this.cerrarFormulario();
      },
      error: () => {
        this.errorMsg = this.modoEdicion
          ? 'Error al actualizar el libro'
          : 'Error al crear el libro';
      }
    });
  }

  private guardarPortadaPendiente(libroId: number): void {
    const blob = this.portadaPreviewBlob;
    if (!blob) {
      this.cargarLibros();
      return;
    }
    const tipo = this.portadaPreviewTipo ?? 'image/jpeg';
    const ext = tipo.split('/')[1] ?? 'jpg';
    const archivo = new File([blob], `portada.${ext}`, { type: tipo });
    this.libroService.subirPortada(libroId, archivo).subscribe({
      next: () => this.cargarLibros(),
      error: () => {
        this.errorMsg = 'El libro se guardó, pero hubo un error al subir su portada';
        this.cargarLibros();
      }
    });
  }

  eliminarLibro(id: number): void {
    if (!confirm('¿Está seguro de eliminar este libro?')) return;
    this.libroService.eliminar(id).subscribe({
      next: () => { this.cargarLibros(); },
      error: () => { this.errorMsg = 'Error al eliminar el libro'; }
    });
  }

  abrirPortada(libroId: number, tienePortada: boolean): void {
    if (!tienePortada) return;
    this.portadaModalVisible = true;
    this.portadaModalCargando = true;
    this.portadaModalUrl = null;
    this.libroService.obtenerPortada(libroId).subscribe({
      next: (blob) => {
        this.portadaModalUrl = URL.createObjectURL(blob);
        this.portadaModalCargando = false;
      },
      error: () => {
        this.portadaModalCargando = false;
      }
    });
  }

  cerrarPortada(): void {
    if (this.portadaModalUrl) {
      URL.revokeObjectURL(this.portadaModalUrl);
    }
    this.portadaModalVisible = false;
    this.portadaModalUrl = null;
    this.portadaModalCargando = false;
  }

}
