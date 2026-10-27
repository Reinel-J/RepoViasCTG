export type EstadoReporte =
  | 'PENDIENTE'
  | 'EN_REVISION'
  | 'EN_PROCESO'
  | 'RESUELTO'
  | 'RECHAZADO'

export type PrioridadReporte = 'BAJA' | 'MEDIA' | 'ALTA'

export type Rol = 'CIUDADANO' | 'MODERADOR' | 'ADMIN'

export interface UsuarioResponse {
  id: string
  nombre: string
  email: string
  telefono: string | null
  rol: Rol
  activo: boolean
  emailVerificado: boolean
  fechaRegistro: string
}

export interface AuthResponse {
  token: string
  tipo: string
  usuario: UsuarioResponse
}

export interface LoginRequest {
  email: string
  password: string
}

export interface UsuarioRegistroRequest {
  nombre: string
  email: string
  password: string
  telefono?: string
}

export interface ReporteCrearRequest {
  categoriaId: string
  descripcion: string
  latitud: number
  longitud: number
}

export interface ReporteActualizarRequest {
  descripcion: string
  latitud: number
  longitud: number
}

export interface ComentarioCrearRequest {
  texto: string
}

export interface CambioEstadoRequest {
  nuevoEstado: EstadoReporte
  comentario?: string
  prioridad?: PrioridadReporte
}

export interface HistorialEstadoResponse {
  id: string
  usuarioAdminId: string
  estadoAnterior: EstadoReporte
  estadoNuevo: EstadoReporte
  comentarioAdmin: string | null
  fecha: string
}

export interface ReporteResponse {
  id: string
  usuarioId: string
  calleId: string | null
  categoriaId: string
  direccionOsm: string | null
  // Barrio según OpenStreetMap; null en reportes antiguos o si no se pudo resolver.
  barrioOsm: string | null
  descripcion: string
  fotos: string[]
  latitud: number
  longitud: number
  estado: EstadoReporte
  prioridad: PrioridadReporte
  fechaCreacion: string
  fechaActualizacion: string
  historialEstados: HistorialEstadoResponse[]
}

// Cifras públicas de la portada (GET /reportes/resumen).
export interface ResumenReportesResponse {
  total: number
  porEstado: Record<EstadoReporte, number>
  creadosUltimas24Horas: number
  barrios: number
}

export interface GeocodingInversoResponse {
  direccionOsm: string | null
  barrioOsm: string | null
  // false si el punto está fuera del área de servicio (Cartagena): el backend rechazaría el reporte.
  dentroDelArea: boolean
}

export interface PosibleDuplicadoResponse {
  reporte: ReporteResponse
  distanciaMetros: number
  confirmaciones: number
}

export interface SeguimientoResumenResponse {
  reporteId: string
  arreglado: number
  sigue: number
  ultimaRespuesta: string | null
  // null si el usuario no ha respondido o no inició sesión.
  miRespuesta: boolean | null
  // Cuándo se cerrará solo por los votos (cierre por la comunidad); null si no hay propuesta de cierre.
  cierrePrevisto: string | null
}

export interface EstadisticasResponse {
  total: number
  porEstado: Record<EstadoReporte, number>
  // Sobre los reportes no rechazados; null si no hay ninguno.
  porcentajeResuelto: number | null
  diasPrimeraRespuesta: number | null
  diasResolucion: number | null
  // Últimos 12 meses, del más antiguo al actual; mes en formato yyyy-MM.
  meses: { mes: string; creados: number; resueltos: number }[]
  categorias: { nombre: string; total: number; resueltos: number }[]
  // Celdas de ~110 m con los reportes activos que contienen.
  densidad: { latitud: number; longitud: number; total: number }[]
}

export interface CalleResponse {
  id: string
  nombre: string
  codigoPostal: string | null
}

export interface BarrioResponse {
  id: string
  nombre: string
  localidad: string | null
  calles: CalleResponse[]
}

export interface CategoriaResponse {
  id: string
  nombre: string
  descripcion: string | null
  activa: boolean
}

export interface ComentarioResponse {
  id: string
  reporteId: string
  usuarioId: string
  usuarioNombre: string | null
  texto: string
  fecha: string
  editado: boolean
}

export interface ConfirmacionResponse {
  id: string
  reporteId: string
  usuarioId: string
  fecha: string
}

export interface UsuarioActualizacionRequest {
  nombre?: string
  telefono?: string
}

export interface NotificacionResponse {
  id: string
  usuarioId: string
  reporteId: string | null
  mensaje: string
  leida: boolean
  fecha: string
}

export interface BarrioRequest {
  nombre: string
  localidad: string
}

export interface CalleRequest {
  nombre: string
  codigoPostal: string
}

export interface CategoriaRequest {
  nombre: string
  descripcion?: string
}
