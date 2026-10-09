import client from './client'
import type { AuthResponse, UsuarioActualizacionRequest, UsuarioResponse } from './types'

export async function perfil(): Promise<UsuarioResponse> {
  const response = await client.get<UsuarioResponse>('/usuarios/me')
  return response.data
}

export async function actualizarPerfil(data: UsuarioActualizacionRequest): Promise<UsuarioResponse> {
  const response = await client.put<UsuarioResponse>('/usuarios/me', data)
  return response.data
}

export async function cambiarPassword(passwordActual: string, passwordNueva: string): Promise<AuthResponse> {
  const response = await client.put<AuthResponse>('/usuarios/me/password', { passwordActual, passwordNueva })
  return response.data
}

export async function cerrarSesiones(): Promise<void> {
  await client.post('/usuarios/me/cerrar-sesiones')
}
