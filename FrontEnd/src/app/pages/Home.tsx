import { useEffect, useState, type FormEvent } from "react"
import { Link, useNavigate } from "react-router"
import * as reportesApi from "../../api/reportes"
import type { ReporteResponse } from "../../api/types"
import { Icon, StatusBadge } from "../components/UI"

export function Home() {
  const navigate = useNavigate()
  const [query, setQuery] = useState("")
  const [reports, setReports] = useState<ReporteResponse[]>([])

  useEffect(() => {
    reportesApi
      .listar()
      .then(setReports)
      .catch(() => undefined)
  }, [])

  const resolvedReports = reports.filter((report) => report.estado === "RESUELTO").length
  const reportsWithResponse = reports.filter((report) => report.estado !== "PENDIENTE").length
  const responseRate = reports.length ? Math.round((reportsWithResponse / reports.length) * 100) : 0

  function search(event: FormEvent) {
    event.preventDefault()
    navigate(query.trim() ? `/reportes?buscar=${encodeURIComponent(query.trim())}` : "/reportes")
  }

  return (
    <main>
      <section className="hero">
        <div className="hero-inner">
          <div className="hero-copy">
            <span className="eyebrow"><i /> Plataforma ciudadana</span>
            <h1>Una calle mejor <em>empieza contigo.</em></h1>
            <p>
              Reporta huecos, vías sin pavimentar y otros daños de tu barrio. Haz visible el
              problema y sigue su avance hasta que sea atendido.
            </p>
            <form className="hero-search" onSubmit={search}>
              <Icon name="location" />
              <input
                aria-label="Dirección o barrio"
                placeholder="Escribe una dirección o barrio"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
              <button className="button button-dark" type="submit">
                Buscar <Icon name="search" />
              </button>
            </form>
            <small className="search-hint">Prueba con “Manga”, “Olaya” o “Avenida San Martín”</small>
          </div>
          <div className="hero-map" aria-label="Vista ilustrada de reportes en Cartagena">
            <div className="sun" />
            <svg viewBox="0 0 540 510" role="img">
              <path d="M-20 72C100 91 112 157 213 165s139-48 347-13V-10H-20Z" fill="#45AAA5" opacity=".72" />
              <path d="M-30 426c94-87 161-53 234-90 82-41 124-99 366-73v267H-30Z" fill="#F3D99E" />
              <g fill="none" stroke="#FFF9ED" strokeLinecap="round">
                <path d="M-15 374C105 330 165 366 243 293s181-75 318-54" strokeWidth="22" />
                <path d="M69 520C75 380 141 322 146 224S117 80 151-18" strokeWidth="17" />
                <path d="M216 526c-13-122 43-188 72-261s25-154 16-280" strokeWidth="12" />
                <path d="M360 520c-12-110 37-157 91-222 40-49 49-111 52-190" strokeWidth="15" />
              </g>
            </svg>
            <span className="map-pin pin-one"><Icon name="road" /></span>
            <span className="map-pin pin-two"><Icon name="camera" /></span>
            <span className="map-pin pin-three"><Icon name="check" /></span>
            <div className="map-alert">
              <span><Icon name="road" /></span>
              <p><small>Nuevo reporte</small><strong>Hueco en Av. Pedro de Heredia</strong></p>
            </div>
          </div>
        </div>
      </section>

      <section className="stats">
        {[[String(reports.length), "Reportes ciudadanos"], [String(resolvedReports), "Problemas resueltos"], ["0", "Barrios participando"], [`${responseRate}%`, "Reciben respuesta"]].map(([value, label]) => (
          <div key={label}><strong>{value}</strong><span>{label}</span></div>
        ))}
      </section>

      <section className="section page-shell">
        <div className="section-heading">
          <div><span className="eyebrow">La ciudad en tiempo real</span><h2>Reportes de la comunidad</h2></div>
          <Link className="arrow-link" to="/reportes">Explorar todos <Icon name="arrow" /></Link>
        </div>
        {reports.length === 0 ? (
          <div className="empty-state">
            <Icon name="road" className="empty-icon" />
            <h2>Aún no hay reportes</h2>
            <p>Sé el primero en reportar un daño vial.</p>
            <Link className="button button-primary" to="/reportes/nuevo">Crear un reporte <Icon name="arrow" /></Link>
          </div>
        ) : (
          <div className="report-grid">
            {reports.slice(0, 3).map((report) => (
            <article className="report-card" key={report.id}>
              <div className="report-card-top"><span className="category-icon"><Icon name="road" /></span><StatusBadge status={report.estado} /></div>
              <small>{report.prioridad} prioridad</small>
              <h3>{report.descripcion}</h3>
              <div className="report-card-footer">
                <span>{new Date(report.fechaCreacion).toLocaleDateString("es-CO")}</span>
                <Link to={`/reportes/${report.id}`}>Ver detalle →</Link>
              </div>
            </article>
            ))}
          </div>
        )}
      </section>

      <section className="how">
        <div className="page-shell">
          <div className="how-heading"><span className="eyebrow">Así de sencillo</span><h2>Tu reporte sí puede hacer la diferencia</h2></div>
          <div className="steps">
            {[
              ["01", "Ubica el problema", "Busca la dirección o marca el punto exacto en el mapa."],
              ["02", "Cuéntanos qué pasa", "Elige la categoría, agrega una foto y describe la situación."],
              ["03", "Sigue el avance", "Recibe actualizaciones hasta que el daño haya sido atendido."],
            ].map(([number, title, text]) => (
              <article key={number}><strong>{number}</strong><h3>{title}</h3><p>{text}</p></article>
            ))}
          </div>
        </div>
      </section>

      <section className="cta">
        <div><span>¿Viste algo en tu calle?</span><h2>Hazlo visible. Repórtalo hoy.</h2></div>
        <Link className="button button-light" to="/reportes/nuevo">Crear un reporte <Icon name="arrow" /></Link>
      </section>
    </main>
  )
}
