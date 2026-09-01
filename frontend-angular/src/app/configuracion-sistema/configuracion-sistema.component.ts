import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConfiguracionSistemaService, ParametroConfiguracion } from '../core/services/configuracion-sistema.service';
import { DevolucionService } from '../core/services/devolucion.service';
import { AuthService } from '../core/services/auth.service';
import { TipoDano, CategoriaDano } from '../core/models/devoluciones.model';
import { BackupService, BackupEntry, BackupProgramacion } from '../core/services/backup.service';
import { ToastService } from '../shared/toast/toast.service';

const VALOR_MAX_LENGTH = 200;

type VistaConfig = 'root' | 'grid' | 'detalle';

interface MetaParametro {
  titulo: string;
  descripcion: string;
  icono: string;
  grupo: string;
  sufijo?: string;
  tipo?: 'texto' | 'numero' | 'lista' | 'bool';
  modulo?: string;
  submodulo?: string;
}

const METADATOS: Record<string, MetaParametro> = {
  dias_prestamo_default: {
    titulo: 'Días de préstamo por defecto',
    descripcion: 'Cuánto tiempo dura un préstamo antes de vencer',
    icono: 'calendar_month',
    grupo: 'Préstamos y reservas',
    sufijo: 'días',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'prestamos'
  },
  max_renovaciones_default: {
    titulo: 'Máximo de renovaciones',
    descripcion: 'Veces que un lector puede renovar el mismo préstamo',
    icono: 'autorenew',
    grupo: 'Préstamos y reservas',
    sufijo: 'veces',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'prestamos'
  },
  max_prestamos_usuario: {
    titulo: 'Máximo de préstamos simultáneos',
    descripcion: 'Cantidad máxima de libros que un lector puede tener prestados al mismo tiempo',
    icono: 'menu_book',
    grupo: 'Préstamos y reservas',
    sufijo: 'libros',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'prestamos'
  },
  max_reservas_por_usuario: {
    titulo: 'Máximo de reservas activas',
    descripcion: 'Cantidad máxima de reservas que un lector puede tener al mismo tiempo',
    icono: 'event_available',
    grupo: 'Préstamos y reservas',
    sufijo: 'reservas',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'prestamos'
  },
  minutos_reserva: {
    titulo: 'Minutos para retirar una reserva',
    descripcion: 'Tiempo antes de que una reserva caduque automáticamente',
    icono: 'event_busy',
    grupo: 'Préstamos y reservas',
    sufijo: 'min',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'prestamos'
  },
  minutos_retirar_reserva: {
    titulo: 'Minutos para retirar una reserva',
    descripcion: 'Tiempo antes de que una reserva caduque automáticamente',
    icono: 'event_busy',
    grupo: 'Préstamos y reservas',
    sufijo: 'min',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'prestamos'
  },
  hora_limite_retiro_reserva: {
    titulo: 'Horario límite de retiro de reserva',
    descripcion: 'Reservas hechas después de esta hora caducan al siguiente día hábil',
    icono: 'schedule',
    grupo: 'Préstamos y reservas',
    modulo: 'sistema',
    submodulo: 'prestamos'
  },
  dias_anticipacion_vencimiento: {
    titulo: 'Días de anticipación para recordatorio',
    descripcion: 'Días antes del vencimiento para enviar notificación de recordatorio',
    icono: 'notifications_active',
    grupo: 'Notificaciones',
    sufijo: 'días',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'notificaciones'
  },
  monto_multa_diaria: {
    titulo: 'Monto de multa por día de atraso',
    descripcion: 'Se cobra por cada día de retraso en la devolución',
    icono: 'payments',
    grupo: 'Multas',
    sufijo: '$',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'multas'
  },
  multa_maxima_por_prestamo: {
    titulo: 'Multa máxima por préstamo',
    descripcion: 'Tope: nunca se genera una multa mayor a este valor',
    icono: 'money_off',
    grupo: 'Multas',
    sufijo: '$',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'multas'
  },
  monto_maximo_deuda_bloqueo: {
    titulo: 'Umbral de bloqueo por deuda',
    descripcion: 'Por encima de este monto acumulado, el usuario queda BLOQUEADO_POR_MULTA',
    icono: 'block',
    grupo: 'Multas',
    sufijo: '$',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'multas'
  },
  metodos_pago_habilitados: {
    titulo: 'Métodos de pago habilitados',
    descripcion: 'Solo informativo — "pagar" sigue siendo marcar la multa como pagada manualmente',
    icono: 'point_of_sale',
    grupo: 'Multas',
    tipo: 'texto',
    modulo: 'sistema',
    submodulo: 'multas'
  },
  max_tamano_portada_mb: {
    titulo: 'Tamaño máximo de portada',
    descripcion: 'Límite para las imágenes de portada subidas por el bibliotecario',
    icono: 'image',
    grupo: 'Archivos',
    sufijo: 'MB',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'archivos'
  },
  max_tamano_evidencia_mb: {
    titulo: 'Tamaño máximo de evidencia',
    descripcion: 'Límite para los archivos de evidencia de daño subidos por el bibliotecario',
    icono: 'attach_file',
    grupo: 'Archivos',
    sufijo: 'MB',
    tipo: 'numero',
    modulo: 'sistema',
    submodulo: 'archivos'
  },
  correo_dominios_permitidos: {
    titulo: 'Dominios de correo permitidos',
    descripcion: 'Dominios separados por coma. Si está vacío, se aceptan todos.',
    icono: 'mail_lock',
    grupo: 'Registro',
    tipo: 'texto',
    modulo: 'sistema',
    submodulo: 'registro'
  }
};

