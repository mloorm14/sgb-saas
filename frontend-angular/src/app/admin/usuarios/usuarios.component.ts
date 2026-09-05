import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { UsuarioAdminService } from '../../core/services/usuario-admin.service';
import { UsuarioAdmin } from '../../core/models/usuario-admin.model';
import { BuscadorUsuarioComponent } from '../../shared/buscador-usuario/buscador-usuario.component';
import { FocusTrapDirective } from '../../shared/focus-trap.directive';
import { ConfirmDialogService } from '../../shared/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../../shared/toast/toast.service';
import { take } from 'rxjs/operators';

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
  imports: [CommonModule, FormsModule, BuscadorUsuarioComponent, FocusTrapDirective],
  templateUrl: './usuarios.component.html'
})
export class UsuariosComponent implements OnInit {
  rolesDisponibles = ROLES_DISPONIBLES;
  // F8-gerente: GERENTE solo crea/ve LECTOR y BIBLIOTECARIO en su apartado.
  rolesCreablesGerente = ['LECTOR', 'BIBLIOTECARIO'];

  // soloMios viene de la ruta admin/mis-usuarios (GERENTE). ADMIN en
  // admin/usuarios ve todo sin filtro, como antes.
  soloMios = false;
  esAdmin = false;
  esGerente = false;

  // ADMIN o GERENTE: ven el listado; ADMIN gestiona todo; GERENTE gestiona
  // solo en su apartado (crear LECTOR/BIBLIOTECARIO + bloquear).
  puedeVer: boolean = false;
  puedeGestionar: boolean = false;

  get rolesParaSelect(): string[] {
    return this.esAdmin ? this.rolesDisponibles : this.rolesCreablesGerente;
  }

  // Modal Crear usuario (F7/F8).
  mostrarModalCrear = false;
  nuevoNombre = '';
  nuevoApellido = '';
  nuevoCorreo = '';
  nuevoPassword = '';
  mostrarPassword = false;
  nuevoRol = 'LECTOR';
  creandoUsuario = false;
  errorCrear = '';

