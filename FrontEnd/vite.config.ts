import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    rolldownOptions: {
      output: {
        // React, el router y Axios casi nunca cambian: en chunks propios, el navegador los conserva en caché
        // entre despliegues. Leaflet NO se agrupa aquí: solo lo importan los mapas, que se cargan con import()
        // dinámico (components/MapasDiferidos.tsx), así que queda fuera de la carga inicial de la portada.
        manualChunks(id) {
          if (!id.includes('node_modules') || id.includes('leaflet')) return
          if (id.includes('axios')) return 'http'
          if (id.includes('react-router')) return 'router'
          return 'vendor'
        },
      },
    },
  },
})
