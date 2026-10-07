import { useEffect, useState } from "react"
import { Link } from "react-router"
import * as reportesApi from "../api/reportes"
import type { EstadoReporte, ReporteResponse } from "../api/types"
import { Icon, StatusBadge } from "../app/components/UI"

const ESTADOS: EstadoReporte[] = ["PENDIENTE", "EN_REVISION", "EN_PROCESO", "RESUELTO", "RECHAZADO"]
const LABELS: Record<EstadoReporte, string> = { PENDIENTE: "Pendiente", EN_REVISION: "En revisión", EN_PROCESO: "En proceso", RESUELTO: "Resuelto", RECHAZADO: "Rechazado" }

export function ListaReportes() {
  const [reportes, setReportes] = useState<ReporteResponse[]>([])
  const [estado, setEstado] = useState<EstadoReporte | "">("")
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState("")

  useEffect(() => {
    setCargando(true)
    setError("")
    reportesApi.listar(estado || undefined)
      .then(setReportes)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar los reportes."))
      .finally(() => setCargando(false))
  }, [estado])

  return (
    <main className="page-shell section internal-page">
      <div className="page-heading">
        <div><span className="eyebrow">Participación ciudadana</span><h1>Reportes viales</h1><p>Conoce qué está pasando en las calles y el avance de cada caso.</p></div>
        <Link className="button button-primary" to="/reportes/nuevo">Nuevo reporte <Icon name="arrow" /></Link>
      </div>
      <div className="toolbar">
        <label>Filtrar por estado
          <select value={estado} onChange={(event) => setEstado(event.target.value as EstadoReporte | "")}>
            <option value="">Todos los reportes</option>
            {ESTADOS.map((option) => <option key={option} value={option}>{LABELS[option]}</option>)}
          </select>
        </label>
        <Link className="map-link" to="/mapa"><Icon name="map" /> Ver en el mapa</Link>
      </div>
      {cargando && <div className="loading">Cargando reportes…</div>}
      {error && <p className="alert error" role="alert">{error}</p>}
      {!cargando && !error && reportes.length === 0 && <div className="empty-state"><Icon name="road" className="empty-icon" /><h2>No hay reportes para este filtro</h2><p>Prueba con otro estado o crea el primer reporte.</p></div>}
      <section className="report-list">
        {reportes.map((reporte) => (
          <article className="list-card" key={reporte.id}>
            <span className={`priority priority-${reporte.prioridad.toLowerCase()}`}>{reporte.prioridad}</span>
            <div><StatusBadge status={reporte.estado} /><h2>{reporte.descripcion}</h2><p>{new Date(reporte.fechaCreacion).toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric" })}</p></div>
            <Link className="round-link" to={`/reportes/${reporte.id}`} aria-label="Ver detalle"><Icon name="arrow" /></Link>
          </article>
        ))}
      </section>
    </main>
  )
}
