import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'theme:mode';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {

  // true = modo oscuro activo. Inicializado de forma sincrona en la
  // declaracion del campo (no en un ngOnInit) para que el estado ya sea
  // correcto en el primer render de Angular -- el script inline de
  // index.html ya aplico la clase 'dark' al <html> antes de que Angular
  // arranque, esto solo sincroniza el estado del servicio con lo que ese
  // script ya decidio (misma logica, ver comentario ahi).
  oscuro = signal<boolean>(this.leerPreferenciaInicial());

  constructor() {
    // Sincroniza la clase .dark del <html> con el signal al iniciar.
    // El script inline de index.html ya la aplicó, pero si hay mismatch
    // (ej. SSR, storage cambiado entre tabs) esto lo corrige sin flash.
    try {
      if (typeof document !== 'undefined') {
        document.documentElement.classList.toggle('dark', this.oscuro());
      }
    } catch {}
  }

  private leerPreferenciaInicial(): boolean {
    try {
      if (typeof localStorage !== 'undefined') {
        const guardado = localStorage.getItem(STORAGE_KEY);
        if (guardado === 'dark') return true;
        if (guardado === 'light') return false;
      }
      if (typeof window !== 'undefined' && window.matchMedia) {
        return window.matchMedia('(prefers-color-scheme: dark)').matches;
      }
    } catch {}
    return false;
  }

  toggle(): void {
    this.set(!this.oscuro());
  }

  set(oscuro: boolean): void {
    this.oscuro.set(oscuro);
    try {
      if (typeof document !== 'undefined') {
        document.documentElement.classList.toggle('dark', oscuro);
      }
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(STORAGE_KEY, oscuro ? 'dark' : 'light');
      }
    } catch {}
  }
}
