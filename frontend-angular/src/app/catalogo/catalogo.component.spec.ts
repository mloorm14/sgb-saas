import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CatalogoComponent } from './catalogo.component';
import { LibroService } from '../core/services/libro.service';
import { CategoriaService } from '../core/services/categoria.service';
import { AutorService } from '../core/services/autor.service';
import { FavoritoService } from '../core/services/favorito.service';
import { ReservacionService } from '../core/services/reservacion.service';
import { AuthService } from '../core/services/auth.service';
import { Libro } from '../core/models/libro.model';
import { ActivatedRoute } from '@angular/router';

describe('CatalogoComponent', () => {
  let component: CatalogoComponent;
  let fixture: ComponentFixture<CatalogoComponent>;
  let libroService: jasmine.SpyObj<LibroService>;
  let categoriaService: jasmine.SpyObj<CategoriaService>;
  let autorService: jasmine.SpyObj<AutorService>;
  let favoritoService: jasmine.SpyObj<FavoritoService>;
  let reservacionService: jasmine.SpyObj<ReservacionService>;

  const libro = (id: number, titulo: string, stockDisponible: number): Libro => ({
    id, titulo, isbn: 'x', resumen: '', portadaUrl: '', tienePortada: false,
    portadaNombre: '', portadaTipo: '', anioPublicacion: 2020, editorialId: 0,
    editorial: '', idiomaId: 0, idioma: '', estadoId: 0, estado: '',
    stockTotal: 5, stockDisponible, ubicacionFisica: '', fechaRegistro: '',
    categorias: [], autores: []
  });

  beforeEach(async () => {
    libroService = jasmine.createSpyObj('LibroService', ['listar', 'sugerencias']);
    categoriaService = jasmine.createSpyObj('CategoriaService', ['listar']);
    autorService = jasmine.createSpyObj('AutorService', ['listar']);
    favoritoService = jasmine.createSpyObj('FavoritoService', ['listar', 'agregar', 'quitar']);
    reservacionService = jasmine.createSpyObj('ReservacionService', ['crear', 'listarPorUsuario']);

    libroService.listar.and.returnValue(of({ content: [libro(1, 'Clean Code', 4)], totalPages: 1 } as any));
    categoriaService.listar.and.returnValue(of([{ id: 1, nombre: 'Ingeniería de Software' }]));
    autorService.listar.and.returnValue(of([{ id: 1, nombre: 'Robert C. Martin' }]));
    favoritoService.listar.and.returnValue(of([]));
    reservacionService.listarPorUsuario.and.returnValue(of({ content: [], totalPages: 0 } as any));

    await TestBed.configureTestingModule({
      imports: [CatalogoComponent],
      providers: [
        { provide: LibroService, useValue: libroService },
        { provide: CategoriaService, useValue: categoriaService },
        { provide: AutorService, useValue: autorService },
        { provide: FavoritoService, useValue: favoritoService },
        { provide: ReservacionService, useValue: reservacionService },
        { provide: AuthService, useValue: { getUserId: () => 2, hasRole: () => false } },
        { provide: ActivatedRoute, useValue: { snapshot: {} } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CatalogoComponent);
    component = fixture.componentInstance;
  });

  it('carga el grid del catálogo con el sort por título', () => {
    fixture.detectChanges();

    expect(libroService.listar).toHaveBeenCalledWith({
      page: 0,
      size: 10,
      sort: 'titulo,asc',
      categoriaId: undefined,
      autorId: undefined
    });
    expect(component.libros.length).toBe(1);
    expect(component.totalPages).toBe(1);
    expect(component.categorias.length).toBe(1);
    expect(component.autores.length).toBe(1);
  });

  it('muestra el estado de error sin romper la UI si el backend falla', () => {
    fixture.detectChanges();
    libroService.listar.and.returnValue(throwError(() => ({ status: 500 })));
    component.cargarPagina();
    expect(component.errorMsg).toBe('Error al cargar el catálogo');
    expect(component.cargando).toBeFalse();
  });

  it('busca sugerencias solo con 2+ caracteres y tras el debounce de 300ms', fakeAsync(() => {
    fixture.detectChanges();
    libroService.sugerencias.and.returnValue(of([{ id: 1, titulo: 'Estructuras', disponible: true }]));

    component.textoBusqueda = 'es';
    component.onBusquedaChange();
    tick(150);
    expect(libroService.sugerencias).not.toHaveBeenCalled();

    tick(200);
    flush();
    expect(libroService.sugerencias).toHaveBeenCalledWith('es');
    expect(component.sugerencias.length).toBe(1);
  }));

  it('con menos de 2 caracteres limpia el dropdown sin llamar al backend', fakeAsync(() => {
    fixture.detectChanges();
    component.textoBusqueda = 'e';
    component.onBusquedaChange();
    tick(400);
    flush();
    expect(libroService.sugerencias).not.toHaveBeenCalled();
    expect(component.sugerencias.length).toBe(0);
  }));

  it('alterna favoritos: agrega y marca la tarjeta', () => {
    fixture.detectChanges();
    favoritoService.agregar.and.returnValue(of({ usuarioId: 1, libroId: 1, tituloLibro: 'x', agregadoEn: '' }));
    component.alternarFavorito(new Event('click'), component.libros[0]);

    expect(favoritoService.agregar).toHaveBeenCalledWith(1);
    expect(component.esFavorito(1)).toBeTrue();
  });

  it('alterna favoritos: quita y desmarca la tarjeta', () => {
    fixture.detectChanges();
    component.favoritosIds.add(1);
    favoritoService.quitar.and.returnValue(of(undefined));

    component.alternarFavorito(new Event('click'), component.libros[0]);

    expect(favoritoService.quitar).toHaveBeenCalledWith(1);
    expect(component.esFavorito(1)).toBeFalse();
  });

  it('carga el estado inicial de favoritos desde FavoritoService.listar', () => {
    fixture.detectChanges();
    expect(favoritoService.listar).toHaveBeenCalled();
  });

  it('filtra por categoría y reinicia a la primera página', () => {
    fixture.detectChanges();
    component.currentPage = 2;
    component.onCategoriaChange({ target: { value: '5' } } as any);

    expect(component.currentPage).toBe(0);
    expect(libroService.listar).toHaveBeenCalledWith(jasmine.objectContaining({ categoriaId: 5 }));
  });

  it('filtra por autor', () => {
    fixture.detectChanges();
    component.onAutorChange({ target: { value: '7' } } as any);

    expect(libroService.listar).toHaveBeenCalledWith(jasmine.objectContaining({ autorId: 7 }));
  });

  it('pagina con la barra de navegación numerada', () => {
    fixture.detectChanges();
    libroService.listar.and.returnValue(of({ content: [], totalPages: 3 } as any));
    component.totalPages = 3;
    expect(component.paginasVisibles).toEqual([0, 1, 2]);

    component.irAPagina(2);
    expect(component.currentPage).toBe(2);
    expect(libroService.listar).toHaveBeenCalledWith(jasmine.objectContaining({ page: 2 }));

    component.paginaSiguiente();
    expect(component.currentPage).toBe(2); // ultima pagina: no avanza

    component.paginaAnterior();
    expect(component.currentPage).toBe(1);
  });

  it('carga las reservaciones pendientes del lector y marca "Ya reservado"', () => {
    reservacionService.listarPorUsuario.and.returnValue(
      of({ content: [{ id: 1, libroId: 1, estadoReservacionId: 1 }], totalPages: 1 } as any)
    );

    fixture.detectChanges();

    expect(reservacionService.listarPorUsuario).toHaveBeenCalledWith(2, jasmine.anything());
    expect(component.estaReservado(1)).toBeTrue();
  });

  it('reserva un libro desde la tarjeta, lo marca como reservado y muestra el toast', () => {
    fixture.detectChanges();
    reservacionService.crear.and.returnValue(of({
      id: 9, usuarioId: 2, libroId: 1, estadoReservacionId: 1,
      fechaReserva: '', fechaLimiteRetiro: '2026-08-18T12:00:00Z'
    } as any));

    component.reservarLibro(new Event('click'), component.libros[0]);
    expect(component.mostrarModalReserva).toBeTrue();
    expect(component.libroParaReservar?.id).toBe(1);

    component.confirmarReserva();

    expect(reservacionService.crear).toHaveBeenCalledWith(jasmine.objectContaining({ usuarioId: 2, libroId: 1 }));
    expect(component.estaReservado(1)).toBeTrue();
    expect(component.toastMsg).toContain('Reservado');
    expect(component.toastMsg).toContain('18/08/2026');
  });

  it('no rompe la tarjeta si la reserva falla', () => {
    fixture.detectChanges();
    reservacionService.crear.and.returnValue(throwError(() => ({ status: 500 })));

    component.reservarLibro(new Event('click'), component.libros[0]);
    component.confirmarReserva();

    expect(component.errorMsg).toBe('Error al reservar el libro');
    expect(component.estaReservado(1)).toBeFalse();
    expect(component.toastMsg).toBeNull();
  });
});