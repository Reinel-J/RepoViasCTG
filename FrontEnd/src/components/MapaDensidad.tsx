import 'leaflet/dist/leaflet.css'
import { useEffect } from 'react'
import { CircleMarker, MapContainer, TileLayer, Tooltip, useMap } from 'react-leaflet'
import type { EstadisticasResponse } from '../api/types'
import { colorDensidad } from './densidad'
import { ATRIBUCION_OSM, CARTAGENA, TESELAS_OSM } from './mapaBase'

type Celda = EstadisticasResponse['densidad'][number]

function AjustarVista({ celdas }: { celdas: Celda[] }) {
  const map = useMap()

  useEffect(() => {
    if (celdas.length === 0) return
    map.fitBounds(celdas.map((celda) => [celda.latitud, celda.longitud] as [number, number]), { padding: [30, 30], maxZoom: 16 })
  }, [map, celdas])

  return null
}

export function MapaDensidad({ celdas }: { celdas: Celda[] }) {
  const maximo = Math.max(1, ...celdas.map((celda) => celda.total))

  return (
    <MapContainer className="leaflet-map density-map" center={CARTAGENA} zoom={12} scrollWheelZoom>
      <TileLayer attribution={ATRIBUCION_OSM} url={TESELAS_OSM} />
      <AjustarVista celdas={celdas} />
      {celdas.map((celda) => {
        const color = colorDensidad(celda.total, maximo)
        return (
          <CircleMarker
            key={`${celda.latitud},${celda.longitud}`}
            center={[celda.latitud, celda.longitud]}
            // El área (no el radio) crece con el número de reportes.
            radius={8 + 14 * Math.sqrt(celda.total / maximo)}
            pathOptions={{ color: '#ffffff', weight: 2, fillColor: color, fillOpacity: 0.85 }}
          >
            <Tooltip direction="top">{celda.total} {celda.total === 1 ? 'reporte activo' : 'reportes activos'} en esta zona</Tooltip>
          </CircleMarker>
        )
      })}
    </MapContainer>
  )
}
