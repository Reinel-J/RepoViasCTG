import { describe, expect, it } from "vitest"
import { motivoNoEditable } from "./reporteVista"

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
