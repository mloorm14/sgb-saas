import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/services/auth.service';
import { environment } from '../../environments/environment';

// Catálogo real de roles y estados_usuario (db/seed.sql). El backend no
// valida estos valores contra un enum fijo en el DTO (ver
// CambioRolRequestDTO/CambioEstadoUsuarioRequestDTO -- el catálogo vive en
// las tablas roles/estados_usuario), así que se listan acá a mano para los
// <select>; si el catálogo cambia en la base, este arreglo también hay que
// actualizarlo.
const ROLES_DISPONIBLES = ['LECTOR', 'BIBLIOTECARIO', 'GERENTE', 'ADMIN'];
const ESTADOS_DISPONIBLES = ['ACTIVO', 'BLOQUEADO_POR_MULTA', 'INACTIVO', 'PENDIENTE_VERIFICACION'];

@Component({
  selector: 'app-admin-usuarios',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-usuarios.component.html'
})
export class AdminUsuariosComponent implements OnInit {
  rolesDisponibles = ROLES_DISPONIBLES;
  estadosDisponibles = ESTADOS_DISPONIBLES;

  puedeVer: boolean = false;       // ADMIN o GERENTE: ven el listado
  puedeGestionar: boolean = false; // solo ADMIN: cambiar rol/estado

  filtro: string = '';
  usuarios: any[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';

  // Modal cambiar rol
  mostrarModalRol: boolean = false;
  usuarioSeleccionado: any = null;
  nuevoRol: string = '';

  // Modal cambiar estado
  mostrarModalEstado: boolean = false;
  nuevoEstado: string = '';
  motivoEstado: string = '';

  private apiUrl = environment.apiUrl + '/v1';

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.puedeVer = this.authService.hasRole('ADMIN', 'GERENTE');
    this.puedeGestionar = this.authService.hasRole('ADMIN');

    if (this.puedeVer) {
      this.cargarPagina();
    }
  }

  buscarUsuarios(): void {
    this.currentPage = 0;
    this.cargarPagina();
  }

  private cargarPagina(): void {
    this.cargando = true;
    this.errorMsg = '';
    const filtroParam = this.filtro.trim() ? `&filtro=${encodeURIComponent(this.filtro.trim())}` : '';
    this.http.get<any>(
      `${this.apiUrl}/admin/usuarios?page=${this.currentPage}&size=${this.pageSize}${filtroParam}`
    ).subscribe({
      next: (data) => {
        this.usuarios = data.content;
        this.totalPages = data.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar el listado de usuarios';
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

  abrirModalRol(usuario: any): void {
    this.usuarioSeleccionado = usuario;
    this.nuevoRol = usuario.roles?.[0] ?? '';
    this.mostrarModalRol = true;
  }

  cerrarModalRol(): void {
    this.mostrarModalRol = false;
    this.usuarioSeleccionado = null;
    this.nuevoRol = '';
  }

  confirmarCambioRol(): void {
    if (!this.nuevoRol || !this.usuarioSeleccionado) return;
    this.http.patch(`${this.apiUrl}/admin/usuarios/${this.usuarioSeleccionado.id}/rol`, { nuevoRol: this.nuevoRol }).subscribe({
      next: () => {
        this.cerrarModalRol();
        this.cargarPagina();
      },
      error: (err) => {
        this.errorMsg = err.status === 403
          ? 'No tenés permisos para cambiar el rol de un usuario'
          : 'Error al cambiar el rol del usuario';
      }
    });
  }

  abrirModalEstado(usuario: any): void {
    this.usuarioSeleccionado = usuario;
    this.nuevoEstado = usuario.estado ?? '';
    this.motivoEstado = '';
    this.mostrarModalEstado = true;
  }

  cerrarModalEstado(): void {
    this.mostrarModalEstado = false;
    this.usuarioSeleccionado = null;
    this.nuevoEstado = '';
    this.motivoEstado = '';
  }

  confirmarCambioEstado(): void {
    if (!this.nuevoEstado || !this.motivoEstado.trim() || !this.usuarioSeleccionado) return;
    this.http.patch(`${this.apiUrl}/admin/usuarios/${this.usuarioSeleccionado.id}/estado`, {
      nuevoEstado: this.nuevoEstado,
      motivo: this.motivoEstado.trim()
    }).subscribe({
      next: () => {
        this.cerrarModalEstado();
        this.cargarPagina();
      },
      error: (err) => {
        this.errorMsg = err.status === 403
          ? 'No tenés permisos para cambiar el estado de un usuario'
          : 'Error al cambiar el estado del usuario';
      }
    });
  }
}
