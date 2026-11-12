import client from './client'
import type {
  EstadoReporte,
  OpcionesFiltroResponse,
  PaginaResponse,
  PosibleDuplicadoResponse,
  ReporteActualizarRequest,
  ReporteCrearRequest,
  ReporteMapaResponse,
  ReporteResponse,
  ResumenReportesResponse,
} from './types'

// Filtros de GET /reportes. Categoría y barrio comparan el valor completo y `q` busca en categoría, descripción,
// dirección y barrio, sin distinguir mayúsculas ni tildes. `pagina` empieza en 0 y `tamano` va de 1 a 100 (20 por
// defecto).
export interface FiltroReportes {
  estado?: EstadoReporte
  categoria?: string
  barrio?: string
  q?: string
  pagina?: number
  tamano?: number
}

// Una página de reportes, del más reciente al más antiguo. Los filtros vacíos no se envían.
export async function listar(filtro: FiltroReportes = {}): Promise<PaginaResponse<ReporteResponse>> {
  const params = Object.fromEntries(Object.entries(filtro).filter(([, valor]) =>
    valor !== undefined && valor !== null && (typeof valor !== 'string' || valor.trim() !== '')))
  const response = await client.get<PaginaResponse<ReporteResponse>>('/reportes', { params })
  return response.data
}

// Todos los puntos del mapa (hasta 5000, los más recientes) con solo lo que dibuja el mapa.
export async function mapa(estado?: EstadoReporte): Promise<ReporteMapaResponse[]> {
  const response = await client.get<ReporteMapaResponse[]>('/reportes/mapa', { params: estado ? { estado } : {} })
  return response.data
}

// Categorías y barrios que tiene algún reporte, para los selectores de filtro.
export async function filtros(): Promise<OpcionesFiltroResponse> {
  const response = await client.get<OpcionesFiltroResponse>('/reportes/filtros')
  return response.data
}

// Reportes del usuario autenticado, del más reciente al más antiguo.
export async function listarMios(estado?: EstadoReporte): Promise<ReporteResponse[]> {
  const response = await client.get<ReporteResponse[]>('/reportes/mios', { params: estado ? { estado } : {} })
  return response.data
}

export async function resumen(): Promise<ResumenReportesResponse> {
  const response = await client.get<ResumenReportesResponse>('/reportes/resumen')
  return response.data
}

export async function obtener(id: string): Promise<ReporteResponse> {
  const response = await client.get<ReporteResponse>(`/reportes/${id}`)
  return response.data
}

export async function crear(data: ReporteCrearRequest): Promise<ReporteResponse> {
  const response = await client.post<ReporteResponse>('/reportes', data)
  return response.data
}

export async function subirFotos(reporteId: string, archivos: File[]): Promise<ReporteResponse> {
  const formData = new FormData()
  archivos.forEach((archivo) => formData.append('archivos', archivo))
  const response = await client.post<ReporteResponse>(`/reportes/${reporteId}/fotos`, formData)
  return response.data
}

export async function actualizar(id: string, data: ReporteActualizarRequest): Promise<ReporteResponse> {
  const response = await client.put<ReporteResponse>(`/reportes/${id}`, data)
  return response.data
}

export async function cercanos(
  lat: number,
  lng: number,
  radioKm: number,
  estado?: EstadoReporte,
): Promise<ReporteResponse[]> {
  const response = await client.get<ReporteResponse[]>('/reportes/cercanos', {
    params: { lat, lng, radioKm, ...(estado ? { estado } : {}) },
  })
  return response.data
}

// Reportes activos a menos de 100 m del punto, del más cercano al más lejano.
export async function posiblesDuplicados(lat: number, lng: number): Promise<PosibleDuplicadoResponse[]> {
  const response = await client.get<PosibleDuplicadoResponse[]>('/reportes/posibles-duplicados', {
    params: { lat, lng },
  })
  return response.data
}
