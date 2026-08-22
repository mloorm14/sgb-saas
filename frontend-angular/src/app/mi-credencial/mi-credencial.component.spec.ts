import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MiCredencialComponent } from './mi-credencial.component';
import { CredencialQrService } from '../core/services/credencial-qr.service';
import { AuthService } from '../core/services/auth.service';

describe('MiCredencialComponent', () => {
  let component: MiCredencialComponent;
  let fixture: ComponentFixture<MiCredencialComponent>;
  let credencialService: jasmine.SpyObj<CredencialQrService>;

  beforeEach(async () => {
    credencialService = jasmine.createSpyObj('CredencialQrService', ['obtenerImagen']);
    credencialService.obtenerImagen.and.returnValue(of(new Blob(['fake'], { type: 'image/png' })));

    await TestBed.configureTestingModule({
      imports: [MiCredencialComponent],
      providers: [
        { provide: CredencialQrService, useValue: credencialService },
        { provide: AuthService, useValue: { getCorreo: () => 'ana@uteq.edu.ec' } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MiCredencialComponent);
    component = fixture.componentInstance;
  });

  it('carga la imagen de la credencial y genera el objectUrl', () => {
    fixture.detectChanges();

    expect(credencialService.obtenerImagen).toHaveBeenCalled();
    expect(component.objectUrl).toBeTruthy();
    expect(component.cargando).toBeFalse();
    expect(component.error).toBe('');
  });

  it('muestra error cuando el servicio falla', () => {
    credencialService.obtenerImagen.and.returnValue(throwError(() => ({ status: 500 })));

    fixture.detectChanges();

    expect(component.error).toContain('No se pudo cargar');
    expect(component.objectUrl).toBeNull();
    expect(component.cargando).toBeFalse();
  });

  it('revoca el objectUrl al destruir el componente', () => {
    fixture.detectChanges();
    const url = component.objectUrl;
    expect(url).toBeTruthy();

    component.ngOnDestroy();
    expect(component.objectUrl).toBe(url);
  });
});
