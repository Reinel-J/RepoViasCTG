import { createBrowserRouter } from "react-router"
import { AuthProvider } from "../context/AuthContext"
import { CrearReporte } from "../pages/CrearReporte"
import { DetalleReporte } from "../pages/DetalleReporte"
import { ListaReportes } from "../pages/ListaReportes"
import { Login } from "../pages/Login"
import { MapaGeneral } from "../pages/MapaGeneral"
import { PanelAdmin } from "../pages/PanelAdmin"
import { Registro } from "../pages/Registro"
import { ProtectedRoute } from "../routes/ProtectedRoute"
import { Layout } from "./components/Layout"
import { Home } from "./pages/Home"

function Root() {
  return (
    <AuthProvider>
      <Layout />
    </AuthProvider>
  )
}

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Root,
    children: [
      { index: true, Component: Home },
      { path: "login", Component: Login },
      { path: "registro", Component: Registro },
      { path: "reportes", Component: ListaReportes },
      {
        path: "reportes/nuevo",
        element: (
          <ProtectedRoute>
            <CrearReporte />
          </ProtectedRoute>
        ),
      },
      { path: "reportes/:id", Component: DetalleReporte },
      { path: "mapa", Component: MapaGeneral },
      {
        path: "admin",
        element: (
          <ProtectedRoute requiredRole="ADMIN">
            <PanelAdmin />
          </ProtectedRoute>
        ),
      },
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
