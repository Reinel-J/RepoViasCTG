import client from './client'
import type {
  EstadoReporte,
  PosibleDuplicadoResponse,
  ReporteActualizarRequest,
  ReporteCrearRequest,
  ReporteResponse,
  ResumenReportesResponse,
} from './types'

// Del más reciente al más antiguo; con `limite` devuelve solo los N más recientes.
export async function listar(estado?: EstadoReporte, limite?: number): Promise<ReporteResponse[]> {
  const response = await client.get<ReporteResponse[]>('/reportes', {
    params: { ...(estado ? { estado } : {}), ...(limite ? { limite } : {}) },
  })
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
