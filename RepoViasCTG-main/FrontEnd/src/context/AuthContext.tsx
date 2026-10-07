import { createContext, useContext, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import type { AuthResponse, Rol } from '../api/types'

interface AuthSession {
  token: string
  usuarioId: string
  nombre: string
  rol: Rol
}

interface AuthContextValue {
  session: AuthSession | null
  login: (email: string, password: string) => Promise<void>
  registro: (nombre: string, email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)
const SESSION_STORAGE_KEY = 'authSession'

function obtenerSesionGuardada(): AuthSession | null {
  const sessionJson = localStorage.getItem(SESSION_STORAGE_KEY)

  if (!sessionJson) {
    return null
  }

  try {
    return JSON.parse(sessionJson) as AuthSession
  } catch {
    localStorage.removeItem(SESSION_STORAGE_KEY)
    localStorage.removeItem('token')
    return null
  }
}

function convertirRespuestaEnSesion(response: AuthResponse): AuthSession {
  return {
    token: response.token,
    usuarioId: response.usuario.id,
    nombre: response.usuario.nombre,
    rol: response.usuario.rol,
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(obtenerSesionGuardada)

  function guardarSesion(response: AuthResponse) {
    const nuevaSesion = convertirRespuestaEnSesion(response)
    localStorage.setItem('token', nuevaSesion.token)
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(nuevaSesion))
    setSession(nuevaSesion)
  }

  async function login(email: string, password: string) {
    guardarSesion(await authApi.login(email, password))
  }

  async function registro(nombre: string, email: string, password: string) {
    guardarSesion(await authApi.registro(nombre, email, password))
  }

  function logout() {
    localStorage.removeItem('token')
    localStorage.removeItem(SESSION_STORAGE_KEY)
    setSession(null)
  }

  return <AuthContext.Provider value={{ session, login, registro, logout }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth debe utilizarse dentro de AuthProvider')
  }

  return context
}
