import client from './client'
import type { CategoriaRequest, CategoriaResponse } from './types'

export async function listar(soloActivas = true): Promise<CategoriaResponse[]> {
  const response = await client.get<CategoriaResponse[]>('/categorias', { params: { soloActivas } })
  return response.data
}

export async function crear(data: CategoriaRequest): Promise<CategoriaResponse> {
  const response = await client.post<CategoriaResponse>('/categorias', data)
  return response.data
}

export async function actualizar(id: string, data: CategoriaRequest): Promise<CategoriaResponse> {
  const response = await client.put<CategoriaResponse>(`/categorias/${id}`, data)
  return response.data
}

export async function cambiarEstado(id: string, activa: boolean): Promise<CategoriaResponse> {
  const response = await client.patch<CategoriaResponse>(`/categorias/${id}/estado`, null, { params: { activa } })
  return response.data
}
