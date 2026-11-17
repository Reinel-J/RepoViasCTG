import { describe, expect, it } from "vitest"
import { enlaceWhatsApp, textoCompartir } from "./compartir"

describe("textoCompartir", () => {
  it("invita a confirmar un reporte activo", () => {
    expect(textoCompartir({ categoria: "Hueco", direccionOsm: "Avenida Pedro de Heredia, Pie de la Popa", estado: "En proceso", cerrado: false }))
      .toBe("Hueco en Avenida Pedro de Heredia, Pie de la Popa (estado: en proceso). ¿Lo has visto? Confírmalo en VíaCTG para darle más fuerza:")
  })

  it("no pide confirmar un caso cerrado y no muestra coordenadas sin dirección", () => {
    expect(textoCompartir({ categoria: "Alcantarilla", direccionOsm: null, estado: "Resuelto", cerrado: true }))
      .toBe("Alcantarilla en Cartagena (estado: resuelto). Míralo en VíaCTG:")
  })
})

describe("enlaceWhatsApp", () => {
  it("codifica el texto y el enlace en un solo mensaje", () => {
    const enlace = enlaceWhatsApp("¿Hueco? Sí & no", "https://viactg.co/reportes/abc")
    expect(enlace.startsWith("https://wa.me/?text=")).toBe(true)
    expect(decodeURIComponent(enlace.slice("https://wa.me/?text=".length))).toBe("¿Hueco? Sí & no https://viactg.co/reportes/abc")
  })
})
