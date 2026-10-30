import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'
import type { LeafletEventHandlerFnMap, Marker as MarcadorLeaflet } from 'leaflet'
import { NavLink } from 'react-router'
import { MapContainer, Marker, Popup, TileLayer, useMap } from 'react-leaflet'
import { imagenCategoria, textoEstado, urlFoto } from '../api/reporteVista'
import type { ReporteResponse } from '../api/types'
import { ATRIBUCION_OSM, CARTAGENA, TESELAS_OSM } from './mapaBase'
import { pinEstado } from './pinMapa'
import { ZoomConRueda } from './ZoomConRueda'

interface MapaReportesProps {
  reportes: ReporteResponse[]
  // En el detalle de un reporte el enlace llevaría a la misma página.
  enlaces?: boolean
}

// Margen para llevar el mouse del marcador a la ventanita sin que se cierre.
const ESPERA_CIERRE_MS = 250
// Al desplazar el mapa para mostrar una ventanita entera se deja libre la esquina de los botones de zoom (arriba a
// la izquierda) y la atribución de OpenStreetMap (abajo).
const MARGEN_VENTANITA_ARRIBA_IZQUIERDA: [number, number] = [56, 16]
const MARGEN_VENTANITA_ABAJO_DERECHA: [number, number] = [16, 28]

function resumirDescripcion(descripcion: string) {
  return descripcion.length > 120 ? `${descripcion.slice(0, 120)}…` : descripcion
}

// Encuadra todos los reportes visibles al cargar o al cambiar el filtro. Depende de las coordenadas como texto y
// no del arreglo: el detalle pasa un arreglo nuevo en cada render y, si no, el mapa volvería a su encuadre
// (perdiendo el zoom del usuario) con cada tecla escrita en un comentario.
// También vuelve a encuadrar cuando cambia el tamaño del mapa (ventana más angosta, la hoja de estilos de Leaflet
// que llega después, el contenedor que termina de medirse), mientras la persona no lo haya movido: Leaflet solo
// escucha el cambio de tamaño de la ventana y, con el tamaño viejo, el marcador del detalle quedaba descentrado.
function AjustarVista({ reportes }: { reportes: ReporteResponse[] }) {
  const map = useMap()
  const puntos = reportes.map((reporte) => `${reporte.latitud},${reporte.longitud}`).join(';')

  useEffect(() => {
    if (!puntos) return
    const coordenadas = puntos.split(';').map((punto) => punto.split(',').map(Number) as [number, number])
    let ajustando = false
    let movidoPorLaPersona = false

    const encuadrar = () => {
      ajustando = true
      if (coordenadas.length === 1) map.setView(coordenadas[0], 16, { animate: false })
      // Más margen arriba: el marcador se dibuja por encima de su coordenada y, si no, los del borde superior se cortaban.
      else map.fitBounds(coordenadas, { paddingTopLeft: [30, 55], paddingBottomRight: [30, 20], maxZoom: 16, animate: false })
      ajustando = false
    }
    const marcarMovido = () => { if (!ajustando) movidoPorLaPersona = true }
    const observador = new ResizeObserver(() => {
      map.invalidateSize({ animate: false })
      if (!movidoPorLaPersona) encuadrar()
    })

    encuadrar()
    map.on('dragstart zoomstart', marcarMovido)
    observador.observe(map.getContainer())
    return () => {
      observador.disconnect()
      map.off('dragstart zoomstart', marcarMovido)
    }
  }, [map, puntos])

  return null
}

