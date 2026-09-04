import { Injectable } from '@angular/core';

// Reloj inyectable (OBS-20): única fuente de "ahora" para lógica dependiente
// de la hora (hora límite de retiro). En producción delega a Date real; en
// tests se stubbea now() con fechas fijas para ramas determinísticas sin
// tocar el reloj global.
@Injectable({
  providedIn: 'root'
})
export class ClockService {
  now(): Date {
    return new Date();
  }
}
