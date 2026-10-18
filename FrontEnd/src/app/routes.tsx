import { createBrowserRouter } from "react-router"
import type { ComponentType } from "react"
import { ProtectedRoute } from "../routes/ProtectedRoute"
import type { Rol } from "../api/types"

const pagina = (nombre: string) => async () => ({
  Component: (await import("./pages/Screens"))[nombre as keyof typeof import("./pages/Screens")] as ComponentType,
})

const paginaProtegida = (nombre: string, requiredRole?: Rol) => async () => {
  const Pantalla = (await import("./pages/Screens"))[nombre as keyof typeof import("./pages/Screens")] as ComponentType
  return { Component: () => <ProtectedRoute requiredRole={requiredRole}><Pantalla /></ProtectedRoute> }
}

export const router = createBrowserRouter([
  { path: "/", lazy: async () => ({ Component: (await import("./pages/Home")).Home }) },
  { path: "/reportes", lazy: pagina("Reportes") },
  { path: "/reportes/nuevo", lazy: paginaProtegida("CrearReporte") },
  { path: "/reportes/:id/editar", lazy: paginaProtegida("EditarReporte") },
  { path: "/reportes/:id", lazy: pagina("DetalleReporte") },
  { path: "/mapa", lazy: pagina("Mapa") },
  { path: "/login", lazy: pagina("Login") },
  { path: "/registro", lazy: pagina("Registro") },
  { path: "/recuperar", lazy: pagina("RecuperarPassword") },
  { path: "/restablecer", lazy: pagina("RestablecerPassword") },
  { path: "/perfil", lazy: paginaProtegida("Perfil") },
  { path: "/mis-reportes", lazy: paginaProtegida("MisReportes") },
  { path: "/notificaciones", lazy: paginaProtegida("Notificaciones") },
  { path: "/admin", lazy: paginaProtegida("Admin", "ADMIN") },
  { path: "*", lazy: async () => ({ Component: (await import("./pages/Home")).Home }) },
])
