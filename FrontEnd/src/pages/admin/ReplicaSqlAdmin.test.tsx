// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import * as adminApi from "../../api/admin"
import type { EstadoReplicaResponse } from "../../api/types"
import { ReplicaSqlAdmin } from "./ReplicaSqlAdmin"

vi.mock("../../api/admin", () => ({ estadoReplica: vi.fn(), reconstruirReplica: vi.fn() }))

const estadoReplica = vi.mocked(adminApi.estadoReplica)
const reconstruirReplica = vi.mocked(adminApi.reconstruirReplica)

const SINCRONIZADA: EstadoReplicaResponse = {
  habilitada: true, conectada: true, pendienteReconstruir: false,
  ultimaSincronizacion: "2026-11-16T20:00:00Z", ultimaReconstruccion: "2026-11-16T19:00:00Z", ultimoError: null, ultimoErrorEn: null,
  tablas: [{ nombre: "reportes", mongo: 20000, sql: 20000 }, { nombre: "comentarios", mongo: 5, sql: 4 }],
}

afterEach(() => {
  cleanup()
  estadoReplica.mockReset()
  reconstruirReplica.mockReset()
})

describe("ReplicaSqlAdmin", () => {
  it("compara MongoDB con PostgreSQL tabla por tabla", async () => {
    estadoReplica.mockResolvedValue(SINCRONIZADA)

    render(<ReplicaSqlAdmin />)

    expect(await screen.findByText("Sincronizada")).toBeTruthy()
    expect(screen.getByText("reportes")).toBeTruthy()
    expect(screen.getByText("Igual")).toBeTruthy()
    expect(screen.getByText("Diferente")).toBeTruthy()
  })

  it("pide la reconstrucción y vuelve a consultar el estado", async () => {
    estadoReplica.mockResolvedValue(SINCRONIZADA)
    reconstruirReplica.mockResolvedValue()
    render(<ReplicaSqlAdmin />)
    await screen.findByText("Sincronizada")

    fireEvent.click(screen.getByRole("button", { name: "Reconstruir réplica" }))

    await waitFor(() => expect(reconstruirReplica).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(estadoReplica).toHaveBeenCalledTimes(2))
  })

  it("deshabilitada explica cómo activarla y no deja reconstruir", async () => {
    estadoReplica.mockResolvedValue({ ...SINCRONIZADA, habilitada: false, conectada: false,
      tablas: [{ nombre: "reportes", mongo: 3, sql: null }] })

    render(<ReplicaSqlAdmin />)

    expect(await screen.findByText("Deshabilitada")).toBeTruthy()
    expect(screen.getByText(/SQL_REPLICA_HABILITADA=true/)).toBeTruthy()
    expect((screen.getByRole("button", { name: "Reconstruir réplica" }) as HTMLButtonElement).disabled).toBe(true)
  })
})
