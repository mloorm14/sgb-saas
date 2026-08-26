import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfiguracionSistemaService, ParametroConfiguracion } from '../core/services/configuracion-sistema.service';
import { DevolucionService } from '../core/services/devolucion.service';
import { AuthService } from '../core/services/auth.service';
import { TipoDano, CategoriaDano } from '../core/models/devoluciones.model';

const VALOR_MAX_LENGTH = 200;

interface MetaParametro {
  titulo: string;
  descripcion: string;
  icono: string;
  grupo: string;
  sufijo?: string;
  tipo?: 'texto' | 'numero' | 'lista';
}

const METADATOS: Record<string, MetaParametro> = {
  dias_prestamo_default: {
    titulo: 'Días de préstamo por defecto',
    descripcion: 'Cuánto tiempo dura un préstamo antes de vencer',
    icono: 'calendar_month',
    grupo: 'Préstamos y reservas',
    sufijo: 'días',
    tipo: 'numero'
  },
  max_renovaciones_default: {
    titulo: 'Máximo de renovaciones',
    descripcion: 'Veces que un lector puede renovar el mismo préstamo',
    icono: 'autorenew',
    grupo: 'Préstamos y reservas',
    sufijo: 'veces',
    tipo: 'numero'
  },
  max_prestamos_usuario: {
    titulo: 'Máximo de préstamos simultáneos',
    descripcion: 'Cantidad máxima de libros que un lector puede tener prestados al mismo tiempo',
    icono: 'menu_book',
    grupo: 'Préstamos y reservas',
    sufijo: 'libros',
    tipo: 'numero'
  },
  minutos_reserva: {
    titulo: 'Minutos para retirar una reserva',
    descripcion: 'Tiempo antes de que una reserva caduque automáticamente',
    icono: 'event_busy',
    grupo: 'Préstamos y reservas',
    sufijo: 'min',
    tipo: 'numero'
  },
  dias_anticipacion_vencimiento: {
    titulo: 'Días de anticipación para recordatorio',
    descripcion: 'Días antes del vencimiento para enviar notificación de recordatorio',
    icono: 'notifications_active',
    grupo: 'Notificaciones',
    sufijo: 'días',
    tipo: 'numero'
  },
  monto_multa_diaria: {
    titulo: 'Monto de multa por día de atraso',
    descripcion: 'Se cobra por cada día de retraso en la devolución',
    icono: 'payments',
    grupo: 'Multas',
    sufijo: '$',
    tipo: 'numero'
  },
  max_tamano_portada_mb: {
    titulo: 'Tamaño máximo de portada',
    descripcion: 'Límite para las imágenes de portada subidas por el bibliotecario',
    icono: 'image',
    grupo: 'Archivos',
    sufijo: 'MB',
    tipo: 'numero'
  },
  correo_dominios_permitidos: {
    titulo: 'Dominios de correo permitidos',
    descripcion: 'Dominios separados por coma. Si está vacío, se aceptan todos.',
    icono: 'mail_lock',
    grupo: 'Registro',
    tipo: 'texto'
  },
  max_tamano_evidencia_mb: {
    titulo: 'Tamaño máximo de evidencia',
    descripcion: 'Límite para los archivos de evidencia de daño subidos por el bibliotecario',
    icono: 'attach_file',
    grupo: 'Archivos',
    sufijo: 'MB',
    tipo: 'numero'
  }
};

const ORDEN_GRUPOS = ['Préstamos y reservas', 'Notificaciones', 'Multas', 'Registro', 'Archivos', 'Otros'];

interface GrupoParametros {
  nombre: string;
  parametros: (ParametroConfiguracion & { meta: MetaParametro })[];
}

@Component({
  selector: 'app-configuracion-sistema',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './configuracion-sistema.component.html'
})
export class ConfiguracionSistemaComponent implements OnInit {
  valorMaxLength = VALOR_MAX_LENGTH;
  esAdmin: boolean = false;

  configuraciones: ParametroConfiguracion[] = [];
  grupos: GrupoParametros[] = [];
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

  constructor(
    private configuracionService: ConfiguracionSistemaService,
    private devolucionService: DevolucionService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.esAdmin = this.authService.hasRole('ADMIN');
    if (this.esAdmin) {
      this.cargarConfiguraciones();
      this.cargarCategoriasDano();
      this.cargarTiposDano();
    }
  }

  private cargarConfiguraciones(): void {
    this.cargando = true;
    this.errorMsg = '';
    this.configuracionService.listar().subscribe({
      next: (data) => {
        this.configuraciones = data;
        this.armarGrupos();
        this.cargando = false;
      },
      error: () => {
        this.errorMsg = 'Error al cargar la configuración del sistema';
        this.cargando = false;
      }
    });
  }

  private armarGrupos(): void {
    const porGrupo = new Map<string, (ParametroConfiguracion & { meta: MetaParametro })[]>();
    for (const config of this.configuraciones) {
      const meta = METADATOS[config.clave] ?? {
        titulo: config.clave,
        descripcion: '',
        icono: 'settings',
        grupo: 'Otros'
      };
      const item = { ...config, meta };
      const lista = porGrupo.get(meta.grupo) ?? [];
      lista.push(item);
      porGrupo.set(meta.grupo, lista);
    }
    this.grupos = [...porGrupo.entries()]
      .map(([nombre, parametros]) => ({ nombre, parametros }))
      .sort((a, b) => {
        const ia = ORDEN_GRUPOS.indexOf(a.nombre);
        const ib = ORDEN_GRUPOS.indexOf(b.nombre);
        return (ia === -1 ? ORDEN_GRUPOS.length : ia) - (ib === -1 ? ORDEN_GRUPOS.length : ib);
      });
  }

  iniciarEdicion(config: ParametroConfiguracion): void {
    this.claveEditando = config.clave;
    this.valorEditando = config.valor;
  }

  cancelarEdicion(): void {
    this.claveEditando = null;
    this.valorEditando = '';
  }

  get puedeGuardarValor(): boolean {
    const valor = this.valorEditando.trim();
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
    const valor = this.valorEditando.trim();
    if (!valor || valor.length > VALOR_MAX_LENGTH) return;

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
}
