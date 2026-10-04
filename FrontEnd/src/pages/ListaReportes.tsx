import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import * as reportesApi from '../api/reportes'
import type { EstadoReporte, ReporteResponse } from '../api/types'

const ESTADOS: EstadoReporte[] = ['PENDIENTE', 'EN_REVISION', 'EN_PROCESO', 'RESUELTO', 'RECHAZADO']

export function ListaReportes() {
  const [reportes, setReportes] = useState<ReporteResponse[]>([])
  const [estado, setEstado] = useState<EstadoReporte | ''>('')
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    async function cargarReportes() {
      setCargando(true)
      setError('')

      try {
        setReportes(await reportesApi.listar(estado || undefined))
      } catch (exception) {
        setError(exception instanceof Error ? exception.message : 'No fue posible cargar los reportes.')
      } finally {
        setCargando(false)
      }
    }

    void cargarReportes()
  }, [estado])

  return (
    <main>
      <h1>Reportes viales</h1>
      <label htmlFor="estado">Filtrar por estado</label>
      <select id="estado" value={estado} onChange={(event) => setEstado(event.target.value as EstadoReporte | '')}>
        <option value="">Todos</option>
        {ESTADOS.map((opcion) => <option key={opcion} value={opcion}>{opcion}</option>)}
      </select>
      {cargando && <p>Cargando reportes…</p>}
      {error && <p role="alert">{error}</p>}
      {!cargando && !error && reportes.length === 0 && <p>No hay reportes para este filtro.</p>}
      <section>
        {reportes.map((reporte) => (
          <article key={reporte.id}>
            <h2>{reporte.descripcion}</h2>
            <p>Estado: {reporte.estado}</p>
            <p>Prioridad: {reporte.prioridad}</p>
            <Link to={`/reportes/${reporte.id}`}>Ver detalle</Link>
          </article>
        ))}
      </section>
    </main>
  )
}
