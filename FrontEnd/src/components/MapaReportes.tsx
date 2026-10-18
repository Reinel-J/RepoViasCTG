import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'
import type { Layer, LeafletEventHandlerFnMap } from 'leaflet'
import { NavLink } from 'react-router'
import { CircleMarker, MapContainer, Popup, TileLayer, useMap } from 'react-leaflet'
import { imagenCategoria, textoEstado, urlFoto } from '../api/reporteVista'
import type { ReporteResponse } from '../api/types'
import { ATRIBUCION_OSM, CARTAGENA, COLOR_ESTADO, TESELAS_OSM } from './mapaBase'

interface MapaReportesProps {
  reportes: ReporteResponse[]
  // En el detalle de un reporte el enlace llevaría a la misma página.
  enlaces?: boolean
}

// Margen para llevar el mouse del punto a la ventanita sin que se cierre.
const ESPERA_CIERRE_MS = 250

function resumirDescripcion(descripcion: string) {
  return descripcion.length > 120 ? `${descripcion.slice(0, 120)}…` : descripcion
}

// Encuadra todos los reportes visibles al cargar o al cambiar el filtro. Depende de las coordenadas como texto y
// no del arreglo: el detalle pasa un arreglo nuevo en cada render y, si no, el mapa volvería a su encuadre
// (perdiendo el zoom del usuario) con cada tecla escrita en un comentario.
function AjustarVista({ reportes }: { reportes: ReporteResponse[] }) {
  const map = useMap()
  const puntos = reportes.map((reporte) => `${reporte.latitud},${reporte.longitud}`).join(';')

  useEffect(() => {
    if (!puntos) return
    const coordenadas = puntos.split(';').map((punto) => punto.split(',').map(Number) as [number, number])
    if (coordenadas.length === 1) {
      map.setView(coordenadas[0], 16, { animate: false })
      return
    }
    map.fitBounds(coordenadas, { padding: [30, 30], maxZoom: 16 })
  }, [map, puntos])

  return null
}

export function MapaReportes({ reportes, enlaces = true }: MapaReportesProps) {
  const cierre = useRef<number | undefined>(undefined)

  useEffect(() => () => window.clearTimeout(cierre.current), [])

  const cancelarCierre = () => window.clearTimeout(cierre.current)
  const programarCierre = (marcador: Layer) => {
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
      const marcador: Layer = event.target
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
    <MapContainer className="leaflet-map" center={CARTAGENA} zoom={13} scrollWheelZoom>
      <TileLayer attribution={ATRIBUCION_OSM} url={TESELAS_OSM} />
      <AjustarVista reportes={reportes} />
      {reportes.map((reporte) => {
        const foto = reporte.fotos?.[0]
        return (
          <CircleMarker
            key={reporte.id}
            center={[reporte.latitud, reporte.longitud]}
            radius={9}
            pathOptions={{ color: '#ffffff', weight: 2, fillColor: COLOR_ESTADO[reporte.estado], fillOpacity: 0.9 }}
            eventHandlers={eventos}
          >
            <Popup autoPan={false}>
              <div className="mapa-popup">
                <div className="mapa-popup-img">
                  <img src={foto ? urlFoto(foto) : imagenCategoria(reporte.categoriaId ?? '')} alt="" />
                  {!foto && <span className="mapa-popup-etiqueta">Imagen ilustrativa</span>}
                </div>
                <strong>{reporte.categoriaId}</strong> · {textoEstado(reporte.estado)}
                <br />
                {resumirDescripcion(reporte.descripcion)}
                {reporte.direccionOsm && <><br /><small>{reporte.direccionOsm}</small></>}
                {enlaces && <><br /><NavLink className="mapa-popup-enlace" to={`/reportes/${reporte.id}`}>Ver reporte →</NavLink></>}
              </div>
            </Popup>
          </CircleMarker>
        )
      })}
    </MapContainer>
  )
}
