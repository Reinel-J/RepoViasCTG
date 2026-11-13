import { describe, expect, it } from "vitest"
import { fechaLocalAISO } from "./admin"

describe("fechaLocalAISO", () => {
  it("convierte una fecha local al formato ISO con zona horaria", () => {
    expect(fechaLocalAISO("2026-11-12T03:00")).toMatch(/^2026-11-12T\d{2}:00:00\.000Z$/)
  })

  it("omite las fechas vacías o inválidas", () => {
    expect(fechaLocalAISO("")).toBeUndefined()
    expect(fechaLocalAISO("fecha inválida")).toBeUndefined()
  })
})
