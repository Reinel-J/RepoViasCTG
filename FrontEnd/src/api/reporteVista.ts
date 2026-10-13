import * as reportesApi from './reportes'
import type { EstadoReporte, HistorialEstadoResponse, PrioridadReporte, ReporteResponse } from './types'

// Reporte tal como lo consumen las pantallas públicas: el DTO del backend más los textos y clases ya resueltos.
export interface Reporte {
  id: string
  usuarioId: string
  categoria: string
  categoriaId: string
  descripcion: string
  lugar: string
  direccionOsm: string | null
  fecha: string
  fechaCreacion: string
  estado: string
  estadoRaw: EstadoReporte
  estadoClase: string
  color: string
  fotos: string[]
  latitud: number
  longitud: number
  prioridad: PrioridadReporte
  historialEstados: HistorialEstadoResponse[]
}

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'
const BACKEND_URL = API_URL.replace(/\/api\/?$/, '')

const estadoVisual: Record<EstadoReporte, { texto: string; clase: string }> = {
  PENDIENTE: { texto: 'Pendiente', clase: 'review' },
  EN_REVISION: { texto: 'En revisión', clase: 'review' },
  EN_PROCESO: { texto: 'En proceso', clase: 'process' },
  RESUELTO: { texto: 'Resuelto', clase: 'done' },
  RECHAZADO: { texto: 'Rechazado', clase: 'rejected' },
}

export function textoEstado(estado: EstadoReporte) {
  return estadoVisual[estado]?.texto ?? estado
}

function tiempoRelativo(fecha?: string) {
  if (!fecha) return 'Recientemente'
  const dias = Math.floor((Date.now() - new Date(fecha).getTime()) / 86_400_000)
  if (dias <= 0) return 'Hoy'
  if (dias === 1) return 'Ayer'
  return `Hace ${dias} días`
}

function normalizar(reporte: ReporteResponse): Reporte {
  const estado = reporte.estado ?? 'PENDIENTE'
  const visual = estadoVisual[estado]
  return {
    id: reporte.id,
    usuarioId: reporte.usuarioId ?? '',
    categoria: reporte.categoriaId ?? 'Reporte ciudadano',
    categoriaId: reporte.categoriaId ?? '',
    descripcion: reporte.descripcion ?? '',
    lugar: reporte.direccionOsm ?? `${Number(reporte.latitud).toFixed(5)}, ${Number(reporte.longitud).toFixed(5)}`,
    direccionOsm: reporte.direccionOsm,
    fecha: tiempoRelativo(reporte.fechaCreacion),
    fechaCreacion: reporte.fechaCreacion,
    estado: visual.texto,
    estadoRaw: estado,
    estadoClase: visual.clase,
    color: estado === 'RESUELTO' ? 'green' : estado === 'EN_PROCESO' ? 'yellow' : estado === 'RECHAZADO' ? 'gray' : 'coral',
    fotos: reporte.fotos ?? [],
    latitud: reporte.latitud,
    longitud: reporte.longitud,
    prioridad: reporte.prioridad ?? 'MEDIA',
    historialEstados: reporte.historialEstados ?? [],
  }
}

export async function cargarReportes(): Promise<Reporte[]> {
  return (await reportesApi.listar()).map(normalizar)
}

export async function cargarReporte(id: string): Promise<Reporte> {
  return normalizar(await reportesApi.obtener(id))
}

// URL de la primera foto real del reporte, o null si no tiene ninguna.
export function fotoReporte(reporte: Reporte): string | null {
  const foto = reporte.fotos[0]
  if (!foto) return null
  return foto.startsWith('http') ? foto : `${BACKEND_URL}${foto}`
}
