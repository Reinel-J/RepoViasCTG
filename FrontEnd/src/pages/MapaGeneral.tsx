import { useEffect, useState } from 'react'
import * as reportesApi from '../api/reportes'
import type { ReporteResponse } from '../api/types'
import { MapaReportes } from '../components/MapaReportes'

export function MapaGeneral() {
  const [reportes, setReportes] = useState<ReporteResponse[]>([])
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    async function cargarReportes() {
      try {
        setReportes(await reportesApi.listar())
      } catch (exception) {
        setError(exception instanceof Error ? exception.message : 'No fue posible cargar el mapa.')
      } finally {
        setCargando(false)
      }
    }

    void cargarReportes()
  }, [])

  return (
    <main>
      <h1>Mapa de daños viales</h1>
      {cargando && <p>Cargando reportes…</p>}
      {error && <p role="alert">{error}</p>}
      {!cargando && !error && <MapaReportes reportes={reportes} />}
    </main>
  )
}
