import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardBibliotecarioComponent } from './dashboard-bibliotecario.component';

describe('DashboardBibliotecarioComponent', () => {
  let component: DashboardBibliotecarioComponent;
  let fixture: ComponentFixture<DashboardBibliotecarioComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardBibliotecarioComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardBibliotecarioComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('crea el componente', () => {
    expect(component).toBeTruthy();
  });

  it('muestra los accesos rápidos del bibliotecario', () => {
    const links = fixture.nativeElement.querySelectorAll('a[routerLink]');
    const rutas = Array.from(links).map((el: Element) => el.getAttribute('routerLink'));
    expect(rutas).toContain('/prestamos/gestion');
    expect(rutas).toContain('/reportes');
    expect(rutas).toContain('/reservaciones');
    expect(rutas).toContain('/multas');
    expect(rutas).toContain('/libros');
  });

  it('muestra el título de bienvenida', () => {
    const titulo = fixture.nativeElement.querySelector('h1');
    expect(titulo?.textContent).toContain('Bienvenida, Biblioteca');
  });
});
