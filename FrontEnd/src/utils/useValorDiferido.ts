import { useEffect, useState } from 'react'

// El valor, pero solo después de `espera` ms sin cambios: así un buscador consulta al servidor cuando la persona deja
// de escribir y no con cada tecla.
export function useValorDiferido<T>(valor: T, espera = 300): T {
  const [diferido, setDiferido] = useState(valor)
  useEffect(() => {
    const temporizador = setTimeout(() => setDiferido(valor), espera)
    return () => clearTimeout(temporizador)
  }, [valor, espera])
  return diferido
}
