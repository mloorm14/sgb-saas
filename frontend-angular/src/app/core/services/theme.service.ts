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

  private leerPreferenciaInicial(): boolean {
    const guardado = localStorage.getItem(STORAGE_KEY);
    if (guardado === 'dark') return true;
    if (guardado === 'light') return false;
    // Sin preferencia guardada: respetar prefers-color-scheme del SO.
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
  }

  toggle(): void {
    this.set(!this.oscuro());
  }

  set(oscuro: boolean): void {
    this.oscuro.set(oscuro);
    document.documentElement.classList.toggle('dark', oscuro);
    localStorage.setItem(STORAGE_KEY, oscuro ? 'dark' : 'light');
  }
}
