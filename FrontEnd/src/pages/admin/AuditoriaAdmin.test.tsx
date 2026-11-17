// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import * as adminApi from "../../api/admin"
import { fechaLocalAISO } from "../../api/admin"
import { AuditoriaAdmin } from "./AuditoriaAdmin"

vi.mock("../../api/admin", () => ({ auditoria: vi.fn(), fechaLocalAISO: (valor?: string) => {
  if (!valor) return undefined
  const fecha = new Date(valor)
  return Number.isNaN(fecha.getTime()) ? undefined : fecha.toISOString()
} }))

const auditoria = vi.mocked(adminApi.auditoria)

afterEach(() => {
  cleanup()
  auditoria.mockReset()
})

describe("AuditoriaAdmin", () => {
  it("muestra la etiqueta legible y envía los filtros con fechas ISO", async () => {
    auditoria.mockResolvedValue({ contenido: [{
      id: "a1", fecha: "2026-11-12T08:00:00Z", actorId: "sistema", actorNombre: "Sistema",
      accion: "REPORTE_CERRADO_POR_COMUNIDAD", entidad: "REPORTE", entidadId: "r1", detalle: "EN_PROCESO → RESUELTO",
    }], pagina: 0, tamano: 50, totalElementos: 1, totalPaginas: 1 })

    render(<AuditoriaAdmin />)

    expect(await screen.findByText("Reporte cerrado por la comunidad")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Acción"), { target: { value: "REPORTE_CERRADO_POR_COMUNIDAD" } })
    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2026-11-12T03:00" } })

    await waitFor(() => expect(auditoria).toHaveBeenLastCalledWith(expect.objectContaining({
      accion: "REPORTE_CERRADO_POR_COMUNIDAD",
      desde: fechaLocalAISO("2026-11-12T03:00"),
      pagina: 0,
      tamano: 50,
    })))
  })

  it("muestra el estado vacío y permite cambiar de página", async () => {
    auditoria.mockResolvedValue({ contenido: [], pagina: 0, tamano: 50, totalElementos: 51, totalPaginas: 2 })

    render(<AuditoriaAdmin />)

    expect(await screen.findByText("Sin registros")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Siguiente →" }))

    await waitFor(() => expect(auditoria).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 1 })))
    expect(screen.getByRole("button", { name: "← Anterior" })).toBeTruthy()
  })
})
