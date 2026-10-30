# Dashboard de ViaCTG en Metabase

Esta guía deja listo el dashboard que se ve en **Panel de moderación › Dashboard**. Metabase (edición open source,
`metabase/metabase:v0.64.4.3`) lee la misma base `viasctg_db` y el panel lo muestra en un iframe con **embebido
estático firmado**: el backend firma una URL de corta duración (`GET /api/admin/dashboard`), así que solo lo ven
moderadores y administradores y la clave de embebido nunca llega al navegador.

Metabase se configura desde su propia interfaz; los nombres de menú de esta guía son los de la interfaz en inglés
(si la eliges en español, los menús son los mismos con otros nombres).

## 1. Primer arranque

1. Levanta todo con `docker compose up -d` (o solo `docker compose up -d metabase`). El primer arranque tarda un par de
   minutos mientras Metabase crea su base interna; `docker compose ps metabase` debe decir `healthy`.
2. Abre <http://localhost:3000> y sigue el asistente.
3. Crea la cuenta de administrador de Metabase con una **contraseña fuerte y única** (por ejemplo, de 16 caracteres o
   más, generada por un gestor de contraseñas). Esta cuenta puede leer toda la base de datos; no es la de ViaCTG.
4. En *Add your data* puedes saltar el paso y agregar la base en el punto 2.

La base interna de Metabase (usuarios, preguntas y dashboards) es un archivo H2 en el volumen `metabase_data`. Para
producción se recomienda PostgreSQL (`MB_DB_TYPE=postgres`, `MB_DB_HOST`, `MB_DB_DBNAME`, `MB_DB_USER`, `MB_DB_PASS`).

## 2. Agregar la base MongoDB

*Admin settings › Databases › Add database*:

| Campo | Valor |
| --- | --- |
| Database type | MongoDB |
| Display name | ViaCTG |
| Host | `mongo` (el nombre del servicio en docker compose) |
| Port | `27017` |
| Database name | `viasctg_db` |
| Username / Password | vacíos (ver «Seguridad») |
| Use a secure connection (SSL) | no |

Guarda y espera a que termine la sincronización: en *Browse data › ViaCTG* deben aparecer las colecciones.

## 3. Seguridad

- **Oculta las colecciones sensibles.** En *Admin settings › Table Metadata › ViaCTG*, marca como *Hidden* las
  colecciones `usuarios` (tiene los hashes de las contraseñas, correos y teléfonos), `tokens_recuperacion`,
  `tokens_verificacion` y `notificaciones`. Así no aparecen al explorar datos ni en el editor de consultas.
- **Ocultar no es un control de acceso.** Una consulta nativa todavía puede leerlas (por ejemplo con
  `{"$lookup": {"from": "usuarios", …}}`). Limita quién escribe consultas nativas: en *Admin settings › Permissions*,
  deja al grupo *All Users* en *Create queries › Query builder only* (o *No*) y crea las preguntas solo con la cuenta
  de administrador.
- **MongoDB corre sin usuario.** Hoy solo escucha en `127.0.0.1` y en la red interna de Docker, y Metabase se conecta
  sin credenciales. La mejora recomendada para producción es habilitar la autenticación de MongoDB y crear para
  Metabase un usuario de **solo lectura** limitado a las colecciones del dashboard, por ejemplo:

  ```javascript
  // mongosh, como administrador de MongoDB, después de habilitar la autenticación
  use viasctg_db
  db.createRole({ role: "lecturaDashboard", roles: [], privileges: [
    { resource: { db: "viasctg_db", collection: "reportes" },   actions: ["find"] },
    { resource: { db: "viasctg_db", collection: "categorias" }, actions: ["find"] },
    { resource: { db: "viasctg_db", collection: "barrios" },    actions: ["find"] } ] })
  db.createUser({ user: "metabase", pwd: passwordPrompt(), roles: ["lecturaDashboard"] })
  ```

  Con ese usuario, ni una consulta nativa puede leer `usuarios` ni los tokens.
- Las descargas (CSV, XLSX, PNG) del dashboard embebido **no se pueden desactivar** en la edición open source (solo en
  Pro/Enterprise): quien vea el panel puede descargar los datos de cada tarjeta. Las preguntas de esta guía solo
  devuelven cifras agregadas y, en el mapa, la ubicación, categoría, barrio, estado y prioridad de los reportes activos
  (datos que ya son públicos en ViaCTG), nunca datos de usuarios.

## 4. Las preguntas

