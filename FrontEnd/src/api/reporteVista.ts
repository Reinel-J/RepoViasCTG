import * as reportesApi from './reportes'
import { empiezaPalabra } from '../utils/texto'
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
  barrio: string | null
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

/**
 * Por qué no se puede editar un reporte (texto para mostrar), o null si sí se puede. Repite las reglas de
 * ReporteService.actualizar para avisar al abrir el formulario y no recién al guardar.
 */
export function motivoNoEditable(reporte: Pick<ReporteResponse, 'usuarioId' | 'estado'>, usuarioId?: string) {
  if (reporte.usuarioId !== usuarioId) return 'Solo quien creó este reporte puede editarlo.'
  if (reporte.estado !== 'PENDIENTE') {
    return `Este reporte ya está «${textoEstado(reporte.estado).toLowerCase()}»: solo se pueden editar los reportes pendientes, antes de que un moderador los revise.`
  }
  return null
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
    barrio: reporte.barrioOsm ?? null,
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

// Del más reciente al más antiguo (así los ordena el backend).
export async function cargarReportes(estado?: EstadoReporte, limite?: number): Promise<Reporte[]> {
  return (await reportesApi.listar(estado, limite)).map(normalizar)
}

export async function cargarMisReportes(): Promise<Reporte[]> {
  return (await reportesApi.listarMios()).map(normalizar)
}

export async function cargarReporte(id: string): Promise<Reporte> {
  return normalizar(await reportesApi.obtener(id))
}

// Las fotos llegan como ruta relativa (/uploads/...): se completan con la URL del backend.
export function urlFoto(ruta: string): string {
  return ruta.startsWith('http') ? ruta : `${BACKEND_URL}${ruta}`
}

// URL de la primera foto real del reporte, o null si no tiene ninguna.
export function fotoReporte(reporte: Reporte): string | null {
  const foto = reporte.fotos[0]
  return foto ? urlFoto(foto) : null
}

// La categoría es texto libre: se busca, en este orden, una palabra que empiece por alguna clave (sin tildes ni
// mayúsculas). Derrumbe va antes que la vía sin pavimentar para que "derrumbe de tierra" no caiga en "tierra".
const ilustraciones: [string[], string][] = [
  [['alcantarilla', 'manjol'], 'alcantarilla'],
  [['hueco', 'bache'], 'hueco'],
  [['hundimiento', 'hundid', 'socavon', 'deformacion'], 'hundimiento'],
  [['derrumbe', 'desliz', 'alud'], 'derrumbe'],
  [['inunda', 'encharc'], 'inundacion'],
  [['alumbrado', 'poste', 'luz', 'lampara'], 'alumbrado'],
  [['sin pavimentar', 'destapad', 'tierra'], 'via_sin_pavimentar'],
  [['anden', 'acera'], 'anden'],
  [['senal', 'semaforo', 'pare'], 'senalizacion'],
  [['escombro', 'basura'], 'escombros'],
  [['obstaculo', 'bloque', 'arbol'], 'obstaculo'],
]

// Ilustración genérica de la categoría, para reportes sin fotos. No es evidencia: debe rotularse como ilustrativa.
export function imagenCategoria(texto: string): string {
  const nombre = ilustraciones.find(([claves]) => claves.some((clave) => empiezaPalabra(texto, clave)))?.[1] ?? 'otro'
  return `/img/categorias/${nombre}.svg`
}

// URLs de todas las fotos del reporte (hasta 3).
export function fotosReporte(reporte: Reporte): string[] {
  return reporte.fotos.map(urlFoto)
}
