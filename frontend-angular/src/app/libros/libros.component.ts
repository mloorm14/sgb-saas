import { Component, OnInit, OnDestroy, HostListener, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LibroService } from '../core/services/libro.service';
import { CategoriaService } from '../core/services/categoria.service';
import { AutorService } from '../core/services/autor.service';
import { EditorialService } from '../core/services/editorial.service';
import { IdiomaService } from '../core/services/idioma.service';
import { EstadoLibroService } from '../core/services/estado-libro.service';
import { AuthService } from '../core/services/auth.service';
import { PortadaLibroComponent } from '../shared/portada-libro/portada-libro.component';
import { FocusTrapDirective } from '../shared/focus-trap.directive';
import { Categoria } from '../core/models/categoria.model';
import { Autor } from '../core/models/autor.model';
import { Editorial } from '../core/models/editorial.model';
import { Idioma } from '../core/models/idioma.model';
import { EstadoLibro } from '../core/models/estado-libro.model';
import { Proveedor } from '../core/models/proveedor.model';
import { ProveedorService } from '../core/services/proveedor.service';
import { Libro, LibroRequest } from '../core/models/libro.model';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';

@Component({
  standalone: true,
    selector: 'app-libros',
    imports: [CommonModule, ReactiveFormsModule, FormsModule, PortadaLibroComponent, FocusTrapDirective],
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
  modoRevisionPendiente: boolean = false;
  libroSeleccionadoId: number | null = null;
  form: FormGroup;
  lookupError: string = '';
  lookupCargando = false;
  private lookupErrorTimer: ReturnType<typeof setTimeout> | null = null;
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
  textoEditorial: string = '';
  textoIdioma: string = '';
  textoProveedor: string = '';

  textoBusqueda: string = '';
  estadoLibroFiltro: number | null = null;

  sugerenciasAutor: Autor[] = [];
  sugerenciasCategoria: Categoria[] = [];
  sugerenciasEditorial: Editorial[] = [];
  sugerenciasIdioma: Idioma[] = [];
  sugerenciasProveedor: Proveedor[] = [];
  mostrarSugerenciasAutor: boolean = false;
  mostrarSugerenciasCategoria: boolean = false;
  mostrarSugerenciasEditorial: boolean = false;
  mostrarSugerenciasIdioma: boolean = false;
  mostrarSugerenciasProveedor: boolean = false;
  indiceAutor: number = -1;
  indiceCategoria: number = -1;
  indiceEditorial: number = -1;
  indiceIdioma: number = -1;
  indiceProveedor: number = -1;

  editorialSeleccionadaNombre: string = '';
  idiomaSeleccionadoNombre: string = '';
  proveedorSeleccionadoNombre: string = '';

  anioMax: number = new Date().getFullYear() + 1;
  esGerenteAdmin: boolean = false;

  confirmacionVisible = false;
  confirmacionMensaje = '';
  confirmacionPendiente: (() => void) | null = null;

  @ViewChild('editorialInput') editorialInputRef!: ElementRef<HTMLInputElement>;

  private busqueda$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  get paginasVisibles(): number[] {
    const windowSize = 4;
    let start = Math.max(0, this.currentPage - 1);
    let end = Math.min(this.totalPages, start + windowSize);
    if (end - start < windowSize) {
      start = Math.max(0, end - windowSize);
    }
    return Array.from({ length: end - start }, (_, i) => start + i);
  }

  get puedeAnterior(): boolean {
    return this.currentPage > 0;
  }

  get puedeSiguiente(): boolean {
    return this.currentPage < this.totalPages - 1;
  }

  constructor(
    private libroService: LibroService,
    private categoriaService: CategoriaService,
    private autorService: AutorService,
    private editorialService: EditorialService,
    private idiomaService: IdiomaService,
    private estadoLibroService: EstadoLibroService,
    private proveedorService: ProveedorService,
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder
  ) {
    this.esGerenteAdmin = this.authService.hasRole('GERENTE', 'ADMIN');
    this.form = this.fb.group({
      isbn: ['', [Validators.required, Validators.pattern('^[0-9]{10,13}$'), Validators.minLength(10), Validators.maxLength(13)]],
      titulo: ['', [Validators.required, Validators.maxLength(255)]],
      resumen: ['', [Validators.maxLength(2000)]],
      ubicacionFisica: ['', [Validators.maxLength(50)]],
      anioPublicacion: ['', [Validators.required, Validators.min(1950), Validators.max(this.anioMax)]],
      numeroPaginas: ['', [Validators.min(1), Validators.max(4100)]],
      precioBase: [{ value: '', disabled: !this.esGerenteAdmin }, [Validators.min(0), Validators.max(250), Validators.pattern('^\\d+(\\.\\d{1,2})?$')]],
      stockTotal: ['', [Validators.required, Validators.min(0)]],
      stockDisponible: ['', [Validators.required, Validators.min(0)]],
      editorialId: [null, [Validators.required]],
      idiomaId: [null, [Validators.required]],
      estadoId: [null, [Validators.required]],
      categoriaIds: [[]],
      autorIds: [[]],
      proveedorId: [null]
    });
  }

  ngOnInit(): void {
    this.cargarLibros();
    this.cargarCatalogo();

    this.busqueda$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => { this.currentPage = 0; this.cargarLibros(); });

    // H3: debounce ISBN — auto-lookup al completar 13 dígitos
    this.form.get('isbn')!.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(valor => {
      const isbn = (valor ?? '').trim();
      if (/^[0-9]{13}$/.test(isbn)) {
        this.ejecutarLookupIsbn(isbn);
      }
    });

    // Soporte para modo revisión pendiente vía query param ?revision=ID
    this.route.queryParams.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const rev = params['revision'];
      if (rev) {
        const id = Number(rev);
        if (!isNaN(id)) {
          this.libroService.obtener(id).subscribe({
            next: (libro) => {
              this.abrirFormularioEditar(libro, true);
            },
            error: () => { this.errorMsg = 'No se pudo cargar el libro pendiente'; }
          });
        }
      }
    });
  }

  ngOnDestroy(): void {
    if (this.lookupErrorTimer) clearTimeout(this.lookupErrorTimer);
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
      q: this.textoBusqueda.trim() || undefined,
      estadoLibroId: this.estadoLibroFiltro ?? undefined,
      categoriaId: this.categoriaFiltro ? Number(this.categoriaFiltro) : undefined
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

  onBusquedaTexto(texto: string): void {
    this.textoBusqueda = texto;
    this.busqueda$.next(texto);
  }

  filtrarPorEstado(): void {
    this.currentPage = 0;
    this.cargarLibros();
  }

  paginaAnterior(): void {
    if (this.puedeAnterior) {
      this.currentPage--;
      this.cargarLibros();
    }
  }

  paginaSiguiente(): void {
    if (this.puedeSiguiente) {
      this.currentPage++;
      this.cargarLibros();
    }
  }

  cambiarTamanoPage(nuevo: number): void {
    this.pageSize = Number(nuevo);
    this.currentPage = 0;
    this.cargarLibros();
  }

  abrirFormularioCrear(): void {
    this.modoEdicion = false;
    this.modoRevisionPendiente = false;
    this.libroSeleccionadoId = null;
    this.form.reset({ categoriaIds: [], autorIds: [], editorialId: null, idiomaId: null, estadoId: null, numeroPaginas: '', precioBase: '', proveedorId: null });
    if (this.esGerenteAdmin) this.form.get('precioBase')?.enable(); else this.form.get('precioBase')?.disable();
    this.limpiarPortada();
    this.textoAutor = '';
    this.textoCategoria = '';
    this.textoEditorial = '';
    this.textoIdioma = '';
    this.textoProveedor = '';
    this.editorialSeleccionadaNombre = '';
    this.idiomaSeleccionadoNombre = '';
    this.proveedorSeleccionadoNombre = '';
    this.sugerenciasAutor = [];
    this.sugerenciasCategoria = [];
    this.sugerenciasEditorial = [];
    this.sugerenciasIdioma = [];
    this.sugerenciasProveedor = [];
    this.mostrarFormulario = true;
  }

  abrirFormularioEditar(libro: Libro, esRevisionPendiente: boolean = false): void {
    this.modoEdicion = true;
    this.modoRevisionPendiente = esRevisionPendiente;
    this.libroSeleccionadoId = libro.id;
    this.limpiarPortada();
    this.textoAutor = '';
    this.textoCategoria = '';
    this.textoEditorial = '';
    this.textoIdioma = '';
    this.textoProveedor = '';
    this.sugerenciasAutor = [];
    this.sugerenciasCategoria = [];
    this.sugerenciasEditorial = [];
    this.sugerenciasIdioma = [];
    this.sugerenciasProveedor = [];
    this.editorialSeleccionadaNombre = libro.editorial ?? '';
    this.idiomaSeleccionadoNombre = libro.idioma ?? '';
    this.proveedorSeleccionadoNombre = (libro as any).proveedor ?? '';
    this.form.patchValue({
      isbn: libro.isbn,
      titulo: libro.titulo,
      resumen: libro.resumen,
      ubicacionFisica: libro.ubicacionFisica,
      anioPublicacion: libro.anioPublicacion,
      numeroPaginas: libro.numeroPaginas ?? '',
      precioBase: libro.precioBase ?? '',
      stockTotal: libro.stockTotal,
      stockDisponible: libro.stockDisponible,
      editorialId: libro.editorialId,
      idiomaId: libro.idiomaId,
      estadoId: libro.estadoId,
      categoriaIds: this.idsDeNombres(this.categorias, libro.categorias),
      autorIds: this.idsDeNombres(this.autores, libro.autores),
      proveedorId: (libro as any).proveedorId ?? null
    });
    // Precio solo editable por GERENTE/ADMIN, y nunca en modo revisión pendiente si es bibliotecario
    if (this.modoRevisionPendiente && !this.esGerenteAdmin) {
      this.form.get('precioBase')?.disable();
    } else if (this.esGerenteAdmin) {
      this.form.get('precioBase')?.enable();
    } else {
      this.form.get('precioBase')?.disable();
    }
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
    this.modoRevisionPendiente = false;
    this.form.reset({ categoriaIds: [], autorIds: [], editorialId: null, idiomaId: null, estadoId: null, numeroPaginas: '', precioBase: '', proveedorId: null });
    if (this.esGerenteAdmin) this.form.get('precioBase')?.enable(); else this.form.get('precioBase')?.disable();
    this.limpiarPortada();
    this.textoAutor = '';
    this.textoCategoria = '';
    this.textoEditorial = '';
    this.textoIdioma = '';
    this.textoProveedor = '';
    this.proveedorSeleccionadoNombre = '';
    this.sugerenciasAutor = [];
    this.sugerenciasCategoria = [];
    this.sugerenciasEditorial = [];
    this.sugerenciasIdioma = [];
    this.sugerenciasProveedor = [];
    // limpiar query param revision si existe
    this.router.navigate([], { queryParams: {}, queryParamsHandling: 'merge' });
  }

  // ── Autocomplete manual: Autores ──

  buscarAutorManualmente(): void {
    const texto = this.textoAutor.trim();
    if (!texto) { this.sugerenciasAutor = []; this.mostrarSugerenciasAutor = false; return; }
    this.autorService.buscar(texto).subscribe({
      next: (result) => { this.sugerenciasAutor = result; this.indiceAutor = -1; this.mostrarSugerenciasAutor = true; },
      error: () => { this.sugerenciasAutor = []; this.mostrarSugerenciasAutor = true; }
    });
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

  agregarAutor(event?: Event): void {
    if (event) event.preventDefault();
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

  // ── Autocomplete manual: Categorías ──

  buscarCategoriaManualmente(): void {
    const texto = this.textoCategoria.trim();
    if (!texto) { this.sugerenciasCategoria = []; this.mostrarSugerenciasCategoria = false; return; }
    this.categoriaService.buscar(texto).subscribe({
      next: (result) => { this.sugerenciasCategoria = result; this.indiceCategoria = -1; this.mostrarSugerenciasCategoria = true; },
      error: () => { this.sugerenciasCategoria = []; this.mostrarSugerenciasCategoria = true; }
    });
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

  agregarCategoria(event?: Event): void {
    if (event) event.preventDefault();
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

  // ── Autocomplete manual: Editorial ──
  buscarEditorialManualmente(): void {
    const texto = this.textoEditorial.trim();
    if (!texto) { this.sugerenciasEditorial = []; this.mostrarSugerenciasEditorial = false; return; }
    this.editorialService.buscar(texto).subscribe({
      next: (res) => { this.sugerenciasEditorial = res; this.indiceEditorial = -1; this.mostrarSugerenciasEditorial = true; },
      error: () => { this.sugerenciasEditorial = []; this.mostrarSugerenciasEditorial = true; }
    });
  }

  seleccionarEditorial(ed: Editorial): void {
    this.form.patchValue({ editorialId: ed.id });
    this.editorialSeleccionadaNombre = ed.nombre;
    this.textoEditorial = '';
    this.sugerenciasEditorial = [];
    this.mostrarSugerenciasEditorial = false;
    if (!this.editoriales.find(e => e.id === ed.id)) this.editoriales = [...this.editoriales, ed];
  }

  agregarEditorial(event?: Event): void {
    if (event) event.preventDefault();
    const texto = this.textoEditorial.trim();
    if (!texto) return;
    const existente = this.editoriales.find(e => e.nombre.toLowerCase() === texto.toLowerCase());
    if (existente) { this.seleccionarEditorial(existente); return; }
    this.editorialService.crear(texto).subscribe({
      next: (nuevo) => {
        this.editoriales = [...this.editoriales, nuevo];
        this.seleccionarEditorial(nuevo);
      },
      error: () => { this.errorMsg = 'No se pudo crear la editorial'; }
    });
  }

  quitarEditorial(): void {
    this.form.patchValue({ editorialId: null });
    this.editorialSeleccionadaNombre = '';
  }

  // ── Autocomplete manual: Idioma ──
  buscarIdiomaManualmente(): void {
    const texto = this.textoIdioma.trim();
    if (!texto) { this.sugerenciasIdioma = []; this.mostrarSugerenciasIdioma = false; return; }
    this.idiomaService.buscar(texto).subscribe({
      next: (res) => { this.sugerenciasIdioma = res; this.indiceIdioma = -1; this.mostrarSugerenciasIdioma = true; },
      error: () => { this.sugerenciasIdioma = []; this.mostrarSugerenciasIdioma = true; }
    });
  }

  seleccionarIdioma(idioma: Idioma): void {
    this.form.patchValue({ idiomaId: idioma.id });
    this.idiomaSeleccionadoNombre = idioma.nombre;
    this.textoIdioma = '';
    this.sugerenciasIdioma = [];
    this.mostrarSugerenciasIdioma = false;
    if (!this.idiomas.find(i => i.id === idioma.id)) this.idiomas = [...this.idiomas, idioma];
  }

  agregarIdioma(event?: Event): void {
    if (event) event.preventDefault();
    const texto = this.textoIdioma.trim();
    if (!texto) return;
    const existente = this.idiomas.find(i => i.nombre.toLowerCase() === texto.toLowerCase());
    if (existente) { this.seleccionarIdioma(existente); return; }
    this.idiomaService.crear(texto).subscribe({
      next: (nuevo) => {
        this.idiomas = [...this.idiomas, nuevo];
        this.seleccionarIdioma(nuevo);
      },
      error: () => { this.errorMsg = 'No se pudo crear el idioma'; }
    });
  }

  quitarIdioma(): void {
    this.form.patchValue({ idiomaId: null });
    this.idiomaSeleccionadoNombre = '';
  }

  // ── Autocomplete manual: Proveedor (opcional, S/P si null) ──
  // Solo GERENTE/ADMIN pueden buscar/vincular proveedor; BIBLIOTECARIO ve campo deshabilitado.
  get puedeGestionarProveedor(): boolean {
    return this.esGerenteAdmin;
  }

  buscarProveedorManualmente(): void {
    if (!this.puedeGestionarProveedor) return;
    const texto = this.textoProveedor.trim();
    if (!texto) { this.sugerenciasProveedor = []; this.mostrarSugerenciasProveedor = false; return; }
    this.proveedorService.buscar(texto).subscribe({
      next: (res) => { this.sugerenciasProveedor = res; this.indiceProveedor = -1; this.mostrarSugerenciasProveedor = true; },
      error: () => { this.sugerenciasProveedor = []; this.mostrarSugerenciasProveedor = true; }
    });
  }

  seleccionarProveedor(proveedor: Proveedor): void {
    if (!this.puedeGestionarProveedor) return;
    this.form.patchValue({ proveedorId: proveedor.id });
    this.proveedorSeleccionadoNombre = proveedor.nombre;
    this.textoProveedor = '';
    this.sugerenciasProveedor = [];
    this.mostrarSugerenciasProveedor = false;
  }

  quitarProveedor(): void {
    if (!this.puedeGestionarProveedor) return;
    this.form.patchValue({ proveedorId: null });
    this.proveedorSeleccionadoNombre = '';
  }

  nombreProveedorSeleccionado(): string {
    if (this.proveedorSeleccionadoNombre) return this.proveedorSeleccionadoNombre;
    return '';
  }

  // ── Validación ISBN solo números ──

  /** Bloquea letras y guiones en ISBN (solo dígitos permitidos). */
  soloNumeros(event: KeyboardEvent): void {
    const permitidas = ['Backspace', 'Delete', 'Tab', 'ArrowLeft', 'ArrowRight', 'Home', 'End'];
    if (permitidas.includes(event.key)) return;
    if (!/^[0-9]$/.test(event.key)) event.preventDefault();
  }

  /** Evita pegar texto con letras/guiones en ISBN. */
  pegarSoloNumeros(event: ClipboardEvent): void {
    const texto = event.clipboardData?.getData('text') ?? '';
    if (!/^[0-9]*$/.test(texto)) event.preventDefault();
  }

  nombreEditorialSeleccionada(): string {
    if (this.editorialSeleccionadaNombre) return this.editorialSeleccionadaNombre;
    const id = this.form.get('editorialId')?.value;
    return this.editoriales.find(e => e.id === id)?.nombre ?? '';
  }

  nombreIdiomaSeleccionado(): string {
    if (this.idiomaSeleccionadoNombre) return this.idiomaSeleccionadoNombre;
    const id = this.form.get('idiomaId')?.value;
    return this.idiomas.find(i => i.id === id)?.nombre ?? '';
  }

  formatarEstado(nombre: string): string {
    if (!nombre) return '';
    return nombre.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
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

  onKeydownEditorial(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.indiceEditorial = Math.min(this.indiceEditorial + 1, this.sugerenciasEditorial.length - 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.indiceEditorial = Math.max(this.indiceEditorial - 1, -1);
        break;
      case 'Enter':
        event.preventDefault();
        if (this.indiceEditorial >= 0 && this.indiceEditorial < this.sugerenciasEditorial.length) {
          this.seleccionarEditorial(this.sugerenciasEditorial[this.indiceEditorial]);
        } else {
          this.agregarEditorial(event);
        }
        break;
      case 'Escape':
        this.mostrarSugerenciasEditorial = false;
        break;
    }
  }

  onKeydownIdioma(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.indiceIdioma = Math.min(this.indiceIdioma + 1, this.sugerenciasIdioma.length - 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.indiceIdioma = Math.max(this.indiceIdioma - 1, -1);
        break;
      case 'Enter':
        event.preventDefault();
        if (this.indiceIdioma >= 0 && this.indiceIdioma < this.sugerenciasIdioma.length) {
          this.seleccionarIdioma(this.sugerenciasIdioma[this.indiceIdioma]);
        } else {
          this.agregarIdioma(event);
        }
        break;
      case 'Escape':
        this.mostrarSugerenciasIdioma = false;
        break;
    }
  }

  onKeydownProveedor(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.indiceProveedor = Math.min(this.indiceProveedor + 1, this.sugerenciasProveedor.length - 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.indiceProveedor = Math.max(this.indiceProveedor - 1, -1);
        break;
      case 'Enter':
        event.preventDefault();
        if (this.indiceProveedor >= 0 && this.indiceProveedor < this.sugerenciasProveedor.length) {
          this.seleccionarProveedor(this.sugerenciasProveedor[this.indiceProveedor]);
        }
        break;
      case 'Escape':
        this.mostrarSugerenciasProveedor = false;
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
    if (!target.closest('[data-autocomplete-editorial]')) {
      this.mostrarSugerenciasEditorial = false;
    }
    if (!target.closest('[data-autocomplete-idioma]')) {
      this.mostrarSugerenciasIdioma = false;
    }
    if (!target.closest('[data-autocomplete-proveedor]')) {
      this.mostrarSugerenciasProveedor = false;
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

  // ── ISBN lookup (solo manual + editorial) ──

  buscarPorIsbn(): void {
    const isbn = (this.form.get('isbn')?.value as string ?? '').trim();
    this.ejecutarLookupIsbn(isbn);
  }

  private mostrarLookupError(mensaje: string): void {
    if (this.lookupErrorTimer) clearTimeout(this.lookupErrorTimer);
    this.lookupError = mensaje;
    this.lookupErrorTimer = setTimeout(() => {
      this.lookupError = '';
      this.lookupErrorTimer = null;
    }, 3000);
  }

  private ejecutarLookupIsbn(isbn: string): void {
    if (!isbn) {
      this.mostrarLookupError('Ingresa un ISBN para buscar');
      return;
    }
    if (!/^[0-9]{10,13}$/.test(isbn)) {
      this.mostrarLookupError('ISBN debe tener 10 a 13 dígitos numéricos');
      return;
    }
    if (this.lookupCargando) return;
    this.lookupCargando = true;
    this.lookupError = '';
    this.libroService.buscarPorIsbn(isbn).subscribe({
      next: (dto) => {
        const patch: Record<string, unknown> = {};
        if (dto.titulo) patch['titulo'] = dto.titulo;
        if (dto.resumen) patch['resumen'] = dto.resumen;
        if (dto.anioPublicacion != null) patch['anioPublicacion'] = dto.anioPublicacion;
        if (dto.numeroPaginas != null) patch['numeroPaginas'] = dto.numeroPaginas;
        if (Object.keys(patch).length) this.form.patchValue(patch);
        if (dto.editorial) {
          const existente = this.editoriales.find(e => e.nombre.toLowerCase() === dto.editorial!.toLowerCase());
          if (existente) {
            this.form.patchValue({ editorialId: existente.id });
            this.editorialSeleccionadaNombre = existente.nombre;
          } else {
            // H7: pre-llenar y abrir dropdown con opción "Crear" + focus
            this.textoEditorial = dto.editorial!;
            this.sugerenciasEditorial = [];
            this.mostrarSugerenciasEditorial = true;
            setTimeout(() => this.editorialInputRef?.nativeElement?.focus(), 0);
          }
        }
        
        if (dto.autor) {
          const existente = this.autores.find(a => a.nombre.toLowerCase() === dto.autor!.toLowerCase());
          if (existente) {
            const ids = this.form.get('autorIds')?.value as number[];
            if (!ids.includes(existente.id)) {
              this.form.patchValue({ autorIds: [...ids, existente.id] });
            }
          } else {
            this.textoAutor = dto.autor!;
          }
        }

        if (!dto.titulo && !dto.resumen && dto.anioPublicacion == null && !dto.editorial && !dto.autor) {
          this.mostrarLookupError('No se encontraron datos para ese ISBN, completa manualmente');
        }

        if (dto.portadaDisponible) {
          this.libroService.portadaPorIsbn(isbn).subscribe({
            next: (blob) => {
              if (this.portadaPreviewUrl) URL.revokeObjectURL(this.portadaPreviewUrl);
              this.portadaPreviewBlob = blob;
              this.portadaPreviewTipo = blob.type;
              this.portadaPreviewUrl = URL.createObjectURL(blob);
              this.lookupCargando = false;
            },
            error: () => {
              console.warn('No se pudo descargar la portada automáticamente');
              this.lookupCargando = false;
            }
          });
        } else {
          this.lookupCargando = false;
        }
      },
      error: (err) => {
        const detail = err?.error?.detail ?? err?.error?.title ?? '';
        if (err?.status === 404) {
          this.mostrarLookupError(detail || 'No se encontró información para el ISBN, completa manualmente');
        } else {
          this.mostrarLookupError(detail || 'No se pudo consultar el ISBN, intenta de nuevo');
        }
        this.lookupCargando = false;
      }
    });
  }

  // ── Guardar ──

  guardarLibro(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const raw = this.form.getRawValue() as LibroRequest;
    // Validación año dinámico ya en validators, pero doble check
    if (raw.anioPublicacion < 1950 || raw.anioPublicacion > this.anioMax) {
      this.errorMsg = `El año debe estar entre 1950 y ${this.anioMax}`;
      return;
    }
    const datos: LibroRequest = {
      titulo: raw.titulo,
      isbn: raw.isbn,
      anioPublicacion: raw.anioPublicacion,
      numeroPaginas: raw.numeroPaginas ? Number(raw.numeroPaginas) : null,
      precioBase: (raw.precioBase as unknown) !== '' && raw.precioBase != null ? Number(raw.precioBase) : null,
      resumen: raw.resumen,
      ubicacionFisica: raw.ubicacionFisica,
      portadaUrl: raw.portadaUrl,
      editorialId: raw.editorialId,
      idiomaId: raw.idiomaId,
      estadoId: raw.estadoId,
      stockTotal: raw.stockTotal,
      stockDisponible: raw.stockDisponible,
      categoriaIds: raw.categoriaIds,
      autorIds: raw.autorIds,
      proveedorId: (raw as any).proveedorId ?? null
    } as LibroRequest;

    const accion = this.modoEdicion && this.libroSeleccionadoId
      ? this.libroService.actualizar(this.libroSeleccionadoId, datos)
      : this.libroService.crear(datos);

    accion.subscribe({
      next: (libro) => {
        this.guardarPortadaPendiente(libro.id);
        this.cerrarFormulario();
      },
      error: (err) => {
        const detail = err?.error?.detail ?? err?.error?.title ?? '';
        this.errorMsg = detail || (this.modoEdicion ? 'Error al actualizar el libro' : 'Error al crear el libro');
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

  mostrarConfirmacion(mensaje: string, accion: () => void) {
    this.confirmacionMensaje = mensaje;
    this.confirmacionPendiente = accion;
    this.confirmacionVisible = true;
  }

  confirmarAccion() {
    this.confirmacionVisible = false;
    if (this.confirmacionPendiente) {
      this.confirmacionPendiente();
      this.confirmacionPendiente = null;
    }
  }

  cancelarConfirmacion() {
    this.confirmacionVisible = false;
    this.confirmacionPendiente = null;
  }

  eliminarLibro(id: number): void {
    this.mostrarConfirmacion('¿Está seguro de eliminar este libro?', () => {
      this.libroService.eliminar(id).subscribe({
        next: () => { this.cargarLibros(); },
        error: () => { this.errorMsg = 'Error al eliminar el libro'; }
      });
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

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.confirmacionVisible) {
      this.cancelarConfirmacion();
    } else if (this.portadaModalVisible) {
      this.cerrarPortada();
    } else if (this.mostrarFormulario) {
      this.cerrarFormulario();
    }
  }

}
