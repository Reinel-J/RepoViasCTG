import type { ComponentType } from "react"
import { createBrowserRouter } from "react-router"
import type { Rol } from "../api/types"
import { ProtectedRoute } from "../routes/ProtectedRoute"
import { Root } from "./components/Root"
import { Home } from "./pages/Home"

type Carga = () => Promise<Record<string, unknown>>

// Cada pantalla se descarga bajo demanda para mantener pequeño el paquete inicial.
const pagina = (carga: Carga, nombre: string) => async () => ({
  Component: (await carga())[nombre] as ComponentType,
})

const paginaProtegida = (carga: Carga, nombre: string, requiredRole?: Rol) => async () => {
  const Pantalla = (await carga())[nombre] as ComponentType
  return {
    Component: () => (
      <ProtectedRoute requiredRole={requiredRole}>
        <Pantalla />
      </ProtectedRoute>
    ),
  }
}

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Root,
    children: [
      { index: true, Component: Home },
      { path: "login", lazy: pagina(() => import("../pages/Login"), "Login") },
      { path: "registro", lazy: pagina(() => import("../pages/Registro"), "Registro") },
      { path: "reportes", lazy: pagina(() => import("../pages/ListaReportes"), "ListaReportes") },
      { path: "reportes/nuevo", lazy: paginaProtegida(() => import("../pages/CrearReporte"), "CrearReporte") },
      { path: "reportes/:id", lazy: pagina(() => import("../pages/DetalleReporte"), "DetalleReporte") },
      { path: "mapa", lazy: pagina(() => import("../pages/MapaGeneral"), "MapaGeneral") },
      { path: "admin", lazy: paginaProtegida(() => import("../pages/admin/PanelAdmin"), "PanelAdmin", "ADMIN") },
      { path: "notificaciones", lazy: paginaProtegida(() => import("../pages/Notificaciones"), "Notificaciones") },
      { path: "perfil", lazy: paginaProtegida(() => import("../pages/Perfil"), "Perfil") },
      {
        path: "*",
        element: (
          <section className="page-shell empty-state">
            <span className="eyebrow">Error 404</span>
            <h1>Parece que esta calle no existe.</h1>
            <p>Regresa al inicio para continuar explorando los reportes de Cartagena.</p>
          </section>
        ),
      },
    ],
  },
])
