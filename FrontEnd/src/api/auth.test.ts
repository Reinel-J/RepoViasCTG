import { AxiosError, AxiosHeaders } from "axios"
import { describe, expect, it } from "vitest"
import { esCorreoSinConfirmar } from "./auth"

function errorHttp(status: number) {
  const config = { headers: new AxiosHeaders() }
  return new AxiosError("error", "ERR_BAD_REQUEST", config, null, { status, statusText: "", headers: {}, config, data: {} })
}

describe("esCorreoSinConfirmar", () => {
  it("reconoce el 403 del login con el correo sin confirmar", () => {
    expect(esCorreoSinConfirmar(errorHttp(403))).toBe(true)
  })

  it("no confunde credenciales incorrectas, límites ni errores de red", () => {
    expect(esCorreoSinConfirmar(errorHttp(401))).toBe(false)
    expect(esCorreoSinConfirmar(errorHttp(429))).toBe(false)
    expect(esCorreoSinConfirmar(new Error("sin conexión"))).toBe(false)
  })
})
