import client from './client'
import type { ConfirmacionResponse } from './types'

export async function listar(reporteId: string): Promise<ConfirmacionResponse[]> {
  const response = await client.get<ConfirmacionResponse[]>(`/reportes/${reporteId}/confirmaciones`)
  return response.data
}

export async function crear(reporteId: string): Promise<ConfirmacionResponse> {
  const response = await client.post<ConfirmacionResponse>(`/reportes/${reporteId}/confirmaciones`)
  return response.data
}

export async function retirar(reporteId: string): Promise<void> {
  await client.delete(`/reportes/${reporteId}/confirmaciones/me`)
}