interface ModuloConfig {
  id: string;
  codigo: string;
  titulo: string;
  descripcion: string;
  icono: string;
  color: string;
}

interface SubmoduloConfig {
  id: string;
  codigo: string;
  titulo: string;
  descripcion: string;
  icono: string;
  color: string;
  badge?: string;
  moduloId?: string;
}

interface SubSubmoduloConfig {
  id: string;
  codigo: string;
  titulo: string;
  descripcion: string;
  icono: string;
  color: string;
  submoduloId: string; // pertenece a qué submódulo de respaldos
}

interface BreadcrumbItem {
  label: string;
  vista: VistaConfig;
  submoduloId?: string;
  subSubmoduloId?: string;
}

@Component({
  selector: 'app-configuracion-sistema',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './configuracion-sistema.component.html'
})
export class ConfiguracionSistemaComponent implements OnInit {
  valorMaxLength = VALOR_MAX_LENGTH;
  esAdmin: boolean = false;

  configuraciones: ParametroConfiguracion[] = [];
  cargando: boolean = false;
  errorMsg: string = '';

  claveEditando: string | null = null;
  valorEditando: string = '';

  tiposDano: TipoDano[] = [];
  categoriasDano: CategoriaDano[] = [];
  cargandoTiposDano = false;
  editandoTipoDano: TipoDano | null = null;
  nuevoTipoDanoNombre = '';
  nuevoTipoDanoCategoriaId: number | null = null;
  nuevoTipoDanoTipoCosto: string = 'FIJO';
  nuevoTipoDanoValor: number | null = null;
  nuevoCategoriaNombre = '';
  errorMsgTiposDano = '';

  vista: VistaConfig = 'root';
  moduloSeleccionado: string | null = null;
  submoduloSeleccionado: string | null = null;
  /** Nivel 3: 'manual-completo' | 'manual-personalizado' | 'auto-completo' | 'auto-personalizado' */
  subSubmoduloSeleccionado: string | null = null;
  breadcrumbs: BreadcrumbItem[] = [];
  tituloActual = 'Configuración';
  descripcionActual = 'Seleccione un módulo para ver y editar sus parámetros.';

  modulos: ModuloConfig[] = [
    {
      id: 'sistema',
      codigo: 'CFG-SIS',
      titulo: 'Configuración del sistema',
      descripcion: 'Préstamos, notificaciones, multas, registro y archivos.',
      icono: 'tune',
      color: 'bg-primary/10 text-primary'
    },
    {
      id: 'danos',
      codigo: 'CFG-DAN',
      titulo: 'Tipos de daño',
      descripcion: 'Categorías y costos por tipo de daño a un ejemplar: fijo ($) o porcentaje del precio del libro.',
      icono: 'report_problem',
      color: 'bg-secondary/10 text-secondary'
    },
    {
      id: 'respaldos',
      codigo: 'CFG-RES',
      titulo: 'Respaldo de datos',
      descripcion: 'Genere respaldos con filtros de fecha, tablas y formato. Descargue y gestione el historial.',
      icono: 'backup',
      color: 'bg-tertiary/10 text-tertiary'
    }
  ];

