import { useEffect, useState } from 'react'
import { BrowserRouter, Link, NavLink, Outlet, Route, Routes, useNavigate } from 'react-router-dom'
import * as reportesApi from './api/reportes'
import type { ReporteResponse } from './api/types'
import { AuthProvider, useAuth } from './context/AuthContext'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { CrearReporte } from './pages/CrearReporte'
import { DetalleReporte } from './pages/DetalleReporte'
import { ListaReportes } from './pages/ListaReportes'
import { Login } from './pages/Login'
import { MapaGeneral } from './pages/MapaGeneral'
import { PanelAdmin } from './pages/PanelAdmin'
import { Registro } from './pages/Registro'

function Layout() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">Via<span>CTG</span></Link>
          <nav aria-label="Principal">
            <NavLink to="/reportes">Reportes</NavLink>
            <NavLink to="/mapa">Mapa</NavLink>
            {session && (session.rol === 'CIUDADANO' ? null : <NavLink to="/admin">Moderación</NavLink>)}
          </nav>
          <div className="topbar-session">
            {session ? (
              <>
                <span>{session.nombre}</span>
                <button type="button" className="btn-ghost" onClick={() => { logout(); navigate('/') }}>Cerrar sesión</button>
              </>
            ) : (
              <>
                <Link to="/login">Iniciar sesión</Link>
                <Link to="/registro" className="btn-link">Crear cuenta</Link>
              </>
            )}
          </div>
        </div>
      </header>
      <Outlet />
      <footer className="footer">
        <div className="footer-inner">
          <strong>ViaCTG</strong>
          <p>Reportes ciudadanos de calles dañadas o sin pavimentar en Cartagena de Indias. Mapa © colaboradores de OpenStreetMap.</p>
        </div>
      </footer>
    </>
  )
}

const PASOS = [
  ['Ubica el daño', 'Marca el punto exacto en el mapa o usa tu ubicación.'],
  ['Cuéntanos qué pasa', 'Elige la calle y la categoría, y describe el problema.'],
  ['Tus vecinos confirman', 'Otros usuarios validan que el daño sigue vigente.'],
  ['Se le da seguimiento', 'Un moderador actualiza el estado hasta resolverlo.'],
]

function Home() {
  const [recientes, setRecientes] = useState<ReporteResponse[] | null>(null)

  useEffect(() => {
    reportesApi.listar().then((data) => setRecientes(data.slice(0, 5))).catch(() => setRecientes([]))
  }, [])

  return (
    <div className="home">
      <section className="hero">
        <div className="hero-inner">
          <div className="hero-copy">
            <h1>Cartagena merece calles sin huecos.</h1>
            <p>Reporta vías dañadas o sin pavimentar en menos de un minuto y sigue cada caso hasta que se resuelva.</p>
            <div className="hero-actions">
              <Link to="/reportes/nuevo" className="btn">Reportar un daño</Link>
              <Link to="/mapa" className="btn btn-outline">Explorar el mapa</Link>
            </div>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="road" />
            <svg className="pin" viewBox="0 0 64 80" width="84" height="105">
              <circle className="ring" cx="32" cy="70" r="8" />
              <path d="M32 0C14.3 0 0 14.3 0 32c0 24 32 48 32 48s32-24 32-48C64 14.3 49.7 0 32 0z" fill="#fff" />
              <circle cx="32" cy="32" r="12" fill="#1c45e0" />
            </svg>
          </div>
        </div>
      </section>

      <section className="band">
        <div className="band-inner">
          <h2>Así funciona</h2>
          <ol className="steps">
            {PASOS.map(([titulo, texto]) => (
              <li key={titulo}><strong>{titulo}</strong><span>{texto}</span></li>
            ))}
          </ol>
        </div>
      </section>

      <section className="latest">
        <h2>Últimos reportes</h2>
        {recientes === null && <p className="muted">Cargando…</p>}
        {recientes?.length === 0 && <p className="muted">Aún no hay reportes. Sé la primera persona en reportar un daño.</p>}
        <ul className="recent">
          {recientes?.map((r) => (
            <li key={r.id}>
              <Link to={`/reportes/${r.id}`}>{r.descripcion.length > 90 ? `${r.descripcion.slice(0, 90)}…` : r.descripcion}</Link>
              <small>{new Date(r.fechaCreacion).toLocaleDateString('es-CO')} · {r.estado.replace('_', ' ').toLowerCase()}</small>
            </li>
          ))}
        </ul>
        <Link to="/reportes" className="more">Ver todos los reportes</Link>
      </section>

      <section className="cta">
        <h2>¿Viste un daño en tu calle?</h2>
        <p>Cada reporte confirmado le da más peso a la solicitud.</p>
        <Link to="/reportes/nuevo" className="btn btn-white">Crear un reporte</Link>
      </section>
    </div>
  )
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<Home />} />
            <Route path="/login" element={<Login />} />
            <Route path="/registro" element={<Registro />} />
            <Route path="/reportes" element={<ListaReportes />} />
            <Route path="/reportes/nuevo" element={<ProtectedRoute><CrearReporte /></ProtectedRoute>} />
            <Route path="/reportes/:id" element={<DetalleReporte />} />
            <Route path="/mapa" element={<MapaGeneral />} />
            <Route path="/admin" element={<ProtectedRoute requiredRole="ADMIN"><PanelAdmin /></ProtectedRoute>} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
