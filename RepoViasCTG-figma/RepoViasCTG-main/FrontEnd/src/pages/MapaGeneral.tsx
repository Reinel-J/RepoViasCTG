import { useEffect, useState } from "react"
import { Link } from "react-router"
import * as reportesApi from "../api/reportes"
import type { ReporteResponse } from "../api/types"
import { Icon } from "../app/components/UI"
import { MapaReportes } from "../components/MapaReportes"

export function MapaGeneral() {
  const [reportes, setReportes] = useState<ReporteResponse[]>([])
  const [error, setError] = useState("")
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    reportesApi.listar().then(setReportes).catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar el mapa.")).finally(() => setCargando(false))
  }, [])

  return (
    <main className="map-page">
      <div className="map-sidebar">
        <span className="eyebrow">Explora Cartagena</span>
        <h1>Mapa de daños viales</h1>
        <p>Visualiza los reportes activos y descubre qué está pasando cerca de ti.</p>
        <div className="map-summary"><strong>{reportes.length}</strong><span>reportes encontrados</span></div>
        <Link className="button button-primary button-wide" to="/reportes/nuevo">Reportar aquí <Icon name="arrow" /></Link>
      </div>
      <div className="map-canvas">
        {cargando && <div className="loading">Preparando el mapa…</div>}
        {error && <p className="alert error" role="alert">{error}</p>}
        {!cargando && !error && <MapaReportes reportes={reportes} />}
      </div>
    </main>
  )
}
