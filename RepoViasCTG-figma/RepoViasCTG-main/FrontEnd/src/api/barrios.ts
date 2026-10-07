import client from './client'
import type { BarrioResponse } from './types'

export async function listar(): Promise<BarrioResponse[]> {
  const response = await client.get<BarrioResponse[]>('/barrios')
  return response.data
}
