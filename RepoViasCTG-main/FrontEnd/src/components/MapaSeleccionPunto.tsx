import { useEffect, useState } from 'react'
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet'

type Punto = [number, number]

interface MapaSeleccionPuntoProps {
  onSeleccionar: (lat: number, lng: number) => void
  puntoInicial?: Punto
}

const CARTAGENA: Punto = [10.391, -75.479]

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

function CentrarMapa({ punto }: { punto: Punto }) {
  const map = useMap()

  useEffect(() => {
    map.setView(punto, 15)
  }, [map, punto])

  return null
}

export function MapaSeleccionPunto({ onSeleccionar, puntoInicial }: MapaSeleccionPuntoProps) {
  const [punto, setPunto] = useState<Punto | undefined>(puntoInicial)
  const [mensajeError, setMensajeError] = useState('')

  function seleccionarPunto(nuevoPunto: Punto) {
    setPunto(nuevoPunto)
    setMensajeError('')
  }

  function usarMiUbicacion() {
    if (!navigator.geolocation) {
      setMensajeError('Tu navegador no permite obtener la ubicación.')
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const nuevoPunto: Punto = [position.coords.latitude, position.coords.longitude]
        seleccionarPunto(nuevoPunto)
        onSeleccionar(...nuevoPunto)
      },
      () => setMensajeError('No fue posible obtener tu ubicación. Revisa los permisos del navegador.'),
    )
  }

  return (
    <section>
      <button type="button" onClick={usarMiUbicacion}>Usar mi ubicación</button>
      {mensajeError && <p role="alert">{mensajeError}</p>}
      <MapContainer center={punto ?? CARTAGENA} zoom={13} scrollWheelZoom style={{ height: '360px', width: '100%' }}>
        <TileLayer
          attribution="&copy; OpenStreetMap contributors"
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <SelectorPunto onSeleccionar={onSeleccionar} seleccionarPunto={seleccionarPunto} />
        {punto && <><CentrarMapa punto={punto} /><Marker position={punto} /></>}
      </MapContainer>
    </section>
  )
}
