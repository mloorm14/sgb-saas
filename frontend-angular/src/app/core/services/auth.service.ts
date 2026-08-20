import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

interface JwtPayload {
  sub: string;
  correo: string;
  roles: string[];
  rol: string;
  jti: string;
  iat: number;
  exp: number;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = environment.apiUrl;

  // El token se guarda AQUI en memoria, nunca en localStorage
  private accessToken: string | null = null;

  constructor(private http: HttpClient, private router: Router) {}

  login(correo: string, password: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/login`, { correo, password }, { withCredentials: true }).pipe(
      tap(response => {
        this.accessToken = response.accessToken;
      })
    );
  }

  registro(nombre: string, apellido: string, correo: string, password: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/registro`, { nombre, apellido, correo, password });
  }

  verificarCorreo(correo: string, codigo: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/verificar-correo`, { correo, codigo });
  }

  logout(): void {
    // Llama al backend para agregar el JTI a la blacklist de Redis
    this.http.post(`${this.apiUrl}/auth/logout`, {}, { withCredentials: true }).subscribe({
      next: () => this.clearSession(),
      error: () => this.clearSession() // limpia igual aunque falle la llamada
    });
  }

  // Se llama desde jwt.interceptor.ts ANTES de desloguear en un 401
  // fuera de /auth/. El refreshToken viaja solo, el navegador la
  // adjunta desde la cookie HttpOnly gracias a withCredentials.
  refresh(): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/auth/refresh`, {}, { withCredentials: true }).pipe(
      tap(response => {
        this.accessToken = response.accessToken;
      })
    );
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

  // --- Lectura del payload del JWT (sub, roles) ---
  // No es una nueva fuente de verdad de autorización: el backend
  // siempre re-valida rol/permiso en cada request (JwtAuthFilter +
  // UserDetailsServiceImpl). Esto es solo para decidir qué botones
  // mostrar en el template, igual que LibrosComponent decide qué
  // acciones ofrecer.
  private decodePayload(): JwtPayload | null {
    if (!this.accessToken) return null;
    try {
      const base64 = this.accessToken.split('.')[1];
      const json = decodeURIComponent(
        atob(base64.replace(/-/g, '+').replace(/_/g, '/'))
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(json);
    } catch {
      return null;
    }
  }

  getUserId(): number | null {
    const payload = this.decodePayload();
    return payload ? Number(payload.sub) : null;
  }

  getRoles(): string[] {
    const payload = this.decodePayload();
    return payload?.roles ?? [];
  }

  hasRole(...roles: string[]): boolean {
    const misRoles = this.getRoles();
    return roles.some(r => misRoles.includes(r));
  }

  getCorreo(): string | null {
    const payload = this.decodePayload();
    return payload?.correo ?? null;
  }

  // Reusa decodePayload(): sin token o sin claim exp => no esta expirado
  // (el backend decidira); con exp pasado => la sesion caduco y el guard
  // no debe esperar el 403 del backend para desloguear.
  tokenExpirado(): boolean {
    const payload = this.decodePayload();
    if (!payload?.exp) return false;
    return payload.exp * 1000 <= Date.now();
  }
}