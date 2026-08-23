import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LoaderService {
  private contador = 0;
  private visible$ = new BehaviorSubject<boolean>(false);

  show(): void {
    this.contador++;
    if (this.contador === 1) {
      this.visible$.next(true);
    }
  }

  hide(): void {
    this.contador = Math.max(0, this.contador - 1);
    if (this.contador === 0) {
      this.visible$.next(false);
    }
  }

  isVisible() {
    return this.visible$.asObservable();
  }
}
