import client from './client'
import type { BarrioRequest, BarrioResponse, CalleRequest } from './types'

export async function listar(): Promise<BarrioResponse[]> {
  const response = await client.get<BarrioResponse[]>('/barrios')
  return response.data
}

export async function crear(data: BarrioRequest): Promise<BarrioResponse> {
  const response = await client.post<BarrioResponse>('/barrios', data)
  return response.data
}

export async function actualizar(id: string, data: BarrioRequest): Promise<BarrioResponse> {
  const response = await client.put<BarrioResponse>(`/barrios/${id}`, data)
  return response.data
}

export async function eliminar(id: string): Promise<void> {
  await client.delete(`/barrios/${id}`)
}

export async function agregarCalle(barrioId: string, data: CalleRequest): Promise<BarrioResponse> {
  const response = await client.post<BarrioResponse>(`/barrios/${barrioId}/calles`, data)
  return response.data
}

export async function eliminarCalle(barrioId: string, calleId: string): Promise<BarrioResponse> {
  const response = await client.delete<BarrioResponse>(`/barrios/${barrioId}/calles/${calleId}`)
  return response.data
}
