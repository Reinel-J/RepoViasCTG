import { useEffect, useState } from 'react'
import * as adminApi from '../api/admin'
import type { EstadoReporte, ReporteResponse } from '../api/types'

const SIGUIENTES_ESTADOS: EstadoReporte[] = ['EN_REVISION', 'RECHAZADO']

export function PanelAdmin() {
  const [reportes, setReportes] = useState<ReporteResponse[]>([])
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(true)
  const [procesandoId, setProcesandoId] = useState<string | null>(null)

  useEffect(() => {
    async function cargarPendientes() {
      try {
        setReportes(await adminApi.listarPendientes())
      } catch (exception) {
        setError(exception instanceof Error ? exception.message : 'No fue posible cargar los reportes pendientes.')
      } finally {
        setCargando(false)
      }
    }

    void cargarPendientes()
  }, [])

  async function actualizarEstado(reporteId: string, nuevoEstado: EstadoReporte) {
    setProcesandoId(reporteId)
    setError('')

    try {
      await adminApi.cambiarEstado(reporteId, nuevoEstado)
      setReportes((actuales) => actuales.filter((reporte) => reporte.id !== reporteId))
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'No fue posible actualizar el estado.')
    } finally {
      setProcesandoId(null)
    }
  }

  return (
    <main>
      <h1>Panel de moderación</h1>
      {cargando && <p>Cargando reportes pendientes…</p>}
      {error && <p role="alert">{error}</p>}
      {!cargando && reportes.length === 0 && <p>No hay reportes pendientes.</p>}
      {reportes.map((reporte) => (
        <article key={reporte.id}>
          <h2>{reporte.descripcion}</h2>
          <p>Prioridad: {reporte.prioridad}</p>
          {SIGUIENTES_ESTADOS.map((estado) => (
            <button key={estado} type="button" disabled={procesandoId === reporte.id} onClick={() => actualizarEstado(reporte.id, estado)}>
              Marcar como {estado}
            </button>
          ))}
        </article>
      ))}
    </main>
  )
}
