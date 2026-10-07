import client from './client'
import type { CambioEstadoRequest, EstadoReporte, ReporteResponse } from './types'

export async function listarPendientes(): Promise<ReporteResponse[]> {
  const response = await client.get<ReporteResponse[]>('/admin/reportes/pendientes')
  return response.data
}

export async function cambiarEstado(
  reporteId: string,
  nuevoEstado: EstadoReporte,
  comentario?: string,
): Promise<ReporteResponse> {
  const request: CambioEstadoRequest = { nuevoEstado, comentario }
  const response = await client.patch<ReporteResponse>(`/admin/reportes/${reporteId}/estado`, request)
  return response.data
}
