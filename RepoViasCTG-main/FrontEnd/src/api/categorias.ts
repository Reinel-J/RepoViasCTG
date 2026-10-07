import client from './client'
import type { CategoriaResponse } from './types'

export async function listar(): Promise<CategoriaResponse[]> {
  const response = await client.get<CategoriaResponse[]>('/categorias')
  return response.data
}
