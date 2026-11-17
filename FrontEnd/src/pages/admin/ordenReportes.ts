import type { PrioridadReporte, ReporteResponse } from "../../api/types"

export type OrdenReportes = "prioridad" | "antiguos" | "recientes"

export const PESO_PRIORIDAD: Record<PrioridadReporte, number> = { ALTA: 0, MEDIA: 1, BAJA: 2 }

/** Devuelve una copia estable: nunca altera la página que entrega la API. */
export function ordenar<T extends Pick<ReporteResponse, "prioridad" | "fechaCreacion">>(lista: readonly T[], orden: OrdenReportes): T[] {
  return lista.map((reporte, indice) => ({ reporte, indice })).sort((a, b) => {
    const fechaA = new Date(a.reporte.fechaCreacion).getTime()
    const fechaB = new Date(b.reporte.fechaCreacion).getTime()
    const comparacion = orden === "prioridad"
      ? PESO_PRIORIDAD[a.reporte.prioridad] - PESO_PRIORIDAD[b.reporte.prioridad] || fechaA - fechaB
      : orden === "antiguos" ? fechaA - fechaB : fechaB - fechaA
    return comparacion || a.indice - b.indice
  }).map(({ reporte }) => reporte)
}
