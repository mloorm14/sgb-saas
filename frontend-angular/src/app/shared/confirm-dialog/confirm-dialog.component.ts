import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConfirmDialogService, ConfirmOptions } from './confirm-dialog.service';
import { FocusTrapDirective } from '../focus-trap.directive';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, FocusTrapDirective],
  template: `
    @if (visible) {
      <div class="fixed inset-0 z-[10000] flex items-center justify-center p-md" (click)="onCancel()">
        <div class="absolute inset-0 bg-black/50"></div>
        <div role="dialog" aria-modal="true" [attr.aria-label]="options?.title ?? 'Confirmar'" tabindex="-1"
          appFocusTrap (cerrar)="onCancel()"
          class="relative z-10 max-w-md w-full bg-surface-container-lowest rounded-xl shadow-xl p-xl"
          (click)="$event.stopPropagation()">
          @if (options?.title) {
            <h3 class="font-headline-md text-headline-md text-on-background mb-sm">{{ options?.title }}</h3>
          }
          <p class="font-body-sm text-body-sm text-on-surface-variant mb-lg">{{ options?.message }}</p>
          <div class="flex justify-end gap-sm">
            <button (click)="onCancel()"
              class="h-9 px-md rounded-lg border border-outline-variant font-label-sm text-label-sm hover:bg-surface-container-low cursor-pointer">
              {{ options?.cancelText ?? 'Cancelar' }}
            </button>
            <button (click)="onAccept()"
              [class]="options?.variant === 'danger' ? 'h-9 px-md rounded-lg bg-error text-on-error font-label-sm text-label-sm hover:bg-error/80 cursor-pointer' : 'h-9 px-md rounded-lg bg-primary text-on-primary font-label-sm text-label-sm hover:bg-on-primary-fixed-variant cursor-pointer'">
              {{ options?.confirmText ?? 'Confirmar' }}
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class ConfirmDialogComponent {
  visible = false;
  options: ConfirmOptions | null = null;

  constructor(private service: ConfirmDialogService) {
    this.service.visible$.subscribe(v => this.visible = v);
    this.service.options$.subscribe(o => this.options = o);
  }

  onAccept(): void { this.service.accept(); }
  onCancel(): void { this.service.cancel(); }
}
