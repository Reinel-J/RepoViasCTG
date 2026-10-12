import { useEffect, useState, type ReactNode } from "react"
import { NavLink, useNavigate } from "react-router"
import { cargarReportes, fotoReporte, type Reporte } from "../../api/reporteVista"
import { useAuth } from "../../context/useAuth"

type IconName = "arrow" | "bell" | "camera" | "check" | "chevron" | "clock" | "location" | "map" | "menu" | "plus" | "search"

function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  const paths: Record<IconName, ReactNode> = {
    arrow: <><path d="M5 12h14" /><path d="m13 6 6 6-6 6" /></>,
    bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" /><path d="M10 21h4" /></>,
    camera: <><path d="M14.5 5 13 3h-2L9.5 5H5a2 2 0 0 0-2 2v11h18V7a2 2 0 0 0-2-2Z" /><circle cx="12" cy="12" r="3.5" /></>,
    check: <path d="m5 12 4 4L19 6" />,
    chevron: <path d="m9 18 6-6-6-6" />,
    clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
    location: <><path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
    map: <><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3Z" /><path d="M9 3v15M15 6v15" /></>,
    menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
    plus: <><path d="M12 5v14M5 12h14" /></>,
    search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></>,
  }

  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {paths[name]}
    </svg>
  )
}

