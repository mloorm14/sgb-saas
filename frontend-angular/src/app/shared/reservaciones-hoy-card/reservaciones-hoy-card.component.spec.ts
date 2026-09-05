import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReservacionesHoyCardComponent } from './reservaciones-hoy-card.component';
import { ReservacionHoy } from '../../core/models/reservacion.model';

describe('ReservacionesHoyCardComponent', () => {
  let component: ReservacionesHoyCardComponent;
  let fixture: ComponentFixture<ReservacionesHoyCardComponent>;

  const reserva: ReservacionHoy = {
    reservacionId: 1,
    usuarioNombre: 'Ana Garcia',
    usuarioCorreo: 'ana@test.com',
    libroTitulo: 'El Principito',
    estadoNombre: 'PENDIENTE',
    fechaLimiteRetiro: '2026-08-24T23:59:59Z'
  };

  async function crear() {
    await TestBed.configureTestingModule({
      imports: [ReservacionesHoyCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ReservacionesHoyCardComponent);
    component = fixture.componentInstance;
  }

  it('debería crear el componente y mostrar estado de carga', async () => {
    await crear();
    fixture.componentRef.setInput('cargando', true);
    fixture.detectChanges();

    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.animate-pulse')).not.toBeNull();
  });

  it('debería mostrar mensaje de error cuando hay error', async () => {
    await crear();
    fixture.componentRef.setInput('error', 'Fallo la carga');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Fallo la carga');
  });

  it('debería listar reservaciones y emitir el id al marcar lista', async () => {
    await crear();
    fixture.componentRef.setInput('reservaciones', [reserva]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('El Principito');

    let emitido = -1;
    component.marcarListaParaRetiro.subscribe(id => { emitido = id; });
    (compiled.querySelector('button') as HTMLButtonElement).click();
    expect(emitido).toBe(1);
  });

  it('debería mostrar estado vacío sin reservaciones', async () => {
    await crear();
    fixture.componentRef.setInput('reservaciones', []);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No hay reservaciones para hoy');
  });
});
