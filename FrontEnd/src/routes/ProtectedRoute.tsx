import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { useAuth } from '../context/useAuth'
import type { Rol } from '../api/types'

interface ProtectedRouteProps {
  children: ReactNode
  requiredRole?: Rol
}

function tieneRolPermitido(rol: Rol, requiredRole: Rol) {
  if (rol === 'ADMIN') {
    return true
  }

  if (requiredRole === 'ADMIN') {
    return rol === 'MODERADOR'
  }

  return rol === requiredRole
}

export function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps) {
  const { session } = useAuth()

  if (!session) {
    return <Navigate to="/login" replace />
  }

  if (requiredRole && !tieneRolPermitido(session.rol, requiredRole)) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