  submodulos: SubmoduloConfig[] = [
    { id: 'prestamos', moduloId: 'sistema', codigo: 'CFG-PRE', titulo: 'Préstamos y reservas', descripcion: 'Duración, renovaciones, límite de préstamos y horario de retiro de reservas.', icono: 'calendar_month', color: 'bg-primary/10 text-primary' },
    { id: 'notificaciones', moduloId: 'sistema', codigo: 'CFG-NOT', titulo: 'Notificaciones', descripcion: 'Recordatorios automáticos, plantillas de mensaje y avisos masivos.', icono: 'notifications_active', color: 'bg-tertiary/10 text-tertiary' },
    { id: 'multas', moduloId: 'sistema', codigo: 'CFG-MUL', titulo: 'Multas', descripcion: 'Monto diario, tope máximo, umbral de bloqueo y métodos de pago.', icono: 'payments', color: 'bg-error/10 text-error' },
    { id: 'registro', moduloId: 'sistema', codigo: 'CFG-REG', titulo: 'Registro', descripcion: 'Dominios de correo permitidos para el autorregistro de usuarios.', icono: 'mail_lock', color: 'bg-primary/10 text-primary' },
    { id: 'archivos', moduloId: 'sistema', codigo: 'CFG-ARC', titulo: 'Archivos', descripcion: 'Tamaño máximo de portadas y evidencias subidas por el bibliotecario.', icono: 'attach_file', color: 'bg-primary/10 text-primary' },
    { id: 'respaldo-manual', moduloId: 'respaldos', codigo: 'CFG-RSM', titulo: 'Respaldo manual', descripcion: 'Genere respaldos inmediatos seleccionando entre volcado completo o exportación de tablas específicas.', icono: 'hard_drive_2', color: 'bg-tertiary/10 text-tertiary' },
    { id: 'respaldo-automatico', moduloId: 'respaldos', codigo: 'CFG-RSA', titulo: 'Respaldo automático', descripcion: 'Configure respaldos automáticos programados: completos (Disaster Recovery) o de tablas específicas.', icono: 'schedule', color: 'bg-primary/10 text-primary' }
  ];

  /** Nivel 3: sub-submódulos de respaldo-manual y respaldo-automatico */
  subSubmodulos: SubSubmoduloConfig[] = [
    { id: 'manual-completo', submoduloId: 'respaldo-manual', codigo: 'CFG-RSM-C', titulo: 'Completo', descripcion: 'Volcado completo de la base de datos (pg_dump). Recomendado para recuperación ante desastres.', icono: 'database', color: 'bg-primary/10 text-primary' },
    { id: 'manual-personalizado', submoduloId: 'respaldo-manual', codigo: 'CFG-RSM-P', titulo: 'Personalizado', descripcion: 'Exporte tablas específicas con filtros de fecha y formato CSV o SQL.', icono: 'filter_alt', color: 'bg-tertiary/10 text-tertiary' },
    { id: 'auto-completo', submoduloId: 'respaldo-automatico', codigo: 'CFG-RSA-C', titulo: 'Completo', descripcion: 'Programa volcados completos de base de datos cada cierta cantidad de horas.', icono: 'database', color: 'bg-primary/10 text-primary' },
    { id: 'auto-personalizado', submoduloId: 'respaldo-automatico', codigo: 'CFG-RSA-P', titulo: 'Personalizado', descripcion: 'Programa exportaciones automáticas de tablas específicas en el formato elegido.', icono: 'filter_alt', color: 'bg-tertiary/10 text-tertiary' }
  ];

  get submodulosFiltrados(): SubmoduloConfig[] {
    if (this.moduloSeleccionado === 'sistema') {
      return this.submodulos.filter(s => s.moduloId === 'sistema');
    } else if (this.moduloSeleccionado === 'respaldos') {
      return this.submodulos.filter(s => s.moduloId === 'respaldos');
    }
    return [];
  }

  get subSubmodulosFiltrados(): SubSubmoduloConfig[] {
    if (!this.submoduloSeleccionado) return [];
    return this.subSubmodulos.filter(s => s.submoduloId === this.submoduloSeleccionado);
  }

  // --- Estado Respaldo de datos ---
  backupDesde = '';
  backupHasta = '';
  backupFormato: 'sql' | 'csv' = 'sql';
  backupTablasSeleccionadas: Record<string, boolean> = {};
  backupTablasDisponibles = ['usuarios', 'libros', 'prestamos', 'reservaciones', 'multas', 'auditoria', 'configuracion_sistema', 'notificaciones', 'favoritos', 'sugerencias_adquisicion', 'categorias', 'autores'];
  nombresTablas: Record<string, string | undefined> = {
    usuarios: 'Usuarios',
    libros: 'Libros',
    prestamos: 'Préstamos',
    reservaciones: 'Reservaciones',
    multas: 'Multas',
    auditoria: 'Auditoría',
    configuracion_sistema: 'Configuración del sistema',
    notificaciones: 'Notificaciones',
    favoritos: 'Favoritos',
    sugerencias_adquisicion: 'Sugerencias de adquisición',
    categorias: 'Categorías',
    autores: 'Autores'
  };
  cargandoBackup = false;
  generandoBackup = false;
  backups: BackupEntry[] = [];
  errorMsgBackup = '';
  filtroBackupFormato = '';
  filtroBackupFecha = '';
  filtroBackupDia = '';
  filtroBackupHora = '';
  cargandoProgramaciones = false;
  programaciones: BackupProgramacion[] = [];
  cadaHoras: number | null = null;
  cadaDias: number | null = null;
  backupModo: 'manual' | 'automatico' = 'manual';

