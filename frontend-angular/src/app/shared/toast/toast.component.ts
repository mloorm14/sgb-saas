import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (mensaje) {
      <div role="status" aria-live="polite" aria-atomic="true"
           class="fixed bottom-4 right-4 z-50 max-w-sm px-4 py-3 rounded-lg shadow-lg font-body-sm text-body-sm transition-all duration-300"
           [class]="tipo === 'exito' ? 'bg-success text-white' : tipo === 'error' ? 'bg-error text-white' : 'bg-surface-container text-on-surface border border-outline-variant'">
        {{ mensaje }}
      </div>
    }
  `
})
export class ToastComponent {
  @Input() mensaje = '';
  @Input() tipo: 'exito' | 'error' | 'info' = 'info';
}