> **Atajo:** con la cuenta de administrador de Metabase ya creada (paso 1), `python3 configurar-metabase.py` (en la raíz
> del repositorio) hace los pasos 2 a 6: conecta la base si falta, oculta las colecciones sensibles, crea estas ocho
> preguntas y el dashboard, lo publica para embeber y escribe `METABASE_SECRET_KEY` y `METABASE_DASHBOARD_ID` en el
> `.env`. Pide tu correo y tu contraseña de Metabase (sin mostrarla ni guardarla) y toma las consultas de esta guía.
> Luego solo falta `docker compose up -d backend`. Los filtros del paso 5 se agregan a mano.

Cada pregunta es una consulta nativa: *+ New › Native query*, elige la base **ViaCTG** y la colección **reportes**, y
pega el pipeline de agregación. Los campos son los del documento `Reporte` del backend: `estado`, `prioridad`,
`categoriaId` (guarda el **nombre** de la categoría, normalizado al catálogo), `barrioOsm`, `latitud`, `longitud`,
`fechaCreacion` e `historialEstados` (`estadoAnterior`, `estadoNuevo`, `fecha`, en orden). Cada pregunta replica la
cifra correspondiente de la pestaña Estadísticas de ViaCTG (`EstadisticasService`).

### a) Reportes por estado (barras)

```json
[
  {"$group": {"_id": "$estado", "total": {"$sum": 1}}},
  {"$project": {"_id": 0, "estado": "$_id", "total": 1}},
  {"$sort": {"total": -1}}
]
```

Visualización: *Bar*, eje X `estado`, eje Y `total`.

### b) Porcentaje resuelto (número)

RESUELTO / (total − RECHAZADO), redondeado como en ViaCTG (los rechazados no cuentan).

```json
[
  {"$group": {
    "_id": null,
    "total": {"$sum": 1},
    "resueltos": {"$sum": {"$cond": [{"$eq": ["$estado", "RESUELTO"]}, 1, 0]}},
    "rechazados": {"$sum": {"$cond": [{"$eq": ["$estado", "RECHAZADO"]}, 1, 0]}}
  }},
  {"$project": {
    "_id": 0,
    "porcentajeResuelto": {"$cond": [
      {"$eq": [{"$subtract": ["$total", "$rechazados"]}, 0]},
      null,
      {"$floor": {"$add": [{"$multiply": [{"$divide": ["$resueltos", {"$subtract": ["$total", "$rechazados"]}]}, 100]}, 0.5]}}
    ]}
  }}
]
```

Visualización: *Number*; en *Formatting* agrega el sufijo ` %`.

### c) Reportes creados vs. resueltos por mes, últimos 12 meses (líneas)

Desde el día 1 del mes de hace 11 meses, con los meses en hora de Colombia (`America/Bogota`). Un reporte cuenta como
resuelto en el mes en que pasó a RESUELTO, aunque se haya creado antes. Los meses sin movimiento no aparecen.

```json
[
  {"$project": {"eventos": {"$concatArrays": [
    [{"tipo": "creados", "fecha": "$fechaCreacion"}],
    {"$map": {
      "input": {"$filter": {"input": {"$ifNull": ["$historialEstados", []]}, "cond": {"$eq": ["$$this.estadoNuevo", "RESUELTO"]}}},
      "in": {"tipo": "resueltos", "fecha": "$$this.fecha"}
    }}
  ]}}},
  {"$unwind": "$eventos"},
  {"$match": {"$expr": {"$gte": ["$eventos.fecha", {"$dateTrunc": {
    "date": {"$dateSubtract": {"startDate": "$$NOW", "unit": "month", "amount": 11, "timezone": "America/Bogota"}},
    "unit": "month", "timezone": "America/Bogota"
  }}]}}},
  {"$group": {
    "_id": {"$dateToString": {"format": "%Y-%m", "date": "$eventos.fecha", "timezone": "America/Bogota"}},
    "creados": {"$sum": {"$cond": [{"$eq": ["$eventos.tipo", "creados"]}, 1, 0]}},
    "resueltos": {"$sum": {"$cond": [{"$eq": ["$eventos.tipo", "resueltos"]}, 1, 0]}}
  }},
  {"$sort": {"_id": 1}},
  {"$project": {"_id": 0, "mes": "$_id", "creados": 1, "resueltos": 1}}
]
```

Visualización: *Line*, eje X `mes`, series `creados` y `resueltos`.

### d) Top 8 categorías (barras horizontales)

