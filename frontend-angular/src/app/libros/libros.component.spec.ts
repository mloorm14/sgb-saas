import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { LibrosComponent } from './libros.component';
import { LibroService } from '../core/services/libro.service';
import { CategoriaService } from '../core/services/categoria.service';
import { AutorService } from '../core/services/autor.service';
import { EditorialService } from '../core/services/editorial.service';
import { IdiomaService } from '../core/services/idioma.service';
import { EstadoLibroService } from '../core/services/estado-libro.service';
import { ToastService } from '../shared/toast/toast.service';

describe('LibrosComponent', () => {
  let component: LibrosComponent;
  let fixture: ComponentFixture<LibrosComponent>;
  let libroService: jasmine.SpyObj<LibroService>;
  let toastService: jasmine.SpyObj<ToastService>;
  let categoriaService: jasmine.SpyObj<CategoriaService>;
  let autorService: jasmine.SpyObj<AutorService>;
  let editorialService: jasmine.SpyObj<EditorialService>;
  let idiomaService: jasmine.SpyObj<IdiomaService>;
  let estadoLibroService: jasmine.SpyObj<EstadoLibroService>;

  const libroBase = {
    id: 1,
    isbn: '9780132350884',
    titulo: 'Clean Code',
    anioPublicacion: 2008,
    stockTotal: 3,
    stockDisponible: 2,
    editorialId: 1,
    idiomaId: 1,
    estadoId: 1,
    ubicacionFisica: 'Estante A-12',
    categorias: ['Tecnología'],
    autores: ['Robert C. Martin']
  };

  beforeEach(async () => {
    libroService = jasmine.createSpyObj('LibroService', [
      'listar', 'crear', 'actualizar', 'eliminar', 'subirPortada', 'obtenerPortada'
    ]);
    categoriaService = jasmine.createSpyObj('CategoriaService', ['listar']);
    autorService = jasmine.createSpyObj('AutorService', ['listar']);
    editorialService = jasmine.createSpyObj('EditorialService', ['listar']);
    idiomaService = jasmine.createSpyObj('IdiomaService', ['listar']);
    estadoLibroService = jasmine.createSpyObj('EstadoLibroService', ['listar']);
    toastService = jasmine.createSpyObj('ToastService', ['success', 'error', 'warning', 'info']);
    libroService.listar.and.returnValue(of({ content: [libroBase], totalPages: 1 } as any));
    libroService.obtenerPortada.and.returnValue(of(new Blob(['img'], { type: 'image/jpeg' })));
    categoriaService.listar.and.returnValue(of([{ id: 1, nombre: 'Tecnología' }, { id: 2, nombre: 'Ficción' }]));
    autorService.listar.and.returnValue(of([{ id: 7, nombre: 'Robert C. Martin' }]));
    editorialService.listar.and.returnValue(of([{ id: 1, nombre: 'Prentice Hall' }, { id: 2, nombre: 'O\'Reilly' }]));
    idiomaService.listar.and.returnValue(of([{ id: 1, nombre: 'Español' }, { id: 2, nombre: 'Inglés' }]));
    estadoLibroService.listar.and.returnValue(of([{ id: 1, nombre: 'Activo' }, { id: 2, nombre: 'Dado de baja' }]));

    await TestBed.configureTestingModule({
      imports: [LibrosComponent],
      providers: [
        { provide: LibroService, useValue: libroService },
        { provide: CategoriaService, useValue: categoriaService },
        { provide: AutorService, useValue: autorService },
        { provide: EditorialService, useValue: editorialService },
        { provide: IdiomaService, useValue: idiomaService },
        { provide: EstadoLibroService, useValue: estadoLibroService },
        { provide: ToastService, useValue: toastService },
        { provide: ActivatedRoute, useValue: { queryParams: of({}) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LibrosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga el listado inicial de libros', () => {
    expect(libroService.listar).toHaveBeenCalled();
    expect(component.libros.length).toBe(1);
  });

  it('carga el catálogo de categorías y autores para los selects del formulario', () => {
    expect(categoriaService.listar).toHaveBeenCalled();
    expect(autorService.listar).toHaveBeenCalled();
    expect(component.categorias.length).toBe(2);
    expect(component.autores.length).toBe(1);
  });

  // FIX 3: editorial/idioma/estado ahora son <select> con [ngValue]
  // (antes inputs de ID a mano) — se cargan de sus catálogos nuevos y
  // el valor numérico del libro preselecciona la opción correcta al editar.
  it('carga editoriales, idiomas y estados para los selects del formulario', () => {
    expect(editorialService.listar).toHaveBeenCalled();
    expect(idiomaService.listar).toHaveBeenCalled();
    expect(estadoLibroService.listar).toHaveBeenCalled();
    expect(component.editoriales.length).toBe(2);
    expect(component.idiomas.length).toBe(2);
    expect(component.estados.length).toBe(2);
    expect(component.errorCatalogo).toBe('');
  });

  it('preselecciona categorías y autores por nombre al editar (el DTO trae nombres, no ids)', () => {
    component.abrirFormularioEditar(libroBase as any);

    expect(component.form.get('categoriaIds')!.value).toEqual([1]);
    expect(component.form.get('autorIds')!.value).toEqual([7]);
  });

  it('precarga ubicacionFisica y los ids de editorial/idioma/estado al editar', () => {
    component.abrirFormularioEditar(libroBase as any);

    expect(component.form.get('ubicacionFisica')!.value).toBe('Estante A-12');
    expect(component.form.get('editorialId')!.value).toBe(1);
    expect(component.form.get('idiomaId')!.value).toBe(1);
    expect(component.form.get('estadoId')!.value).toBe(1);
  });

  it('filtra el listado por categoría y vuelve a la primera página', () => {
    component.currentPage = 3;
    component.categoriaFiltro = '1';
    component.filtrarPorCategoria();

    expect(component.currentPage).toBe(0);
    expect(libroService.listar).toHaveBeenCalledWith(
      jasmine.objectContaining({ categoriaId: 1 })
    );
  });

  it('abre el formulario de crear sin portada ni estado previo', () => {
    component.lookupError = 'sobra';
    component.portadaPreviewUrl = 'blob:previo';

    component.abrirFormularioCrear();

    expect(component.mostrarFormulario).toBeTrue();
    expect(component.modoEdicion).toBeFalse();
    expect(component.lookupError).toBe('');
    expect(component.portadaPreviewUrl).toBeNull();
  });

  describe('guardarLibro', () => {
    it('crea el libro y sube la portada del autocompletar como archivo', async () => {
      const creado = { ...libroBase };
      libroService.crear.and.returnValue(of(creado as any));
      libroService.subirPortada.and.returnValue(of(creado as any));
      component.portadaPreviewBlob = new Blob(['img'], { type: 'image/jpeg' });
      component.portadaPreviewTipo = 'image/jpeg';
      component.form.patchValue({
        isbn: '9780132350884',
        titulo: 'Clean Code',
        resumen: 'resumen',
        anioPublicacion: 2008,
        stockTotal: 3,
        stockDisponible: 2,
        editorialId: 1,
        idiomaId: 1,
        estadoId: 1
      });

      component.guardarLibro();
      await fixture.whenStable();

      expect(libroService.crear).toHaveBeenCalled();
      expect(libroService.subirPortada).toHaveBeenCalledWith(1, jasmine.any(File));
      expect(component.mostrarFormulario).toBeFalse();
    });

    it('actualiza un libro sin subir portada si no hay preview', () => {
      libroService.actualizar.and.returnValue(of(libroBase as any));

      component.modoEdicion = true;
      component.libroSeleccionadoId = 5;
      component.form.patchValue({
        isbn: '9780132350884',
        titulo: 'Clean Code',
        resumen: 'resumen',
        anioPublicacion: 2008,
        stockTotal: 3,
        stockDisponible: 2,
        editorialId: 1,
        idiomaId: 1,
        estadoId: 1
      });

      component.guardarLibro();

      expect(libroService.actualizar).toHaveBeenCalledWith(5, jasmine.any(Object));
      expect(libroService.subirPortada).not.toHaveBeenCalled();
    });

    it('no llama al servicio con el formulario inválido', () => {
      component.guardarLibro();
      expect(libroService.crear).not.toHaveBeenCalled();
      expect(libroService.actualizar).not.toHaveBeenCalled();
    });

    it('crea un libro SIN autocompletar ISBN: año tipeado a mano deja el form válido', () => {
      const creado = { ...libroBase };
      libroService.crear.and.returnValue(of(creado as any));

      component.abrirFormularioCrear();
      component.form.patchValue({
        isbn: '9789878001234',
        titulo: 'Libro tipeado a mano',
        anioPublicacion: 2020,
        stockTotal: 1,
        stockDisponible: 1,
        editorialId: 1,
        idiomaId: 1,
        estadoId: 1
      });

      expect(component.form.valid).toBeTrue();
      component.guardarLibro();
      expect(libroService.crear).toHaveBeenCalled();
    });

    it('muestra toast verde al crear el libro', () => {
      libroService.crear.and.returnValue(of(libroBase as any));
      component.form.patchValue({
        isbn: '9780132350884',
        titulo: 'Clean Code',
        anioPublicacion: 2008,
        stockTotal: 3,
        stockDisponible: 2,
        editorialId: 1,
        idiomaId: 1,
        estadoId: 1
      });

      component.guardarLibro();

      expect(toastService.success).toHaveBeenCalledTimes(1);
      expect(toastService.success).toHaveBeenCalledWith('Libro creado', jasmine.any(String));
    });

    it('muestra toast verde al actualizar el libro', () => {
      libroService.actualizar.and.returnValue(of(libroBase as any));
      component.modoEdicion = true;
      component.libroSeleccionadoId = 5;
      component.form.patchValue({
        isbn: '9780132350884',
        titulo: 'Clean Code',
        anioPublicacion: 2008,
        stockTotal: 3,
        stockDisponible: 2,
        editorialId: 1,
        idiomaId: 1,
        estadoId: 1
      });

      component.guardarLibro();

      expect(toastService.success).toHaveBeenCalledTimes(1);
      expect(toastService.success).toHaveBeenCalledWith('Libro actualizado', jasmine.any(String));
    });

    it('no muestra toast si el backend rechaza el guardado', () => {
      libroService.crear.and.returnValue(throwError(() => ({ error: { detail: 'ISBN duplicado' } })));
      component.form.patchValue({
        isbn: '9780132350884',
        titulo: 'Clean Code',
        anioPublicacion: 2008,
        stockTotal: 3,
        stockDisponible: 2,
        editorialId: 1,
        idiomaId: 1,
        estadoId: 1
      });

      component.guardarLibro();

      expect(toastService.success).not.toHaveBeenCalled();
      expect(component.errorMsg).toContain('ISBN duplicado');
    });
  });

  describe('portada manual', () => {
    function eventoConArchivo(archivo: File | null): Event {
      const input = document.createElement('input');
      Object.defineProperty(input, 'files', { value: archivo ? [archivo] : [] });
      const event = new Event('change');
      Object.defineProperty(event, 'target', { value: input });
      return event;
    }

    it('acepta PNG/JPEG/WEBP de hasta 2MB y deja el preview listo para guardar', async () => {
      const archivo = new File(['img'], 'portada.png', { type: 'image/png' });

      await component.onArchivoPortadaSeleccionado(eventoConArchivo(archivo));

      expect(component.portadaPreviewBlob).toBe(archivo);
      expect(component.portadaPreviewTipo).toBe('image/png');
      expect(component.portadaPreviewUrl).toContain('blob:');
      expect(component.lookupError).toBe('');
    });

    it('detecta por magic bytes una imagen cuyo type es application/octet-stream', async () => {
      const bytes = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0]);
      const archivo = new File([bytes], 'portada.bin', { type: 'application/octet-stream' });

      await component.onArchivoPortadaSeleccionado(eventoConArchivo(archivo));

      expect(component.portadaPreviewBlob).toBe(archivo);
      expect(component.portadaPreviewTipo).toBe('image/png');
      expect(component.portadaPreviewUrl).toContain('blob:');
      expect(component.lookupError).toBe('');
    });

    it('rechaza un tipo no permitido (gif) y desconocido sin tocar el preview previo', async () => {
      const archivo = new File(['img'], 'portada.gif', { type: 'image/gif' });

      await component.onArchivoPortadaSeleccionado(eventoConArchivo(archivo));

      expect(component.lookupError).toBe('Formato no permitido. Usá JPG, JPEG, PNG, WebP o AVIF.');
      expect(component.portadaPreviewBlob).toBeNull();
    });

    it('rechaza una imagen de más de 2MB (max_tamano_portada_mb = 2 en V13)', async () => {
      const archivo = new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'grande.png', { type: 'image/png' });

      await component.onArchivoPortadaSeleccionado(eventoConArchivo(archivo));

      expect(component.lookupError).toBe('La imagen supera los 2MB permitidos.');
      expect(component.portadaPreviewBlob).toBeNull();
    });

    it('sube la portada manual al guardar con el nombre y tipo derivados', async () => {
      const creado = { ...libroBase };
      libroService.crear.and.returnValue(of(creado as any));
      libroService.subirPortada.and.returnValue(of(creado as any));
      component.portadaPreviewBlob = new Blob(['img'], { type: 'image/png' });
      component.portadaPreviewTipo = 'image/png';
      component.form.patchValue({
        isbn: '9789878001234',
        titulo: 'Con portada manual',
        anioPublicacion: 2020,
        stockTotal: 1,
        stockDisponible: 1,
        editorialId: 1,
        idiomaId: 1,
        estadoId: 1
      });

      component.guardarLibro();
      await fixture.whenStable();

      expect(libroService.crear).toHaveBeenCalled();
      expect(libroService.subirPortada).toHaveBeenCalledWith(1, jasmine.any(File));
      const archivo = libroService.subirPortada.calls.mostRecent().args[1] as File;
      expect(archivo.name).toBe('portada.png');
      expect(archivo.type).toBe('image/png');
    });

    it('al guardar, si el blob quedó sin tipo usa image/jpeg como fallback', async () => {
      const creado = { ...libroBase };
      libroService.crear.and.returnValue(of(creado as any));
      libroService.subirPortada.and.returnValue(of(creado as any));
      const bytes = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
      component.portadaPreviewBlob = new File([bytes], 'foto.bin', { type: 'application/octet-stream' });
      component.portadaPreviewTipo = null;
      component.form.patchValue({
        isbn: '9789878001235',
        titulo: 'Con portada sin tipo',
        anioPublicacion: 2020,
        stockTotal: 1,
        stockDisponible: 1,
        editorialId: 1,
        idiomaId: 1,
        estadoId: 1
      });

      component.guardarLibro();
      await fixture.whenStable();

      expect(libroService.subirPortada).toHaveBeenCalledWith(1, jasmine.any(File));
      const archivo = libroService.subirPortada.calls.mostRecent().args[1] as File;
      expect(archivo.type).toBe('image/jpeg');
      expect(archivo.name).toBe('portada.jpeg');
    });
  });

  describe('modal portada', () => {
    it('abrirPortada muestra el modal', () => {
      component.abrirPortada(1, true);

      expect(component.portadaModalVisible).toBeTrue();
    });

    it('cerrarPortada oculta el modal y limpia la url', () => {
      component.portadaModalVisible = true;
      component.portadaModalUrl = 'blob:test';
      component.cerrarPortada();

      expect(component.portadaModalVisible).toBeFalse();
      expect(component.portadaModalUrl).toBeNull();
      expect(component.portadaModalCargando).toBeFalse();
    });

    it('no abre el modal si el libro no tiene portada', () => {
      component.abrirPortada(2, false);

      expect(component.portadaModalVisible).toBeFalse();
    });
  });
});