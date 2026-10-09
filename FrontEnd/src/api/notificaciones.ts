import client from './client'
import type { NotificacionResponse } from './types'

export async function listar(): Promise<NotificacionResponse[]> {
  const response = await client.get<NotificacionResponse[]>('/notificaciones')
  return response.data
}

export async function marcarLeida(id: string): Promise<NotificacionResponse> {
  const response = await client.patch<NotificacionResponse>(`/notificaciones/${id}/leida`)
  return response.data
}
