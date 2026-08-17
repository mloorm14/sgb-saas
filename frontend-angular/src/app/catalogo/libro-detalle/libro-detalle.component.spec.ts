import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LibroDetalleComponent } from './libro-detalle.component';
import { ActivatedRoute } from '@angular/router';
import { LibroService } from '../../core/services/libro.service';
import { FavoritoService } from '../../core/services/favorito.service';
import { ReservacionService } from '../../core/services/reservacion.service';
import { AuthService } from '../../core/services/auth.service';
import { Libro } from '../../core/models/libro.model';

describe('LibroDetalleComponent', () => {
  let component: LibroDetalleComponent;
  let fixture: ComponentFixture<LibroDetalleComponent>;
  let libroService: jasmine.SpyObj<LibroService>;
  let favoritoService: jasmine.SpyObj<FavoritoService>;
  let reservacionService: jasmine.SpyObj<ReservacionService>;

  beforeEach(async () => {
    libroService = jasmine.createSpyObj('LibroService', ['obtener', 'obtenerPortada']);
    favoritoService = jasmine.createSpyObj('FavoritoService', ['listar', 'agregar', 'quitar']);
    reservacionService = jasmine.createSpyObj('ReservacionService', ['crear', 'listarPorUsuario']);

    libroService.obtenerPortada.and.returnValue(throwError(() => ({ status: 404 })));

    libroService.obtener.and.returnValue(of({
      id: 5,
      titulo: 'Clean Code',
      isbn: '978-0132350884',
      resumen: 'Prácticas de código limpio.',
      portadaUrl: '',
      tienePortada: true,
      portadaNombre: '',
      portadaTipo: '',
      anioPublicacion: 2008,
      editorialId: 1,
      editorial: 'Prentice Hall',
      idiomaId: 1,
      idioma: 'Español',
      estadoId: 1,
      estado: 'Disponible',
      stockTotal: 6,
      stockDisponible: 4,
      ubicacionFisica: '',
      fechaRegistro: '',
      categorias: ['Ingeniería de Software'],
      autores: ['Robert C. Martin']
    } as Libro));
    favoritoService.listar.and.returnValue(of([]));
    reservacionService.listarPorUsuario.and.returnValue(of({ content: [], totalPages: 0 } as any));

    await TestBed.configureTestingModule({
      imports: [LibroDetalleComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (clave: string) => (clave === 'id' ? '5' : null) } } }
        },
        { provide: LibroService, useValue: libroService },
        { provide: FavoritoService, useValue: favoritoService },
        { provide: ReservacionService, useValue: reservacionService },
        { provide: AuthService, useValue: { getUserId: () => 2, hasRole: () => false } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LibroDetalleComponent);
    component = fixture.componentInstance;
  });

  it('carga el libro por el id de la ruta', () => {
    fixture.detectChanges();

    expect(libroService.obtener).toHaveBeenCalledWith(5);
    expect(component.libro?.titulo).toBe('Clean Code');
    expect(component.categoriasTexto()).toBe('Ingeniería de Software');
    expect(component.autoresTexto()).toBe('Robert C. Martin');
  });

  it('agrega el libro a favoritos desde el detalle', () => {
    fixture.detectChanges();
    favoritoService.agregar.and.returnValue(of({ usuarioId: 1, libroId: 5, tituloLibro: 'Clean Code', agregadoEn: '' }));

    component.alternarFavorito();

    expect(favoritoService.agregar).toHaveBeenCalledWith(5);
    expect(component.esFavorito()).toBeTrue();
  });

  it('quita el libro de favoritos si ya estaba marcado', () => {
    fixture.detectChanges();
    favoritoService.listar.and.returnValue(of([{ usuarioId: 1, libroId: 5, tituloLibro: 'Clean Code', agregadoEn: '' }]));
    component.ngOnInit();
    fixture.detectChanges();
    favoritoService.quitar.and.returnValue(of(undefined));

    expect(component.esFavorito()).toBeTrue();
    component.alternarFavorito();

    expect(favoritoService.quitar).toHaveBeenCalledWith(5);
    expect(component.esFavorito()).toBeFalse();
  });

  it('carga las reservaciones pendientes del lector y bloquea el botón', () => {
    reservacionService.listarPorUsuario.and.returnValue(
      of({ content: [{ id: 1, libroId: 5, estadoReservacionId: 2 }], totalPages: 1 } as any)
    );

    fixture.detectChanges();

    expect(component.estaReservado()).toBeTrue();
  });

  it('reserva desde el detalle y muestra la confirmación con la fecha límite real', () => {
    fixture.detectChanges();
    reservacionService.crear.and.returnValue(of({
      id: 9, usuarioId: 2, libroId: 5, estadoReservacionId: 1,
      fechaReserva: '2026-08-16T00:00:00Z', fechaLimiteRetiro: '2026-08-18T00:00:00Z'
    } as any));

    component.reservarLibro();

    expect(reservacionService.crear).toHaveBeenCalledWith({ usuarioId: 2, libroId: 5 });
    expect(component.reservaCreada?.fechaLimiteRetiro).toBe('2026-08-18T00:00:00Z');
    expect(component.estaReservado()).toBeTrue();
  });

  it('no rompe el detalle si la reserva falla', () => {
    fixture.detectChanges();
    reservacionService.crear.and.returnValue(throwError(() => ({ status: 500 })));

    component.reservarLibro();

    expect(component.errorMsg).toBe('Error al reservar el libro');
    expect(component.reservaCreada).toBeNull();
    expect(component.estaReservado()).toBeFalse();
  });
});