El `$lookup` busca cada `categoriaId` en `categorias` por su `nombre`; si un reporte antiguo tiene una categoría que no
está en el catálogo, sale como «(fuera del catálogo)».

```json
[
  {"$group": {
    "_id": "$categoriaId",
    "total": {"$sum": 1},
    "resueltos": {"$sum": {"$cond": [{"$eq": ["$estado", "RESUELTO"]}, 1, 0]}}
  }},
  {"$lookup": {"from": "categorias", "localField": "_id", "foreignField": "nombre", "as": "catalogo"}},
  {"$project": {
    "_id": 0,
    "categoria": {"$ifNull": [{"$first": "$catalogo.nombre"}, {"$concat": [{"$ifNull": ["$_id", "Sin categoría"]}, " (fuera del catálogo)"]}]},
    "total": 1,
    "resueltos": 1
  }},
  {"$sort": {"total": -1, "categoria": 1}},
  {"$limit": 8}
]
```

Visualización: *Row*, eje `categoria`, valor `total`.

### e) Días promedio hasta la primera respuesta y hasta resolver (números)

Dos preguntas, una por número. La primera respuesta es el primer cambio del historial; los reportes sin respuesta o
sin resolver no cuentan en el promedio.

Días hasta la primera respuesta:

```json
[
  {"$project": {"dias": {"$divide": [{"$subtract": [{"$arrayElemAt": ["$historialEstados.fecha", 0]}, "$fechaCreacion"]}, 86400000]}}},
  {"$group": {"_id": null, "promedio": {"$avg": "$dias"}}},
  {"$project": {"_id": 0, "diasPrimeraRespuesta": {"$divide": [{"$floor": {"$add": [{"$multiply": ["$promedio", 10]}, 0.5]}}, 10]}}}
]
```

Días hasta resolver:

```json
[
  {"$project": {"dias": {"$divide": [{"$subtract": [
    {"$getField": {"field": "fecha", "input": {"$first": {"$filter": {"input": "$historialEstados", "cond": {"$eq": ["$$this.estadoNuevo", "RESUELTO"]}}}}}},
    "$fechaCreacion"
  ]}, 86400000]}}},
  {"$group": {"_id": null, "promedio": {"$avg": "$dias"}}},
  {"$project": {"_id": 0, "diasResolucion": {"$divide": [{"$floor": {"$add": [{"$multiply": ["$promedio", 10]}, 0.5]}}, 10]}}}
]
```

Visualización: *Number* en ambas.

### f) Mapa de los reportes activos (pines)

```json
[
  {"$match": {"estado": {"$in": ["PENDIENTE", "EN_REVISION", "EN_PROCESO"]}}},
  {"$project": {"_id": 0, "latitud": 1, "longitud": 1, "estado": 1, "categoria": "$categoriaId", "barrio": "$barrioOsm", "prioridad": 1, "fechaCreacion": 1}}
]
```

Visualización: *Map › Pin map*, *Latitude field* `latitud`, *Longitude field* `longitud`.

### g) Reportes por prioridad (dona)

```json
[
  {"$group": {"_id": "$prioridad", "total": {"$sum": 1}}},
  {"$project": {"_id": 0, "prioridad": "$_id", "total": 1}},
  {"$sort": {"total": -1}}
]
```

Visualización: *Pie* (dona), dimensión `prioridad`, medida `total`. Los reportes pendientes están en MEDIA hasta que un
moderador fija la prioridad al cambiar el estado.

## 5. Armar el dashboard

1. *+ New › Dashboard*, por ejemplo «ViaCTG · Reportes ciudadanos», y agrega las ocho preguntas. Una distribución que
   funciona bien en el iframe: arriba los tres números (b, e1, e2); luego estado y prioridad lado a lado; la línea
   mensual a lo ancho; y abajo categorías y mapa.
2. **Filtros (opcionales): rango de fechas y estado.** Las consultas nativas de MongoDB aceptan *field filters*:
   - En cada pregunta que quieras filtrar, agrega al inicio del pipeline una etapa `$match` por variable:

     ```
     [
       {"$match": {{estado}}},
       {"$match": {{fecha}}},
       {"$group": {"_id": "$estado", "total": {"$sum": 1}}},
       …
     ]
     ```

   - En el panel de variables, `estado` es de tipo *Field Filter* sobre **Reportes › Estado** (widget *String*), y
     `fecha` es *Field Filter* sobre **Reportes › FechaCreacion** (widget *Date filter*). Sin valor, la etapa no
     filtra nada.
   - En el dashboard, agrega los filtros *Text or Category* «Estado» y *Date picker* «Fecha de creación» y conéctalos a
     esas variables en cada tarjeta.
   - Tiene sentido en a, b, d, e, f y g; no en c, cuya ventana de 12 meses ya está fija.
