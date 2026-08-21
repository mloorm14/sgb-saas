import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfiguracionSistemaService, ParametroConfiguracion } from '../core/services/configuracion-sistema.service';
import { AuthService } from '../core/services/auth.service';

const VALOR_MAX_LENGTH = 200;

interface MetaParametro {
  titulo: string;
  descripcion: string;
  icono: string;
  grupo: string;
  sufijo?: string;
}

// Metadatos de presentacion por clave conocida (mockup 25-configuracion-
// sistema-v2: tarjetas agrupadas por categoria). Las claves que no esten
// aca caen en el grupo "Otros" mostrando la clave cruda.
const METADATOS: Record<string, MetaParametro> = {
  dias_prestamo_default: {
    titulo: 'Días de préstamo por defecto',
    descripcion: 'Cuánto tiempo dura un préstamo antes de vencer',
    icono: 'calendar_month',
    grupo: 'Préstamos y reservas',
    sufijo: 'días'
  },
  max_renovaciones_default: {
    titulo: 'Máximo de renovaciones',
    descripcion: 'Veces que un lector puede renovar el mismo préstamo',
    icono: 'autorenew',
    grupo: 'Préstamos y reservas',
    sufijo: 'veces'
  },
  minutos_reserva: {
    titulo: 'Minutos para retirar una reserva',
    descripcion: 'Tiempo antes de que una reserva caduque automáticamente',
    icono: 'event_busy',
    grupo: 'Préstamos y reservas',
    sufijo: 'min'
  },
  monto_multa_diaria: {
    titulo: 'Monto de multa por día de atraso',
    descripcion: 'Se cobra por cada día de retraso en la devolución',
    icono: 'payments',
    grupo: 'Multas',
    sufijo: '$'
  },
  max_tamano_portada_mb: {
    titulo: 'Tamaño máximo de portada',
    descripcion: 'Límite para las imágenes de portada subidas por el bibliotecario',
    icono: 'image',
    grupo: 'Archivos',
    sufijo: 'MB'
  }
};

const ORDEN_GRUPOS = ['Préstamos y reservas', 'Multas', 'Archivos', 'Otros'];

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

  // El route guard (roleGuard('ADMIN')) ya bloquea la navegación directa
  // por URL, pero este chequeo queda igual acá como defensa en profundidad
  // por si el componente se instancia por otra vía (o el guard cambia).
  esAdmin: boolean = false;

  configuraciones: ParametroConfiguracion[] = [];
  grupos: GrupoParametros[] = [];
  cargando: boolean = false;
  errorMsg: string = '';

  claveEditando: string | null = null;
  valorEditando: string = '';

  constructor(
    private configuracionService: ConfiguracionSistemaService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.esAdmin = this.authService.hasRole('ADMIN');
    if (this.esAdmin) {
      this.cargarConfiguraciones();
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
          ? 'No tenés permisos para modificar la configuración del sistema'
          : 'Error al actualizar el valor de configuración';
      }
    });
  }
}
