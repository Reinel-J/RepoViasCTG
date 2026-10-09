import { createContext } from 'react'
import type { AuthResponse, Rol } from '../api/types'

export interface AuthSession {
  token: string
  usuarioId: string
  nombre: string
  rol: Rol
}

export interface AuthContextValue {
  session: AuthSession | null
  login: (email: string, password: string) => Promise<void>
  registro: (nombre: string, email: string, password: string) => Promise<void>
  logout: () => void
  actualizarNombre: (nombre: string) => void
  /** Guarda el token nuevo que devuelve el backend al cambiar la contraseña. */
  renovarSesion: (response: AuthResponse) => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