3. **Auto-refresco cada minuto.** Dentro de Metabase se activa con el reloj del dashboard (*Auto-refresh › 1 minute*),
   pero ese ajuste es de cada persona y no aplica al embebido. En el panel de ViaCTG ya viene activado: la URL firmada
   lleva `#refresh=60`, y el panel pide una URL nueva antes de que venza (y con el botón **Actualizar**).

## 6. Embebido estático

1. *Admin settings › Embedding*: el embebido estático (en esta versión, **guest embeds**) ya está habilitado por
   `MB_ENABLE_EMBEDDING_STATIC=true` en `docker-compose.yml`.
2. Copia la **clave secreta de embebido** (64 caracteres hexadecimales; si no aparece, pulsa *Regenerate key*) en
   `METABASE_SECRET_KEY` del `.env`. Si después regeneras la clave, cópiala de nuevo: las URL firmadas con la anterior
   dejan de funcionar.
3. Abre el dashboard › icono *Share* › *Embed* › autenticación **Guest** (embebido estático firmado). Si agregaste los
   filtros, márcalos como **Editable** (los bloqueados exigirían un valor en el token, y el backend no envía ninguno).
   Pulsa **Publish**.
4. Copia el número del dashboard de su URL (`http://localhost:3000/dashboard/2-viactg-…` → `2`) en
   `METABASE_DASHBOARD_ID`.
5. Recrea el backend para que lea las variables: `docker compose up -d backend`. En el panel, la pestaña **Dashboard**
   debe mostrarlo. Si dice «El dashboard no está configurado» (503), falta alguna de las tres variables, el id no es un
   número o la clave está incompleta (el log del backend lo indica, sin mostrar la clave).

`METABASE_SITE_URL` debe ser la dirección con la que **el navegador** llega a Metabase (por defecto
`http://localhost:3000`): el iframe la carga directamente. Desde otro equipo de la red usa la IP del PC; la demo por
túnel (`demo-tunel.sh`) solo publica el frontend, así que desde internet el dashboard no carga.

## 7. Verificación de datos

Compara cada cifra del dashboard con la pestaña **Estadísticas** de ViaCTG y con una consulta en mongosh
(`docker compose exec mongo mongosh viasctg_db`). Las tres deben coincidir; si no, revisa la consulta de esa pregunta.

| Cifra | Pregunta de Metabase | Estadísticas de ViaCTG | mongosh | Con los datos demo |
| --- | --- | --- | --- | --- |
| Total de reportes | suma de a) | «Reportes en total» | `db.reportes.countDocuments()` | 200 |
| Reportes RESUELTO | a), barra RESUELTO | — | `db.reportes.countDocuments({estado: "RESUELTO"})` | 60 |
| Reportes RECHAZADO | a), barra RECHAZADO | — | `db.reportes.countDocuments({estado: "RECHAZADO"})` | 20 |
| Porcentaje resuelto | b) | «Resueltos (sin contar rechazados)» | resueltos × 100 / (total − rechazados), redondeado | 33 % |
| Días hasta la primera respuesta | e) primera respuesta | «Días promedio hasta la primera respuesta» | — | varía con la fecha de carga |
| Días hasta resolver | e) resolver | «Días promedio hasta resolver» | — | varía con la fecha de carga |
| Creados y resueltos de un mes | c), punto del mes | gráfica mensual | `db.reportes.countDocuments({fechaCreacion: {$gte: ISODate("2026-10-01T05:00:00Z")}})` (mes actual; 05:00 Z es la medianoche en Colombia) | — |
| Reportes de una categoría | d), barra de la categoría | «Categorías con más reportes» | `db.reportes.countDocuments({categoriaId: "Hueco"})` | — |
| Pines del mapa | f), número de filas | suma de pendientes, en revisión y en proceso | `db.reportes.countDocuments({estado: {$in: ["PENDIENTE", "EN_REVISION", "EN_PROCESO"]}})` | 120 |
| Reportes de prioridad ALTA | g), sector ALTA | — | `db.reportes.countDocuments({prioridad: "ALTA"})` | — |

Estas consultas se verificaron contra Metabase v0.64.4.3, la pestaña Estadísticas y mongosh con los datos demo
(`SEED_DEMO=true`): las tres fuentes dieron las mismas cifras.
