import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
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
  const location = useLocation()

  if (!session) {
    // Tras iniciar sesión se vuelve a la página que se quería abrir.
    return <Navigate to="/login" replace state={{ desde: `${location.pathname}${location.search}` }} />
  }

  if (requiredRole && !tieneRolPermitido(session.rol, requiredRole)) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
