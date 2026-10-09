import client from './client'
import type { AuthResponse, LoginRequest, UsuarioRegistroRequest } from './types'

export async function login(email: string, password: string): Promise<AuthResponse> {
  const request: LoginRequest = { email, password }
  const response = await client.post<AuthResponse>('/auth/login', request)
  return response.data
}

export async function registro(nombre: string, email: string, password: string): Promise<AuthResponse> {
  const request: UsuarioRegistroRequest = { nombre, email, password }
  const response = await client.post<AuthResponse>('/auth/registro', request)
  return response.data
}

export async function recuperar(email: string): Promise<void> {
  await client.post('/auth/recuperar', { email })
}

export async function restablecer(token: string, passwordNueva: string): Promise<void> {
  await client.post('/auth/restablecer', { token, passwordNueva })
}
