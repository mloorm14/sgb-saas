import { Component, HostListener, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs';
import { AuditoriaService } from '../../core/services/auditoria.service';
import { UsuarioAdminService } from '../../core/services/usuario-admin.service';
import { EventoAuditoria } from '../../core/models/evento-auditoria.model';
import { UsuarioAdmin } from '../../core/models/usuario-admin.model';
import { ResumenCategoriaAuditoria } from '../../core/models/resumen-auditoria.model';
import { FocusTrapDirective } from '../../shared/focus-trap.directive';

export interface ModuloOpcion {
  valor: string;
  etiqueta: string;
}

const MODULOS: ModuloOpcion[] = [
  { valor: 'usuarios', etiqueta: 'Usuarios' },
  { valor: 'sesiones', etiqueta: 'Accesos al sistema' },
  { valor: 'prestamos', etiqueta: 'Préstamos' },
  { valor: 'libros', etiqueta: 'Libros' },
  { valor: 'multas', etiqueta: 'Multas' },
  { valor: 'reservaciones', etiqueta: 'Reservaciones' },
  { valor: 'registro_danos', etiqueta: 'Registro de daños' },
  { valor: 'sugerencias_adquisicion', etiqueta: 'Sugerencias de adquisición' },
  { valor: 'configuracion_sistema', etiqueta: 'Configuración del sistema' },
  { valor: 'proveedores', etiqueta: 'Proveedores' },
  { valor: 'tipos_dano', etiqueta: 'Tipos de daño' },
  { valor: 'categorias_dano', etiqueta: 'Categorías de daño' },
  { valor: 'respaldos', etiqueta: 'Respaldos' },
  { valor: 'roles_permisos', etiqueta: 'Roles y permisos' }
];

@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [CommonModule, FormsModule, FocusTrapDirective],
  templateUrl: './auditoria.component.html'
})
export class AuditoriaComponent implements OnInit, OnDestroy {
  modulos = MODULOS;

  // Vista actual: 'tarjetas' (default) o 'historial'
  vista: 'tarjetas' | 'historial' = 'tarjetas';

  // Datos del resumen por categoría
  resumenCategorias: ResumenCategoriaAuditoria[] = [];
  cargandoResumen: boolean = false;

  // Filtros del historial
  filtroUsuarioId: string = '';
  filtroModulo: string = '';
  filtroDesde: string = '';
  filtroHasta: string = '';
  filtroDia: string = '';
  filtroHora: string = '';

  eventos: EventoAuditoria[] = [];
  totalPages: number = 0;
  currentPage: number = 0;
  pageSize: number = 20;
  cargando: boolean = false;
  errorMsg: string = '';

  ordenColumna: string = '';
  direccionAsc: boolean = true;

  // Modal de detalle
  modalVisible: boolean = false;
  eventoSeleccionado: EventoAuditoria | null = null;
  jsonCopiado: boolean = false;

  // Export CSV (GET /export del backend con los filtros vigentes)
  exportando = false;

  // Autocomplete de usuarios
  busquedaUsuario: string = '';
  usuarioSeleccionado: UsuarioAdmin | null = null;
  resultadosBusqueda: UsuarioAdmin[] = [];
  mostrandoDropdown: boolean = false;
  buscandoUsuarios: boolean = false;
  private busquedaSubject = new Subject<string>();
  private destroy$ = new Subject<void>();

  constructor(
    private auditoriaService: AuditoriaService,
    private usuarioAdminService: UsuarioAdminService
  ) {}

  ngOnInit(): void {
    this.cargarResumen();

    // Suscripción al autocomplete de usuarios
    this.busquedaSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(texto => {
        if (!texto.trim()) {
          this.resultadosBusqueda = [];
          this.buscandoUsuarios = false;
          return [];
        }
        this.buscandoUsuarios = true;
        return this.usuarioAdminService.listar(texto, 0, 5);
      }),
      takeUntil(this.destroy$)
    ).subscribe({
      next: (page) => {
        this.resultadosBusqueda = page.content ?? [];
        this.buscandoUsuarios = false;
      },
      error: () => {
        this.resultadosBusqueda = [];
        this.buscandoUsuarios = false;
      }
    });
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
    if (!col) return this.eventos;
    return [...this.eventos].sort((a: any, b: any) => {
      const va = a[col] ?? '';
      const vb = b[col] ?? '';
      const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb), 'es');
      return asc ? cmp : -cmp;
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  cargarResumen(): void {
    this.cargandoResumen = true;
    this.auditoriaService.resumen().subscribe({
      next: (data) => {
        this.resumenCategorias = data;
        this.cargandoResumen = false;
      },
      error: () => {
        this.cargandoResumen = false;
      }
    });
  }

