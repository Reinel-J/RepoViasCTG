import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet'
import type { ReporteResponse } from '../api/types'

interface MapaReportesProps {
  reportes: ReporteResponse[]
}

const CARTAGENA: [number, number] = [10.391, -75.479]

function resumirDescripcion(descripcion: string) {
  return descripcion.length > 120 ? `${descripcion.slice(0, 120)}…` : descripcion
}

export function MapaReportes({ reportes }: MapaReportesProps) {
  const center: [number, number] = reportes.length > 0
    ? [reportes[0].latitud, reportes[0].longitud]
    : CARTAGENA

  return (
    <MapContainer className="leaflet-map" center={center} zoom={13} scrollWheelZoom>
      <TileLayer
        attribution="&copy; OpenStreetMap contributors"
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {reportes.map((reporte) => (
        <Marker key={reporte.id} position={[reporte.latitud, reporte.longitud]}>
          <Popup>
            <strong>{resumirDescripcion(reporte.descripcion)}</strong>
            <br />
            Estado: {reporte.estado}
            <br />
            Prioridad: {reporte.prioridad}
          </Popup>
        </Marker>
      ))}
    </MapContainer>
  )
}
