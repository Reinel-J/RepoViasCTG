import client from './client'
import type {
  CambioEstadoRequest,
  EstadoReporte,
  HistorialEstadoResponse,
  PrioridadReporte,
  ReporteResponse,
  Rol,
  UsuarioResponse,
} from './types'

export async function listarPendientes(): Promise<ReporteResponse[]> {
  const response = await client.get<ReporteResponse[]>('/admin/reportes/pendientes')
  return response.data
}

export async function cambiarEstado(
  reporteId: string,
  nuevoEstado: EstadoReporte,
  comentario?: string,
  prioridad?: PrioridadReporte,
): Promise<ReporteResponse> {
  const request: CambioEstadoRequest = { nuevoEstado, comentario, prioridad }
  const response = await client.patch<ReporteResponse>(`/admin/reportes/${reporteId}/estado`, request)
  return response.data
}

export async function historial(reporteId: string): Promise<HistorialEstadoResponse[]> {
  const response = await client.get<HistorialEstadoResponse[]>(`/admin/reportes/${reporteId}/historial`)
  return response.data
}

export async function listarUsuarios(): Promise<UsuarioResponse[]> {
  const response = await client.get<UsuarioResponse[]>('/usuarios')
  return response.data
}

export async function cambiarRol(usuarioId: string, rol: Rol): Promise<UsuarioResponse> {
  const response = await client.patch<UsuarioResponse>(`/usuarios/${usuarioId}/rol`, { rol })
  return response.data
}

export async function cambiarActivo(usuarioId: string, activo: boolean): Promise<UsuarioResponse> {
  const response = await client.patch<UsuarioResponse>(`/usuarios/${usuarioId}/activo`, null, { params: { activo } })
  return response.data
}
