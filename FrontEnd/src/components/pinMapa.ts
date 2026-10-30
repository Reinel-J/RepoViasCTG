import L from 'leaflet'
import { textoEstado } from '../api/reporteVista'
import type { EstadoReporte } from '../api/types'
import { COLOR_ESTADO } from './mapaBase'

// Marcador en forma de gota (como los de FixMyStreet) pintado con el color del estado. Es SVG dentro de un divIcon,
// así que no depende de imágenes ni de la ruta que Leaflet deduce para ellas. Solo lo importan los componentes de
// mapa: importa Leaflet, que debe quedar fuera de la carga inicial (ver vite.config.ts).
const ANCHO = 30
const ALTO = 42

// La etiqueta va dentro del HTML del ícono: solo se le pasan textos fijos, nunca datos escritos por usuarios.
function crearPin(color: string, etiqueta: string) {
  return L.divIcon({
    className: 'mapa-pin',
    html: `<svg width="${ANCHO}" height="${ALTO}" viewBox="0 0 30 42" role="img" aria-label="${etiqueta}">`
      + `<path d="M15 1C7.3 1 1 7.2 1 14.9c0 10.4 12.3 24.6 13.2 25.6a1.1 1.1 0 0 0 1.6 0C16.7 39.5 29 25.3 29 14.9 29 7.2 22.7 1 15 1z" fill="${color}" stroke="#ffffff" stroke-width="2"/>`
      + '<circle cx="15" cy="15" r="5.5" fill="#ffffff"/></svg>',
    iconSize: [ANCHO, ALTO],
    // La punta de la gota es la que señala la coordenada.
    iconAnchor: [ANCHO / 2, ALTO - 1],
    popupAnchor: [0, -ALTO + 6],
  })
}

const pinesPorEstado = new Map<EstadoReporte, L.DivIcon>()

export function pinEstado(estado: EstadoReporte) {
  let pin = pinesPorEstado.get(estado)
  if (!pin) {
    pin = crearPin(COLOR_ESTADO[estado], `Reporte ${textoEstado(estado).toLowerCase()}`)
    pinesPorEstado.set(estado, pin)
  }
  return pin
}

// El punto que se está marcando en el formulario: un reporte nuevo nace pendiente.
export const PIN_NUEVO = crearPin(COLOR_ESTADO.PENDIENTE, 'Punto del reporte')
