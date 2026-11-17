import client from './client'
import type {
  CambioEstadoRequest,
  CambioEstadoLoteRequest,
  CambioEstadoLoteResponse,
  DashboardResponse,
  EstadoReplicaResponse,
  EstadisticasResponse,
  EstadoReporte,
  HistorialEstadoResponse,
  PrioridadReporte,
  ReporteResponse,
  Rol,
  SeguimientoResumenResponse,
  UsuarioResponse,
  PaginaResponse,
  RegistroAuditoriaResponse,
} from './types'

export async function listarPendientes(): Promise<ReporteResponse[]> {
  const response = await client.get<ReporteResponse[]>('/admin/reportes/pendientes')
  return response.data
}

export function fechaLocalAISO(valor?: string): string | undefined {
  if (!valor) return undefined
  const fecha = new Date(valor)
  return Number.isNaN(fecha.getTime()) ? undefined : fecha.toISOString()
}

export async function auditoria(params: { entidad?: string; accion?: string; desde?: string; hasta?: string; pagina?: number; tamano?: number }): Promise<PaginaResponse<RegistroAuditoriaResponse>> {
  const response = await client.get<PaginaResponse<RegistroAuditoriaResponse>>('/admin/auditoria', { params })
  return response.data
}

export async function cambiarEstadoLote(request: CambioEstadoLoteRequest): Promise<CambioEstadoLoteResponse> {
  const response = await client.post<CambioEstadoLoteResponse>('/admin/reportes/estado-lote', request)
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

export async function estadisticas(): Promise<EstadisticasResponse> {
  const response = await client.get<EstadisticasResponse>('/admin/estadisticas')
  return response.data
}

// URL firmada del dashboard de Metabase; 503 si el backend no lo tiene configurado.
export async function obtenerDashboard(): Promise<DashboardResponse> {
  const response = await client.get<DashboardResponse>('/admin/dashboard')
  return response.data
}

// Votos "¿ya lo arreglaron?" de todos los reportes que tienen alguno.
export async function seguimientos(): Promise<SeguimientoResumenResponse[]> {
  const response = await client.get<SeguimientoResumenResponse[]>('/admin/reportes/seguimiento')
  return response.data
}

// Ejecuta ya la tarea diaria: recordatorios y cierres por la comunidad cuya espera venció.
export async function enviarRecordatorios(): Promise<{ reportesRecordados: number; reportesCerrados: number }> {
  const response = await client.post<{ reportesRecordados: number; reportesCerrados: number }>('/admin/reportes/seguimiento/recordatorios')
  return response.data
}

// Descarta la propuesta de cierre por los votos: solo contarán los que lleguen desde ahora.
export async function mantenerAbierto(reporteId: string): Promise<SeguimientoResumenResponse> {
  const response = await client.post<SeguimientoResumenResponse>(`/admin/reportes/${reporteId}/mantener-abierto`)
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

// Base secundaria: registros por colección en MongoDB y filas por tabla en PostgreSQL.
export async function estadoReplica(): Promise<EstadoReplicaResponse> {
  const response = await client.get<EstadoReplicaResponse>('/admin/replica-sql')
  return response.data
}

// Reconstruye la réplica completa en segundo plano (202): el estado indica cuándo terminó.
export async function reconstruirReplica(): Promise<void> {
  await client.post('/admin/replica-sql/reconstruir')
}