  filtro: string = '';
  usuarios: UsuarioAdmin[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  cargando: boolean = false;
  errorMsg: string = '';
  mensajeOk: string = '';

  ordenColumna: string = '';
  direccionAsc: boolean = true;

  // Modal de motivo para cambio de estado (motivo @NotBlank del backend)
  mostrarModalEstado: boolean = false;
  usuarioAccion: UsuarioAdmin | null = null;
  nuevoEstado: string = '';
  motivoEstado: string = '';
  enviandoEstado: boolean = false;
  errorModal: string = '';

  constructor(
    private usuarioAdminService: UsuarioAdminService,
    private authService: AuthService,
    private route: ActivatedRoute,
    private confirm: ConfirmDialogService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.soloMios = this.route.snapshot.data['soloMios'] === true;
    this.esAdmin = this.authService.hasRole('ADMIN');
    this.esGerente = this.authService.hasRole('GERENTE');
    this.puedeVer = this.esAdmin || this.esGerente;
    // ADMIN gestiona en admin/usuarios; GERENTE solo en su apartado mis-usuarios.
    this.puedeGestionar = this.esAdmin || (this.esGerente && this.soloMios);
    if (this.esGerente && !this.esAdmin) {
      this.nuevoRol = 'LECTOR';
    }
    if (this.puedeVer) {
      this.cargarPagina();
    }
  }

  ordenarPor(columna: string): void {
    if (this.ordenColumna === columna) {
      this.direccionAsc = !this.direccionAsc;
    } else {
      this.ordenColumna = columna;
      this.direccionAsc = true;
    }
  }

  get datosOrdenados() {
    const col = this.ordenColumna;
    const asc = this.direccionAsc;
    if (!col) return this.usuarios;
    return [...this.usuarios].sort((a: any, b: any) => {
      const va = a[col] ?? '';
      const vb = b[col] ?? '';
      const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
      return asc ? cmp : -cmp;
    });
  }

  get paginasVisibles(): number[] {
    const windowSize = 4;
    let start = Math.max(0, this.currentPage - 1);
    let end = Math.min(this.totalPages, start + windowSize);
    if (end - start < windowSize) {
      start = Math.max(0, end - windowSize);
    }
    return Array.from({ length: end - start }, (_, i) => start + i);
  }

  get puedeAnterior(): boolean {
    return this.currentPage > 0;
  }

  get puedeSiguiente(): boolean {
    return this.currentPage < this.totalPages - 1;
  }

  buscarUsuarios(): void {
    this.currentPage = 0;
    this.cargarPagina();
  }

  onBuscarAhora(texto: string): void {
    this.filtro = texto;
    this.buscarUsuarios();
  }

  // Se llama desde el template (paginacion numerada) -> no private.
  cargarPagina(): void {
    this.cargando = true;
    this.errorMsg = '';
    // F8-gerente: GERENTE siempre filtra por sus creados (aunque abra admin/usuarios a mano).
    const mios = this.soloMios || (this.esGerente && !this.esAdmin);
    this.usuarioAdminService.listar(this.filtro, this.currentPage, this.pageSize, mios).subscribe({
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

  irAPagina(pagina: number): void {
    if (pagina < 0 || pagina >= this.totalPages || pagina === this.currentPage) return;
    this.currentPage = pagina;
    this.cargarPagina();
  }

  paginaAnterior(): void {
    if (this.puedeAnterior) {
      this.irAPagina(this.currentPage - 1);
    }
  }

  paginaSiguiente(): void {
    if (this.puedeSiguiente) {
      this.irAPagina(this.currentPage + 1);
    }
  }

  cambiarTamanoPage(nuevo: number): void {
    this.pageSize = Number(nuevo);
    this.currentPage = 0;
    this.cargarPagina();
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
    const nombreUsuario = this.usuarioAccion.nombre;
    const esBloqueo = this.nuevoEstado === 'INACTIVO';

    this.usuarioAdminService.cambiarEstado(this.usuarioAccion.id, this.nuevoEstado, motivo).subscribe({
      next: () => {
        this.enviandoEstado = false;
        this.cerrarModalEstado();
        this.mensajeOk = esBloqueo
          ? `Usuario ${nombreUsuario} bloqueado`
          : `Usuario ${nombreUsuario} activado`;
        this.cargarPagina();
      },
      error: (err) => {
        this.enviandoEstado = false;
        const detail = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al cambiar el estado del usuario';
        this.errorModal = detail;
        // Fix 404 fantasma: purga fila stale y cierra modal si el usuario ya no existe
        if ((err as { status?: number })?.status === 404) {
          this.toast.error('No encontrado', detail);
          this.cerrarModalEstado();
          this.cargarPagina();
        }
      }
    });
  }

  // F7/F8: modal Crear (ADMIN todos los roles, GERENTE solo LECTOR/BIBLIOTECARIO).
  abrirModalCrear(): void {
    this.mostrarModalCrear = true;
    this.errorCrear = '';
    this.nuevoNombre = '';
    this.nuevoApellido = '';
    this.nuevoCorreo = '';
    this.nuevoPassword = '';
    this.nuevoRol = 'LECTOR';
  }

  cerrarModalCrear(): void {
    this.mostrarModalCrear = false;
    this.errorCrear = '';
  }

  get puedeCrear(): boolean {
    return this.nuevoNombre.trim().length >= 2
      && this.nuevoApellido.trim().length >= 2
      && /.+@.+\..+/.test(this.nuevoCorreo.trim())
      && this.nuevoPassword.length >= 8
      && this.nuevoRol.length > 0;
  }

  confirmarCrear(): void {
    if (!this.puedeCrear || this.creandoUsuario) return;
    this.creandoUsuario = true;
    this.errorCrear = '';
    this.usuarioAdminService.crear({
      nombre: this.nuevoNombre.trim(),
      apellido: this.nuevoApellido.trim(),
      correo: this.nuevoCorreo.trim(),
      password: this.nuevoPassword,
      rol: this.nuevoRol
    }).subscribe({
      next: () => {
        this.creandoUsuario = false;
        this.cerrarModalCrear();
        this.mensajeOk = `Usuario ${this.nuevoCorreo.trim()} creado`;
        this.toast.success('Usuario creado', 'La cuenta quedó activa de inmediato');
        this.cargarPagina();
      },
      error: (err) => {
        this.creandoUsuario = false;
        this.errorCrear = err?.status === 409
          ? (err?.error?.detail ?? 'El correo ya está registrado')
          : (err?.error?.detail ?? 'Error al crear el usuario');
      }
    });
  }

  // Solo ADMIN (backend DELETE soft a INACTIVO). GERENTE usa Bloquear.
  eliminarDefinitivo(usuario: UsuarioAdmin): void {
    this.confirm.confirm({
      title: 'Eliminar usuario',
      message: `¿Eliminar a ${usuario.nombre} ${usuario.apellido}? Quedará en estado INACTIVO.`,
      confirmText: 'Eliminar',
      cancelText: 'Cancelar',
      variant: 'danger'
    }).pipe(take(1)).subscribe(ok => {
      if (!ok) return;
      this.usuarioAdminService.eliminar(usuario.id).subscribe({
        next: () => {
          this.mensajeOk = `Usuario ${usuario.nombre} eliminado`;
          this.toast.success('Eliminado', 'El usuario quedó en estado INACTIVO');
          this.cargarPagina();
        },
        error: (err) => {
          this.errorMsg = err?.error?.detail ?? 'Error al eliminar el usuario';
          if ((err as { status?: number })?.status === 404) {
            this.toast.error('No encontrado', this.errorMsg);
            this.cargarPagina();
          }
        }
      });
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