import 'leaflet/dist/leaflet.css'
import { useEffect, useState } from 'react'
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import { ATRIBUCION_OSM, CARTAGENA, TESELAS_OSM } from './mapaBase'
import { PIN_NUEVO } from './pinMapa'
import { ZoomConRueda } from './ZoomConRueda'

type Punto = [number, number]

// Por encima de este margen la posición no sirve para ubicar un daño: suele ser la estimación por la conexión a
// internet de un computador sin GPS, que en Colombia a menudo cae en Bogotá aunque la persona esté en Cartagena.
const MARGEN_MAXIMO_M = 2000

interface MapaSeleccionPuntoProps {
  onSeleccionar: (lat: number, lng: number) => void
  puntoInicial?: Punto
}

function SelectorPunto({ onSeleccionar, seleccionarPunto }: {
  onSeleccionar: (lat: number, lng: number) => void
  seleccionarPunto: (punto: Punto) => void
}) {
  useMapEvents({
    click(event) {
      const punto: Punto = [event.latlng.lat, event.latlng.lng]
      seleccionarPunto(punto)
      onSeleccionar(...punto)
    },
  })

  return null
}

// Centra el mapa solo cuando el punto llega de fuera (posición inicial o "Usar mi ubicación"), no tras un clic:
// así quien acercó el mapa para marcar el punto exacto no pierde el zoom.
function CentrarMapa({ destino }: { destino: Punto }) {
  const map = useMap()

  useEffect(() => {
    map.setView(destino, Math.max(map.getZoom(), 16), { animate: false })
  }, [map, destino])

  return null
}

export function MapaSeleccionPunto({ onSeleccionar, puntoInicial }: MapaSeleccionPuntoProps) {
  const [punto, setPunto] = useState<Punto | undefined>(puntoInicial)
  const [destino, setDestino] = useState<Punto | undefined>(puntoInicial)
  const [mensajeError, setMensajeError] = useState('')
  const [buscando, setBuscando] = useState(false)

  function seleccionarPunto(nuevoPunto: Punto) {
    setPunto(nuevoPunto)
    setMensajeError('')
  }

  function usarMiUbicacion() {
    if (!navigator.geolocation) {
      setMensajeError('Tu navegador no permite obtener la ubicación.')
      return
    }

    setBuscando(true)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setBuscando(false)
        const margen = position.coords.accuracy
        if (margen > MARGEN_MAXIMO_M) {
          // No se mueve el mapa: llevarlo a cientos de kilómetros obligaría a volver arrastrándolo.
          setMensajeError(`Tu dispositivo solo pudo estimar tu ubicación con un margen de unos ${Math.round(margen / 1000)} km (pasa en computadores sin GPS). Marca el punto tocando el mapa.`)
          return
        }
        const nuevoPunto: Punto = [position.coords.latitude, position.coords.longitude]
        seleccionarPunto(nuevoPunto)
        setDestino(nuevoPunto)
        onSeleccionar(...nuevoPunto)
        if (position.coords.accuracy > 100) {
          setMensajeError(`Tu ubicación tiene un margen de unos ${Math.round(position.coords.accuracy)} m: ajusta el punto tocando el mapa.`)
        }
      },
      () => {
        setBuscando(false)
        setMensajeError('No fue posible obtener tu ubicación. Revisa los permisos del navegador.')
      },
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 },
    )
  }

  return (
    <section className="location-picker">
      <button className="button button-ghost location-button" type="button" onClick={usarMiUbicacion} disabled={buscando}>
        {buscando ? 'Buscando tu ubicación…' : 'Usar mi ubicación'}
      </button>
      {mensajeError && <p className="alert error" role="alert">{mensajeError}</p>}
      <MapContainer className="leaflet-map leaflet-map-picker" center={puntoInicial ?? CARTAGENA} zoom={puntoInicial ? 16 : 13} scrollWheelZoom={false}>
        <TileLayer attribution={ATRIBUCION_OSM} url={TESELAS_OSM} />
        <ZoomConRueda />
        <SelectorPunto onSeleccionar={onSeleccionar} seleccionarPunto={seleccionarPunto} />
        {destino && <CentrarMapa destino={destino} />}
        {punto && <Marker position={punto} icon={PIN_NUEVO} />}
      </MapContainer>
    </section>
  )
}
