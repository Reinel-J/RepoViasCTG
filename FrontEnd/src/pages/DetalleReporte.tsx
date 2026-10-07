import { useEffect, useState, type FormEvent } from "react"
import { Link, useParams } from "react-router"
import * as comentariosApi from "../api/comentarios"
import * as confirmacionesApi from "../api/confirmaciones"
import * as reportesApi from "../api/reportes"
import type { ComentarioResponse, ConfirmacionResponse, ReporteResponse } from "../api/types"
import { Icon, StatusBadge } from "../app/components/UI"
import { MapaReportes } from "../components/MapaReportes"
import { useAuth } from "../context/AuthContext"

export function DetalleReporte() {
  const { id } = useParams()
  const { session } = useAuth()
  const [reporte, setReporte] = useState<ReporteResponse | null>(null)
  const [comentarios, setComentarios] = useState<ComentarioResponse[]>([])
  const [confirmaciones, setConfirmaciones] = useState<ConfirmacionResponse[]>([])
  const [textoComentario, setTextoComentario] = useState("")
  const [error, setError] = useState("")
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    if (!id) return
    reportesApi.obtener(id).then(setReporte).catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar el reporte.")).finally(() => setCargando(false))
  }, [id])

  useEffect(() => {
    if (!id || !session) return
    Promise.all([comentariosApi.listar(id), confirmacionesApi.listar(id)])
      .then(([comments, confirmations]) => { setComentarios(comments); setConfirmaciones(confirmations) })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar las interacciones."))
  }, [id, session])

  async function enviarComentario(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!id || !textoComentario.trim()) return
    try {
      const comentario = await comentariosApi.crear(id, textoComentario.trim())
      setComentarios((current) => [...current, comentario])
      setTextoComentario("")
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible publicar el comentario.")
    }
  }

  async function confirmarReporte() {
    if (!id) return
    try {
      const confirmation = await confirmacionesApi.crear(id)
      setConfirmaciones((current) => [...current, confirmation])
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible confirmar el reporte.")
    }
  }

  if (cargando) return <main className="page-shell section"><div className="loading">Cargando reporte…</div></main>
  if (!reporte) return <main className="page-shell section"><div className="empty-state"><h1>Reporte no encontrado</h1><p>{error || "No pudimos encontrar este reporte."}</p></div></main>

  return (
    <main className="page-shell section internal-page detail-page">
      <Link className="back-link" to="/reportes">← Volver a reportes</Link>
      {error && <p className="alert error" role="alert">{error}</p>}
      <div className="detail-grid">
        <div className="detail-main">
          <div className="detail-title"><StatusBadge status={reporte.estado} /><span className={`priority priority-${reporte.prioridad.toLowerCase()}`}>{reporte.prioridad}</span><h1>{reporte.descripcion}</h1><p>Publicado el {new Date(reporte.fechaCreacion).toLocaleString("es-CO")}</p></div>
          {reporte.fotoUrl && <img className="report-image" src={reporte.fotoUrl} alt="Daño vial reportado" />}
          <div className="detail-map"><MapaReportes reportes={[reporte]} /></div>
          <section className="comments">
            <h2>Conversación <span>{comentarios.length}</span></h2>
            {comentarios.length === 0 && <p className="muted">Todavía no hay comentarios.</p>}
            {comentarios.map((comentario) => <article key={comentario.id}><span><Icon name="user" /></span><div><strong>Miembro de la comunidad</strong><p>{comentario.texto}</p></div></article>)}
            {session ? (
              <form onSubmit={enviarComentario}><label>Añadir comentario<textarea maxLength={1000} value={textoComentario} onChange={(event) => setTextoComentario(event.target.value)} placeholder="Comparte información útil sobre este reporte…" required /></label><button className="button button-dark" type="submit">Comentar</button></form>
            ) : <p className="signin-note"><Link to="/login">Inicia sesión</Link> para participar en la conversación.</p>}
          </section>
        </div>
        <aside className="detail-aside">
          <div className="confirm-card"><span className="confirm-icon"><Icon name="check" /></span><strong>{confirmaciones.length}</strong><h2>personas confirman este problema</h2><p>Tu confirmación ayuda a darle más visibilidad.</p>{session ? <button className="button button-primary button-wide" onClick={confirmarReporte}>Confirmar reporte</button> : <Link className="button button-primary button-wide" to="/login">Inicia sesión</Link>}</div>
          <div className="info-card"><h3>Ubicación</h3><p>{reporte.latitud.toFixed(5)}, {reporte.longitud.toFixed(5)}</p><h3>Última actualización</h3><p>{new Date(reporte.fechaActualizacion).toLocaleDateString("es-CO")}</p></div>
        </aside>
      </div>
    </main>
  )
}
