// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import * as authApi from "../api/auth"
import { BotonGoogle } from "./BotonGoogle"

vi.mock("../api/auth", () => ({ configuracionGoogle: vi.fn() }))

const initialize = vi.fn()
const renderButton = vi.fn()

describe("BotonGoogle", () => {
  beforeEach(() => {
    window.google = { accounts: { id: { initialize, renderButton } } }
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    delete window.google
  })

  it("no aparece si el backend no tiene Google configurado", async () => {
    vi.mocked(authApi.configuracionGoogle).mockResolvedValue({ clientId: null })

    const { container } = render(<BotonGoogle texto="signin_with" onCredencial={vi.fn()} />)

    await waitFor(() => expect(authApi.configuracionGoogle).toHaveBeenCalled())
    expect(container.innerHTML).toBe("")
    expect(initialize).not.toHaveBeenCalled()
  })

  it("no aparece si el backend no responde", async () => {
    vi.mocked(authApi.configuracionGoogle).mockRejectedValue(new Error("sin conexión"))

    const { container } = render(<BotonGoogle texto="signin_with" onCredencial={vi.fn()} />)

    await waitFor(() => expect(authApi.configuracionGoogle).toHaveBeenCalled())
    expect(container.innerHTML).toBe("")
  })

  it("dibuja el botón con el Client ID del backend y entrega la credencial de Google", async () => {
    vi.mocked(authApi.configuracionGoogle).mockResolvedValue({ clientId: "123.apps.googleusercontent.com" })
    const onCredencial = vi.fn()

    render(<BotonGoogle texto="signup_with" onCredencial={onCredencial} />)

    await waitFor(() => expect(renderButton).toHaveBeenCalled())
    expect(initialize).toHaveBeenCalledWith(expect.objectContaining({ client_id: "123.apps.googleusercontent.com" }))
    expect(renderButton).toHaveBeenCalledWith(screen.getByTestId("boton-google"), expect.objectContaining({ text: "signup_with", locale: "es" }))

    const { callback } = initialize.mock.calls[0][0]
    callback({ credential: "id-token" })
    callback({})
    expect(onCredencial).toHaveBeenCalledTimes(1)
    expect(onCredencial).toHaveBeenCalledWith("id-token")
  })
})