// Marcadores con su ventanita, que se abre al pasar el mouse (o con un toque en el celular). Si no cabe, el mapa se
// desplaza para mostrarla entera (autoPan): con autoPan apagado, junto al borde quedaba cortada o bajo los botones.
function MarcadoresReportes({ reportes, enlaces }: Required<MapaReportesProps>) {
  const map = useMap()
  const cierre = useRef<number | undefined>(undefined)
  const abierto = useRef<MarcadorLeaflet | null>(null)
  const raton = useRef<{ x: number; y: number } | null>(null)
  const desplazando = useRef(false)

  const cancelarCierre = () => window.clearTimeout(cierre.current)
  const programarCierre = (marcador: MarcadorLeaflet) => {
    window.clearTimeout(cierre.current)
    cierre.current = window.setTimeout(() => marcador.closePopup(), ESPERA_CIERRE_MS)
  }

  useEffect(() => {
    const contenedor = map.getContainer()
    const registrarRaton = (event: MouseEvent) => { raton.current = { x: event.clientX, y: event.clientY } }
    const alDesplazar = () => { desplazando.current = true }
    // Al desplazarse, el marcador se aleja del mouse aunque la persona no lo mueva. Terminado el movimiento, la
    // ventanita sigue abierta solo si el mouse quedó sobre ella o sobre su marcador.
    const alTerminar = () => {
      if (!desplazando.current) return
      desplazando.current = false
      const marcador = abierto.current
      if (!marcador || !raton.current) return
      const debajo = document.elementFromPoint(raton.current.x, raton.current.y)
      const encima = debajo && (marcador.getElement()?.contains(debajo) || marcador.getPopup()?.getElement()?.contains(debajo))
      if (!encima) {
        window.clearTimeout(cierre.current)
        cierre.current = window.setTimeout(() => marcador.closePopup(), ESPERA_CIERRE_MS)
      }
    }

    contenedor.addEventListener('mousemove', registrarRaton)
    map.on('autopanstart', alDesplazar)
    map.on('moveend', alTerminar)
    return () => {
      window.clearTimeout(cierre.current)
      contenedor.removeEventListener('mousemove', registrarRaton)
      map.off('autopanstart', alDesplazar)
      map.off('moveend', alTerminar)
    }
  }, [map])

  const eventos: LeafletEventHandlerFnMap = {
    mouseover: (event) => {
      cancelarCierre()
      event.target.openPopup()
    },
    // Mientras el mapa se desplaza para mostrar la ventanita, salir del marcador no la cierra (ver alTerminar).
    mouseout: (event) => { if (!desplazando.current) programarCierre(event.target) },
    // Un toque en celular emite mouseover y luego click: sin esto, el click cerraría la ventanita recién abierta.
    click: (event) => {
      const marcador: MarcadorLeaflet = event.target
      cancelarCierre()
      window.setTimeout(() => marcador.openPopup(), 0)
    },
    popupopen: (event) => {
      abierto.current = event.target
      const contenedor = event.popup.getElement()
      if (!contenedor) return
      contenedor.onmouseenter = cancelarCierre
      contenedor.onmouseleave = () => programarCierre(event.target)
    },
    popupclose: (event) => { if (abierto.current === event.target) abierto.current = null },
  }

  return reportes.map((reporte) => {
    const foto = reporte.fotos?.[0]
    return (
      <Marker
        key={reporte.id}
        position={[reporte.latitud, reporte.longitud]}
        icon={pinEstado(reporte.estado)}
        riseOnHover
        eventHandlers={eventos}
      >
        <Popup autoPanPaddingTopLeft={MARGEN_VENTANITA_ARRIBA_IZQUIERDA} autoPanPaddingBottomRight={MARGEN_VENTANITA_ABAJO_DERECHA}>
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
      </Marker>
    )
  })
}

export function MapaReportes({ reportes, enlaces = true }: MapaReportesProps) {
  return (
    <MapContainer className="leaflet-map" center={CARTAGENA} zoom={13} scrollWheelZoom={false}>
      <TileLayer attribution={ATRIBUCION_OSM} url={TESELAS_OSM} />
      <ZoomConRueda />
      <AjustarVista reportes={reportes} />
      <MarcadoresReportes reportes={reportes} enlaces={enlaces} />
    </MapContainer>
  )
}
