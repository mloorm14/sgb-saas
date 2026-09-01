import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { DetallePublicoComponent } from './detalle-publico.component';
import { LibroPublicoService } from '../../core/services/libro-publico.service';
import { Libro } from '../../core/models/libro.model';
import { ActivatedRoute } from '@angular/router';

describe('DetallePublicoComponent', () => {
  let component: DetallePublicoComponent;
  let fixture: ComponentFixture<DetallePublicoComponent>;
  let libroPublicoService: jasmine.SpyObj<LibroPublicoService>;

  const libro = (overrides: Partial<Libro> = {}): Libro => ({
    id: 1, titulo: 'Clean Code', isbn: '9780132350884', resumen: 'resumen',
    portadaUrl: '', tienePortada: false, portadaNombre: '', portadaTipo: '',
    anioPublicacion: 2008, editorialId: 1, editorial: 'Prentice Hall',
    idiomaId: 1, idioma: 'Español', estadoId: 1, estado: 'ACTIVO',
    stockTotal: 3, stockDisponible: 3, ubicacionFisica: '', fechaRegistro: '',
    categorias: ['Ingeniería de Software'], autores: ['Robert C. Martin'],
    ...overrides
  });

  beforeEach(async () => {
    libroPublicoService = jasmine.createSpyObj('LibroPublicoService', ['obtener', 'portadaUrl']);
    libroPublicoService.obtener.and.returnValue(of(libro()));

    await TestBed.configureTestingModule({
      imports: [DetallePublicoComponent],
      providers: [
        { provide: LibroPublicoService, useValue: libroPublicoService },
        { provide: ActivatedRoute, useValue: { snapshot: { data: {}, paramMap: { get: () => '1' } } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DetallePublicoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga el libro público por id', () => {
    expect(libroPublicoService.obtener).toHaveBeenCalledWith(1);
    expect(component.libro?.titulo).toBe('Clean Code');
  });

  it('con id inválido no llama al backend y muestra error', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [DetallePublicoComponent],
      providers: [
        { provide: LibroPublicoService, useValue: libroPublicoService },
        { provide: ActivatedRoute, useValue: { snapshot: { data: {}, paramMap: { get: () => null } } } }
      ]
    }).compileComponents();

    const nuevaFixture = TestBed.createComponent(DetallePublicoComponent);
    const nuevoComponent = nuevaFixture.componentInstance;
    libroPublicoService.obtener.calls.reset();
    nuevaFixture.detectChanges();

    expect(libroPublicoService.obtener).not.toHaveBeenCalled();
    expect(nuevoComponent.errorMsg).toBe('Libro no encontrado');
  });

  it('muestra error si el backend falla', () => {
    libroPublicoService.obtener.and.returnValue(throwError(() => ({ status: 500 })));
    component.ngOnInit();
    expect(component.errorMsg).toBe('Error al cargar el libro');
  });

  it('arma el detalle de autor omitiendo partes vacías', () => {
    component.libro = libro();
    expect(component.detalleAutor()).toBe('Robert C. Martin — Prentice Hall — 2008');
  });

  it('expone la URL pública de la portada', () => {
    libroPublicoService.portadaUrl.and.returnValue('http://localhost:8080/api/publico/libros/1/portada');
    expect(component.portadaUrl(1)).toBe('http://localhost:8080/api/publico/libros/1/portada');
  });
});