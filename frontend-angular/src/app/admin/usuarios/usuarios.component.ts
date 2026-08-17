import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { UsuarioAdminService } from '../../core/services/usuario-admin.service';
import { UsuarioAdmin } from '../../core/models/usuario-admin.model';

// Catálogo real de roles y estados_usuario (db/seed.sql). El backend no
// valida estos valores contra un enum en el DTO (el catálogo vive en las
// tablas roles/estados_usuario), así que se listan acá a mano para los
// <select>; si el catálogo cambia en la base, estos arreglos también hay
// que actualizarlos.
const ROLES_DISPONIBLES = ['LECTOR', 'BIBLIOTECARIO', 'GERENTE', 'ADMIN'];

const ESTADO_LABEL: Record<string, string> = {
  ACTIVO: 'Activo',
  BLOQUEADO_POR_MULTA: 'Bloqueado por multa',
  INACTIVO: 'Inactivo',
  PENDIENTE_VERIFICACION: 'Pendiente de verificación'
};

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './usuarios.component.html'
})
export class UsuariosComponent implements OnInit {
  rolesDisponibles = ROLES_DISPONIBLES;

  // ADMIN o GERENTE: ven el listado; solo ADMIN puede cambiar rol/estado
  // (refleja el @PreAuthorize real de UsuarioAdminController).
  puedeVer: boolean = false;
  puedeGestionar: boolean = false;

  filtro: string = '';
  usuarios: UsuarioAdmin[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';
  mensajeOk: string = '';

  // Modal de motivo para cambio de estado (motivo @NotBlank del backend)
  mostrarModalEstado: boolean = false;
  usuarioAccion: UsuarioAdmin | null = null;
  nuevoEstado: string = '';
  motivoEstado: string = '';
  enviandoEstado: boolean = false;
  errorModal: string = '';

  constructor(
    private usuarioAdminService: UsuarioAdminService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.puedeVer = this.authService.hasRole('ADMIN', 'GERENTE');
    this.puedeGestionar = this.authService.hasRole('ADMIN');
    if (this.puedeVer) {
      this.cargarPagina();
    }
  }

  get paginasVisibles(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }

  buscarUsuarios(): void {
    this.currentPage = 0;
    this.cargarPagina();
  }

  // Se llama desde el template (paginacion numerada) -> no private.
  cargarPagina(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.usuarioAdminService.listar(this.filtro, this.currentPage, this.pageSize).subscribe({
      next: (data) => {
        this.usuarios = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: (err) => {
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al cargar el listado de usuarios';
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

  etiquetaEstado(estado: string): string {
    return ESTADO_LABEL[estado] ?? estado;
  }

  // Acción contextual por estado (mockup 20): ACTIVO -> bloquear (INACTIVO),
  // INACTIVO -> activar (ACTIVO). BLOQUEADO_POR_MULTA es automático (no se
  // puede cambiar a mano) y PENDIENTE_VERIFICACION no tiene acción.
  esBloqueable(usuario: UsuarioAdmin): boolean {
    return usuario.estado === 'ACTIVO';
  }

  esActivatable(usuario: UsuarioAdmin): boolean {
    return usuario.estado === 'INACTIVO';
  }

  esBloqueoAutomatico(usuario: UsuarioAdmin): boolean {
    return usuario.estado === 'BLOQUEADO_POR_MULTA';
  }

  abrirModalEstado(usuario: UsuarioAdmin, estadoDestino: 'ACTIVO' | 'INACTIVO'): void {
    this.usuarioAccion = usuario;
    this.nuevoEstado = estadoDestino;
    this.motivoEstado = '';
    this.errorModal = '';
    this.mostrarModalEstado = true;
  }

  cerrarModalEstado(): void {
    this.mostrarModalEstado = false;
    this.usuarioAccion = null;
    this.nuevoEstado = '';
    this.motivoEstado = '';
    this.errorModal = '';
  }

  confirmarCambioEstado(): void {
    const motivo = this.motivoEstado.trim();
    if (!this.usuarioAccion || !motivo || this.enviandoEstado) return;
    this.enviandoEstado = true;
    this.errorModal = '';

    this.usuarioAdminService.cambiarEstado(this.usuarioAccion.id, this.nuevoEstado, motivo).subscribe({
      next: () => {
        this.enviandoEstado = false;
        this.cerrarModalEstado();
        this.mensajeOk = this.nuevoEstado === 'INACTIVO'
          ? `Usuario ${this.usuarioAccion?.nombre} bloqueado`
          : `Usuario ${this.usuarioAccion?.nombre} activado`;
        this.cargarPagina();
      },
      error: (err) => {
        this.enviandoEstado = false;
        this.errorModal = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al cambiar el estado del usuario';
      }
    });
  }

  cambiarRol(usuario: UsuarioAdmin, nuevoRol: string): void {
    this.errorMsg = '';
    this.usuarioAdminService.cambiarRol(usuario.id, nuevoRol).subscribe({
      next: () => {
        this.mensajeOk = `Rol de ${usuario.nombre} actualizado a ${nuevoRol}`;
        this.cargarPagina();
      },
      error: (err) => {
        // El select ya cambió visualmente; se recarga el listado para
        // que muestre el rol real del backend.
        this.cargarPagina();
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al cambiar el rol del usuario';
      }
    });
  }
}