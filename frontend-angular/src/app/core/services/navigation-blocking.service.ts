import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router, NavigationStart, NavigationEnd, NavigationCancel, NavigationError } from '@angular/router';
import { ToastService } from '../../shared/toast/toast.service';

@Injectable({ providedIn: 'root' })
export class NavigationBlockingService {
  private timeoutId: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private router: Router,
    private toast: ToastService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    if (!isPlatformBrowser(this.platformId)) return;
    this.router.events.subscribe(event => {
      if (event instanceof NavigationStart) {
        if (this.timeoutId) clearTimeout(this.timeoutId);
        this.timeoutId = setTimeout(() => {
          this.toast.warning('Carga demorada', 'Se demoro mucho al cargar los datos, intentalo de nuevo');
        }, 90000);
      } else if (event instanceof NavigationEnd || event instanceof NavigationCancel || event instanceof NavigationError) {
        if (this.timeoutId) { clearTimeout(this.timeoutId); this.timeoutId = null; }
      }
    });
  }
}
