import { isAxiosError } from 'axios'
import client from './client'
import type { AuthResponse, LoginRequest, UsuarioRegistroRequest, UsuarioResponse } from './types'

export async function login(email: string, password: string): Promise<AuthResponse> {
  const request: LoginRequest = { email, password }
  const response = await client.post<AuthResponse>('/auth/login', request)
  return response.data
}

/** Crea la cuenta sin iniciar sesión: el backend envía un enlace para confirmar el correo. */
export async function registro(nombre: string, email: string, password: string): Promise<UsuarioResponse> {
  const request: UsuarioRegistroRequest = { nombre, email, password }
  const response = await client.post<UsuarioResponse>('/auth/registro', request)
  return response.data
}

/** Confirma el correo con el token del enlace; la respuesta trae la sesión ya iniciada. */
export async function verificarCorreo(token: string): Promise<AuthResponse> {
  const response = await client.post<AuthResponse>('/auth/verificar', { token })
  return response.data
}

export async function reenviarVerificacion(email: string): Promise<void> {
  await client.post('/auth/reenviar-verificacion', { email })
}

/** En el login, 403 significa contraseña correcta pero correo sin confirmar (las credenciales malas dan 401). */
export function esCorreoSinConfirmar(error: unknown) {
  return isAxiosError(error) && error.response?.status === 403
}

export async function recuperar(email: string): Promise<void> {
  await client.post('/auth/recuperar', { email })
}

export async function restablecer(token: string, passwordNueva: string): Promise<void> {
  await client.post('/auth/restablecer', { token, passwordNueva })
}
