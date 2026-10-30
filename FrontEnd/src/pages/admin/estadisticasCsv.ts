import type { EstadisticasResponse } from "../../api/types"
import { textoCsv, type CeldaCsv } from "../../utils/csv"
import { ESTADOS } from "./estados"

// Formato largo (una cifra por fila): se filtra en Excel y se carga en Power BI sin reorganizar columnas.
// Los estados van con su nombre del sistema (PENDIENTE…), como en Metabase y en los CSV de Power BI.
export function estadisticasACsv(datos: EstadisticasResponse): string {
  const filas: CeldaCsv[][] = [
    ["seccion", "elemento", "medida", "valor"],
    ["Resumen", "", "Total de reportes", datos.total],
    ["Resumen", "", "Porcentaje resuelto (sin rechazados)", datos.porcentajeResuelto],
    ["Resumen", "", "Días promedio hasta la primera respuesta", datos.diasPrimeraRespuesta],
    ["Resumen", "", "Días promedio hasta resolver", datos.diasResolucion],
    ...ESTADOS.map((estado): CeldaCsv[] => ["Por estado", estado, "Reportes", datos.porEstado[estado] ?? 0]),
    ...datos.meses.flatMap((mes): CeldaCsv[][] => [
      ["Por mes", mes.mes, "Creados", mes.creados],
      ["Por mes", mes.mes, "Resueltos", mes.resueltos],
    ]),
    ...datos.categorias.flatMap((categoria): CeldaCsv[][] => [
      ["Categorías", categoria.nombre, "Reportes", categoria.total],
      ["Categorías", categoria.nombre, "Resueltos", categoria.resueltos],
    ]),
  ]
  return textoCsv(filas)
}

// viactg-estadisticas-2026-10-30.csv, con la fecha local de quien descarga.
export function nombreArchivoEstadisticas(fecha = new Date()): string {
  const dos = (n: number) => String(n).padStart(2, "0")
  return `viactg-estadisticas-${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}.csv`
}
