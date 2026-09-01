import { Directive, ElementRef, EventEmitter, OnInit, OnDestroy, AfterViewInit, Output } from '@angular/core';

const FOCUSABLE_SELECTOR = [
  'a[href]', 'button:not([disabled])', 'textarea:not([disabled])',
  'input:not([disabled])', 'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(',');

// Directiva reusable para modales (confirm-dialog, dropdowns tipo dialog,
// etc.): al aparecer, guarda el foco previo y lo mueve dentro del modal;
// mientras esta activa, Tab/Shift+Tab quedan atrapados dentro de los
// elementos enfocables del modal (no se escapan al contenido de atras);
// Escape emite (cerrar) para que el componente que la usa decida como
// cerrar. Al desaparecer (el host se destruye, p.ej. via @if), devuelve el
// foco al elemento que lo tenia antes de abrir el modal.
@Directive({
  selector: '[appFocusTrap]',
  standalone: true
})
export class FocusTrapDirective implements OnInit, AfterViewInit, OnDestroy {

  @Output() readonly cerrar = new EventEmitter<void>();

  private elementoPrevio: HTMLElement | null = null;
  private readonly listener = (e: KeyboardEvent) => this.onKeydown(e);

  constructor(private readonly host: ElementRef<HTMLElement>) {}

  ngOnInit(): void {
    document.addEventListener('keydown', this.listener, true);
  }

  ngAfterViewInit(): void {
    this.elementoPrevio = document.activeElement as HTMLElement | null;
    const primero = this.host.nativeElement.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
    // Si el modal no tiene ningun elemento enfocable, el contenedor mismo
    // recibe el foco (necesita tabindex="-1" en la plantilla que use esta
    // directiva) para que el foco no se quede "perdido" en el <body>.
    (primero ?? this.host.nativeElement).focus();
  }

  ngOnDestroy(): void {
    document.removeEventListener('keydown', this.listener, true);
    this.elementoPrevio?.focus?.();
  }

  private onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.stopPropagation();
      this.cerrar.emit();
      return;
    }
    if (event.key !== 'Tab') return;

    const focusables = Array.from(
      this.host.nativeElement.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
    ).filter(el => el.offsetParent !== null);
    if (focusables.length === 0) return;

    const primero = focusables.at(0)!;
    const ultimo = focusables.at(-1)!;
    const activo = document.activeElement;

    if (event.shiftKey && activo === primero) {
      event.preventDefault();
      ultimo.focus();
    } else if (!event.shiftKey && activo === ultimo) {
      event.preventDefault();
      primero.focus();
    }
  }
}
