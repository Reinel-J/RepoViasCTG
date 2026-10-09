import type { EstadoReporte, PrioridadReporte, Rol } from "../../api/types"

export const ESTADOS: EstadoReporte[] = ["PENDIENTE", "EN_REVISION", "EN_PROCESO", "RESUELTO", "RECHAZADO"]

export const ETIQUETA_ESTADO: Record<EstadoReporte, string> = {
  PENDIENTE: "Pendiente",
  EN_REVISION: "En revisión",
  EN_PROCESO: "En proceso",
  RESUELTO: "Resuelto",
  RECHAZADO: "Rechazado",
}

// Debe coincidir con ReporteService.validarTransicion en el backend.
export const SIGUIENTES: Record<EstadoReporte, EstadoReporte[]> = {
  PENDIENTE: ["EN_REVISION", "RECHAZADO"],
  EN_REVISION: ["EN_PROCESO", "RECHAZADO"],
  EN_PROCESO: ["RESUELTO", "RECHAZADO"],
  RESUELTO: [],
  RECHAZADO: [],
}

export const ACCION_ESTADO: Record<EstadoReporte, string> = {
  PENDIENTE: "Pendiente",
  EN_REVISION: "Pasar a revisión",
  EN_PROCESO: "Iniciar trabajo",
  RESUELTO: "Marcar resuelto",
  RECHAZADO: "Rechazar",
}

export const PRIORIDADES: PrioridadReporte[] = ["BAJA", "MEDIA", "ALTA"]

export const ROLES: Rol[] = ["CIUDADANO", "MODERADOR", "ADMIN"]

export function mensajeError(exception: unknown, porDefecto: string) {
  return exception instanceof Error ? exception.message : porDefecto
}
