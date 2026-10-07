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
  calleId: string
  categoriaId: string
  descripcion: string
  fotoUrl?: string
  latitud: number
  longitud: number
  prioridad: PrioridadReporte
}

export interface ReporteActualizarRequest {
  descripcion: string
  fotoUrl?: string
  latitud: number
  longitud: number
  prioridad: PrioridadReporte
}

export interface ComentarioCrearRequest {
  texto: string
}

export interface CambioEstadoRequest {
  nuevoEstado: EstadoReporte
  comentario?: string
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
  calleId: string
  categoriaId: string
  descripcion: string
  fotoUrl: string | null
  latitud: number
  longitud: number
  estado: EstadoReporte
  prioridad: PrioridadReporte
  fechaCreacion: string
  fechaActualizacion: string
  historialEstados: HistorialEstadoResponse[]
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
