import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LibrosComponent } from './libros.component';
import { LibroService } from '../core/services/libro.service';
import { AuthService } from '../core/services/auth.service';
import { Router } from '@angular/router';

describe('LibrosComponent', () => {
  let component: LibrosComponent;
  let fixture: ComponentFixture<LibrosComponent>;
  let libroService: jasmine.SpyObj<LibroService>;
  let authService: jasmine.SpyObj<AuthService>;

  const libroBase = {
    id: 1,
    isbn: '9780132350884',
    titulo: 'Clean Code',
    anioPublicacion: 2008,
    stockTotal: 3,
    stockDisponible: 2,
    editorialId: 1,
    idiomaId: 1,
    estadoId: 1
  };

  beforeEach(async () => {
    libroService = jasmine.createSpyObj('LibroService', [
      'listar', 'crear', 'actualizar', 'eliminar', 'subirPortada',
      'buscarPorIsbn', 'portadaPorIsbn'
    ]);
    authService = jasmine.createSpyObj('AuthService', ['logout']);
    libroService.listar.and.returnValue(of({ content: [libroBase], totalPages: 1 } as any));

    await TestBed.configureTestingModule({
      imports: [LibrosComponent],
      providers: [
        { provide: LibroService, useValue: libroService },
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: { navigate: jasmine.createSpy('navigate') } }
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

  it('abre el formulario de crear sin portada ni estado de autocompletar previo', () => {
    component.lookupError = 'sobra';
    component.portadaPreviewUrl = 'blob:previo';

    component.abrirFormularioCrear();

    expect(component.mostrarFormulario).toBeTrue();
    expect(component.modoEdicion).toBeFalse();
    expect(component.lookupError).toBe('');
    expect(component.portadaPreviewUrl).toBeNull();
  });

  describe('autocompletar por ISBN', () => {
    it('habilita el botón solo cuando el ISBN tiene 10-13 dígitos', () => {
      component.form.patchValue({ isbn: '9780132350884' });
      expect(component.esIsbnAutocompletable()).toBeTrue();

      component.form.patchValue({ isbn: '978-0132350884' });
      expect(component.esIsbnAutocompletable()).toBeTrue();

      component.form.patchValue({ isbn: '12345' });
      expect(component.esIsbnAutocompletable()).toBeFalse();
    });

    it('no consulta con ISBN inválido', () => {
      component.form.patchValue({ isbn: '123' });
      component.autocompletar();
      expect(libroService.buscarPorIsbn).not.toHaveBeenCalled();
    });

    it('rellena título, resumen y año; y descarga la portada como blob', () => {
      const info = {
        titulo: 'Clean Code',
        autor: 'Robert C. Martin',
        resumen: 'resumen largo',
        anioPublicacion: 2008,
        portadaDisponible: true
      };
      libroService.buscarPorIsbn.and.returnValue(of(info as any));
      libroService.portadaPorIsbn.and.returnValue(of(new Blob(['img'], { type: 'image/jpeg' })));

      component.form.patchValue({ isbn: '9780132350884' });
      component.autocompletar();

      expect(libroService.buscarPorIsbn).toHaveBeenCalledWith('9780132350884');
      expect(component.form.get('titulo')!.value).toBe('Clean Code');
      expect(component.form.get('resumen')!.value).toBe('resumen largo');
      expect(component.form.get('anioPublicacion')!.value).toBe(2008);
      expect(component.autocompletarAutor).toBe('Robert C. Martin');
      expect(component.lookupMensaje).not.toBe('');
      expect(libroService.portadaPorIsbn).toHaveBeenCalledWith('9780132350884');
      expect(component.portadaPreviewUrl).toContain('blob:');
      expect(component.portadaPreviewBlob).not.toBeNull();
    });

    it('muestra el mensaje exacto cuando el backend responde 404', () => {
      libroService.buscarPorIsbn.and.returnValue(throwError(() => ({ status: 404 })));

      component.form.patchValue({ isbn: '0000000000000' });
      component.autocompletar();

      expect(component.lookupError).toBe('No se encontró información para ese ISBN, completá los campos manualmente');
      expect(component.buscandoIsbn).toBeFalse();
    });

    it('muestra error genérico si Google Books falla de otra forma', () => {
      libroService.buscarPorIsbn.and.returnValue(throwError(() => ({ status: 500 })));

      component.form.patchValue({ isbn: '0000000000000' });
      component.autocompletar();

      expect(component.lookupError).toBe('Error al consultar Google Books, intentá de nuevo');
    });
  });

  describe('guardarLibro', () => {
    it('crea el libro y sube la portada del autocompletar como archivo', () => {
      const creado = { ...libroBase };
      libroService.crear.and.returnValue(of(creado as any));
      libroService.subirPortada.and.returnValue(of(creado as any));
      component.portadaPreviewBlob = new Blob(['img'], { type: 'image/jpeg' });
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
  });
});