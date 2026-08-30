import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

@Injectable({ providedIn: 'root' })
export class ToastService {
  private toast: Toast | null = null;

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {}

  private ensure(): Toast | null {
    if (!isPlatformBrowser(this.platformId)) return null;
    if (!this.toast) {
      this.toast = new Toast('tr', 4);
    }
    return this.toast;
  }

  success(title: string, msg: string, duration = 4000): void {
    this.ensure()?.show('success', title, msg, duration);
  }

  error(title: string, msg: string, duration = 5000): void {
    this.ensure()?.show('error', title, msg, duration);
  }

  warning(title: string, msg: string, duration = 5000): void {
    this.ensure()?.show('warning', title, msg, duration);
  }

  info(title: string, msg: string, duration = 4000): void {
    this.ensure()?.show('info', title, msg, duration);
  }
}

class Toast {
  maxStack: number;
  container: HTMLElement;

  constructor(pos = 'tr', maxStack = 3) {
    this.maxStack = maxStack;
    const existing = document.querySelector(`.toast-container[data-position="${pos}"]`) as HTMLElement | null;
    if (existing) {
      this.container = existing;
    } else {
      this.container = document.createElement('div');
      this.container.className = 'toast-container';
      (this.container.dataset as any)['position'] = pos;
      document.body.appendChild(this.container);
    }
  }

  show(type: string, title: string, msg: string, duration = 4000): void {
    const activeToasts = this.container.querySelectorAll('.toast');
    if (activeToasts.length >= this.maxStack) {
      activeToasts[0].remove();
    }
    const t = document.createElement('div');
    t.className = `toast style-solid toast-${type}`;
    t.innerHTML = `
      <div class="toast-icon">${this.getIcon(type)}</div>
      <div class="toast-content"><b>${title}</b><div>${msg}</div></div>
      <button class="toast-close">&times;</button>
      <div class="toast-progress"></div>`;
    const animMode: string = 'slide';
    const baseEntry = 'slideInRight';
    if ((animMode as string) === 'zoom') {
      t.style.animation = 'zoomIn 0.4s forwards';
    } else if ((animMode as string) === 'shake') {
      t.style.animation = `${baseEntry} 0.4s forwards, shake 0.4s 0.4s`;
    } else {
      t.style.animation = `${baseEntry} 0.4s forwards`;
    }
    this.container.appendChild(t);
    const currentPos = ((this.container.dataset as any)['position'] as string) ?? 'tr';
    let animOut = currentPos.includes('r') ? 'slideOutRight' : 'slideOutLeft';
    if (currentPos === 'tc') animOut = 'slideOutUp';
    if (currentPos === 'bc') animOut = 'slideOutDown';
    const bar = t.querySelector('.toast-progress') as HTMLElement | null;
    if (bar) {
      bar.style.transform = 'scaleX(1)';
      setTimeout(() => {
        bar.style.transition = `transform ${duration}ms linear`;
        bar.style.transform = 'scaleX(0)';
      }, 50);
    }
    const dismiss = () => {
      t.style.animation = `${animOut} 0.3s forwards`;
      t.addEventListener('animationend', () => t.remove());
    };
    const closeBtn = t.querySelector('.toast-close') as HTMLElement | null;
    if (closeBtn) closeBtn.onclick = dismiss;
    setTimeout(dismiss, duration);
  }

  getIcon(type: string): string {
    const icons: Record<string, string> = {
      success: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>',
      error: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/></svg>',
      warning: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>',
      info: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>'
    };
    return icons[type] ?? '';
  }
}
