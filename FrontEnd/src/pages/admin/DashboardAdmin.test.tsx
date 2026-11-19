// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import * as adminApi from "../../api/admin"
import { DashboardAdmin } from "./DashboardAdmin"
import { inicioMetabase } from "./metabase"

vi.mock("../../api/admin", () => ({ obtenerDashboard: vi.fn(), estadisticas: vi.fn() }))

const obtenerDashboard = vi.mocked(adminApi.obtenerDashboard)
const estadisticas = vi.mocked(adminApi.estadisticas)
const TITULO = "Dashboard de reportes de ViaCTG (Metabase)"

function respuesta(token: string) {
  return { url: `http://localhost:3000/embed/dashboard/${token}#bordered=false&titled=true`, expiraEn: new Date(Date.now() + 10 * 60_000).toISOString() }
}

afterEach(() => {
  cleanup()
  obtenerDashboard.mockReset()
  estadisticas.mockReset()
  vi.restoreAllMocks()
})

describe("DashboardAdmin", () => {
  it("muestra el iframe con la URL firmada que entrega la API", async () => {
    obtenerDashboard.mockResolvedValue(respuesta("uno"))

    render(<DashboardAdmin esAdmin={false} />)

    const iframe = await screen.findByTitle(TITULO)
    expect(iframe.getAttribute("src")).toBe(respuesta("uno").url)
    expect(screen.queryByText("Abrir en Metabase")).toBeNull()
  })

  it("muestra el mensaje del backend cuando la API falla", async () => {
    obtenerDashboard.mockRejectedValue(new Error("El dashboard no está configurado"))

    render(<DashboardAdmin esAdmin />)

    expect((await screen.findByRole("alert")).textContent).toBe("El dashboard no está configurado")
    expect(screen.queryByTitle(TITULO)).toBeNull()
  })

  it("el botón Actualizar pide una URL nueva", async () => {
    obtenerDashboard.mockResolvedValueOnce(respuesta("uno")).mockResolvedValueOnce(respuesta("dos"))
    render(<DashboardAdmin esAdmin />)
    await screen.findByTitle(TITULO)

    fireEvent.click(screen.getByRole("button", { name: "Actualizar" }))

    await waitFor(() => expect(screen.getByTitle(TITULO).getAttribute("src")).toBe(respuesta("dos").url))
    expect(obtenerDashboard).toHaveBeenCalledTimes(2)
  })

  it("solo un administrador ve el enlace para editar en Metabase", async () => {
    obtenerDashboard.mockResolvedValue(respuesta("uno"))

    render(<DashboardAdmin esAdmin />)

    expect((await screen.findByText("Abrir en Metabase")).getAttribute("href")).toBe("http://localhost:3000/")
  })

  it("el enlace a Metabase funciona con la ruta relativa que usa docker compose", () => {
    expect(inicioMetabase("/metabase/embed/dashboard/abc.def.ghi#bordered=false")).toBe("/metabase/")
    expect(inicioMetabase("https://viactg.example/metabase/embed/dashboard/abc#x")).toBe("https://viactg.example/metabase/")
  })

  it("descarga las estadísticas en un CSV con BOM", async () => {
    obtenerDashboard.mockResolvedValue(respuesta("uno"))
    estadisticas.mockResolvedValue({ total: 2, porEstado: { PENDIENTE: 1, EN_REVISION: 0, EN_PROCESO: 0, RESUELTO: 1, RECHAZADO: 0 },
      porcentajeResuelto: 50, diasPrimeraRespuesta: 0.5, diasResolucion: 2, meses: [], categorias: [], densidad: [],
      prioridadActivos: { BAJA: 0, MEDIA: 1, ALTA: 0 }, pendienteMasAntiguo: null })
    const archivos: Blob[] = []
    URL.createObjectURL = vi.fn((archivo: Blob) => { archivos.push(archivo); return "blob:estadisticas" })
    URL.revokeObjectURL = vi.fn()
    const clic = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {})
    render(<DashboardAdmin esAdmin={false} />)

    fireEvent.click(screen.getByRole("button", { name: "Descargar estadísticas (.csv)" }))

    await waitFor(() => expect(clic).toHaveBeenCalledTimes(1))
    // ignoreBOM: sin él, TextDecoder quita el BOM y no se podría comprobar que está.
    const texto = new TextDecoder("utf-8", { ignoreBOM: true }).decode(await archivos[0].arrayBuffer())
    expect(texto.startsWith("\uFEFFseccion,elemento,medida,valor\r\nResumen,,Total de reportes,2\r\n")).toBe(true)
    expect(clic.mock.contexts[0]).toHaveProperty("download", expect.stringMatching(/^viactg-estadisticas-\d{4}-\d{2}-\d{2}\.csv$/))
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:estadisticas")
  })

  it("muestra el error si no se pueden obtener las estadísticas", async () => {
    obtenerDashboard.mockResolvedValue(respuesta("uno"))
    estadisticas.mockRejectedValue(new Error("Tu sesión no es válida o ya venció. Inicia sesión de nuevo"))
    render(<DashboardAdmin esAdmin />)

    fireEvent.click(screen.getByRole("button", { name: "Descargar estadísticas (.csv)" }))

    expect((await screen.findByRole("status")).textContent).toBe("Tu sesión no es válida o ya venció. Inicia sesión de nuevo")
  })
})
