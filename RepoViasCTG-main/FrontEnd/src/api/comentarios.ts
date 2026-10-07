import client from './client'
import type { ComentarioCrearRequest, ComentarioResponse } from './types'

export async function listar(reporteId: string): Promise<ComentarioResponse[]> {
  const response = await client.get<ComentarioResponse[]>(`/reportes/${reporteId}/comentarios`)
  return response.data
}

export async function crear(reporteId: string, texto: string): Promise<ComentarioResponse> {
  const request: ComentarioCrearRequest = { texto }
  const response = await client.post<ComentarioResponse>(`/reportes/${reporteId}/comentarios`, request)
  return response.data
}
