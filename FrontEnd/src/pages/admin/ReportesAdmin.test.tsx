// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { MemoryRouter } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import * as adminApi from "../../api/admin"
import * as reportesApi from "../../api/reportes"
import type { ReporteResponse } from "../../api/types"
import { ReportesAdmin } from "./ReportesAdmin"

vi.mock("../../api/admin", () => ({
  seguimientos: vi.fn(),
  cambiarEstadoLote: vi.fn(),
  cambiarEstado: vi.fn(),
  historial: vi.fn(),
  mantenerAbierto: vi.fn(),
}))
vi.mock("../../api/reportes", () => ({ listar: vi.fn() }))

const seguimientos = vi.mocked(adminApi.seguimientos)
const cambiarEstadoLote = vi.mocked(adminApi.cambiarEstadoLote)
const listar = vi.mocked(reportesApi.listar)

const reportes: ReporteResponse[] = [
  { id: "r1", usuarioId: "u1", calleId: null, categoria: { id: "c1", nombre: "Hueco" }, direccionOsm: null, barrioOsm: null, descripcion: "Hueco frente al colegio", latitud: 10.4, longitud: -75.5, estado: "PENDIENTE", prioridad: "MEDIA", fotos: [], fechaCreacion: "2026-11-01T12:00:00Z", fechaActualizacion: "2026-11-01T12:00:00Z", historialEstados: [] },
  { id: "r2", usuarioId: "u2", calleId: null, categoria: { id: "c2", nombre: "Andén" }, direccionOsm: null, barrioOsm: null, descripcion: "Andén roto", latitud: 10.41, longitud: -75.51, estado: "PENDIENTE", prioridad: "BAJA", fotos: [], fechaCreacion: "2026-11-02T12:00:00Z", fechaActualizacion: "2026-11-02T12:00:00Z", historialEstados: [] },
]

function renderizar() {
  return render(<MemoryRouter><ReportesAdmin estadoInicial={null} /></MemoryRouter>)
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe("ReportesAdmin", () => {
  it("actualiza en lote los reportes seleccionados y muestra el resultado", async () => {
    seguimientos.mockResolvedValue([])
    listar.mockResolvedValue({ contenido: reportes, pagina: 0, tamano: 10, totalElementos: 2, totalPaginas: 1 })
    cambiarEstadoLote.mockResolvedValue({ actualizados: ["r1"], fallidos: [{ id: "r2", motivo: "El reporte cambió de estado" }] })

    renderizar()

    await screen.findByText("Hueco frente al colegio")
    fireEvent.click(screen.getByLabelText("Seleccionar reporte Hueco frente al colegio"))
    expect(screen.getByText("1 seleccionado")).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Seleccionar reporte Andén roto"))
    expect(screen.getByText("2 seleccionados")).toBeTruthy()
    const acciones = within(screen.getByRole("region", { name: "Acciones en lote" }))
    fireEvent.change(acciones.getByLabelText("Nuevo estado"), { target: { value: "EN_REVISION" } })
    fireEvent.change(acciones.getByLabelText("Prioridad"), { target: { value: "ALTA" } })
    fireEvent.change(acciones.getByPlaceholderText("Comentario opcional"), { target: { value: "Asignado" } })
    fireEvent.click(acciones.getByRole("button", { name: "Aplicar" }))

    await waitFor(() => expect(cambiarEstadoLote).toHaveBeenCalledWith({
      ids: ["r1", "r2"], nuevoEstado: "EN_REVISION", comentario: "Asignado", prioridad: "ALTA",
    }))
    expect((await screen.findByRole("status")).textContent).toContain("1 reporte actualizado")
    expect(screen.getByText("r2: El reporte cambió de estado")).toBeTruthy()
  })

  it("limpia la selección al cambiar el filtro o la página", async () => {
    seguimientos.mockResolvedValue([])
    listar.mockResolvedValue({ contenido: reportes, pagina: 0, tamano: 10, totalElementos: 11, totalPaginas: 2 })

    renderizar()

    await screen.findByText("Hueco frente al colegio")
    fireEvent.click(screen.getByLabelText("Seleccionar reporte Hueco frente al colegio"))
    expect(screen.getByText("1 seleccionado")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Estado"), { target: { value: "PENDIENTE" } })
    await waitFor(() => expect(screen.queryByText("1 seleccionado")).toBeNull())

    fireEvent.click(screen.getByLabelText("Seleccionar reporte Hueco frente al colegio"))
    expect(screen.getByText("1 seleccionado")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Siguiente →" }))
    await waitFor(() => expect(screen.queryByText("1 seleccionado")).toBeNull())
  })
})
