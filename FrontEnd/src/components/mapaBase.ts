import type { EstadoReporte } from '../api/types'

// Teselas oficiales de OpenStreetMap (sin subdominios, como recomienda su política de uso) y la atribución
// que exige la licencia ODbL, con enlace a la página de derechos de autor.
export const TESELAS_OSM = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
export const ATRIBUCION_OSM = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

export const CARTAGENA: [number, number] = [10.391, -75.479]

// Color de cada estado en los mapas, con borde blanco para que se distingan sobre las calles.
export const COLOR_ESTADO: Record<EstadoReporte, string> = {
  PENDIENTE: '#e0533d',
  EN_REVISION: '#d97706',
  EN_PROCESO: '#2563eb',
  RESUELTO: '#15803d',
  RECHAZADO: '#6b7280',
}
