# ViaCTG — Pruebas unitarias con Mockito

Esta rama agrega pruebas unitarias con **JUnit 5 y Mockito** para dos servicios del backend que aún no tenían cobertura.

| Servicio | Clase de prueba | Qué cubre |
| --- | --- | --- |
| `BarrioService` | `BarrioServiceTests` | Crear, buscar, actualizar y eliminar barrios; agregar y eliminar calles (incluidas listas inmutables o nulas); y el error `ResourceNotFoundException` cuando no existen. |
| `CorreoService` | `CorreoServiceTests` | Que no se envíe nada sin SMTP configurado; el envío con destinatario, asunto y cuerpo correctos; el remitente por defecto; y que un fallo de envío no afecte a quien llama. |

## Pruebas HTTP de controladores (`@WebMvcTest`)

En `ViasCTG/src/test/java/com/viactg/controller/` se prueban los 13 controladores sobre MockMvc con la seguridad real
(`SecurityConfig`, filtro JWT, `JwtService` y `GlobalExceptionHandler`) y los mappers reales; los servicios son mocks de
Mockito, así que no hace falta MongoDB. `ControllerTestBase` reúne la configuración común (todas las clases comparten un
solo contexto de Spring) y emite JWT reales por rol.

| Clase de prueba | Qué cubre |
| --- | --- |
| `SeguridadRutasTests` | Qué rutas son públicas, cuáles responden 401 sin sesión y cuáles 403 según el rol (ciudadano, moderador, admin), sin llegar a los servicios; tokens mal formados, firmados con otra clave, vencidos, de cuentas desactivadas, eliminadas o con la versión revocada; que el rol se lea de la base de datos y no del token; `/uploads/**` público y CORS. |
| `AuthControllerTests` | Registro sin token hasta confirmar el correo, confirmación y reenvío del enlace, login con la IP del cliente (403 si el correo no está confirmado), sin `passwordHash`; 400 por campo, JSON mal formado, 401, 415 y 429; recuperar y restablecer contraseña. |
| `ReporteControllerTests` | Filtros y límites del listado, resumen, cercanos y posibles duplicados; que el autor salga del token y no del cuerpo; 400/403/404/409; subida de fotos multipart. |
| `AdminControllerTests` | Cambio de estado con moderador, comentario y prioridad; estados o prioridades inexistentes; historial, votos de seguimiento y recordatorios. |
| `UsuarioControllerTests` | Perfil, cambio de contraseña con token nuevo (el anterior queda revocado), cierre de sesiones, cambio de rol y activación. |
| `DashboardControllerTests` | URL firmada del dashboard: 401 sin sesión, 403 para ciudadanos, 200 con `{url, expiraEn}` para moderador y admin, 503 sin configuración. |
| `ComentarioControllerTests`, `SeguimientoControllerTests`, `NotificacionControllerTests`, `GeocodingControllerTests`, `CatalogoControllerTests` | Usuario tomado del token, validaciones en español, límite por IP de la geocodificación, 405, y que los errores del driver de Mongo o las excepciones inesperadas no expongan detalles técnicos. |

## Dashboard (Metabase)

