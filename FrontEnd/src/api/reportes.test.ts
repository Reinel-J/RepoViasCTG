import { beforeEach, describe, expect, it, vi } from "vitest"

const get = vi.hoisted(() => vi.fn())
vi.mock("./client", () => ({ default: { get } }))

import { listar } from "./reportes"

describe("listar", () => {
  beforeEach(() => {
    get.mockReset()
    get.mockResolvedValue({ data: { contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 } })
  })

  it("envía los filtros y la página, sin los vacíos", async () => {
    await listar({ estado: "PENDIENTE", categoria: "Hueco", barrio: "", q: "   ", pagina: 0, tamano: 12 })

    expect(get).toHaveBeenCalledWith("/reportes", { params: { estado: "PENDIENTE", categoria: "Hueco", pagina: 0, tamano: 12 } })
  })

  it("sin filtros no envía parámetros", async () => {
    await listar()

    expect(get).toHaveBeenCalledWith("/reportes", { params: {} })
  })
})
