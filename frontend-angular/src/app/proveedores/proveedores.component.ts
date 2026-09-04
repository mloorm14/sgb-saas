import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProveedorService } from '../core/services/proveedor.service';
import { Proveedor } from '../core/models/proveedor.model';

@Component({
  selector: 'app-proveedores',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './proveedores.component.html'
})
export class ProveedoresComponent implements OnInit {
  proveedores: Proveedor[] = [];
  filtrados: Proveedor[] = [];
  cargando = false;
  errorMsg: string | null = null;
  busqueda = '';
  filtroActivo: 'todos' | 'activo' | 'inactivo' = 'todos';

  pagina = 0;
  tamanoPagina = 10;
  totalPaginasServer = 1;

  // form
  mostrarForm = false;
  editando: Proveedor | null = null;
  form: Partial<Proveedor> = { nombre: '', activo: true };
  guardando = false;
  formError: string | null = null;

  constructor(private proveedorService: ProveedorService) {}

  ngOnInit(): void { this.cargar(); }

  totalElementos = 0;

  cargar(): void {
    this.cargando = true;
    this.errorMsg = null;
    this.proveedorService.listar(this.pagina, this.tamanoPagina).subscribe({
      next: (page: any) => {
        const contenido = Array.isArray(page) ? page : page.content ?? [];
        const totalPages = Array.isArray(page) ? Math.ceil(contenido.length / this.tamanoPagina) : page.totalPages ?? 1;
        this.totalPaginasServer = totalPages;
        this.proveedores = contenido;
        if (!Array.isArray(page) && page.content !== undefined) {
          this.filtrados = contenido;
        } else {
          this.aplicarFiltro();
        }
        this.cargando = false;
      },
      error: () => { this.errorMsg = 'No se pudieron cargar los proveedores.'; this.cargando = false; }
    });
  }

  aplicarFiltro(): void {
    let r = this.proveedores;
    if (this.busqueda.trim()) {
      const q = this.busqueda.toLowerCase();
      r = r.filter(p => p.nombre.toLowerCase().includes(q) || (p.ruc ?? '').toLowerCase().includes(q));
    }
    if (this.filtroActivo !== 'todos') {
      r = r.filter(p => this.filtroActivo === 'activo' ? p.activo : !p.activo);
    }
    this.filtrados = r;
    this.pagina = 0;
  }

  get totalPaginas(): number {
    // Si viene Page del backend, usar totalPaginasServer; si no, fallback client
    if (this.totalPaginasServer > 1) return this.totalPaginasServer;
    return Math.max(1, Math.ceil(this.filtrados.length / this.tamanoPagina));
  }
  get datosPaginados() {
    // Server pagination: filtrados ya es la pagina actual, no hacer slice
    if (this.totalPaginasServer > 1) return this.filtrados;
    const start = this.pagina * this.tamanoPagina;
    return this.filtrados.slice(start, start + this.tamanoPagina);
  }
  get paginasVisibles(): number[] {
    const windowSize = 4;
    let start = Math.max(0, this.pagina - 1);
    let end = Math.min(this.totalPaginas, start + windowSize);
    if (end - start < windowSize) start = Math.max(0, end - windowSize);
    return Array.from({ length: end - start }, (_, i) => start + i);
  }
  get puedeAnterior(): boolean { return this.pagina > 0; }
  get puedeSiguiente(): boolean { return this.pagina < this.totalPaginas - 1; }
  irAPagina(p: number): void {
    if (p < 0 || p >= this.totalPaginas || p === this.pagina) return;
    this.pagina = p;
    this.cargar();
  }
  paginaAnterior(): void { if (this.puedeAnterior) { this.pagina--; this.cargar(); } }
  paginaSiguiente(): void { if (this.puedeSiguiente) { this.pagina++; this.cargar(); } }
  cambiarTamano(n: number): void {
    this.tamanoPagina = Number(n);
    this.pagina = 0;
    this.cargar();
  }

  abrirCrear(): void {
    this.editando = null;
    this.form = { nombre: '', ruc: '', direccion: '', telefono: '', email: '', personaContacto: '', activo: true };
    this.formError = null;
    this.mostrarForm = true;
  }

  abrirEditar(p: Proveedor): void {
    this.editando = p;
    this.form = { ...p };
    this.formError = null;
    this.mostrarForm = true;
  }

  cancelar(): void { this.mostrarForm = false; this.editando = null; }

  guardar(): void {
    if (!this.form.nombre?.trim()) { this.formError = 'El nombre es obligatorio.'; return; }
    if (!this.form.ruc?.trim()) { this.formError = 'El RUC es obligatorio.'; return; }
    if (!/^[0-9]{13}$/.test(this.form.ruc.trim())) { this.formError = 'El RUC debe tener 13 dígitos numéricos sin guion.'; return; }
    if (!this.form.direccion?.trim()) { this.formError = 'La dirección es obligatoria.'; return; }
    if (!this.form.telefono?.trim()) { this.formError = 'El teléfono es obligatorio.'; return; }
    if (!this.form.email?.trim()) { this.formError = 'El email es obligatorio.'; return; }
    this.guardando = true;
    this.formError = null;
    const obs = this.editando
      ? this.proveedorService.actualizar(this.editando.id, this.form)
      : this.proveedorService.crear(this.form);
    obs.subscribe({
      next: () => { this.guardando = false; this.mostrarForm = false; this.cargar(); },
      error: (err: any) => {
        this.guardando = false;
        const status = err?.error?.status ?? err?.status;
        if (status === 422) this.formError = 'Ya existe un proveedor con ese nombre o RUC.';
        else this.formError = err?.error?.detail ?? 'No se pudo guardar el proveedor.';
      }
    });
  }
}
