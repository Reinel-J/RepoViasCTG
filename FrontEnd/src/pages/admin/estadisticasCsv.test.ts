import { describe, expect, it } from "vitest"
import type { EstadisticasResponse } from "../../api/types"
import { estadisticasACsv, nombreArchivoEstadisticas } from "./estadisticasCsv"

const DATOS: EstadisticasResponse = {
  total: 200,
  porEstado: { PENDIENTE: 50, EN_REVISION: 30, EN_PROCESO: 40, RESUELTO: 60, RECHAZADO: 20 },
  porcentajeResuelto: 33,
  diasPrimeraRespuesta: 1.9,
  diasResolucion: 24.8,
  meses: [{ mes: "2026-09", creados: 29, resueltos: 7 }, { mes: "2026-10", creados: 63, resueltos: 10 }],
  categorias: [{ id: "c1", nombre: "Daño en alcantarillado, sumidero", total: 20, resueltos: 4 }],
  densidad: [{ latitud: 10.42, longitud: -75.55, total: 3 }],
  prioridadActivos: { BAJA: 10, MEDIA: 90, ALTA: 20 },
  pendienteMasAntiguo: "2026-09-01T12:00:00Z",
}

describe("estadisticasACsv", () => {
  it("escribe una cifra por fila: resumen, estados, meses y categorías", () => {
    expect(estadisticasACsv(DATOS).split("\r\n")).toEqual([
      "seccion,elemento,medida,valor",
      "Resumen,,Total de reportes,200",
      "Resumen,,Porcentaje resuelto (sin rechazados),33",
      "Resumen,,Días promedio hasta la primera respuesta,1.9",
      "Resumen,,Días promedio hasta resolver,24.8",
      "Por estado,PENDIENTE,Reportes,50",
      "Por estado,EN_REVISION,Reportes,30",
      "Por estado,EN_PROCESO,Reportes,40",
      "Por estado,RESUELTO,Reportes,60",
      "Por estado,RECHAZADO,Reportes,20",
      "Por mes,2026-09,Creados,29",
      "Por mes,2026-09,Resueltos,7",
      "Por mes,2026-10,Creados,63",
      "Por mes,2026-10,Resueltos,10",
      'Categorías,"Daño en alcantarillado, sumidero",Reportes,20',
      'Categorías,"Daño en alcantarillado, sumidero",Resueltos,4',
      "",
    ])
  })

  it("deja vacías las cifras que aún no se pueden calcular", () => {
    const vacio = { ...DATOS, total: 0, porcentajeResuelto: null, diasPrimeraRespuesta: null, diasResolucion: null }
    expect(estadisticasACsv(vacio)).toContain("Resumen,,Porcentaje resuelto (sin rechazados),\r\n")
  })
})

describe("nombreArchivoEstadisticas", () => {
  it("lleva la fecha local en formato AAAA-MM-DD", () => {
    expect(nombreArchivoEstadisticas(new Date(2026, 9, 5, 23, 30))).toBe("viactg-estadisticas-2026-10-05.csv")
  })
})
