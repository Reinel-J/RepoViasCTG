import { useEffect, useState, type FormEvent } from "react"
import { Link, useParams } from "react-router"
import * as comentariosApi from "../api/comentarios"
import * as confirmacionesApi from "../api/confirmaciones"
import * as reportesApi from "../api/reportes"
import * as seguimientoApi from "../api/seguimiento"
import type { ComentarioResponse, ConfirmacionResponse, ReporteResponse, SeguimientoResumenResponse } from "../api/types"
import { Icon, StatusBadge } from "../app/components/UI"
import { MapaReportes } from "../components/MapaReportes"
import { useAuth } from "../context/useAuth"

const URL_BACKEND = (import.meta.env.VITE_API_URL ?? "http://localhost:8080/api").replace(/\/api\/?$/, "")

function urlFoto(ruta: string) {
  return ruta.startsWith("http") ? ruta : `${URL_BACKEND}${ruta}`
}

export function DetalleReporte() {
  const { id } = useParams()
  const { session } = useAuth()
  const [reporte, setReporte] = useState<ReporteResponse | null>(null)
  const [comentarios, setComentarios] = useState<ComentarioResponse[]>([])
  const [confirmaciones, setConfirmaciones] = useState<ConfirmacionResponse[]>([])
  const [seguimiento, setSeguimiento] = useState<SeguimientoResumenResponse | null>(null)
  const [votando, setVotando] = useState(false)
  const [textoComentario, setTextoComentario] = useState("")
  const [error, setError] = useState("")
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    if (!id) return
    reportesApi.obtener(id).then(setReporte).catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar el reporte.")).finally(() => setCargando(false))
  }, [id])

  // La dirección se resuelve de forma asíncrona en el backend: si aún no está, se consulta de nuevo.
  useEffect(() => {
    if (!id || !reporte || reporte.direccionOsm) return
    const temporizador = setTimeout(() => {
      reportesApi.obtener(id).then(setReporte).catch(() => undefined)
    }, 4000)
    return () => clearTimeout(temporizador)
  }, [id, reporte])

  useEffect(() => {
    if (!id || !session) return
    Promise.all([comentariosApi.listar(id), confirmacionesApi.listar(id)])
      .then(([comments, confirmations]) => { setComentarios(comments); setConfirmaciones(confirmations) })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "No fue posible cargar las interacciones."))
  }, [id, session])

  // Público; se vuelve a pedir al iniciar o cerrar sesión para saber si este usuario ya respondió.
  useEffect(() => {
    if (!id) return
    seguimientoApi.obtener(id).then(setSeguimiento).catch(() => setSeguimiento(null))
  }, [id, session])

  async function responderSeguimiento(arreglado: boolean) {
    if (!id) return
    setVotando(true)
    try {
      setSeguimiento(await seguimientoApi.responder(id, arreglado))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible guardar tu respuesta.")
    } finally {
      setVotando(false)
    }
  }

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
          {reporte.fotos.length > 0 && <div className="report-gallery">
            {reporte.fotos.map((foto, index) => <img className="report-image" key={foto} src={urlFoto(foto)} alt={`Daño vial reportado, foto ${index + 1}`} />)}
          </div>}
          <div className="detail-map"><MapaReportes reportes={[reporte]} /></div>
          <section className="comments">
            <h2>Conversación <span>{comentarios.length}</span></h2>
            {comentarios.length === 0 && <p className="muted">Todavía no hay comentarios.</p>}
            {comentarios.map((comentario) => <article key={comentario.id}><span><Icon name="user" /></span><div><strong>{comentario.usuarioNombre ?? "Miembro de la comunidad"}</strong><p>{comentario.texto}</p></div></article>)}
            {session ? (
              <form onSubmit={enviarComentario}><label>Añadir comentario<textarea maxLength={1000} value={textoComentario} onChange={(event) => setTextoComentario(event.target.value)} placeholder="Comparte información útil sobre este reporte…" required /></label><button className="button button-dark" type="submit">Comentar</button></form>
            ) : <p className="signin-note"><Link to="/login">Inicia sesión</Link> para participar en la conversación.</p>}
          </section>
        </div>
        <aside className="detail-aside">
          <div className="confirm-card"><span className="confirm-icon"><Icon name="check" /></span><strong>{confirmaciones.length}</strong><h2>personas confirman este problema</h2><p>Tu confirmación ayuda a darle más visibilidad.</p>{session ? <button className="button button-primary button-wide" onClick={confirmarReporte}>Confirmar reporte</button> : <Link className="button button-primary button-wide" to="/login">Inicia sesión</Link>}</div>
          {seguimiento && <SeguimientoCard reporte={reporte} seguimiento={seguimiento} conSesion={Boolean(session)} votando={votando} onResponder={responderSeguimiento} />}
          <div className="info-card"><h3>Ubicación</h3>{reporte.direccionOsm && <p>{reporte.direccionOsm}</p>}<p>{reporte.latitud.toFixed(5)}, {reporte.longitud.toFixed(5)}</p><h3>Última actualización</h3><p>{new Date(reporte.fechaActualizacion).toLocaleDateString("es-CO")}</p></div>
        </aside>
      </div>
    </main>
  )
}

interface SeguimientoCardProps {
  reporte: ReporteResponse
  seguimiento: SeguimientoResumenResponse
  conSesion: boolean
  votando: boolean
  onResponder: (arreglado: boolean) => void
}

// "¿Ya lo arreglaron?": los votos son una señal para los moderadores, no cambian el estado del reporte.
function SeguimientoCard({ reporte, seguimiento, conSesion, votando, onResponder }: SeguimientoCardProps) {
  const activo = reporte.estado !== "RESUELTO" && reporte.estado !== "RECHAZADO"
  const total = seguimiento.arreglado + seguimiento.sigue
  if (!activo && total === 0) return null

  return (
    <div className="info-card followup-card">
      <h3>¿Ya lo arreglaron?</h3>
      {total > 0
        ? <p><strong>{seguimiento.arreglado}</strong> {seguimiento.arreglado === 1 ? "persona dice" : "personas dicen"} que ya está arreglado · <strong>{seguimiento.sigue}</strong> que sigue igual</p>
        : <p>Nadie ha respondido todavía. Si pasas por ahí, cuéntanos cómo está.</p>}
      {activo && conSesion && <div className="followup-buttons" role="group" aria-label="¿El daño sigue?">
        <button className={`button button-small ${seguimiento.miRespuesta === true ? "button-primary" : "button-ghost"}`} aria-pressed={seguimiento.miRespuesta === true} disabled={votando} onClick={() => onResponder(true)}>Ya está arreglado</button>
        <button className={`button button-small ${seguimiento.miRespuesta === false ? "button-dark" : "button-ghost"}`} aria-pressed={seguimiento.miRespuesta === false} disabled={votando} onClick={() => onResponder(false)}>Sigue igual</button>
      </div>}
      {activo && !conSesion && <p><Link to="/login">Inicia sesión</Link> para responder.</p>}
      {activo && seguimiento.miRespuesta !== null && <p className="muted">Puedes cambiar tu respuesta cuando quieras. Un moderador la revisará antes de cerrar el reporte.</p>}
    </div>
  )
}