  constructor(
    private configuracionService: ConfiguracionSistemaService,
    private devolucionService: DevolucionService,
    private authService: AuthService,
    private backupService: BackupService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.esAdmin = this.authService.hasRole('ADMIN');
    if (this.esAdmin) {
      this.cargarConfiguraciones();
      this.cargarCategoriasDano();
      this.cargarTiposDano();
    }
  }

  abrirModulo(moduloId: string): void {
    this.moduloSeleccionado = moduloId;
    this.subSubmoduloSeleccionado = null;
    if (moduloId === 'danos') {
      this.vista = 'detalle';
      this.submoduloSeleccionado = 'danos';
      this.tituloActual = 'Tipos de daño';
      this.descripcionActual = 'Categorías y costos por tipo de daño a un ejemplar.';
      this.breadcrumbs = [
        { label: 'Configuración', vista: 'root' },
        { label: 'Tipos de daño', vista: 'detalle', submoduloId: 'danos' }
      ];
    } else if (moduloId === 'respaldos') {
      this.vista = 'grid';
      this.submoduloSeleccionado = null;
      this.tituloActual = 'Respaldo de datos';
      this.descripcionActual = 'Seleccione el tipo de respaldo que desea realizar o configurar.';
      this.breadcrumbs = [
        { label: 'Configuración', vista: 'root' }
      ];
    } else {
      this.vista = 'grid';
      this.submoduloSeleccionado = null;
      this.tituloActual = 'Configuración del sistema';
      this.descripcionActual = 'Cinco submódulos, cada uno con sus propios parámetros.';
      this.breadcrumbs = [
        { label: 'Configuración', vista: 'root' }
      ];
    }
  }

  abrirSubmodulo(submoduloId: string): void {
    const sub = this.submodulos.find(s => s.id === submoduloId);
    if (!sub) return;
    this.submoduloSeleccionado = submoduloId;
    this.subSubmoduloSeleccionado = null;

    // Los submódulos de respaldos abren una cuadrícula de nivel 3 (Completo/Personalizado)
    if (sub.moduloId === 'respaldos') {
      this.vista = 'grid';
      this.tituloActual = sub.titulo;
      this.descripcionActual = sub.descripcion;
      this.breadcrumbs = [
        { label: 'Configuración', vista: 'root' },
        { label: 'Respaldo de datos', vista: 'grid' },
        { label: sub.titulo, vista: 'detalle', submoduloId }
      ];
    } else {
      this.vista = 'detalle';
      this.tituloActual = sub.titulo;
      this.descripcionActual = sub.descripcion;
      this.breadcrumbs = [
        { label: 'Configuración', vista: 'root' },
        { label: 'Configuración del sistema', vista: 'grid' },
        { label: sub.titulo, vista: 'detalle', submoduloId }
      ];
    }
  }

  abrirSubSubmodulo(subSubmoduloId: string): void {
    const ss = this.subSubmodulos.find(s => s.id === subSubmoduloId);
    if (!ss) return;
    const sub = this.submodulos.find(s => s.id === ss.submoduloId);
    this.subSubmoduloSeleccionado = subSubmoduloId;
    this.vista = 'detalle';
    this.tituloActual = ss.titulo;
    this.descripcionActual = ss.descripcion;
    // Limpiar historial propio de este sub-submódulo
    this.backups = [];
    this.programaciones = [];
    this.errorMsgBackup = '';
    this.breadcrumbs = [
      { label: 'Configuración', vista: 'root' },
      { label: 'Respaldo de datos', vista: 'grid' },
      { label: sub?.titulo ?? ss.submoduloId, vista: 'grid', submoduloId: ss.submoduloId },
      { label: ss.titulo, vista: 'detalle', subSubmoduloId }
    ];
    // Cargar historial exclusivo según el sub-submódulo
    if (subSubmoduloId === 'manual-personalizado') {
      this.cargarBackupsPorTipo('manual');
    } else if (subSubmoduloId === 'auto-personalizado') {
      this.cargarBackupsPorTipo('automatico');
      this.cargarProgramaciones();
    } else if (subSubmoduloId === 'manual-completo') {
      this.cargarRegistrosRespaldo('manual');
    } else if (subSubmoduloId === 'auto-completo') {
      this.cargarRegistrosRespaldo('automatico');
      this.cargarConfigDR();
    }
  }

  volverARoot(): void {
    this.vista = 'root';
    this.submoduloSeleccionado = null;
    this.moduloSeleccionado = null;
    this.subSubmoduloSeleccionado = null;
    this.breadcrumbs = [];
    this.tituloActual = 'Configuración';
    this.descripcionActual = 'Seleccione un módulo para ver y editar sus parámetros.';
    this.claveEditando = null;
  }

