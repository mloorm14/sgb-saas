import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { GestionSugerenciasComponent } from './gestion-sugerencias.component';
import { SugerenciaAdquisicionService } from '../../core/services/sugerencia-adquisicion.service';

// Gestión por demanda: gráfica top-8 + tabla paginada de mas-pedidos con
// botón Confirmar adquisición por ISBN (sin filtros ni estados).
describe('GestionSugerenciasComponent', () => {
  let component: GestionSugerenciasComponent;
  let fixture: ComponentFixture<GestionSugerenciasComponent>;
  let sugerenciaService: jasmine.SpyObj<SugerenciaAdquisicionService>;

  const pagina = {
    content: [
      { isbn: '9781449373320', titulo: 'Designing Data-Intensive Applications', autor: 'Martin Kleppmann', cantidad: 5 },
      { isbn: '9780134757599', titulo: 'Refactoring', autor: 'Martin Fowler', cantidad: 2 }
    ],
    totalPages: 1,
    totalElements: 2
  };

  beforeEach(async () => {
    sugerenciaService = jasmine.createSpyObj('SugerenciaAdquisicionService', [
      'listarMasPedidos', 'confirmarAdquisicion'
    ]);
    sugerenciaService.listarMasPedidos.and.returnValue(of(pagina as any));

    await TestBed.configureTestingModule({
      imports: [GestionSugerenciasComponent],
      providers: [
        { provide: SugerenciaAdquisicionService, useValue: sugerenciaService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(GestionSugerenciasComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('carga el agrupado paginado al iniciar', () => {
    expect(sugerenciaService.listarMasPedidos).toHaveBeenCalledWith(
      jasmine.objectContaining({ page: 0, size: 10 }));
    expect(component.masPedidos.length).toBe(2);
  });

  it('la gráfica muestra el top en % del máximo', () => {
    expect(component.chartItems.length).toBe(2);
    expect(component.maxCantidad).toBe(5);
    expect(component.alturaBarra(5)).toBe(100);
    expect(component.alturaBarra(2)).toBe(40);
  });

  it('confirma un ISBN y recarga la página', () => {
    sugerenciaService.confirmarAdquisicion.and.returnValue(of({ isbn: '9781449373320', confirmadas: 5 }));

    component.confirmarAdquisicion('9781449373320');

    expect(sugerenciaService.confirmarAdquisicion).toHaveBeenCalledWith('9781449373320');
    expect(sugerenciaService.listarMasPedidos).toHaveBeenCalledTimes(2);
    expect(component.confirmandoIsbn).toBeNull();
  });

  it('no confirma dos veces el mismo clic en vuelo', () => {
    component.confirmandoIsbn = '9781449373320';

    component.confirmarAdquisicion('9781449373320');

    expect(sugerenciaService.confirmarAdquisicion).not.toHaveBeenCalled();
  });

  it('muestra el detail del backend si confirmar falla', () => {
    sugerenciaService.confirmarAdquisicion.and.returnValue(
      throwError(() => ({ error: { detail: 'ISBN inválido' } }))
    );

    component.confirmarAdquisicion('9781449373320');

    expect(component.errorMsg).toBe('ISBN inválido');
    expect(component.confirmandoIsbn).toBeNull();
  });

  it('cambia de página y de tamaño recargando', () => {
    component.totalPages = 3;
    component.irAPagina(2);
    expect(component.currentPage).toBe(2);

    component.cambiarTamano(20);
    expect(component.pageSize).toBe(20);
    expect(component.currentPage).toBe(0);
    expect(sugerenciaService.listarMasPedidos).toHaveBeenCalledWith(
      jasmine.objectContaining({ size: 20, page: 0 }));
  });
});
