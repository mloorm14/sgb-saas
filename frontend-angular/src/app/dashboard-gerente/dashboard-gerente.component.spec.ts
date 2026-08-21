import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { DashboardGerenteComponent } from './dashboard-gerente.component';
import { ReporteService, LibroMasPrestado, ReporteMorosidad } from '../core/services/reporte-gerencial.service';
import { AuthService } from '../core/services/auth.service';
import { MultaService } from '../core/services/multa.service';
import { ResumenFinancieroMultas } from '../core/models/multa.model';
import { AuditoriaService } from '../core/services/auditoria.service';
import { Page } from '../core/models/pagina.model';
import { EventoAuditoria } from '../core/models/evento-auditoria.model';

describe('DashboardGerenteComponent', () => {
  let component: DashboardGerenteComponent;
  let fixture: ComponentFixture<DashboardGerenteComponent>;
  let reporteService: jasmine.SpyObj<ReporteService>;
  let multaService: jasmine.SpyObj<MultaService>;
  let auditoriaService: jasmine.SpyObj<AuditoriaService>;

  const seisLibros: LibroMasPrestado[] = [1, 2, 3, 4, 5, 6].map(n => ({
    libroId: n,
    titulo: `Libro ${n}`,
    isbn: `ISBN-${n}`,
    totalPrestamos: 40 - n
  }));

  const resumenFinanciero: ResumenFinancieroMultas = {
    totalRecaudado: 125.5,
    totalPendiente: 40
  };

  const dosMorosos: ReporteMorosidad[] = [
    { usuarioId: 1, nombre: 'Ana', apellido: 'Pérez', correo: 'ana@uteq.edu.ec', montoTotalAdeudado: 15.5, cantidadMultasPendientes: 1, diasAtrasoPromedio: 3 },
    { usuarioId: 2, nombre: 'Luis', apellido: 'Gómez', correo: 'luis@uteq.edu.ec', montoTotalAdeudado: 24.5, cantidadMultasPendientes: 2, diasAtrasoPromedio: 5 }
  ];

  const paginaAuditoria: Page<EventoAuditoria> = {
    content: [
      { id: 1, usuario: 'admin@uteq.edu.ec', accion: 'UPDATE', fechaHora: '2026-08-19T10:00:00Z', modulo: 'usuarios', detalle: 'ejemplo' }
    ],
    totalPages: 1,
    totalElements: 1,
    size: 5,
    number: 0,
    numberOfElements: 1,
    empty: false
  };

  beforeEach(async () => {
    reporteService = jasmine.createSpyObj('ReporteService', ['librosMasPrestados', 'morosidad']);
    multaService = jasmine.createSpyObj('MultaService', ['resumenFinanciero']);
    auditoriaService = jasmine.createSpyObj('AuditoriaService', ['listar']);

    // Valores por defecto (happy path): los tests de error los sobrescriben.
    reporteService.librosMasPrestados.and.returnValue(of(seisLibros));
    reporteService.morosidad.and.returnValue(of(dosMorosos));
    multaService.resumenFinanciero.and.returnValue(of(resumenFinanciero));
    auditoriaService.listar.and.returnValue(of(paginaAuditoria));

    await TestBed.configureTestingModule({
      imports: [DashboardGerenteComponent],
      providers: [
        { provide: ReporteService, useValue: reporteService },
        { provide: AuthService, useValue: { hasRole: () => true } },
        { provide: MultaService, useValue: multaService },
        { provide: AuditoriaService, useValue: auditoriaService },
        { provide: ActivatedRoute, useValue: { snapshot: {} } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardGerenteComponent);
    component = fixture.componentInstance;
  });

  it('carga el top 5 de libros más prestados desde ReporteService', () => {
    fixture.detectChanges(); // ngOnInit

    expect(reporteService.librosMasPrestados).toHaveBeenCalled();
    expect(component.librosMasPrestados.length).toBe(5); // slice(0,5) igual que el mockup
    expect(component.librosMasPrestados[0].titulo).toBe('Libro 1');
    expect(component.cargando).toBeFalse();
    expect(component.error).toBe('');
  });

  it('muestra mensaje de error si el reporte de libros falla y deja de cargar', () => {
    reporteService.librosMasPrestados.and.returnValue(throwError(() => ({ status: 403 })));

    fixture.detectChanges();

    expect(component.error).toContain('No se pudo cargar');
    expect(component.cargando).toBeFalse();
    expect(component.librosMasPrestados.length).toBe(0);
  });

  it('carga el resumen financiero desde MultaService', () => {
    fixture.detectChanges();

    expect(multaService.resumenFinanciero).toHaveBeenCalled();
    expect(component.resumenFinanciero).toEqual(resumenFinanciero);
    expect(component.cargandoFinanciero).toBeFalse();
    expect(component.errorFinanciero).toBe('');
  });

  it('muestra mensaje de error si el resumen financiero falla', () => {
    multaService.resumenFinanciero.and.returnValue(throwError(() => ({ status: 403 })));

    fixture.detectChanges();

    expect(component.errorFinanciero).toContain('No se pudo cargar');
    expect(component.cargandoFinanciero).toBeFalse();
    expect(component.resumenFinanciero).toBeNull();
  });

  it('carga la morosidad y deriva cantidad + monto total adeudado (no un porcentaje)', () => {
    fixture.detectChanges();

    expect(reporteService.morosidad).toHaveBeenCalled();
    expect(component.usuariosEnMora.length).toBe(2);
    expect(component.montoTotalAdeudado).toBe(40); // 15.5 + 24.5
    expect(component.cargandoMorosidad).toBeFalse();
  });

  it('muestra mensaje de error si el reporte de morosidad falla', () => {
    reporteService.morosidad.and.returnValue(throwError(() => ({ status: 403 })));

    fixture.detectChanges();

    expect(component.errorMorosidad).toContain('No se pudo cargar');
    expect(component.cargandoMorosidad).toBeFalse();
    expect(component.usuariosEnMora.length).toBe(0);
  });

  it('carga los últimos 5 eventos de auditoría desde AuditoriaService', () => {
    fixture.detectChanges();

    expect(auditoriaService.listar).toHaveBeenCalledWith({ page: 0, size: 5 });
    expect(component.eventosAuditoria.length).toBe(1);
    expect(component.eventosAuditoria[0].accion).toBe('UPDATE');
    expect(component.cargandoAuditoria).toBeFalse();
  });

  it('muestra mensaje de error si la auditoría reciente falla', () => {
    auditoriaService.listar.and.returnValue(throwError(() => ({ status: 403 })));

    fixture.detectChanges();

    expect(component.errorAuditoria).toContain('No se pudo cargar');
    expect(component.cargandoAuditoria).toBeFalse();
    expect(component.eventosAuditoria.length).toBe(0);
  });

  it('ADMIN no dispara reportes de libros/morosidad (endpoints excluyen su rol)', () => {
    // Re-mock de AuthService: solo GERENTE es true, ADMIN es false.
    // ADMIN aun asi ve resumen financiero y auditoría.
    const adminAuth = { hasRole: (...roles: string[]) => false };
    TestBed.resetTestingModule();
    reporteService = jasmine.createSpyObj('ReporteService', ['librosMasPrestados', 'morosidad']);
    multaService = jasmine.createSpyObj('MultaService', ['resumenFinanciero']);
    auditoriaService = jasmine.createSpyObj('AuditoriaService', ['listar']);
    reporteService.librosMasPrestados.and.returnValue(of(seisLibros));
    reporteService.morosidad.and.returnValue(of(dosMorosos));
    multaService.resumenFinanciero.and.returnValue(of(resumenFinanciero));
    auditoriaService.listar.and.returnValue(of(paginaAuditoria));

    TestBed.configureTestingModule({
      imports: [DashboardGerenteComponent],
      providers: [
        { provide: ReporteService, useValue: reporteService },
        { provide: AuthService, useValue: adminAuth },
        { provide: MultaService, useValue: multaService },
        { provide: AuditoriaService, useValue: auditoriaService },
        { provide: ActivatedRoute, useValue: { snapshot: {} } }
      ]
    });

    fixture = TestBed.createComponent(DashboardGerenteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    // No se disparan los endpoints que ADMIN no tiene:
    expect(reporteService.librosMasPrestados).not.toHaveBeenCalled();
    expect(reporteService.morosidad).not.toHaveBeenCalled();
    // ...pero sí los que admiten ADMIN:
    expect(multaService.resumenFinanciero).toHaveBeenCalled();
    expect(auditoriaService.listar).toHaveBeenCalled();
    // El titulo se adapta al rol:
    expect(component.tituloBienvenida).toBe('Bienvenida, Administración');
    expect(component.esGerente).toBeFalse();
  });
});