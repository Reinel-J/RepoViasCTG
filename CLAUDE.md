# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

ViaCTG reporta y da seguimiento a daños viales en Cartagena (modelo FixMyStreet). Monorepo con dos proyectos independientes: `ViasCTG/` (API Spring Boot 3, Java 21, MongoDB, JWT) y `FrontEnd/` (React 19 + TypeScript + Vite + Leaflet). El directorio raíz se llama `ViasCTG.` (con punto final). Documentación de usuario en español: `README.md` (raíz), `ViasCTG/README.md`, `FrontEnd/README.md`; pendientes en `ViasCTG/PROGRESS.md`. `ViasCTG/AGENTS.md` fija las convenciones del backend (léelo).

## Comandos

Backend (desde `ViasCTG/`; `mvnw` puede no ser ejecutable: usar `sh mvnw`):

```bash
export MONGODB_URI='mongodb://localhost:27017/viasctg_db' JWT_SECRET="$(openssl rand -base64 32)"
sh mvnw spring-boot:run                      # API en :8080 (SERVER_PORT para cambiarlo)
sh mvnw test                                 # JUnit 5 + Mockito, no necesita Mongo
sh mvnw test -Dtest=ReporteServiceTests#cambiarEstadoNotificaAlAutorDelReporte
sh mvnw clean verify
```

`JWT_SECRET` es obligatorio (sin valor por defecto), también para `test`. Variables opcionales: `CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:5173,http://localhost:3000`), `UPLOADS_DIR` (por defecto `uploads`, relativo al cwd).

Frontend (desde `FrontEnd/`): `npm run dev` (:5173), `npm run lint`, `npm run build` (`tsc -b && vite build`). No hay pruebas del frontend. La URL de la API sale de `VITE_API_URL` (por defecto `http://localhost:8080/api`; se fija en build).

Todo junto: `cp .env.example .env`, definir `JWT_SECRET`, `docker compose up --build` (Mongo, backend :8080, frontend :5173). No uses `pkill -f` con cadenas presentes en tu propio comando: mata el shell.

## Arquitectura del backend

Capas bajo `com.viactg`: controller → service → repository, con `dto`/`mapper` en la frontera (los controladores nunca devuelven documentos Mongo). Reglas que cruzan varios archivos:

- **Integridad referencial en servicios**, no en la BD (Mongo no la impone). `Calle` va embebida en `Barrio` e `HistorialEstado` en `Reporte`.
- **Cambio de estado** (`ReporteService.cambiarEstadoReporte`): transiciones válidas en `validarTransicion`, actualización atómica con `findAndModify` filtrando por estado actual, empuje de historial y creación de una `Notificacion` para el autor (fallos de notificación solo se registran).
- **Geolocalización**: cada reporte guarda `latitud`/`longitud` y un `GeoJsonPoint ubicacion` (orden `[lng, lat]`, índice `2dsphere`); `/api/reportes/cercanos` usa `nearSphere`.
- **Dirección OSM**: `GeocodingService` (Nominatim, 1 petición/s, timeout 5 s) se ejecuta de forma **asíncrona** en un hilo único tras guardar el reporte, así que `POST /api/reportes` responde con `direccionOsm: null`; también se recalcula en `PUT` si cambian las coordenadas. La categoría es texto libre (`categoriaId`); la prioridad la fija solo un moderador/admin al cambiar estado (los reportes inician en `MEDIA`).
- **Fotos**: `POST /api/reportes/{id}/fotos` (máx. 3, 5 MB, validación por firma binaria), guardadas en `UPLOADS_DIR` y servidas por `WebConfig` en `/uploads/**`.
- **Seguridad** (`SecurityConfig` + `security/`): JWT sin estado; GET de reportes/barrios/categorías/geocoding, `/api/auth/**` y `/uploads/**` son públicos, el resto autenticado, y los endpoints de admin usan `@PreAuthorize`. Sin autenticar o credenciales malas → 401 (`HttpStatusEntryPoint`, `BadCredentialsException`); el filtro JWT recarga el usuario desde Mongo en cada petición, así que los cambios de rol y las desactivaciones aplican de inmediato (la sesión guardada en el frontend solo se refresca al volver a iniciar sesión). El primer `ADMIN` lo crea `config/AdminInicial` con `ADMIN_EMAIL`/`ADMIN_PASSWORD`.
- **Cuentas** (`AuthService`): límites de intentos en memoria (`security/LimitadorIntentos`, 429), recuperación con token de un solo uso (solo su SHA-256 en `tokens_recuperacion`, TTL; sin `SPRING_MAIL_HOST` el enlace va al log), y revocación por `Usuario.versionToken`, que viaja como claim `ver` en el JWT y que suben `cerrar-sesiones` y los cambios de contraseña.
- **Errores**: `GlobalExceptionHandler` mapea todo a `ErrorResponse {status, message, validationErrors}` (409 para reglas de negocio, 400 para validación/JSON/parámetros, 413 para uploads grandes, 429 por límite de intentos); cualquier excepción nueva sin handler cae en 500.
- `ReporteService` recibe `NotificacionService`, `GeocodingService` y la ruta de uploads por constructor; los tests lo construyen a mano, así que cambiar el constructor obliga a actualizar `ReporteServiceTests`.

## Arquitectura del frontend

- Router en `src/app/routes.tsx` (react-router, `createBrowserRouter`); cada pantalla se carga con `lazy` (helpers `pagina` / `paginaProtegida`), solo `Home` es estática. Leaflet y su CSS se importan dentro de los componentes de mapa para no entrar en el paquete inicial.
- Sesión: `context/AuthContext.tsx` (proveedor), `authContextValue.ts` (contexto) y `useAuth.ts` (hook) están separados por la regla `react-refresh/only-export-components`. Se persiste en `localStorage` (`token` y `authSession`). `routes/ProtectedRoute.tsx` aplica la protección por rol.
- `src/api/client.ts` es el único cliente Axios: añade el Bearer, convierte los errores del backend en `error.message` (mensaje + `validationErrors`), y ante un 401 con token guardado (salvo `/auth/*`) limpia la sesión y redirige a `/login`. Las pantallas muestran `exception.message` directamente.
- Los tipos en `src/api/types.ts` reflejan a mano los DTO del backend: al cambiar un DTO hay que actualizarlos.
- Panel en `src/pages/admin/` (pestañas por `?seccion=`); `estados.ts` duplica las transiciones de `ReporteService.validarTransicion`: mantenlas sincronizadas.
- La lista de reportes pagina en el cliente (10 por página) sobre `GET /api/reportes`, que no pagina. Estilos en un único `src/index.css`.
- Reglas del lint (React Compiler): nada de `setState` síncrono dentro de `useEffect`; mover esos cambios a los manejadores de eventos.

## Convenciones

Commits estilo Conventional Commits en español o inglés conciso (`feat:`, `test:`). Mantén los README y `PROGRESS.md` sincronizados con los cambios de contrato.
