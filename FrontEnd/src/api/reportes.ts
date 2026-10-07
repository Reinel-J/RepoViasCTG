import client from './client'
import type {
  EstadoReporte,
  ReporteActualizarRequest,
  ReporteCrearRequest,
  ReporteResponse,
} from './types'

export async function listar(estado?: EstadoReporte): Promise<ReporteResponse[]> {
  const response = await client.get<ReporteResponse[]>('/reportes', {
    params: estado ? { estado } : undefined,
  })
  return response.data
}

export async function obtener(id: string): Promise<ReporteResponse> {
  const response = await client.get<ReporteResponse>(`/reportes/${id}`)
  return response.data
}

export async function crear(data: ReporteCrearRequest): Promise<ReporteResponse> {
  const response = await client.post<ReporteResponse>('/reportes', data)
  return response.data
}

export async function actualizar(id: string, data: ReporteActualizarRequest): Promise<ReporteResponse> {
  const response = await client.put<ReporteResponse>(`/reportes/${id}`, data)
  return response.data
}

export async function cercanos(
  lat: number,
  lng: number,
  radioKm: number,
  estado?: EstadoReporte,
): Promise<ReporteResponse[]> {
  const response = await client.get<ReporteResponse[]>('/reportes/cercanos', {
    params: { lat, lng, radioKm, ...(estado ? { estado } : {}) },
  })
  return response.data
}
