import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChatbotRespuesta } from '../models/chatbot.model';
import { ProblemDetail } from '../models/problem-detail.model';

interface ChatHistorialEntry {
  rol: string;
  contenido: string;
  creadoEn: string;
}

@Injectable({ providedIn: 'root' })
export class ChatbotService {

  private apiUrl = `${environment.apiUrl}/v1/chatbot`;

  constructor(private http: HttpClient) {}

  enviarMensaje(sesionId: string | null, texto: string): Observable<ChatbotRespuesta> {
    return this.http.post<ChatbotRespuesta>(`${this.apiUrl}/mensajes`, { sesionId, texto }).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  historial(sesionId: string): Observable<ChatHistorialEntry[]> {
    return this.http.get<ChatHistorialEntry[]>(`${this.apiUrl}/sesiones/${sesionId}/historial`).pipe(
      catchError(err => this.manejarError(err))
    );
  }

  private manejarError(err: unknown): Observable<never> {
    const problem = (err as { error?: ProblemDetail })?.error;
    if (problem?.status) {
      console.warn(`[chatbot.service] ${problem.title} (${problem.status}): ${problem.detail}`);
    }
    return throwError(() => err);
  }
}
