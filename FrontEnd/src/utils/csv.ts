export type CeldaCsv = string | number | null | undefined

// Un campo CSV: entre comillas solo si lleva coma, comillas o salto de línea (las comillas internas se duplican).
// Los números van con punto decimal y los valores ausentes como celda vacía.
export function campoCsv(valor: CeldaCsv): string {
  if (valor === null || valor === undefined) return ""
  const texto = String(valor)
  return /[",\r\n]/.test(texto) ? `"${texto.replaceAll('"', '""')}"` : texto
}

export function textoCsv(filas: CeldaCsv[][]): string {
  return filas.map((fila) => fila.map(campoCsv).join(",")).join("\r\n") + "\r\n"
}

// Descarga el archivo desde el navegador. Con BOM para que Excel y Power BI lean bien las tildes.
export function descargarCsv(nombre: string, contenido: string) {
  const url = URL.createObjectURL(new Blob(["﻿", contenido], { type: "text/csv;charset=utf-8" }))
  const enlace = document.createElement("a")
  enlace.href = url
  enlace.download = nombre
  enlace.click()
  URL.revokeObjectURL(url)
}
