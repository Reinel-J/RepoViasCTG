// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { afterEach, describe, expect, it, vi } from "vitest"
import { CampoContrasena } from "./CampoContrasena"

describe("CampoContrasena", () => {
  afterEach(cleanup)

  it("oculta la contraseña por defecto y la muestra al pulsar el botón", () => {
    render(<CampoContrasena aria-label="Contraseña" />)

    const campo = screen.getByLabelText("Contraseña")
    const boton = screen.getByRole("button", { name: "Mostrar contraseña" })

    expect(campo.getAttribute("type")).toBe("password")
    expect(boton.getAttribute("type")).toBe("button")
    expect(boton.getAttribute("aria-pressed")).toBe("false")

    fireEvent.click(boton)

    expect(campo.getAttribute("type")).toBe("text")
    expect(screen.getByRole("button", { name: "Ocultar contraseña" }).getAttribute("aria-pressed")).toBe("true")
  })

  it("no envía el formulario al alternar la visibilidad", () => {
    const enviar = vi.fn()
    render(<form onSubmit={(event) => { event.preventDefault(); enviar() }}><CampoContrasena aria-label="Contraseña" /></form>)

    fireEvent.click(screen.getByRole("button", { name: "Mostrar contraseña" }))

    expect(enviar).not.toHaveBeenCalled()
  })
})
