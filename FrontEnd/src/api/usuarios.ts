import client from './client'
import type { UsuarioActualizacionRequest, UsuarioResponse } from './types'

export async function perfil(): Promise<UsuarioResponse> {
  const response = await client.get<UsuarioResponse>('/usuarios/me')
  return response.data
}

export async function actualizarPerfil(data: UsuarioActualizacionRequest): Promise<UsuarioResponse> {
  const response = await client.put<UsuarioResponse>('/usuarios/me', data)
  return response.data
}
