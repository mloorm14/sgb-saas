import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditoriaService } from '../../core/services/auditoria.service';
import { EventoAuditoria } from '../../core/models/evento-auditoria.model';

// Valores posibles de tablaAfectada (bitacora_auditoria). Hoy solo
// "usuarios" se escribe de verdad (AuthService y UsuarioAdminService);
// el resto son valores que el filtro ?modulo= del backend acepta
// (String libre) y que el mockup 21 lista para la operación diaria.
const MODULOS = ['usuarios', 'prestamos', 'libros', 'multas', 'sugerencias_adquisicion'];

@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auditoria.component.html'
})
export class AuditoriaComponent implements OnInit {
  modulos = MODULOS;

  filtroUsuarioId: string = '';
  filtroModulo: string = '';
  filtroDesde: string = '';
  filtroHasta: string = '';

  eventos: EventoAuditoria[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 20;
  cargando: boolean = false;
  errorMsg: string = '';

  constructor(private auditoriaService: AuditoriaService) {}

  ngOnInit(): void {
    this.cargarPagina();
  }

  get paginasVisibles(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  // type="date" da "yyyy-MM-dd"; el backend pide OffsetDateTime ISO, así
  // que se convierte a rango de ese día en UTC (offset Z explícito).
  private fechaInicio(fecha: string): string | undefined {
    return fecha ? `${fecha}T00:00:00.000Z` : undefined;
  }

  private fechaFin(fecha: string): string | undefined {
    return fecha ? `${fecha}T23:59:59.999Z` : undefined;
  }

  private usuarioId(): number | null {
    const valor = this.filtroUsuarioId.trim();
    if (!valor) return null;
    const id = Number(valor);
    return Number.isInteger(id) && id > 0 ? id : null;
  }

  filtrar(): void {
    this.currentPage = 0;
    this.cargarPagina();
  }

  // Se llama desde el template (paginacion numerada) -> no private.
  cargarPagina(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.auditoriaService.listar({
      usuarioId: this.usuarioId(),
      modulo: this.filtroModulo || undefined,
      desde: this.fechaInicio(this.filtroDesde),
      hasta: this.fechaFin(this.filtroHasta),
      page: this.currentPage,
      size: this.pageSize
    }).subscribe({
      next: (data) => {
        this.eventos = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: (err) => {
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al cargar la bitácora de auditoría';
        this.cargando = false;
      }
    });
  }

  paginaAnterior(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.cargarPagina();
    }
  }

  paginaSiguiente(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.cargarPagina();
    }
  }

  // El DTO puede traer usuario null (EventoAuditoriaResponseDTO) -- la
  // vista lo muestra como "—", no como texto vacío ni como error.
  usuarioLabel(evento: EventoAuditoria): string {
    return evento.usuario ?? '—';
  }

  formatoFecha(iso: string): string {
    const fecha = new Date(iso);
    return fecha.toLocaleString('es-ES', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }

  // Semáforo de acciones del mockup 21: INSERT/LOGIN_OK en verde,
  // DELETE/LOGIN_FAIL en rojo, UPDATE/LOGOUT en gris.
  claseAccion(accion: string): string {
    switch (accion) {
      case 'INSERT':
      case 'LOGIN_OK':
        return 'bg-success/15 text-success';
      case 'DELETE':
      case 'LOGIN_FAIL':
        return 'bg-error-container text-on-error-container';
      case 'UPDATE':
        return 'bg-surface-variant/50 text-primary';
      default:
        return 'bg-surface-variant/50 text-on-surface-variant';
    }
  }

  iconoAccion(accion: string): string {
    switch (accion) {
      case 'INSERT': return 'add_circle';
      case 'UPDATE': return 'edit';
      case 'DELETE': return 'delete';
      case 'LOGIN_OK': return 'check_circle';
      case 'LOGIN_FAIL': return 'error';
      case 'LOGOUT': return 'logout';
      case 'CORREO_VERIFICADO': return 'verified';
      default: return 'event_note';
    }
  }
}