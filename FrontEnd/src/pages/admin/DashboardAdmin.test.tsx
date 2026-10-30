// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import * as adminApi from "../../api/admin"
import { DashboardAdmin } from "./DashboardAdmin"

vi.mock("../../api/admin", () => ({ obtenerDashboard: vi.fn() }))

const obtenerDashboard = vi.mocked(adminApi.obtenerDashboard)
const TITULO = "Dashboard de reportes de ViaCTG (Metabase)"

function respuesta(token: string) {
  return { url: `http://localhost:3000/embed/dashboard/${token}#bordered=false&titled=true`, expiraEn: new Date(Date.now() + 10 * 60_000).toISOString() }
}

afterEach(() => {
  cleanup()
  obtenerDashboard.mockReset()
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

    expect((await screen.findByText("Abrir en Metabase")).getAttribute("href")).toBe("http://localhost:3000")
  })
})