  volverAGrid(): void {
    this.claveEditando = null;
    // Si estábamos en un sub-submódulo (nivel 3) volvemos al grid del submódulo (nivel 2)
    if (this.subSubmoduloSeleccionado) {
      this.subSubmoduloSeleccionado = null;
      this.vista = 'grid';
      const sub = this.submodulos.find(s => s.id === this.submoduloSeleccionado);
      this.tituloActual = sub?.titulo ?? 'Respaldo de datos';
      this.descripcionActual = sub?.descripcion ?? '';
      this.breadcrumbs = [
        { label: 'Configuración', vista: 'root' },
        { label: 'Respaldo de datos', vista: 'grid' },
        { label: sub?.titulo ?? '', vista: 'grid', submoduloId: this.submoduloSeleccionado ?? undefined }
      ];
    } else {
      // Volvemos al grid del módulo (nivel 1)
      this.submoduloSeleccionado = null;
      this.vista = 'grid';
      this.breadcrumbs = [
        { label: 'Configuración', vista: 'root' }
      ];
      if (this.moduloSeleccionado === 'respaldos') {
        this.tituloActual = 'Respaldo de datos';
        this.descripcionActual = 'Seleccione el tipo de respaldo que desea realizar o configurar.';
      } else {
        this.tituloActual = 'Configuración del sistema';
        this.descripcionActual = 'Cinco submódulos, cada uno con sus propios parámetros.';
      }
    }
  }

  navegarBreadcrumb(item: BreadcrumbItem): void {
    this.claveEditando = null;
    if (item.vista === 'root') {
      this.volverARoot();
    } else if (item.subSubmoduloId) {
      this.abrirSubSubmodulo(item.subSubmoduloId);
    } else if (item.submoduloId) {
      // Volver al grid del submódulo (nivel 2 de respaldos)
      this.subSubmoduloSeleccionado = null;
      this.submoduloSeleccionado = item.submoduloId;
      this.vista = 'grid';
      const sub = this.submodulos.find(s => s.id === item.submoduloId);
      this.tituloActual = sub?.titulo ?? item.label;
      this.descripcionActual = sub?.descripcion ?? '';
      this.breadcrumbs = [
        { label: 'Configuración', vista: 'root' },
        { label: 'Respaldo de datos', vista: 'grid' },
        { label: sub?.titulo ?? item.label, vista: 'grid', submoduloId: item.submoduloId }
      ];
    } else if (item.vista === 'grid') {
      // Grid de nivel 1 (respaldos o sistema)
      this.subSubmoduloSeleccionado = null;
      this.submoduloSeleccionado = null;
      this.vista = 'grid';
      this.breadcrumbs = [{ label: 'Configuración', vista: 'root' }];
      if (this.moduloSeleccionado === 'respaldos') {
        this.tituloActual = 'Respaldo de datos';
        this.descripcionActual = 'Seleccione el tipo de respaldo que desea realizar o configurar.';
      } else {
        this.tituloActual = 'Configuración del sistema';
        this.descripcionActual = 'Cinco submódulos, cada uno con sus propios parámetros.';
      }
    }
  }

  configsPorSubmodulo(submoduloId: string): (ParametroConfiguracion & { meta: MetaParametro })[] {
    return this.configuraciones
      .map(c => ({ ...c, meta: METADATOS[c.clave] ?? { titulo: c.clave, descripcion: '', icono: 'settings', grupo: 'Otros' } }))
      .filter(c => c.meta.submodulo === submoduloId);
  }

  private cargarConfiguraciones(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.configuracionService.listar().subscribe({
      next: (data) => {
        this.configuraciones = data;
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar la configuración del sistema';
        this.cargando = false;
      }
    });
  }

  iniciarEdicion(config: ParametroConfiguracion): void {
    this.claveEditando = config.clave;
    this.valorEditando = config.valor ?? '';
  }

  cancelarEdicion(): void {
    this.claveEditando = null;
    this.valorEditando = '';
  }

  get puedeGuardarValor(): boolean {
    const valor = (this.valorEditando ?? '').trim();
    if (this.claveEditando === 'correo_dominios_permitidos') {
      return valor.length <= this.valorMaxLength;
    }
    return !!valor && valor.length <= this.valorMaxLength;
  }

  get puedeAgregarTipoDano(): boolean {
    return !!this.nuevoTipoDanoNombre.trim() && this.nuevoTipoDanoCategoriaId !== null
      && !!this.nuevoTipoDanoTipoCosto && this.nuevoTipoDanoValor !== null && this.nuevoTipoDanoValor >= 0
      && (this.nuevoTipoDanoTipoCosto !== 'PORCENTAJE' || this.nuevoTipoDanoValor <= 100);
  }

