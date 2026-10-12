export type Estado = "PENDIENTE" | "EN_REVISION" | "EN_PROCESO" | "RESUELTO" | "RECHAZADO"

export interface Reporte {
  id: string
  usuarioId?: string
  categoria: string
  categoriaId?: string
  descripcion?: string
  lugar: string
  direccionOsm?: string | null
  fecha: string
  fechaCreacion?: string
  estado: string
  estadoRaw?: Estado
  estadoClase: string
  color: string
  foto?: string
  fotos?: string[]
  latitud?: number
  longitud?: number
  prioridad?: "BAJA" | "MEDIA" | "ALTA"
}

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api"
export const BACKEND_URL = API_URL.replace(/\/api\/?$/, "")

const estadoVisual: Record<Estado, { texto: string; clase: string }> = {
  PENDIENTE: { texto: "Pendiente", clase: "review" },
  EN_REVISION: { texto: "En revisión", clase: "review" },
  EN_PROCESO: { texto: "En proceso", clase: "process" },
  RESUELTO: { texto: "Resuelto", clase: "done" },
  RECHAZADO: { texto: "Rechazado", clase: "rejected" },
}

function tiempoRelativo(fecha?: string) {
  if (!fecha) return "Recientemente"
  const dias = Math.floor((Date.now() - new Date(fecha).getTime()) / 86_400_000)
  if (dias <= 0) return "Hoy"
  if (dias === 1) return "Ayer"
  return `Hace ${dias} días`
}

function normalizar(reporte: Record<string, unknown>): Reporte {
  const estado = (reporte.estado as Estado) ?? "PENDIENTE"
  const visual = estadoVisual[estado]
  return {
    id: String(reporte.id),
    usuarioId: String(reporte.usuarioId ?? ""),
    categoria: String(reporte.categoriaId ?? "Reporte ciudadano"),
    categoriaId: String(reporte.categoriaId ?? ""),
    descripcion: String(reporte.descripcion ?? ""),
    lugar: String(reporte.direccionOsm ?? `${Number(reporte.latitud).toFixed(5)}, ${Number(reporte.longitud).toFixed(5)}`),
    direccionOsm: reporte.direccionOsm as string | null,
    fecha: tiempoRelativo(reporte.fechaCreacion as string),
    fechaCreacion: reporte.fechaCreacion as string,
    estado: visual.texto,
    estadoRaw: estado,
    estadoClase: visual.clase,
    color: estado === "RESUELTO" ? "green" : estado === "EN_PROCESO" ? "yellow" : "coral",
    fotos: (reporte.fotos as string[]) ?? [],
    latitud: reporte.latitud as number,
    longitud: reporte.longitud as number,
    prioridad: reporte.prioridad as Reporte["prioridad"],
  }
}

export async function cargarReportes(): Promise<Reporte[]> {
  const respuesta = await fetch(`${API_URL}/reportes`)
  if (!respuesta.ok) throw new Error("No fue posible cargar los reportes")
  return (await respuesta.json()).map(normalizar)
}

export async function cargarReporte(id: string): Promise<Reporte> {
  const respuesta = await fetch(`${API_URL}/reportes/${id}`)
  if (!respuesta.ok) throw new Error("Reporte no encontrado")
  return normalizar(await respuesta.json())
}

const fondos: Record<string, string> = {
  hueco: "https://images.unsplash.com/photo-1583531352515-8884af319dc1?crop=entropy&cs=tinysrgb&fit=crop&fm=jpg&q=80&w=900",
  alumbrado: "https://images.unsplash.com/photo-1714686495394-73e2bb1bbd39?crop=entropy&cs=tinysrgb&fit=crop&fm=jpg&q=80&w=900",
  inundación: "https://images.unsplash.com/photo-1561369391-9a5190eba03d?crop=entropy&cs=tinysrgb&fit=crop&fm=jpg&q=80&w=900",
}

export function fotoReporte(reporte: Reporte) {
  const foto = reporte.fotos?.[0] ?? reporte.foto
  if (foto) return foto.startsWith("http") ? foto : `${BACKEND_URL}${foto}`
  const categoria = reporte.categoria.toLocaleLowerCase("es")
  return Object.entries(fondos).find(([clave]) => categoria.includes(clave))?.[1] ?? fondos.hueco
}
