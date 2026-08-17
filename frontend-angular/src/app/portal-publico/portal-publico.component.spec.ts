import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PortalPublicoComponent } from './portal-publico.component';
import { LibroPublicoService } from '../core/services/libro-publico.service';
import { Libro } from '../core/models/libro.model';
import { ActivatedRoute } from '@angular/router';

describe('PortalPublicoComponent', () => {
  let component: PortalPublicoComponent;
  let fixture: ComponentFixture<PortalPublicoComponent>;
  let libroPublicoService: jasmine.SpyObj<LibroPublicoService>;

  const libro = (id: number, titulo: string, tienePortada: boolean): Libro => ({
    id, titulo, isbn: 'x', resumen: '', portadaUrl: '', tienePortada,
    portadaNombre: '', portadaTipo: '', anioPublicacion: 2020, editorialId: 0,
    editorial: '', idiomaId: 0, idioma: '', estadoId: 0, estado: '',
    stockTotal: 5, stockDisponible: 4, ubicacionFisica: '', fechaRegistro: '',
    categorias: [], autores: ['Robert C. Martin']
  });

  beforeEach(async () => {
    libroPublicoService = jasmine.createSpyObj('LibroPublicoService', ['listar', 'sugerencias', 'portadaUrl']);
    libroPublicoService.listar.and.returnValue(of({ content: [libro(1, 'Clean Code', false)], totalPages: 1 } as any));
    libroPublicoService.sugerencias.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [PortalPublicoComponent],
      providers: [
        { provide: LibroPublicoService, useValue: libroPublicoService },
        { provide: ActivatedRoute, useValue: { snapshot: {} } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PortalPublicoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga el grid público con el sort por título sin pedir sesión', () => {
    expect(libroPublicoService.listar).toHaveBeenCalledWith({
      page: 0,
      size: 10,
      sort: 'titulo,asc'
    });
    expect(component.libros.length).toBe(1);
    expect(component.totalPages).toBe(1);
  });

  it('muestra el estado de error sin romper la UI si el backend falla', () => {
    libroPublicoService.listar.and.returnValue(throwError(() => ({ status: 500 })));
    component.cargarPagina();
    expect(component.errorMsg).toBe('Error al cargar el catálogo');
    expect(component.cargando).toBeFalse();
  });

  it('busca sugerencias solo con 2+ caracteres y tras el debounce de 300ms', fakeAsync(() => {
    libroPublicoService.sugerencias.and.returnValue(of([{ id: 1, titulo: 'Estructuras', disponible: true }]));

    component.textoBusqueda = 'es';
    component.onBusquedaChange();
    tick(150);
    expect(libroPublicoService.sugerencias).not.toHaveBeenCalled();

    tick(200);
    flush();
    expect(libroPublicoService.sugerencias).toHaveBeenCalledWith('es');
    expect(component.sugerencias.length).toBe(1);
  }));

  it('con menos de 2 caracteres limpia el dropdown sin llamar al backend', fakeAsync(() => {
    component.textoBusqueda = 'e';
    component.onBusquedaChange();
    tick(400);
    flush();
    expect(libroPublicoService.sugerencias).not.toHaveBeenCalled();
    expect(component.sugerencias.length).toBe(0);
  }));

  it('expone la URL pública de la portada para usarla directo en <img>', () => {
    libroPublicoService.portadaUrl.and.returnValue('http://localhost:8080/api/publico/libros/1/portada');
    expect(component.portadaUrl(1)).toBe('http://localhost:8080/api/publico/libros/1/portada');
    expect(libroPublicoService.portadaUrl).toHaveBeenCalledWith(1);
  });

  it('pagina con la barra de navegación numerada', () => {
    libroPublicoService.listar.and.returnValue(of({ content: [], totalPages: 3 } as any));
    component.totalPages = 3;
    expect(component.paginasVisibles).toEqual([0, 1, 2]);

    component.irAPagina(2);
    expect(component.currentPage).toBe(2);
    expect(libroPublicoService.listar).toHaveBeenCalledWith(jasmine.objectContaining({ page: 2 }));

    component.paginaSiguiente();
    expect(component.currentPage).toBe(2); // ultima pagina: no avanza

    component.paginaAnterior();
    expect(component.currentPage).toBe(1);
  });
});