  get puedeAgregarCategoria(): boolean {
    return !!this.nuevoCategoriaNombre.trim();
  }

  guardarValor(): void {
    if (!this.claveEditando) return;
    const valor = (this.valorEditando ?? '').trim();
    const permiteVacio = this.claveEditando === 'correo_dominios_permitidos';
    if (!permiteVacio && (!valor || valor.length > VALOR_MAX_LENGTH)) return;
    if (permiteVacio && valor.length > VALOR_MAX_LENGTH) return;

    this.configuracionService.actualizar(this.claveEditando, valor).subscribe({
      next: () => {
        this.cancelarEdicion();
        this.cargarConfiguraciones();
      },
      error: (err) => {
        this.errorMsg = err.status === 403
          ? 'No tienes permisos para modificar la configuración del sistema'
          : 'Error al actualizar el valor de configuración';
      }
    });
  }

  private cargarCategoriasDano(): void {
    this.devolucionService.listarCategoriasDano().subscribe({
      next: (data) => { this.categoriasDano = data; },
      error: () => { this.errorMsgTiposDano = 'Error al cargar categorías de daño'; }
    });
  }

  private cargarTiposDano(): void {
    this.cargandoTiposDano = true;
    this.devolucionService.listarTiposDano().subscribe({
      next: (data) => { this.tiposDano = data; this.cargandoTiposDano = false; },
      error: () => { this.errorMsgTiposDano = 'Error al cargar tipos de daño'; this.cargandoTiposDano = false; }
    });
  }

  iniciarEdicionTipoDano(tipo: TipoDano): void {
    this.editandoTipoDano = { ...tipo };
  }

  cancelarEdicionTipoDano(): void {
    this.editandoTipoDano = null;
  }

  guardarTipoDano(): void {
    if (!this.editandoTipoDano) return;
    const { id, nombre, categoriaId, tipoCosto, valor } = this.editandoTipoDano;
    if (!nombre?.trim() || categoriaId == null || !tipoCosto || valor == null || valor < 0) return;
    if (tipoCosto === 'PORCENTAJE' && valor > 100) return;
    this.devolucionService.actualizarTipoDano(id, nombre.trim(), categoriaId, tipoCosto, valor).subscribe({
      next: () => { this.cancelarEdicionTipoDano(); this.cargarTiposDano(); },
      error: (err) => { this.errorMsgTiposDano = err.message; }
    });
  }

  agregarTipoDano(): void {
    if (!this.nuevoTipoDanoNombre.trim() || this.nuevoTipoDanoCategoriaId == null || !this.nuevoTipoDanoTipoCosto || this.nuevoTipoDanoValor == null || this.nuevoTipoDanoValor < 0) return;
    if (this.nuevoTipoDanoTipoCosto === 'PORCENTAJE' && this.nuevoTipoDanoValor > 100) return;
    this.devolucionService.crearTipoDano(this.nuevoTipoDanoNombre.trim(), this.nuevoTipoDanoCategoriaId, this.nuevoTipoDanoTipoCosto, this.nuevoTipoDanoValor).subscribe({
      next: () => {
        this.nuevoTipoDanoNombre = '';
        this.nuevoTipoDanoValor = null;
        this.cargarTiposDano();
      },
      error: (err) => { this.errorMsgTiposDano = err.message; }
    });
  }

  agregarCategoriaDano(): void {
    if (!this.nuevoCategoriaNombre.trim()) return;
    this.devolucionService.crearCategoriaDano(this.nuevoCategoriaNombre.trim()).subscribe({
      next: () => { this.nuevoCategoriaNombre = ''; this.cargarCategoriasDano(); },
      error: (err) => { this.errorMsgTiposDano = err.message; }
    });
  }

  eliminarTipoDano(tipo: TipoDano): void {
    this.devolucionService.eliminarTipoDano(tipo.id).subscribe({
      next: () => this.cargarTiposDano(),
      error: (err) => { this.errorMsgTiposDano = err.message; }
    });
  }