  abrirHistorial(modulo: string): void {
    this.filtroModulo = modulo;
    this.currentPage = 0;
    this.vista = 'historial';
    this.cargarPagina();
  }

  volverATarjetas(): void {
    this.vista = 'tarjetas';
    this.filtroModulo = '';
    this.filtroUsuarioId = '';
    this.filtroDesde = '';
    this.filtroHasta = '';
    this.filtroDia = '';
    this.filtroHora = '';
    this.eventos = [];
    this.usuarioSeleccionado = null;
    this.cargarResumen();
  }

  onBusquedaUsuario(texto: string): void {
    this.busquedaSubject.next(texto);
  }

  seleccionarUsuario(usuario: UsuarioAdmin): void {
    this.usuarioSeleccionado = usuario;
    this.filtroUsuarioId = String(usuario.id);
    this.mostrandoDropdown = false;
    this.busquedaUsuario = '';
    this.resultadosBusqueda = [];
  }

  limpiarSeleccion(): void {
    this.usuarioSeleccionado = null;
    this.filtroUsuarioId = '';
    this.busquedaUsuario = '';
  }

  cerrarDropdown(): void {
    setTimeout(() => { this.mostrandoDropdown = false; }, 200);
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

  cambiarTamanoPage(nuevo: number): void {
    this.pageSize = Number(nuevo);
    this.currentPage = 0;
    this.cargarPagina();
  }

  // Combina filtroDia + filtroHora para generar desde/hasta precisos.
  // Si se proporciona dia pero no hora: rango completo de ese día.
  // Si se proporciona dia + hora: ventana de 1 minuto exacta.
  // Si NO se proporciona dia: usa los filtros Desde/Hasta tradicionales.
  private construirRangoFechas(): { desde?: string; hasta?: string } {
    if (this.filtroDia) {
      const hora = this.filtroHora || '00:00';
      const [h, m] = hora.split(':');
      const desde = `${this.filtroDia}T${h}:${m}:00.000Z`;
      let hasta: string;
      if (this.filtroHora) {
        // Ventana de 1 minuto: de HH:MM a HH:MM:59.999
        hasta = `${this.filtroDia}T${h}:${m}:59.999Z`;
      } else {
        // Día completo
        hasta = `${this.filtroDia}T23:59:59.999Z`;
      }
      return { desde, hasta };
    }
    return {
      desde: this.fechaInicio(this.filtroDesde),
      hasta: this.fechaFin(this.filtroHasta)
    };
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

  limpiarFiltros(): void {
    this.filtroUsuarioId = '';
    this.filtroModulo = '';
    this.filtroDesde = '';
    this.filtroHasta = '';
    this.filtroDia = '';
    this.filtroHora = '';
    this.usuarioSeleccionado = null;
    this.busquedaUsuario = '';
    this.currentPage = 0;
    this.cargarPagina();
  }

  // Se llama desde el template (paginacion numerada) -> no private.
  cargarPagina(): void {
    this.cargando = true;
    this.errorMsg = '';
    const rango = this.construirRangoFechas();
    this.auditoriaService.listar({
      usuarioId: this.usuarioId(),
      modulo: this.filtroModulo || undefined,
      desde: rango.desde,
      hasta: rango.hasta,
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

  // Descarga auditoria.csv con los filtros vigentes (mismo criterio que
  // cargarPagina: usuarioId + modulo + rango día/hora o desde/hasta).
  exportarCsv(): void {
    if (this.exportando) return;
    this.exportando = true;
    this.errorMsg = '';
    const rango = this.construirRangoFechas();
    this.auditoriaService.exportar({
      usuarioId: this.usuarioId(),
      modulo: this.filtroModulo || undefined,
      desde: rango.desde,
      hasta: rango.hasta
    }).subscribe({
      next: (blob) => {
        this.exportando = false;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'auditoria.csv';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.exportando = false;
        this.errorMsg = (err as { error?: { detail?: string } })?.error?.detail
          || 'Error al exportar la bitácora de auditoría';
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
  // vista lo muestra como ícono + "Sistema", nunca como texto vacío.
  esSistema(evento: EventoAuditoria): boolean {
    return !evento.usuario || evento.usuario.trim() === '';
  }

  usuarioLabel(evento: EventoAuditoria): string {
    return evento.usuario ?? '';
  }

  moduloLabel(modulo: string): string {
    const grupos: Record<string, string> = {
      'roles': 'Roles y permisos',
      'usuario_roles': 'Roles y permisos',
      'permisos': 'Roles y permisos',
      'rol_permisos': 'Roles y permisos',
      'roles_permisos': 'Roles y permisos',
      'backups': 'Respaldos',
      'backups_tablas': 'Respaldos',
      'backup_programacion': 'Respaldos',
      'configuracion_respaldo': 'Respaldos',
      'registros_respaldo': 'Respaldos',
      'respaldos': 'Respaldos'
    };
    if (grupos[modulo]) return grupos[modulo];
    const encontrado = this.modulos.find(m => m.valor === modulo);
    if (encontrado) return encontrado.etiqueta;
    return modulo ? modulo.replace(/_/g, ' ') : '—';
  }

  formatoFecha(iso: string): string {
    const fecha = new Date(iso);
    return fecha.toLocaleString('es-ES', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }

  // ── Modal de detalle ──────────────────────────────────────
  verCompleto = false;

  abrirDetalle(evento: EventoAuditoria): void {
    this.eventoSeleccionado = evento;
    this.modalVisible = true;
    this.jsonCopiado = false;
    this.verCompleto = false;
  }

  cerrarDetalle(): void {
    this.modalVisible = false;
    this.eventoSeleccionado = null;
    this.verCompleto = false;
  }

  alternarVerCompleto(): void {
    this.verCompleto = !this.verCompleto;
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.modalVisible) {
      this.cerrarDetalle();
    }
  }

  detalleFormateado(): string {
    if (!this.eventoSeleccionado?.detalle) return '';
    try {
      const formateado = JSON.stringify(JSON.parse(this.eventoSeleccionado.detalle), null, 2);
      // B9: truncar a 2000 chars para no congelar el modal; ver completo bajo demanda.
      if (!this.verCompleto && formateado.length > 2000) {
        return formateado.slice(0, 2000) + '\n… (truncado, usar Ver completo)';
      }
      return formateado;
    } catch {
      const texto = this.eventoSeleccionado.detalle;
      if (!this.verCompleto && texto.length > 2000) {
        return texto.slice(0, 2000) + '… (truncado, usar Ver completo)';
      }
      return texto;
    }
  }

  // B12: separa {"antes":{...},"despues":{...}} de UPDATE (BitacoraAuditoria.fn_auditoria_generica).
  // Si no es UPDATE con ambas caras, esUpdate=false y la vista usa ventana única.
  getAntesDespues(detalle: string, accion: string): { antes: any; despues: any; esUpdate: boolean; textoPlano: string | null } {
    try {
      const parsed = JSON.parse(detalle);
      if (accion === 'UPDATE' && parsed && typeof parsed === 'object' && parsed.antes && parsed.despues) {
        return { antes: parsed.antes, despues: parsed.despues, esUpdate: true, textoPlano: null };
      }
      return { antes: null, despues: parsed ?? null, esUpdate: false, textoPlano: null };
    } catch {
      return { antes: null, despues: null, esUpdate: false, textoPlano: detalle };
    }
  }

  // Keys cuyo valor cambió entre antes y después (comparación por JSON).
  diffKeys(antes: any, despues: any): string[] {
    if (!antes || !despues || typeof antes !== 'object' || typeof despues !== 'object') return [];
    const todas = new Set([...Object.keys(antes), ...Object.keys(despues)]);
    return [...todas].filter(k => JSON.stringify((antes as any)[k]) !== JSON.stringify((despues as any)[k]));
  }

  // Resalta un objeto con resaltarJson() sobre slice(0,5000) y marca las keys del diff con bg-yellow-200.
  resaltarConDiff(obj: any, diffs: string[]): string {
    if (obj == null) return '';
    const base = this.resaltarJson(JSON.stringify(obj, null, 2).slice(0, 5000));
    let out = base;
    for (const k of diffs) {
      const esc = k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      // resaltarJson envuelve claves como <span class="text-primary">"k"</span>
      const re = new RegExp(`<span class="text-primary">("${esc}")</span>`, 'g');
      out = out.replace(re, '<span class="text-primary bg-yellow-200 px-0.5 rounded">$1</span>');
    }
    return out;
  }

  resaltarJson(json: string): string {
    if (!json) return '';
    return json
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      // Claves: "nombre":
      .replace(/"([^"]+)"(?=\s*:)/g, '<span class="text-primary">"$1"</span>')
      // Strings values: ": "valor"
      .replace(/:\s*"([^"]*)"/g, ': <span class="text-success">"$1"</span>')
      // Numbers
      .replace(/:\s*(\d+\.?\d*)/g, ': <span class="text-tertiary">$1</span>')
      // Booleans
      .replace(/:\s*(true|false)/g, ': <span class="text-error">$1</span>')
      // Null
      .replace(/:\s*(null)/g, ': <span class="text-on-surface-variant">$1</span>');
  }

  copiarJson(): void {
    const json = this.detalleFormateado();
    if (json) {
      navigator.clipboard.writeText(json).then(() => {
        this.jsonCopiado = true;
        setTimeout(() => { this.jsonCopiado = false; }, 2000);
      });
    }
  }

  copiarAntes(): void {
    if (!this.eventoSeleccionado?.detalle) return;
    const split = this.getAntesDespues(this.eventoSeleccionado.detalle, this.eventoSeleccionado.accion);
    if (split.esUpdate) {
      navigator.clipboard.writeText(JSON.stringify(split.antes, null, 2)).then(() => {
        this.jsonCopiado = true;
        setTimeout(() => { this.jsonCopiado = false; }, 2000);
      });
    }
  }

  copiarDespues(): void {
    if (!this.eventoSeleccionado?.detalle) return;
    const split = this.getAntesDespues(this.eventoSeleccionado.detalle, this.eventoSeleccionado.accion);
    if (split.esUpdate) {
      navigator.clipboard.writeText(JSON.stringify(split.despues, null, 2)).then(() => {
        this.jsonCopiado = true;
        setTimeout(() => { this.jsonCopiado = false; }, 2000);
      });
    }
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

  // ── Helpers para tarjetas ─────────────────────────────────
  iconoCategoria(tabla: string): string {
    const iconos: Record<string, string> = {
      'usuarios': 'manage_accounts',
      'sesiones': 'shield',
      'prestamos': 'menu_book',
      'libros': 'inventory_2',
      'multas': 'payments',
      'reservaciones': 'event_available',
      'registro_danos': 'report_problem',
      'sugerencias_adquisicion': 'lightbulb',
      'configuracion_sistema': 'settings',
      'proveedores': 'local_shipping',
      'tipos_dano': 'handyman',
      'categorias_dano': 'category',
      'respaldos': 'backup',
      'roles_permisos': 'admin_panel_settings',
      'roles': 'admin_panel_settings',
      'usuario_roles': 'admin_panel_settings',
      'permisos': 'admin_panel_settings',
      'rol_permisos': 'admin_panel_settings',
      'backups': 'backup',
      'backups_tablas': 'backup',
      'backup_programacion': 'backup',
      'configuracion_respaldo': 'backup',
      'registros_respaldo': 'backup'
    };
    return iconos[tabla] || 'folder';
  }

  codigoCategoria(tabla: string): string {
    const codigos: Record<string, string> = {
      'usuarios': 'AUD-USR',
      'sesiones': 'AUD-SES',
      'prestamos': 'AUD-PRE',
      'libros': 'AUD-LIB',
      'multas': 'AUD-MUL',
      'reservaciones': 'AUD-RES',
      'registro_danos': 'AUD-DAN',
      'sugerencias_adquisicion': 'AUD-SUG',
      'configuracion_sistema': 'AUD-CFG',
      'proveedores': 'AUD-PRO',
      'tipos_dano': 'AUD-TDA',
      'categorias_dano': 'AUD-CDA',
      'respaldos': 'AUD-BAK',
      'roles_permisos': 'AUD-ROL',
      'roles': 'AUD-ROL',
      'usuario_roles': 'AUD-ROL',
      'permisos': 'AUD-ROL',
      'rol_permisos': 'AUD-ROL',
      'backups': 'AUD-BAK',
      'backups_tablas': 'AUD-BAK',
      'backup_programacion': 'AUD-BAK',
      'configuracion_respaldo': 'AUD-BAK',
      'registros_respaldo': 'AUD-BAK'
    };
    return codigos[tabla] || tabla.toUpperCase().substring(0, 7);
  }

  descripcionCategoria(tabla: string): string {
    const descripciones: Record<string, string> = {
      'usuarios': 'Gestión de cuentas y permisos',
      'sesiones': 'Login, logout y intentos de acceso',
      'prestamos': 'Creación, devolución y renovación',
      'libros': 'Alta, edición y baja del catálogo',
      'multas': 'Pago y anulación de sanciones',
      'reservaciones': 'Aceptación y rechazo de reservas',
      'registro_danos': 'Devoluciones con daños reportados',
      'sugerencias_adquisicion': 'Evaluación de propuestas',
      'configuracion_sistema': 'Cambios de parámetros globales',
      'proveedores': 'Alta y edición de proveedores',
      'tipos_dano': 'Precios y categorías de daños',
      'categorias_dano': 'Categorías de daño (Leve/Grave)',
      'respaldos': 'Respaldos y programaciones',
      'roles_permisos': 'Asignación de roles y permisos',
      'roles': 'Asignación de roles y permisos',
      'usuario_roles': 'Asignación de roles y permisos',
      'permisos': 'Asignación de roles y permisos',
      'rol_permisos': 'Asignación de roles y permisos',
      'backups': 'Respaldos y programaciones',
      'backups_tablas': 'Respaldos y programaciones',
      'backup_programacion': 'Respaldos y programaciones',
      'configuracion_respaldo': 'Respaldos y programaciones',
      'registros_respaldo': 'Respaldos y programaciones'
    };
    return descripciones[tabla] || tabla;
  }

  colorCategoria(tabla: string): string {
    const colores: Record<string, string> = {
      'usuarios': 'bg-primary/10 text-primary',
      'sesiones': 'bg-secondary/10 text-secondary',
      'prestamos': 'bg-tertiary/10 text-tertiary',
      'libros': 'bg-primary-container/30 text-primary',
      'multas': 'bg-error/10 text-error',
      'reservaciones': 'bg-success/10 text-success',
      'registro_danos': 'bg-error/10 text-error',
      'sugerencias_adquisicion': 'bg-warning/20 text-tertiary',
      'configuracion_sistema': 'bg-surface-variant/50 text-on-surface-variant',
      'proveedores': 'bg-secondary/10 text-secondary',
      'tipos_dano': 'bg-error/10 text-error',
      'categorias_dano': 'bg-error/10 text-error',
      'respaldos': 'bg-tertiary/10 text-tertiary',
      'roles_permisos': 'bg-primary/10 text-primary',
      'roles': 'bg-primary/10 text-primary',
      'usuario_roles': 'bg-primary/10 text-primary',
      'permisos': 'bg-primary/10 text-primary',
      'rol_permisos': 'bg-primary/10 text-primary',
      'backups': 'bg-tertiary/10 text-tertiary',
      'backups_tablas': 'bg-tertiary/10 text-tertiary',
      'backup_programacion': 'bg-tertiary/10 text-tertiary',
      'configuracion_respaldo': 'bg-tertiary/10 text-tertiary',
      'registros_respaldo': 'bg-tertiary/10 text-tertiary'
    };
    return colores[tabla] || 'bg-surface-variant/50 text-on-surface-variant';
  }

  etiquetaModulo(valor: string): string {
    return this.modulos.find(m => m.valor === valor)?.etiqueta || valor;
  }
}