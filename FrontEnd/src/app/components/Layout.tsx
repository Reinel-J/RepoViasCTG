import { useState } from "react"
import { NavLink, Outlet, useNavigate } from "react-router"
import { useAuth } from "../../context/useAuth"
import { BrandMark, Icon } from "./UI"

export function Layout() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  function signOut() {
    logout()
    navigate("/")
  }

  return (
    <div className="site">
      <div className="announcement">
        Juntos cuidamos las calles de Cartagena <span>•</span> Reportar toma menos de 3 minutos
      </div>
      <header className="topbar">
        <div className="topbar-inner">
          <BrandMark />
          <button className="menu-button" aria-label="Abrir menú" onClick={() => setOpen(!open)}>
            <Icon name="menu" />
          </button>
          <div className={`nav-wrap ${open ? "is-open" : ""}`}>
            <nav aria-label="Navegación principal">
              <NavLink to="/reportes" onClick={() => setOpen(false)}>Reportes</NavLink>
              <NavLink to="/mapa" onClick={() => setOpen(false)}>Mapa</NavLink>
              {session && <NavLink to="/notificaciones" onClick={() => setOpen(false)}>Notificaciones</NavLink>}
              {session && session.rol !== "CIUDADANO" && (
                <NavLink to="/admin" onClick={() => setOpen(false)}>Moderación</NavLink>
              )}
            </nav>
            <div className="session-actions">
              {session ? (
                <>
                  <NavLink className="user-chip" to="/perfil" onClick={() => setOpen(false)}><Icon name="user" /> {session.nombre}</NavLink>
                  <button className="text-button" onClick={signOut}>Cerrar sesión</button>
                </>
              ) : (
                <>
                  <NavLink to="/login">Ingresar</NavLink>
                  <NavLink className="button button-primary button-small" to="/registro">Crear cuenta</NavLink>
                </>
              )}
            </div>
          </div>
        </div>
      </header>
      <Outlet />
      <footer className="footer">
        <div className="footer-inner">
          <BrandMark compact />
          <p>Una Cartagena más caminable, segura y conectada.</p>
          <p>Mapa © colaboradores de OpenStreetMap</p>
        </div>
      </footer>
    </div>
  )
}