export function Home() {
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [filtro, setFiltro] = useState("Todos")
  const [totalReportes, setTotalReportes] = useState<Reporte[]>([])
  const [recientes, setRecientes] = useState<number | null>(null)
  const [error, setError] = useState("")

  useEffect(() => {
    cargarReportes()
      .then((datos) => {
        setTotalReportes(datos)
        const haceUnDia = Date.now() - 86_400_000
        setRecientes(datos.filter((reporte) => reporte.fechaCreacion && new Date(reporte.fechaCreacion).getTime() >= haceUnDia).length)
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar los reportes."))
  }, [])
  const estados: Record<string, Reporte["estadoRaw"]> = { Pendientes: "PENDIENTE", "En proceso": "EN_PROCESO", Resueltos: "RESUELTO" }
  const reportes = (filtro === "Todos" ? totalReportes : totalReportes.filter((reporte) => reporte.estadoRaw === estados[filtro])).slice(0, 3)
  const resueltos = totalReportes.filter((reporte) => reporte.estadoRaw === "RESUELTO").length
  const atendidos = totalReportes.filter((reporte) => reporte.estadoRaw !== "PENDIENTE").length

  return (
    <div className="app-shell">
      <div className="top-ribbon">
        <span>Cartagena de Indias</span>
        <span className="ribbon-dot" />
        <span>La calle también es nuestra</span>
      </div>

      <header className="site-header">
        <NavLink className="brand" to="/" aria-label="ViaCTG, inicio">
          <span className="brand-mark">
            <span className="brand-road" />
          </span>
          <span className="brand-copy">
            <strong>Vía<span>CTG</span></strong>
            <small>Cartagena se mueve</small>
          </span>
        </NavLink>

        <nav className={menuOpen ? "main-nav open" : "main-nav"} aria-label="Navegación principal">
          <a className="active" href="#inicio">Inicio</a>
          <NavLink to="/reportes">Reportes</NavLink>
          <NavLink to="/mapa">Mapa ciudadano</NavLink>
          <a href="#como-funciona">Cómo funciona</a>
          {(session?.rol === "ADMIN" || session?.rol === "MODERADOR") && <NavLink to="/admin">Panel de gestión</NavLink>}
        </nav>

        <div className="header-actions">
          {session && <button className="icon-button" onClick={() => navigate("/notificaciones")} aria-label="Notificaciones"><Icon name="bell" /></button>}
          <button className="login-button" onClick={() => { if (session) logout(); else navigate("/login") }}>{session ? "Cerrar sesión" : "Iniciar sesión"}</button>
          <button className="menu-button" onClick={() => setMenuOpen(!menuOpen)} aria-label="Abrir menú"><Icon name="menu" /></button>
        </div>
      </header>

      <main>
        <section className="hero" id="inicio">
          <div className="hero-photo">
            <div className="photo-credit">Foto: Leandro Loureiro / Unsplash</div>
          </div>
          <div className="hero-overlay" />
          <div className="hero-sun" />
          <div className="hero-content">
            <div className="eyebrow"><span /> Plataforma ciudadana de Cartagena</div>
            <h1>Por unas calles<br /><em>bacanas pa’ todos.</em></h1>
            <p>Reporta daños en tu barrio, súmale tu voz a la comunidad y sigue cada solución de principio a fin.</p>
            <div className="hero-actions">
              <button className="primary-button" onClick={() => navigate("/reportes/nuevo")}><Icon name="plus" /> Hacer un reporte</button>
              <button className="text-button" onClick={() => navigate("/mapa")}><Icon name="map" /> Explorar el mapa <Icon name="arrow" /></button>
            </div>
          </div>

          {recientes !== null && (
            <div className="hero-status-card">
              <span className="pulse" />
              <div><strong>Comunidad activa</strong><small>{recientes === 1 ? "1 reporte creado" : `${recientes} reportes creados`} en las últimas 24 horas</small></div>
              <Icon name="chevron" />
            </div>
          )}
          <div className="wave" />
        </section>

        <section className="stats-strip" aria-label="Estadísticas de impacto">
          <div className="stat-intro">
            <span className="mini-label">Juntos avanzamos</span>
            <p>La participación ciudadana sí transforma.</p>
          </div>
          <div className="stat"><strong>{totalReportes.length}</strong><span>Reportes ciudadanos</span></div>
          <div className="stat"><strong>{resueltos}</strong><span>Problemas resueltos</span></div>
          <div className="stat"><strong>{totalReportes.filter((reporte) => reporte.estadoRaw === "EN_PROCESO").length}</strong><span>En proceso</span></div>
          <div className="stat"><strong>{totalReportes.length ? Math.round(atendidos / totalReportes.length * 100) : 0}<sup>%</sup></strong><span>Reciben respuesta</span></div>
        </section>

        <section className="reports-section" id="reportes">
          <div className="section-heading">
            <div>
              <span className="section-kicker">Lo que pasa en la calle</span>
              <h2>Reportes de la comunidad</h2>
            </div>
            <div className="report-tools">
              <div className="filters" aria-label="Filtrar reportes">
                {["Todos", "Pendientes", "En proceso", "Resueltos"].map((item) => (
                  <button key={item} className={filtro === item ? "selected" : ""} onClick={() => setFiltro(item)}>{item}</button>
                ))}
              </div>
              <button className="search-button" onClick={() => navigate("/reportes")} aria-label="Buscar reportes"><Icon name="search" /></button>
            </div>
          </div>

          <div className="report-grid">
            {reportes.map((reporte, index) => {
              const foto = fotoReporte(reporte)
              return (
              <article className="report-card" key={reporte.id}>
                <div className="card-image-wrap">
                  {foto ? <img src={foto} alt={`Evidencia de ${reporte.categoria}`} /> : <div className="no-photo"><Icon name="camera" size={26} /><span>Sin fotografía</span></div>}
                  <span className={`category-chip ${reporte.color}`}>{reporte.categoria}</span>
                  <span className="card-number">0{index + 1}</span>
                </div>
                <div className="card-content">
                  <div className="location-line"><Icon name="location" size={17} /><h3>{reporte.lugar}</h3></div>
                  <div className="card-meta">
                    <span><Icon name="clock" size={15} /> {reporte.fecha}</span>
                    <span className={`status ${reporte.estadoClase}`}><i />{reporte.estado}</span>
                  </div>
                  <NavLink to={`/reportes/${reporte.id}`}>Ver reporte <Icon name="arrow" size={17} /></NavLink>
                </div>
              </article>
              )
            })}
          </div>
          {error && <div className="empty-panel"><h2>No pudimos cargar los reportes</h2><p>{error}</p></div>}
          {!error && reportes.length === 0 && <div className="empty-panel"><h2>Aún no hay reportes</h2><p>Sé la primera persona en reportar un problema de tu sector.</p></div>}

          <button className="outline-button" onClick={() => navigate("/reportes")}>Ver todos los reportes <Icon name="arrow" /></button>
        </section>

        <section className="map-section" id="mapa">
          <div className="map-copy">
            <span className="section-kicker light">Mapa ciudadano</span>
            <h2>Tu barrio,<br /><em>a un vistazo.</em></h2>
            <p>Descubre qué está pasando cerca de ti. Cada punto es una historia que la comunidad está ayudando a mover.</p>
            <button className="sand-button" onClick={() => navigate("/mapa")}>Abrir mapa completo <Icon name="arrow" /></button>
          </div>
          <div className="map-visual" aria-label="Mapa ilustrativo de Cartagena">
            <svg viewBox="0 0 720 430" aria-hidden="true">
              <path className="water-line" d="M-20 80C95 38 171 138 279 92s191-26 240 27 144 40 226 13" />
              <path className="road-line thick" d="M35 347c86-40 144-111 231-110 89 1 114-73 196-84 74-10 136 36 235 19" />
              <path className="road-line" d="M61 83c84 37 98 101 165 128 101 40 169 6 212 63 52 69 94 82 221 67" />
              <path className="road-line" d="M170 19c-12 79 28 115 8 188-13 47-67 99-71 208" />
              <path className="road-line" d="M510 9c-31 73 20 120-6 190-20 53-5 113 43 213" />
              <g className="blocks">
                <path d="m233 51 82-17 26 54-81 20z" /><path d="m359 98 84-21 19 57-85 19z" />
                <path d="m291 281 92-16 23 60-97 19z" /><path d="m473 232 83-14 17 57-77 17z" />
                <path d="m82 226 66-15 18 51-70 20z" /><path d="m583 71 74-12 18 53-70 15z" />
              </g>
            </svg>
          </div>
        </section>

        <section className="steps-section" id="como-funciona">
          <div className="steps-heading">
            <span className="section-kicker">Así de fácil</span>
            <h2>Tu reporte pone<br />la solución en marcha.</h2>
          </div>
          <div className="steps">
            <div className="step"><span>01</span><div className="step-icon coral-bg"><Icon name="location" /></div><h3>Ubica el problema</h3><p>Marca el punto exacto en el mapa.</p></div>
            <div className="step"><span>02</span><div className="step-icon yellow-bg"><Icon name="plus" /></div><h3>Cuéntanos qué pasa</h3><p>Agrega una foto y una descripción.</p></div>
            <div className="step"><span>03</span><div className="step-icon green-bg"><Icon name="check" /></div><h3>Sigue la solución</h3><p>Recibe avances hasta verlo resuelto.</p></div>
          </div>
        </section>

        <section className="final-cta">
          <div className="cta-pattern" />
          <span>¿Viste algo que podemos mejorar?</span>
          <h2>Échale una mano<br />a tu Cartagena.</h2>
          <button className="primary-button dark" onClick={() => navigate("/reportes/nuevo")}>Crear mi primer reporte <Icon name="arrow" /></button>
        </section>
      </main>

      <footer>
        <NavLink className="brand footer-brand" to="/">
          <span className="brand-mark"><span className="brand-road" /></span>
          <span className="brand-copy"><strong>Vía<span>CTG</span></strong><small>Cartagena se mueve</small></span>
        </NavLink>
        <p>Una plataforma hecha para cuidar las calles que nos conectan.</p>
        <span>© 2026 VíaCTG · Cartagena, Colombia</span>
      </footer>
    </div>
  )
}
