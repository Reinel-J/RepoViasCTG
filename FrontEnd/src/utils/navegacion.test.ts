import { describe, expect, it } from "vitest"
import { rutaSegura } from "./navegacion"

describe("rutaSegura", () => {
  it("acepta rutas internas", () => {
    expect(rutaSegura("/reportes/nuevo")).toBe("/reportes/nuevo")
    expect(rutaSegura("/reportes?estado=PENDIENTE")).toBe("/reportes?estado=PENDIENTE")
  })

  it("descarta destinos externos o vacíos", () => {
    expect(rutaSegura("//sitio-falso.com")).toBe("/")
    expect(rutaSegura("/\\sitio-falso.com")).toBe("/")
    expect(rutaSegura("https://sitio-falso.com")).toBe("/")
    expect(rutaSegura(undefined)).toBe("/")
  })
})
