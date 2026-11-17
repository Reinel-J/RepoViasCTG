import client from './client'
import type { SeguimientoResumenResponse } from './types'

export async function obtener(reporteId: string): Promise<SeguimientoResumenResponse> {
  const response = await client.get<SeguimientoResumenResponse>(`/reportes/${reporteId}/seguimiento`)
  return response.data
}

export async function responder(reporteId: string, arreglado: boolean): Promise<SeguimientoResumenResponse> {
  const response = await client.post<SeguimientoResumenResponse>(`/reportes/${reporteId}/seguimiento`, { arreglado })
  return response.data
}
