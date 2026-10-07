# ViaCTG

ViaCTG es una API REST para registrar y hacer seguimiento a daños viales de Cartagena. Usa Java 21, Spring Boot 3.3.4, Spring Data MongoDB, Spring Security y JWT.

## Requisitos y configuración

- JDK 21 o superior.
- MongoDB accesible desde la aplicación.
- Un secreto JWT en Base64 de 32 bytes o más.

```bash
export MONGODB_URI='mongodb://localhost:27017/viasctg_db'
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

No se guardan credenciales en el repositorio. Mongo crea al iniciar los índices únicos de `usuarios.email` y `confirmaciones(reporteId, usuarioId)`.

## Arquitectura

El código se organiza bajo `com.viactg` por responsabilidad: `controller`, `service`, `repository`, `model`, `dto`, `mapper`, `security`, `exception` y `config`. Los controladores solo usan DTOs; los servicios concentran reglas de negocio y validan referencias MongoDB.

`Calle` está embebida en `Barrio`, y `HistorialEstado` en `Reporte`. Las demás entidades tienen su propia colección.

El cliente React vive en el directorio hermano `../FrontEnd`. Consume esta API en desarrollo desde `http://localhost:5173` y usa `http://localhost:8080/api` como URL base.

## Geolocalización de reportes

Cada reporte guarda `latitud`, `longitud` y una ubicación GeoJSON en el campo `ubicacion`. Este último tiene un índice geoespacial MongoDB `2dsphere`, creado automáticamente al iniciar la aplicación, y almacena las coordenadas en el orden requerido por GeoJSON: `[longitud, latitud]`.

Al crear o actualizar un reporte, las coordenadas son obligatorias y se validan antes de llegar al servicio:

- Latitud: entre `-90` y `90`.
- Longitud: entre `-180` y `180`.

Ejemplo de cuerpo para crear o actualizar un reporte:

```json
{
  "calleId": "id-de-la-calle",
  "categoriaId": "id-de-la-categoria",
  "descripcion": "Hueco de gran tamaño cerca de la intersección.",
  "fotoUrl": "https://ejemplo.com/foto.jpg",
  "latitud": 10.391,
  "longitud": -75.479,
  "prioridad": "ALTA"
}
```

La consulta pública de reportes cercanos usa este índice geoespacial:

```text
GET /api/reportes/cercanos?lat=10.391&lng=-75.479&radioKm=2&estado=PENDIENTE
```

`radioKm` debe ser mayor que cero; `estado` es opcional. La respuesta es una lista de
`ReporteResponse` ordenada por cercanía. El frontend la puede consumir para mostrar
marcadores en un mapa Leaflet/OpenStreetMap.

## Seguridad

| Acción | Acceso |
| --- | --- |
| Registro y login | Público |
| Consultar reportes, barrios y categorías | Público |
| Crear/editar reportes, comentar, confirmar, consultar comentarios/confirmaciones y datos propios | Usuario autenticado |
| Cambiar estado e historial de reportes | `ADMIN` o `MODERADOR` |
| Administrar barrios y categorías; listar usuarios | `ADMIN` |

El registro público crea un usuario `CIUDADANO`. La base migrada debe contar con un `ADMIN` inicial. Usa el JWT recibido en el encabezado `Authorization: Bearer <token>`.

## Rutas principales

| Recurso | Rutas |
| --- | --- |
| Autenticación | `POST /api/auth/registro`, `POST /api/auth/login` |
| Perfil | `GET/PUT /api/usuarios/me`, `GET /api/usuarios` (`ADMIN`) |
| Reportes | `GET/POST /api/reportes`, `GET /api/reportes/cercanos`, `GET/PUT /api/reportes/{id}` |
| Administración de reportes | `GET /api/admin/reportes/pendientes`, `PATCH /api/admin/reportes/{id}/estado`, `GET /api/admin/reportes/{id}/historial` |
| Barrios y calles | `GET/POST /api/barrios`, `GET/PUT/DELETE /api/barrios/{id}`, `POST /api/barrios/{id}/calles` |
| Categorías | `GET/POST /api/categorias`, `GET/PUT /api/categorias/{id}`, `PATCH /api/categorias/{id}/estado` |
| Confirmaciones | `GET/POST /api/reportes/{reporteId}/confirmaciones`, `DELETE /api/reportes/{reporteId}/confirmaciones/me` |
| Comentarios | `GET/POST /api/reportes/{reporteId}/comentarios`, `PUT/DELETE /api/comentarios/{id}` |
| Notificaciones | `GET /api/notificaciones`, `PATCH /api/notificaciones/{id}/leida` |

La documentación interactiva está en `http://localhost:8080/swagger-ui/index.html`.

## Verificación

```bash
./mvnw test
./mvnw clean verify
```

## Desarrollo con frontend

En una terminal, inicia la API con MongoDB local y un secreto JWT temporal:

```bash
export MONGODB_URI='mongodb://localhost:27017/viasctg_db'
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

En otra terminal:

```bash
cd ../FrontEnd
npm install
npm run dev
```

La API queda disponible en `http://localhost:8080`, Swagger en
`http://localhost:8080/swagger-ui/index.html` y Vite normalmente en
`http://localhost:5173`.

Consulta [HELP.md](HELP.md) para solucionar problemas comunes y [PROGRESS.md](PROGRESS.md) para conocer el trabajo pendiente.
