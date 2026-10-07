import { useEffect, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import * as comentariosApi from '../api/comentarios'
import * as confirmacionesApi from '../api/confirmaciones'
import * as reportesApi from '../api/reportes'
import type { ComentarioResponse, ConfirmacionResponse, ReporteResponse } from '../api/types'
import { MapaReportes } from '../components/MapaReportes'
import { useAuth } from '../context/AuthContext'

export function DetalleReporte() {
  const { id } = useParams()
  const { session } = useAuth()
  const [reporte, setReporte] = useState<ReporteResponse | null>(null)
  const [comentarios, setComentarios] = useState<ComentarioResponse[]>([])
  const [confirmaciones, setConfirmaciones] = useState<ConfirmacionResponse[]>([])
  const [textoComentario, setTextoComentario] = useState('')
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    const reporteId = id ?? ''
    if (!reporteId) return

    async function cargarReporte() {
      try {
        setReporte(await reportesApi.obtener(reporteId))
      } catch (exception) {
        setError(exception instanceof Error ? exception.message : 'No fue posible cargar el reporte.')
      } finally {
        setCargando(false)
      }
    }

    void cargarReporte()
  }, [id])

  useEffect(() => {
    const reporteId = id ?? ''
    if (!reporteId || !session) {
      setComentarios([])
      setConfirmaciones([])
      return
    }

    async function cargarInteracciones() {
      try {
        const [comentariosResponse, confirmacionesResponse] = await Promise.all([
          comentariosApi.listar(reporteId),
          confirmacionesApi.listar(reporteId),
        ])
        setComentarios(comentariosResponse)
        setConfirmaciones(confirmacionesResponse)
      } catch (exception) {
        setError(exception instanceof Error ? exception.message : 'No fue posible cargar las interacciones.')
      }
    }

    void cargarInteracciones()
  }, [id, session])

  async function enviarComentario(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!id || !textoComentario.trim()) return

    try {
      const comentario = await comentariosApi.crear(id, textoComentario.trim())
      setComentarios((actuales) => [...actuales, comentario])
      setTextoComentario('')
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'No fue posible publicar el comentario.')
    }
  }

  async function confirmarReporte() {
    if (!id) return

    try {
      const confirmacion = await confirmacionesApi.crear(id)
      setConfirmaciones((actuales) => [...actuales, confirmacion])
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'No fue posible confirmar el reporte.')
    }
  }

  if (!id) return <main><p>Identificador de reporte inválido.</p></main>
  if (cargando) return <main><p>Cargando reporte…</p></main>
  if (error && !reporte) return <main><p role="alert">{error}</p></main>
  if (!reporte) return <main><p>Reporte no encontrado.</p></main>

  return (
    <main>
      <h1>Detalle del reporte</h1>
      {error && <p role="alert">{error}</p>}
      <p>{reporte.descripcion}</p>
      <p>Estado: {reporte.estado}</p>
      <p>Prioridad: {reporte.prioridad}</p>
      <p>Creado: {new Date(reporte.fechaCreacion).toLocaleString()}</p>
      {reporte.fotoUrl && <img src={reporte.fotoUrl} alt="Daño vial reportado" />}
      <MapaReportes reportes={[reporte]} />
      {session ? (
        <>
          <section>
            <h2>Confirmaciones ({confirmaciones.length})</h2>
            <button type="button" onClick={confirmarReporte}>Confirmar este reporte</button>
          </section>
          <section>
            <h2>Comentarios</h2>
            {comentarios.map((comentario) => <p key={comentario.id}>{comentario.texto}</p>)}
            <form onSubmit={enviarComentario}>
              <label htmlFor="comentario">Añadir comentario</label>
              <textarea id="comentario" maxLength={1000} value={textoComentario} onChange={(event) => setTextoComentario(event.target.value)} required />
              <button type="submit">Comentar</button>
            </form>
          </section>
        </>
      ) : <p>Inicia sesión para comentar o confirmar este reporte.</p>}
    </main>
  )
}
