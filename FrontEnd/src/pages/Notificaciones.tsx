import { useEffect, useState } from "react"
import { Link } from "react-router"
import * as notificacionesApi from "../api/notificaciones"
import type { NotificacionResponse } from "../api/types"
import { Icon } from "../app/components/UI"

export function Notificaciones() {
  const [notificaciones, setNotificaciones] = useState<NotificacionResponse[]>([])
  const [error, setError] = useState("")
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    notificacionesApi.listar()
      .then(setNotificaciones)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar las notificaciones."))
      .finally(() => setCargando(false))
  }, [])

  async function marcarLeida(id: string) {
    try {
      const actualizada = await notificacionesApi.marcarLeida(id)
      setNotificaciones((actuales) => actuales.map((notificacion) => (notificacion.id === id ? actualizada : notificacion)))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible marcar la notificación como leída.")
    }
  }

  const sinLeer = notificaciones.filter((notificacion) => !notificacion.leida).length

  return (
    <main className="page-shell section internal-page">
      <div className="page-heading"><div><span className="eyebrow">Tu actividad</span><h1>Notificaciones</h1><p>{sinLeer > 0 ? `Tienes ${sinLeer} sin leer.` : "Estás al día."}</p></div></div>
      {cargando && <div className="loading">Cargando notificaciones…</div>}
      {error && <p className="alert error" role="alert">{error}</p>}
      {!cargando && !error && notificaciones.length === 0 && <div className="empty-state"><Icon name="check" className="empty-icon" /><h2>Sin notificaciones</h2><p>Te avisaremos cuando cambie el estado de tus reportes.</p></div>}
      <section className="report-list">
        {notificaciones.map((notificacion) => (
          <article className={`list-card notification${notificacion.leida ? " is-read" : ""}`} key={notificacion.id}>
            <span className="notification-dot" aria-label={notificacion.leida ? "Leída" : "Sin leer"} />
            <div>
              <h2>{notificacion.mensaje}</h2>
              <p>{new Date(notificacion.fecha).toLocaleString("es-CO")}</p>
              {notificacion.reporteId && <Link className="map-link" to={`/reportes/${notificacion.reporteId}`}>Ver reporte</Link>}
            </div>
            {!notificacion.leida && <button className="button button-ghost button-small" onClick={() => marcarLeida(notificacion.id)}>Marcar como leída</button>}
          </article>
        ))}
      </section>
    </main>
  )
}
