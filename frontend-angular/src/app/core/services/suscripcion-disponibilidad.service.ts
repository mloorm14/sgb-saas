import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SuscripcionDisponibilidadService {
  private apiUrl = `${environment.apiUrl}/v1/libros`;
  constructor(private http: HttpClient) {}

  suscribir(libroId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${libroId}/suscripciones`, {});
  }

  desuscribir(libroId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${libroId}/suscripciones`);
  }

  misSuscripciones(): Observable<number[]> {
    return this.http.get<number[]>(`${this.apiUrl}/suscripciones/mias`);
  }
}