El panel de administración tiene una pestaña **Dashboard** (moderador y admin) con un dashboard de
[Metabase](https://www.metabase.com/) open source conectado a la misma base `viasctg_db`. Se muestra en un iframe con
**embebido estático firmado**: `GET /api/admin/dashboard` devuelve `{url, expiraEn}`, una URL con un JWT HS256
(`resource.dashboard`, `params` vacío, vence en `METABASE_TOKEN_MINUTOS`) firmado con la clave de embebido de Metabase,
que nunca sale del backend. El panel pide otra URL un minuto antes de que venza y el dashboard se actualiza solo cada
minuto (`#refresh=60`). Sin configuración, la API responde `503` «El dashboard no está configurado».
El botón **Descargar estadísticas (.csv)** de esa pestaña baja las cifras de la pestaña Estadísticas en un solo CSV
(una cifra por fila: resumen, estados, meses y categorías), y cada tarjeta del dashboard también se descarga desde su menú.

### Cómo levantarlo

```bash
cp .env.example .env            # si aún no existe; define JWT_SECRET
docker compose up -d --build    # Mongo, backend, frontend y Metabase (http://localhost:3000)
```

Luego sigue [docs/metabase.md](docs/metabase.md): crear el administrador de Metabase, conectar MongoDB (host `mongo`,
puerto `27017`, base `viasctg_db`), ocultar las colecciones sensibles, crear las ocho preguntas, armar y publicar el
dashboard. Copia la clave de embebido y el id del dashboard al `.env` y recrea el backend con
`docker compose up -d backend`.

Atajo: después de crear la cuenta de administrador en el asistente de Metabase, `python3 configurar-metabase.py` hace el
resto (conexión, colecciones ocultas, preguntas, dashboard publicado y las dos variables en el `.env`); pide la contraseña
de Metabase sin mostrarla.

También hay una versión en Power BI de los mismos ocho indicadores: [docs/powerbi.md](docs/powerbi.md) (modelo,
medidas DAX y cifras esperadas), con los datos demo en `docs/powerbi/datos-demo/` y `python3 exportar-powerbi.py` para
exportar a CSV los datos reales (a `powerbi-datos/`, que git ignora).

### Variables nuevas

| Variable | Por defecto | Para qué |
| --- | --- | --- |
| `METABASE_SITE_URL` | `http://localhost:3000` | Dirección de Metabase tal como la abre el navegador (el iframe la carga directamente). |
| `METABASE_SECRET_KEY` | vacía | Clave de embebido de *Admin › Embedding* de Metabase. |
| `METABASE_DASHBOARD_ID` | vacía | Número del dashboard publicado (`/dashboard/<id>`). |
| `METABASE_TOKEN_MINUTOS` | `10` | Vigencia de cada URL firmada. |
| `SEED_DEMO` | `false` | Con `true`, carga datos de demostración al arrancar (ver abajo). |

El servicio `metabase` de `docker-compose.yml` usa `metabase/metabase:v0.64.4.3` (rama LTS), guarda su base interna H2 en
el volumen `metabase_data` (en producción se recomienda PostgreSQL) y activa el embebido con
`MB_ENABLE_EMBEDDING_STATIC=true`.

### Datos de demostración

Con `SEED_DEMO=true` y menos de 20 reportes, el backend crea al arrancar 5 ciudadanos (`ciudadano1…5@demo.viasctg.local`,
correo verificado y una contraseña aleatoria que nadie conoce) y 200 reportes de los últimos 12 meses en nueve barrios
reales (25 % pendientes, 15 % en revisión, 20 % en proceso, 30 % resueltos y 10 % rechazados), con historiales de
transiciones válidas y fechas crecientes. Se insertan en bloque, sin geocodificar ni enviar notificaciones ni correos.

```bash
# Cargarlos (con la base casi vacía)
SEED_DEMO=true docker compose up -d backend
docker compose logs backend | grep SEED_DEMO

# Borrarlos: los reportes empiezan con "[DEMO]" y los ciudadanos usan @demo.viasctg.local
docker compose exec mongo mongosh viasctg_db --eval '
  db.reportes.deleteMany({descripcion: /^\[DEMO\]/});
  db.usuarios.deleteMany({email: /@demo\.viasctg\.local$/})'
```

Después de borrarlos, deja `SEED_DEMO=false` en el `.env`: si no, se vuelven a crear en el siguiente arranque mientras
haya menos de 20 reportes.

Pruebas: `MetabaseServiceTests` (token verificable con la misma clave y HS256, dashboard, `params` vacío, vencimiento y
formato de la URL, 503 sin configuración), `DashboardControllerTests` y `SeguridadRutasTests` (permisos por rol),
`DatosDemoTests` (apagado no hace nada, no duplica, área de servicio, transiciones válidas con fechas crecientes y
categorías del catálogo) y `DashboardAdmin.test.tsx` en el frontend (iframe, mensaje de error, botón Actualizar).
