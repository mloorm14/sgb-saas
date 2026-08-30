import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router, NavigationStart, NavigationEnd, NavigationCancel, NavigationError } from '@angular/router';
import { ToastService } from '../../shared/toast/toast.service';

@Injectable({ providedIn: 'root' })
export class NavigationBlockingService {
  private timeoutId: ReturnType<typeof setTimeout> | null = null;
  private navigating = false;

  constructor(
    private router: Router,
    private toast: ToastService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    if (!isPlatformBrowser(this.platformId)) return;
    this.router.events.subscribe(event => {
      if (event instanceof NavigationStart) {
        this.navigating = true;
        // Usar loader existente: mostrar via document loader ya se activa por http, pero para navegacion sin http mostramos overlay manual
        // El loader global ya se muestra via interceptor http; para rutas con resolve que tardan, mantenemos flag
        if (this.timeoutId) clearTimeout(this.timeoutId);
        this.timeoutId = setTimeout(() => {
          if (this.navigating) {
            this.toast.warning('Carga demorada', 'Se demoro mucho al cargar los datos, intentalo de nuevo');
            // quedarse en vista actual: cancelar navegacion no es posible ya iniciada, pero el toast avisa y el router seguira intentando hasta resolver
            this.navigating = false;
          }
        }, 90000);
      } else if (event instanceof NavigationEnd || event instanceof NavigationCancel || event instanceof NavigationError) {
        this.navigating = false;
        if (this.timeoutId) { clearTimeout(this.timeoutId); this.timeoutId = null; }
      }
    });
  }
}
