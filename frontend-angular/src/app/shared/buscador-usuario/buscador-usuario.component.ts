import { Component, EventEmitter, OnDestroy, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { PrestamoService } from '../../core/services/prestamo.service';
import { UsuarioSugerencia } from '../../core/models/prestamos-gestion.model';

// Buscador predictivo de usuarios por correo para la ventanilla de
// préstamos. Patrón igual al BuscadorLibroComponent: Subject con
// debounceTime(300) + distinctUntilChanged → API de sugerencias.
// Emite el correo seleccionado (click o Tab) y muestra el placeholder
// dinámico con la sugerencia más probable.
@Component({
  selector: 'app-buscador-usuario',
  imports: [CommonModule, FormsModule],
  templateUrl: './buscador-usuario.component.html'
})
export class BuscadorUsuarioComponent implements OnDestroy {

  @Output() correoSeleccionado = new EventEmitter<string>();
  @Output() buscarAhora = new EventEmitter<string>();

  texto: string = '';
  sugerencias: UsuarioSugerencia[] = [];
  buscando: boolean = false;
  mostrarSugerencias: boolean = false;
  placeholderDinamico: string = 'Ingresa el correo del usuario';
  sugerenciaActiva: UsuarioSugerencia | null = null;
  indiceActivo: number = -1;

  private busqueda$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  constructor(private prestamoService: PrestamoService) {
    this.busqueda$.pipe(
      debounceTime(1300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(texto => this.buscarSugerencias(texto));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.busqueda$.complete();
  }

  onInput(): void {
    const texto = this.texto.trim();
    this.indiceActivo = -1;

    if (texto.length < 2) {
      this.sugerencias = [];
      this.mostrarSugerencias = false;
      this.placeholderDinamico = 'Ingresa el correo del usuario';
      this.sugerenciaActiva = null;
      return;
    }

    this.busqueda$.next(texto);
  }

  onKeydown(event: KeyboardEvent): void {
    if (!this.mostrarSugerencias || this.sugerencias.length === 0) {
      if (event.key === 'Enter') {
        this.buscarAhora.emit(this.texto.trim());
      }
      return;
    }

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.indiceActivo = Math.min(this.indiceActivo + 1, this.sugerencias.length - 1);
        this.actualizarPlaceholder();
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.indiceActivo = Math.max(this.indiceActivo - 1, -1);
        this.actualizarPlaceholder();
        break;
      case 'Tab':
        event.preventDefault();
        if (this.indiceActivo >= 0 && this.indiceActivo < this.sugerencias.length) {
          this.seleccionar(this.sugerencias[this.indiceActivo]);
        } else if (this.sugerencias.length > 0) {
          this.seleccionar(this.sugerencias[0]);
        }
        break;
      case 'Enter':
        event.preventDefault();
        if (this.indiceActivo >= 0 && this.indiceActivo < this.sugerencias.length) {
          this.seleccionar(this.sugerencias[this.indiceActivo]);
        } else {
          this.buscarAhora.emit(this.texto.trim());
        }
        break;
      case 'Escape':
        this.mostrarSugerencias = false;
        this.indiceActivo = -1;
        break;
    }
  }

  onFocus(): void {
    if (this.sugerencias.length > 0 && this.texto.trim().length >= 2) {
      this.mostrarSugerencias = true;
    }
  }

  onBlur(): void {
    // Delay para permitir el click en la sugerencia antes de cerrar.
    setTimeout(() => {
      this.mostrarSugerencias = false;
      this.indiceActivo = -1;
    }, 200);
  }

  private buscarSugerencias(texto: string): void {
    if (texto.length < 2) {
      this.sugerencias = [];
      this.mostrarSugerencias = false;
      this.placeholderDinamico = 'Ingresa el correo del usuario';
      this.sugerenciaActiva = null;
      return;
    }
    this.buscando = true;
    this.prestamoService.sugerenciasUsuarios(texto).subscribe({
      next: (lista) => {
        this.sugerencias = lista;
        this.mostrarSugerencias = lista.length > 0;
        this.buscando = false;
        this.actualizarPlaceholder();
      },
      error: () => {
        this.sugerencias = [];
        this.mostrarSugerencias = false;
        this.buscando = false;
        this.placeholderDinamico = 'Ingresa el correo del usuario';
        this.sugerenciaActiva = null;
      }
    });
  }

  private actualizarPlaceholder(): void {
    if (this.indiceActivo >= 0 && this.indiceActivo < this.sugerencias.length) {
      this.sugerenciaActiva = this.sugerencias[this.indiceActivo];
      this.placeholderDinamico = this.sugerencias[this.indiceActivo].correo;
    } else if (this.sugerencias.length > 0) {
      this.sugerenciaActiva = this.sugerencias[0];
      this.placeholderDinamico = this.sugerencias[0].correo;
    } else {
      this.sugerenciaActiva = null;
      this.placeholderDinamico = 'Ingresa el correo del usuario';
    }
  }

  seleccionar(sugerencia: UsuarioSugerencia): void {
    this.texto = sugerencia.correo;
    this.sugerencias = [];
    this.mostrarSugerencias = false;
    this.placeholderDinamico = sugerencia.correo;
    this.sugerenciaActiva = sugerencia;
    this.correoSeleccionado.emit(sugerencia.correo);
  }

  limpiar(): void {
    this.texto = '';
    this.sugerencias = [];
    this.mostrarSugerencias = false;
    this.placeholderDinamico = 'Ingresa el correo del usuario';
    this.sugerenciaActiva = null;
    this.indiceActivo = -1;
  }

  claseEstado(estado: string): string {
    switch (estado) {
      case 'ACTIVO': return 'bg-success text-white';
      case 'BLOQUEADO_POR_MULTA': return 'bg-error text-on-error';
      case 'PENDIENTE_VERIFICACION': return 'bg-warning text-tertiary';
      default: return 'bg-surface-container-highest text-on-surface-variant';
    }
  }

  etiquetaEstado(estado: string): string {
    switch (estado) {
      case 'ACTIVO': return 'Activo';
      case 'BLOQUEADO_POR_MULTA': return 'Suspendido';
      case 'PENDIENTE_VERIFICACION': return 'Pendiente de verificación';
      case 'INACTIVO': return 'Inactivo';
      default: return estado;
    }
  }

  getIniciales(nombre: string): string {
    return nombre.split(' ').map(p => p.charAt(0)).join('').substring(0, 2).toUpperCase();
  }
}