  // ===== Respaldo de datos =====
  get tablasSeleccionadas(): string[] {
    return Object.keys(this.backupTablasSeleccionadas).filter(k => this.backupTablasSeleccionadas[k]);
  }
  get puedeGenerarBackup(): boolean {
    if (!this.backupDesde || !this.backupHasta) return false;
    if (new Date(this.backupDesde) > new Date(this.backupHasta)) return false;
    const diffMs = new Date(this.backupHasta).getTime() - new Date(this.backupDesde).getTime();
    if (diffMs > 30 * 24 * 60 * 60 * 1000) return false;
    if (this.tablasSeleccionadas.length === 0) return false;
    return true;
  }
  get backupsFiltrados(): BackupEntry[] {
    let resultado = this.backups;
    if (this.filtroBackupFormato) resultado = resultado.filter(b => b.formato === this.filtroBackupFormato);
    if (this.filtroBackupFecha) resultado = resultado.filter(b => b.creadoEn.startsWith(this.filtroBackupFecha));
    if (this.filtroBackupDia) {
      const dia = parseInt(this.filtroBackupDia, 10);
      resultado = resultado.filter(b => new Date(b.creadoEn).getDate() === dia);
    }
    if (this.filtroBackupHora) {
      const hora = parseInt(this.filtroBackupHora, 10);
      resultado = resultado.filter(b => new Date(b.creadoEn).getHours() === hora);
    }
    return resultado;
  }
  get cadaHorasHabilitado(): boolean { return this.cadaHoras !== null && this.cadaDias === null; }
  get cadaDiasHabilitado(): boolean { return this.cadaDias !== null && this.cadaHoras === null; }
  get puedeProgramar(): boolean { return (this.cadaHoras !== null || this.cadaDias !== null) && this.tablasSeleccionadas.length > 0; }
  toggleTabla(tabla: string): void { this.backupTablasSeleccionadas[tabla] = !this.backupTablasSeleccionadas[tabla]; }
  get todasTablasSeleccionadas(): boolean { return this.backupTablasDisponibles.length > 0 && this.backupTablasDisponibles.every(t => !!this.backupTablasSeleccionadas[t]); }
  toggleTodasTablas(): void {
    const seleccionar = !this.todasTablasSeleccionadas;
    this.backupTablasDisponibles.forEach(t => this.backupTablasSeleccionadas[t] = seleccionar);
  }
  // Historial de exportaciones (Personalizado)
  cargarBackups(): void {
    this.cargandoBackup = true; this.errorMsgBackup = '';
    this.backupService.listar().subscribe({ next: (data) => { this.backups = data; this.cargandoBackup = false; }, error: (err) => { const detail = err?.error?.detail ?? err?.message ?? 'Error al cargar respaldos'; this.errorMsgBackup = detail; this.cargandoBackup = false; } });
  }
  cargarBackupsPorTipo(tipo: string): void {
    this.cargandoBackup = true; this.errorMsgBackup = '';
    this.backupService.listar().subscribe({
      next: (data) => { this.backups = data.filter((b: any) => b.tipo === tipo); this.cargandoBackup = false; },
      error: (err) => { const detail = err?.error?.detail ?? err?.message ?? 'Error al cargar respaldos'; this.errorMsgBackup = detail; this.cargandoBackup = false; }
    });
  }

  // Historial de Backups Completos (DR)
  registrosRespaldo: any[] = [];
  cargandoRegistros = false;
  configDR: any = null;
  cargandoConfigDR = false;
  frecuenciaHoras: number = 6;
  diasRetencion: number = 14;
  drHabilitado: boolean = false;
  guardandoConfigDR = false;

  cargarRegistrosRespaldo(tipo: string): void {
    this.cargandoRegistros = true;
    this.backupService.listarRegistrosRespaldo(tipo).subscribe({
      next: (data) => { this.registrosRespaldo = data; this.cargandoRegistros = false; },
      error: () => { this.cargandoRegistros = false; }
    });
  }
  cargarConfigDR(): void {
    this.cargandoConfigDR = true;
    this.backupService.obtenerConfigDR().subscribe({
      next: (cfg) => {
        this.configDR = cfg;
        this.frecuenciaHoras = cfg.frecuenciaHoras ?? 6;
        this.diasRetencion = cfg.diasRetencion ?? 14;
        this.drHabilitado = cfg.habilitado ?? false;
        this.cargandoConfigDR = false;
      },
      error: () => { this.cargandoConfigDR = false; }
    });
  }
  guardarConfigDR(): void {
    this.guardandoConfigDR = true;
    this.backupService.actualizarConfigDR({ frecuenciaHoras: this.frecuenciaHoras, diasRetencion: this.diasRetencion, habilitado: this.drHabilitado }).subscribe({
      next: (cfg) => { this.configDR = cfg; this.guardandoConfigDR = false; this.toast.success('Configuración guardada', 'Los cambios se aplicarán en el próximo ciclo del cron.'); },
      error: (err) => { this.guardandoConfigDR = false; this.toast.error('Error', err?.error?.detail ?? 'Error al guardar la configuración'); }
    });
  }
  dispararBackupCompleto(): void {
    this.generandoBackup = true;
    this.backupService.dispararBackupCompleto().subscribe({
      next: () => { 
        this.generandoBackup = false; 
        this.toast.success('Backup iniciado', 'El volcado completo se está ejecutando.'); 
        // Darle 1 segundo al microservicio de Node.js para que inserte el registro "EN_PROGRESO" en la BD
        setTimeout(() => {
          this.cargarRegistrosRespaldo(this.subSubmoduloSeleccionado?.includes('auto') ? 'automatico' : 'manual'); 
        }, 1000);
      },
      error: (err) => { 
        this.generandoBackup = false; 
        this.toast.error('Error', err?.error?.detail ?? 'Error al iniciar el backup'); 
      }
    });
  }

