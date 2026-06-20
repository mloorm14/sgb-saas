import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = 'http://localhost:8080/api';

  // El token se guarda AQUI en memoria, nunca en localStorage
  private accessToken: string | null = null;

  constructor(private http: HttpClient, private router: Router) {}

  login(correo: string, password: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/login`, { correo, password }).pipe(
      tap(response => {
        this.accessToken = response.accessToken;
      })
    );
  }

  registro(nombre: string, correo: string, password: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/registro`, { nombre, correo, password });
  }

  logout(): void {
    // Llama al backend para agregar el JTI a la blacklist de Redis
    this.http.post(`${this.apiUrl}/auth/logout`, {}).subscribe({
      next: () => this.clearSession(),
      error: () => this.clearSession() // limpia igual aunque falle la llamada
    });
  }

  private clearSession(): void {
    this.accessToken = null;
    this.router.navigate(['/login']);
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  isLoggedIn(): boolean {
    return this.accessToken !== null;
  }
}