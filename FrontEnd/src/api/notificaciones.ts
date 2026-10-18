import client from './client'
import type { NotificacionResponse } from './types'

export async function listar(): Promise<NotificacionResponse[]> {
  const response = await client.get<NotificacionResponse[]>('/notificaciones')
  return response.data
}

// Solo el número de notificaciones sin leer (lo consulta la campana de la cabecera).
export async function contarSinLeer(): Promise<number> {
  const response = await client.get<{ total: number }>('/notificaciones/sin-leer')
  return response.data.total
}

export async function marcarLeida(id: string): Promise<NotificacionResponse> {
  const response = await client.patch<NotificacionResponse>(`/notificaciones/${id}/leida`)
  return response.data
}

export async function marcarTodasLeidas(): Promise<number> {
  const response = await client.patch<{ marcadas: number }>('/notificaciones/leidas')
  return response.data.marcadas
}
