import { describe, expect, it } from "vitest"
import { ESTADOS, SIGUIENTES } from "./estados"

// Debe coincidir con ReporteService.validarTransicion del backend (y con el documento de visión:
// PENDIENTE → EN_REVISION → EN_PROCESO → RESUELTO / RECHAZADO).
describe("transiciones de estado", () => {
  it("siguen el flujo del backend", () => {
    expect(SIGUIENTES).toEqual({
      PENDIENTE: ["EN_REVISION", "RECHAZADO"],
      EN_REVISION: ["EN_PROCESO", "RECHAZADO"],
      EN_PROCESO: ["RESUELTO", "RECHAZADO"],
      RESUELTO: [],
      RECHAZADO: [],
    })
  })

  it("todo estado activo se puede rechazar y los finales no cambian", () => {
    for (const estado of ESTADOS) {
      const final = estado === "RESUELTO" || estado === "RECHAZADO"
      expect(SIGUIENTES[estado].includes("RECHAZADO")).toBe(!final)
      if (final) expect(SIGUIENTES[estado]).toHaveLength(0)
    }
  })
})
