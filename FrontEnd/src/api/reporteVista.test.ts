import { describe, expect, it } from "vitest"
import { imagenCategoria, motivoNoEditable } from "./reporteVista"

describe("motivoNoEditable", () => {
  it("deja editar al autor mientras el reporte está pendiente", () => {
    expect(motivoNoEditable({ usuarioId: "u1", estado: "PENDIENTE" }, "u1")).toBeNull()
  })

  it("no deja editar a otra persona ni sin sesión", () => {
    expect(motivoNoEditable({ usuarioId: "u1", estado: "PENDIENTE" }, "u2")).toBe("Solo quien creó este reporte puede editarlo.")
    expect(motivoNoEditable({ usuarioId: "u1", estado: "PENDIENTE" }, undefined)).toBe("Solo quien creó este reporte puede editarlo.")
  })

  it("explica que solo se editan los pendientes", () => {
    expect(motivoNoEditable({ usuarioId: "u1", estado: "EN_REVISION" }, "u1")).toContain("«en revisión»")
    expect(motivoNoEditable({ usuarioId: "u1", estado: "RESUELTO" }, "u1")).toContain("solo se pueden editar los reportes pendientes")
  })
})

describe("imagenCategoria", () => {
  it("tiene ilustración propia para las categorías del catálogo inicial", () => {
    expect(imagenCategoria("Hundimiento")).toBe("/img/categorias/hundimiento.svg")
    expect(imagenCategoria("Derrumbe")).toBe("/img/categorias/derrumbe.svg")
    expect(imagenCategoria("Obstáculo en la vía")).toBe("/img/categorias/obstaculo.svg")
    expect(imagenCategoria("Señal de PARE caída")).toBe("/img/categorias/senalizacion.svg")
  })

  it("compara por inicio de palabra: «pare» no coincide dentro de «reparar» ni «desaparecida»", () => {
    expect(imagenCategoria("Tapa desaparecida")).toBe("/img/categorias/otro.svg")
    expect(imagenCategoria("Vía por reparar")).toBe("/img/categorias/otro.svg")
  })

  it("un derrumbe de tierra no se confunde con una vía sin pavimentar", () => {
    expect(imagenCategoria("Derrumbe de tierra")).toBe("/img/categorias/derrumbe.svg")
    expect(imagenCategoria("Calle de tierra")).toBe("/img/categorias/via_sin_pavimentar.svg")
  })
})
