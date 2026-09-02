// Contratos exactos de PrestamosGestionController (/api/v1/prestamos/gestion),
// ver los DTOs en backend-springboot. Módulo de ventanilla del bibliotecario:
// buscar usuario por cédula, reserva vigente e historial reciente.

// UsuarioPrestamosGestionDTO: tarjeta de identificación del usuario
// encontrado. "cedula" es usuarios.identificacion_usuario; "tiposUsuario"
// son los nombres de sus roles; "estadoCuenta" el nombre del catálogo
// estados_usuario (ACTIVO / BLOQUEADO_POR_MULTA / INACTIVO /
// PENDIENTE_VERIFICACION); las multas pendientes viajan calculadas.
export interface UsuarioPrestamos {
  id: number;
  nombreCompleto: string;
  cedula: string | null;
  correo: string;
  tiposUsuario: string[];
  estadoCuenta: string;
  montoMultasPendientes: number;
  cantidadMultasPendientes: number;
  diasPrestamoSugerido: number;
}

// ReservaActivaDTO: reserva vigente (PENDIENTE o LISTA_PARA_RETIRO) que la
// pantalla convierte en préstamo ("Confirmar Entrega"). El backend responde
// 404 cuando no existe -> Caso B (préstamo directo).
export interface ReservaActiva {
  reservacionId: number;
  libroId: number;
  titulo: string;
  autores: string[];
  isbn: string;
  fechaReserva: string;
  fechaLimiteRetiro: string;
  diasPrestamoSugerido: number;
  anioPublicacion: number;
  stockDisponible: number;
  stockTotal: number;
  ubicacionFisica: string | null;
  categorias: string[];
  tienePortada: boolean;
}

// HistorialPrestamoDTO: línea de tiempo del historial reciente. El ícono y
// el texto secundario se derivan de estadoNombre + multaPendiente.
export interface HistorialPrestamo {
  prestamoId: number;
  libroId: number;
  libroTitulo: string;
  libroIsbn: string;
  autores: string[];
  categorias: string[];
  fechaPrestamo: string;
  fechaDevolucionEstimada: string;
  fechaDevolucionReal: string | null;
  estadoNombre: string;
  multaPendiente: boolean;
  montoMultaPendiente: number;
  usuarioNombre: string;
  usuarioCorreo: string;
}

// UsuarioSugerenciaDTO: resultado ligero para el autocompletado predictivo
// de usuarios por correo (GET /api/v1/prestamos/gestion/sugerencias-usuarios).
export interface UsuarioSugerencia {
  id: number;
  nombreCompleto: string;
  correo: string;
  estadoCuenta: string;
}
