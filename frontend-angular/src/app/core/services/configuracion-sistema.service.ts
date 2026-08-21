import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

// Contrato de ConfiguracionSistemaController (/api/v1/configuracion),
// verificado en backend-springboot: solo ADMIN a nivel de clase.
export interface ParametroConfiguracion {
  clave: string;
  valor: string;
}

@Injectable({
  providedIn: 'root'
})
export class ConfiguracionSistemaService {

  private apiUrl = `${environment.apiUrl}/v1/configuracion`;

  constructor(private http: HttpClient) {}

  listar(): Observable<ParametroConfiguracion[]> {
    return this.http.get<ParametroConfiguracion[]>(this.apiUrl);
  }

  actualizar(clave: string, valor: string): Observable<ParametroConfiguracion> {
    return this.http.put<ParametroConfiguracion>(`${this.apiUrl}/${clave}`, { valor });
  }
}
