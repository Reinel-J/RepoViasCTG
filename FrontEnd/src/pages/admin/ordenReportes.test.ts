import { describe, expect, it } from "vitest"
import type { ReporteResponse } from "../../api/types"
import { ordenar } from "./ordenReportes"

function reporte(id: string, prioridad: ReporteResponse["prioridad"], fechaCreacion: string): Pick<ReporteResponse, "id" | "prioridad" | "fechaCreacion"> {
  return { id, prioridad, fechaCreacion }
}

describe("ordenar", () => {
  it("pone primero las prioridades altas", () => {
    const lista = [reporte("b", "BAJA", "2026-01-01"), reporte("a", "ALTA", "2026-01-03"), reporte("m", "MEDIA", "2026-01-02")]
    expect(ordenar(lista, "prioridad").map(({ id }) => id)).toEqual(["a", "m", "b"])
  })

  it("desempata la prioridad por los reportes más antiguos", () => {
    const lista = [reporte("nuevo", "ALTA", "2026-02-01"), reporte("viejo", "ALTA", "2026-01-01")]
    expect(ordenar(lista, "prioridad").map(({ id }) => id)).toEqual(["viejo", "nuevo"])
  })

  it("acepta una lista vacía", () => {
    expect(ordenar([], "recientes")).toEqual([])
  })

  it("no modifica la lista recibida", () => {
    const lista = [reporte("viejo", "MEDIA", "2026-01-01"), reporte("nuevo", "ALTA", "2026-02-01")]
    const copia = [...lista]
    ordenar(lista, "recientes")
    expect(lista).toEqual(copia)
  })
})
