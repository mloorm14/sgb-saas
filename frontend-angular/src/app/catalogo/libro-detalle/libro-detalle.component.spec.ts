import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { LibroDetalleComponent } from './libro-detalle.component';
import { ActivatedRoute } from '@angular/router';
import { LibroService } from '../../core/services/libro.service';
import { FavoritoService } from '../../core/services/favorito.service';
import { ReservacionService } from '../../core/services/reservacion.service';
import { ReservacionPendienteService } from '../../core/services/reservacion-pendiente.service';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { ClockService } from '../../core/services/clock.service';
import { Libro } from '../../core/models/libro.model';

describe('LibroDetalleComponent', () => {
  let component: LibroDetalleComponent;
  let fixture: ComponentFixture<LibroDetalleComponent>;
  let libroService: jasmine.SpyObj<LibroService>;
  let favoritoService: jasmine.SpyObj<FavoritoService>;
  let reservacionService: jasmine.SpyObj<ReservacionService>;
  let reservacionesPendientes: jasmine.SpyObj<ReservacionPendienteService>;
  let authService: jasmine.SpyObj<AuthService>;
  let confirmDialog: jasmine.SpyObj<ConfirmDialogService>;
  let clock: jasmine.SpyObj<ClockService>;

  // Fechas locales fijas (OBS-20): deterministas en cualquier zona horaria.
  const MANANA_10H = new Date(2026, 8, 4, 10, 0, 0);
  const TARDE_1930H = new Date(2026, 8, 4, 19, 30, 0);

  beforeEach(async () => {
    libroService = jasmine.createSpyObj('LibroService', ['obtener', 'obtenerPortada']);
    favoritoService = jasmine.createSpyObj('FavoritoService', ['listar', 'agregar', 'quitar']);
    reservacionService = jasmine.createSpyObj('ReservacionService', ['crear', 'listarPorUsuario']);
    reservacionesPendientes = jasmine.createSpyObj('ReservacionPendienteService', ['cargar', 'esPendiente', 'marcarReservada']);
    authService = jasmine.createSpyObj('AuthService', ['getUserId', 'hasRole']);

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
    reservacionesPendientes.cargar.and.returnValue(of(undefined));
    reservacionesPendientes.esPendiente.and.returnValue(false);
    authService.getUserId.and.returnValue(2);
    authService.hasRole.and.returnValue(false);
    // OBS-20: reloj y diálogo stubbeados — la suite ya no depende de la hora
    // real (antes fallaba ≥18:00 porque el ConfirmDialog real nunca emitía).
    confirmDialog = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    confirmDialog.confirm.and.returnValue(of(true));
    clock = jasmine.createSpyObj('ClockService', ['now']);
    clock.now.and.returnValue(MANANA_10H);

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
        { provide: ReservacionPendienteService, useValue: reservacionesPendientes },
        { provide: AuthService, useValue: authService },
        { provide: ConfirmDialogService, useValue: confirmDialog },
        { provide: ClockService, useValue: clock }
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
    reservacionesPendientes.esPendiente.and.returnValue(true);

    fixture.detectChanges();

    expect(component.estaReservado()).toBeTrue();
  });

  it('reserva desde el detalle y muestra la confirmación con la fecha límite real', () => {
    fixture.detectChanges();
    reservacionService.crear.and.returnValue(of({
      id: 9, usuarioId: 2, libroId: 5, estadoReservacionId: 1,
      fechaReserva: '2026-08-16T00:00:00Z', fechaLimiteRetiro: '2026-08-18T00:00:00Z'
    } as any));

    component.confirmarReserva();

    expect(reservacionService.crear).toHaveBeenCalledWith(jasmine.objectContaining({ usuarioId: 2, libroId: 5 }));
    expect(component.reservaCreada?.fechaLimiteRetiro).toBe('2026-08-18T00:00:00Z');
    expect(reservacionesPendientes.marcarReservada).toHaveBeenCalledWith(5);
  });

  it('no rompe el detalle si la reserva falla', () => {
    fixture.detectChanges();
    reservacionService.crear.and.returnValue(throwError(() => ({ status: 500 })));

    component.confirmarReserva();

    expect(component.errorMsg).toBe('No se pudo reservar el libro');
    expect(component.reservaCreada).toBeNull();
  });

  // ── OBS-20: ramas determinísticas de la hora límite ──────────────

  it('no abre el diálogo de hora límite antes de las 18:00', () => {
    clock.now.and.returnValue(MANANA_10H);
    fixture.detectChanges();
    reservacionService.crear.and.returnValue(of({
      id: 9, usuarioId: 2, libroId: 5, estadoReservacionId: 1,
      fechaReserva: '2026-09-04T00:00:00-05:00', fechaLimiteRetiro: '2026-09-04T12:00:00-05:00'
    } as any));

    component.confirmarReserva();

    expect(confirmDialog.confirm).not.toHaveBeenCalled();
    expect(reservacionService.crear).toHaveBeenCalledWith(jasmine.objectContaining({ usuarioId: 2, libroId: 5 }));
  });

  it('reprograma para mañana y reserva al confirmar tras las 18:00', () => {
    clock.now.and.returnValue(TARDE_1930H);
    confirmDialog.confirm.and.returnValue(of(true));
    fixture.detectChanges();
    reservacionService.crear.and.returnValue(of({
      id: 9, usuarioId: 2, libroId: 5, estadoReservacionId: 1,
      fechaReserva: '2026-09-04T00:00:00-05:00', fechaLimiteRetiro: '2026-09-05T12:00:00-05:00'
    } as any));

    expect(component.fechaRetiro).toBe('2026-09-04');
    component.confirmarReserva();

    expect(confirmDialog.confirm).toHaveBeenCalled();
    expect(component.fechaRetiro).toBe('2026-09-05');
    expect(reservacionService.crear).toHaveBeenCalledWith(jasmine.objectContaining({
      usuarioId: 2, libroId: 5, fechaRetiro: jasmine.stringMatching(/^2026-09-05/)
    }));
    expect(component.reservaCreada?.fechaLimiteRetiro).toBe('2026-09-05T12:00:00-05:00');
    expect(reservacionesPendientes.marcarReservada).toHaveBeenCalledWith(5);
  });

  it('no reserva al cancelar el diálogo tras las 18:00', () => {
    clock.now.and.returnValue(TARDE_1930H);
    confirmDialog.confirm.and.returnValue(of(false));
    fixture.detectChanges();

    component.confirmarReserva();

    expect(confirmDialog.confirm).toHaveBeenCalled();
    expect(reservacionService.crear).not.toHaveBeenCalled();
    expect(component.reservaCreada).toBeNull();
    expect(component.fechaRetiro).toBe('2026-09-04');
  });

  it('requiere inicio de sesión para abrir el modal de reserva', () => {
    fixture.detectChanges();
    authService.getUserId.and.returnValue(null);

    component.abrirModalReserva();

    expect(component.errorMsg).toBe('Inicia sesión para reservar');
    expect(component.mostrarModalReserva).toBeFalse();
  });
});