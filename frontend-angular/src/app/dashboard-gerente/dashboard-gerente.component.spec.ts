import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { DashboardGerenteComponent } from './dashboard-gerente.component';
import { ReporteService, LibroMasPrestado } from '../core/services/reporte-gerencial.service';
import { AuthService } from '../core/services/auth.service';

describe('DashboardGerenteComponent', () => {
  let component: DashboardGerenteComponent;
  let fixture: ComponentFixture<DashboardGerenteComponent>;
  let reporteService: jasmine.SpyObj<ReporteService>;

  const seisLibros: LibroMasPrestado[] = [1, 2, 3, 4, 5, 6].map(n => ({
    libroId: n,
    titulo: `Libro ${n}`,
    isbn: `ISBN-${n}`,
    totalPrestamos: 40 - n
  }));

  beforeEach(async () => {
    reporteService = jasmine.createSpyObj('ReporteService', ['librosMasPrestados']);

    await TestBed.configureTestingModule({
      imports: [DashboardGerenteComponent],
      providers: [
        { provide: ReporteService, useValue: reporteService },
        { provide: AuthService, useValue: { hasRole: () => true } },
        { provide: ActivatedRoute, useValue: { snapshot: {} } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardGerenteComponent);
    component = fixture.componentInstance;
  });

  it('carga el top 5 de libros más prestados desde ReporteService', () => {
    reporteService.librosMasPrestados.and.returnValue(of(seisLibros));

    fixture.detectChanges(); // ngOnInit

    expect(reporteService.librosMasPrestados).toHaveBeenCalled();
    expect(component.librosMasPrestados.length).toBe(5); // slice(0,5) igual que el mockup
    expect(component.librosMasPrestados[0].titulo).toBe('Libro 1');
    expect(component.cargando).toBeFalse();
    expect(component.error).toBe('');
  });

  it('muestra mensaje de error si el reporte falla y deja de cargar', () => {
    reporteService.librosMasPrestados.and.returnValue(throwError(() => ({ status: 403 })));

    fixture.detectChanges();

    expect(component.error).toContain('No se pudo cargar');
    expect(component.cargando).toBeFalse();
    expect(component.librosMasPrestados.length).toBe(0);
  });
});