  cargarProgramaciones(): void {
    this.cargandoProgramaciones = true;
    this.backupService.listarProgramaciones().subscribe({ next: (data) => { this.programaciones = data; this.cargandoProgramaciones = false; }, error: () => { this.cargandoProgramaciones = false; } });
  }
  generarBackup(): void {
    if (!this.puedeGenerarBackup) return;
    this.generandoBackup = true; this.errorMsgBackup = '';
    this.backupService.generar({ desde: this.backupDesde, hasta: this.backupHasta, tablas: this.tablasSeleccionadas, formato: this.backupFormato }).subscribe({ next: () => { this.generandoBackup = false; this.toast.success('Respaldo generado', 'El archivo .zip se generó correctamente'); this.cargarBackups(); }, error: (err) => { this.generandoBackup = false; const detail = err?.error?.detail ?? err?.message ?? 'Error al generar el respaldo'; this.errorMsgBackup = detail; this.toast.error('Error', detail); } });
  }
  generarBackupAutomatico(): void {
    if (!this.puedeProgramar) return;
    this.generandoBackup = true; 
    this.errorMsgBackup = '';
    
    const payload = {
      tablas: this.tablasSeleccionadas,
      formato: this.backupFormato,
      cadaHoras: this.cadaHoras,
      cadaDias: this.cadaDias,
      activo: true
    };

    this.backupService.guardarProgramacion(payload).subscribe({ 
      next: (prog) => { 
        this.generandoBackup = false; 
        this.toast.success('Programación guardada', 'Backup programado con éxito'); 
        this.cargarProgramaciones(); 
      }, 
      error: (err) => { 
        this.generandoBackup = false; 
        const detail = err?.error?.detail ?? err?.message ?? 'Error al guardar la programación'; 
        this.errorMsgBackup = detail; 
        this.toast.error('Error', detail); 
      } 
    });
  }
  programarBackup(id: number): void {
    this.backupService.programar(id).subscribe({ next: (p) => { this.toast.success('Programado', 'Respaldo automático programado correctamente'); this.cargarProgramaciones(); }, error: (err) => { const detail = err?.error?.detail ?? err?.message ?? 'Error al programar'; this.toast.error('Error', detail); } });
  }
  ejecutarAhora(id: number): void {
    this.backupService.ejecutarAhora(id).subscribe({ next: () => { this.toast.success('Ejecutado', 'Backup ejecutado ahora mismo'); this.cargarProgramaciones(); }, error: (err) => { const detail = err?.error?.detail ?? err?.message ?? 'Error al ejecutar'; this.toast.error('Error', detail); } });
  }
  descargarBackup(entry: BackupEntry): void {
    this.backupService.descargar(entry.id).subscribe({ next: (blob) => { const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `backup-${entry.id}.zip`; a.click(); URL.revokeObjectURL(url); this.toast.success('Descarga iniciada', 'El respaldo se está descargando'); }, error: (err) => { const detail = err?.error?.detail ?? 'Error al descargar el respaldo'; this.toast.error('Error', detail); } });
  }
  borrarBackup(entry: BackupEntry): void {
    this.backupService.eliminar(entry.id).subscribe({ next: () => { this.toast.success('Eliminado', 'Respaldo eliminado del historial'); this.cargarBackups(); }, error: (err) => { const detail = err?.error?.detail ?? 'Error al eliminar el respaldo'; this.errorMsgBackup = detail; this.toast.error('Error', detail); } });
  }
  formatearTamano(entry: BackupEntry): string {
    if (entry.tamano) return entry.tamano;
    if (entry.tamanoBytes == null) return '—';
    const kb = entry.tamanoBytes / 1024; if (kb < 1024) return `${kb.toFixed(1)} KB`; return `${(kb / 1024).toFixed(2)} MB`;
  }
  cambiarModo(modo: 'manual' | 'automatico'): void {
    this.backupModo = modo;
    if (modo === 'automatico') { this.cargarProgramaciones(); }
  }
  limpiarFiltros(): void { this.filtroBackupFecha = ''; this.filtroBackupDia = ''; this.filtroBackupHora = ''; }
  get filtrosActivos(): boolean { return !!this.filtroBackupFecha || !!this.filtroBackupDia || !!this.filtroBackupHora; }
}
