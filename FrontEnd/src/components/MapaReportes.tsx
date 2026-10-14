import 'leaflet/dist/leaflet.css'
import './leafletIconFix'
import { useEffect, useRef } from 'react'
import type { LeafletEventHandlerFnMap, Marker as LeafletMarker } from 'leaflet'
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet'
import { NavLink } from 'react-router'
import { imagenCategoria, urlFoto } from '../api/reporteVista'
import type { ReporteResponse } from '../api/types'

interface MapaReportesProps {
  reportes: ReporteResponse[]
}

const CARTAGENA: [number, number] = [10.391, -75.479]
// Margen para llevar el mouse del punto a la ventanita sin que se cierre.
const ESPERA_CIERRE_MS = 250

function resumirDescripcion(descripcion: string) {
  return descripcion.length > 120 ? `${descripcion.slice(0, 120)}…` : descripcion
}

export function MapaReportes({ reportes }: MapaReportesProps) {
  const center: [number, number] = reportes.length > 0
    ? [reportes[0].latitud, reportes[0].longitud]
    : CARTAGENA
  const cierre = useRef<number | undefined>(undefined)

  useEffect(() => () => window.clearTimeout(cierre.current), [])

  const cancelarCierre = () => window.clearTimeout(cierre.current)
  const programarCierre = (marcador: LeafletMarker) => {
    cancelarCierre()
    cierre.current = window.setTimeout(() => marcador.closePopup(), ESPERA_CIERRE_MS)
  }

  const eventos: LeafletEventHandlerFnMap = {
    mouseover: (event) => {
      cancelarCierre()
      event.target.openPopup()
    },
    mouseout: (event) => programarCierre(event.target),
    // Un toque en celular emite mouseover y luego click: sin esto, el click cerraría la ventanita recién abierta.
    click: (event) => {
      const marcador: LeafletMarker = event.target
      cancelarCierre()
      window.setTimeout(() => marcador.openPopup(), 0)
    },
    popupopen: (event) => {
      const contenedor = event.popup.getElement()
      if (!contenedor) return
      contenedor.onmouseenter = cancelarCierre
      contenedor.onmouseleave = () => programarCierre(event.target)
    },
  }

  return (
    <MapContainer className="leaflet-map" center={center} zoom={13} scrollWheelZoom>
      <TileLayer
        attribution="&copy; OpenStreetMap contributors"
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {reportes.map((reporte) => {
        const foto = reporte.fotos?.[0]
        return (
          <Marker key={reporte.id} position={[reporte.latitud, reporte.longitud]} eventHandlers={eventos}>
            <Popup autoPan={false}>
              <div className="mapa-popup">
                <div className="mapa-popup-img">
                  <img src={foto ? urlFoto(foto) : imagenCategoria(reporte.categoriaId ?? '')} alt="" />
                  {!foto && <span className="mapa-popup-etiqueta">Imagen ilustrativa</span>}
                </div>
                <strong>{resumirDescripcion(reporte.descripcion)}</strong>
                <br />
                Estado: {reporte.estado}
                <br />
                Prioridad: {reporte.prioridad}
                <br />
                <NavLink className="mapa-popup-enlace" to={`/reportes/${reporte.id}`}>Ver reporte →</NavLink>
              </div>
            </Popup>
          </Marker>
        )
      })}
    </MapContainer>
  )
}
