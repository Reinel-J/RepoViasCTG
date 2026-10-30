# Dashboard de ViaCTG en Power BI

Versión en Power BI del dashboard de Metabase (`docs/metabase.md`): los mismos ocho indicadores, con las mismas
definiciones que la pestaña Estadísticas de ViaCTG, construidos sobre tres archivos CSV. Power BI Desktop es gratuito y
solo corre en Windows; esta guía no se pudo probar dentro de Power BI, pero los datos y las cifras esperadas sí se
verificaron contra ViaCTG (ver «Verificación»).

## Archivos

| Archivo | Contenido | Filas (datos demo) |
| --- | --- | --- |
| `docs/powerbi/datos-demo/reportes.csv` | Un reporte por fila: categoría, estado, prioridad, barrio, coordenadas, fechas de creación, primera respuesta y resolución | 200 |
| `docs/powerbi/datos-demo/historial_estados.csv` | Cada cambio de estado de cada reporte, en orden | 322 |
| `docs/powerbi/datos-demo/categorias.csv` | Catálogo de categorías | 7 |

Son los datos de demostración del proyecto (`SEED_DEMO=true`), exportados el 30 de octubre de 2026: los mismos 200
reportes del dashboard de Metabase. No incluyen datos de usuarios (ni autores, ni moderadores, ni descripciones).

Para exportar los datos reales de tu base: `python3 exportar-powerbi.py` (escribe en `powerbi-datos/`, que git ignora).

Formato: UTF-8 con BOM, separados por comas, punto decimal, fechas en hora de Colombia como `AAAA-MM-DD HH:MM:SS`;
`activo` es `SI` para los estados PENDIENTE, EN_REVISION y EN_PROCESO.

## 1. Cargar los datos

1. Abre Power BI Desktop y, **antes de importar**, ve a *Archivo › Opciones y configuración › Opciones › Archivo
   actual › Configuración regional* y elige **Inglés (Estados Unidos)**. Con la configuración en español, Power BI lee
   `10.4162` como 104162 y las coordenadas quedan fuera de Cartagena.
2. *Inicio › Obtener datos › Texto/CSV* y carga los tres archivos (*Origen del archivo*: 65001: Unicode (UTF-8)).
   Elige **Transformar datos** para revisar los tipos.
3. En el Editor de Power Query, revisa que los tipos queden así y luego *Cerrar y aplicar*:

| Tabla | Columna | Tipo |
| --- | --- | --- |
| reportes | `latitud`, `longitud` | Número decimal |
| reportes | `fecha_creacion`, `fecha_actualizacion`, `fecha_primera_respuesta`, `fecha_resuelto` | Fecha y hora |
| reportes | `dia_creacion`, `dia_resuelto` | Fecha |
| reportes | las demás | Texto |
| historial_estados | `orden` | Número entero |
| historial_estados | `fecha` | Fecha y hora |
| categorias | todas | Texto |

Las celdas vacías de `fecha_primera_respuesta`, `fecha_resuelto` y `dia_resuelto` (reportes sin respuesta o sin
resolver) deben quedar como valores nulos, no como error.

## 2. Modelo

1. *Modelado › Nueva tabla* y crea el calendario. La ventana de 12 meses termina en el mes del dato más reciente, como
   la gráfica mensual de ViaCTG el día de la exportación:

   ```dax
   Calendario =
   VAR Ultimo = MAX ( MAX ( reportes[dia_creacion] ), MAX ( reportes[dia_resuelto] ) )
   VAR Inicio = DATE ( YEAR ( MIN ( reportes[dia_creacion] ) ), MONTH ( MIN ( reportes[dia_creacion] ) ), 1 )
   VAR Dias = SELECTCOLUMNS ( CALENDAR ( Inicio, EOMONTH ( Ultimo, 0 ) ), "Fecha", [Date] )
   RETURN
       ADDCOLUMNS (
           Dias,
           "Mes", FORMAT ( [Fecha], "yyyy-MM" ),
           "Ultimos 12 meses", [Fecha] >= EDATE ( DATE ( YEAR ( Ultimo ), MONTH ( Ultimo ), 1 ), -11 )
       )
   ```

   Márcala como tabla de fechas (*Herramientas de tabla › Marcar como tabla de fechas*, columna `Fecha`).
2. En la *Vista de modelo*, crea estas relaciones (todas de uno a varios, filtro en una sola dirección):

| De (uno) | A (varios) | Activa |
| --- | --- | --- |
| `Calendario[Fecha]` | `reportes[dia_creacion]` | Sí |
| `Calendario[Fecha]` | `reportes[dia_resuelto]` | **No** (la usa la medida «Resueltos en el mes») |
| `categorias[nombre]` | `reportes[categoria]` | Sí (equivale al `$lookup` de Metabase) |
| `reportes[id]` | `historial_estados[reporte_id]` | Sí |

Power BI puede crear algunas relaciones solo al cargar; borra las que no estén en esta tabla.

## 3. Medidas

Créalas en la tabla `reportes` (*Modelado › Nueva medida*), una por una:

```dax
Total reportes = COUNTROWS ( reportes )

Resueltos = CALCULATE ( [Total reportes], reportes[estado] = "RESUELTO" )

Rechazados = CALCULATE ( [Total reportes], reportes[estado] = "RECHAZADO" )

Activos = CALCULATE ( [Total reportes], reportes[activo] = "SI" )

% resuelto = DIVIDE ( [Resueltos], [Total reportes] - [Rechazados] )

Días hasta la primera respuesta =
AVERAGEX (
    FILTER ( reportes, NOT ISBLANK ( reportes[fecha_primera_respuesta] ) ),
    DATEDIFF ( reportes[fecha_creacion], reportes[fecha_primera_respuesta], SECOND ) / 86400
)

Días hasta resolver =
AVERAGEX (
    FILTER ( reportes, NOT ISBLANK ( reportes[fecha_resuelto] ) ),
    DATEDIFF ( reportes[fecha_creacion], reportes[fecha_resuelto], SECOND ) / 86400
)

Resueltos en el mes =
CALCULATE ( [Total reportes], USERELATIONSHIP ( Calendario[Fecha], reportes[dia_resuelto] ) )
```

Formato: `% resuelto` como porcentaje con 0 decimales; los dos promedios de días como número decimal con 1 decimal.

Las definiciones son las de ViaCTG: el porcentaje no cuenta los rechazados; la primera respuesta es el primer cambio de
estado; un reporte cuenta como resuelto en el mes en que pasó a RESUELTO, aunque se haya creado antes.

## 4. La página del dashboard

Mismo orden que en Metabase: de lo general a lo particular.

| Visual | Campos | Ajustes |
| --- | --- | --- |
| Segmentación de datos «Estado» | `reportes[estado]` | Estilo lista desplegable |
| Segmentación de datos «Fecha de creación» | `reportes[dia_creacion]` | Estilo «Entre» |
| Tarjeta | `% resuelto` | Título «Porcentaje resuelto (sin rechazados)» |
| Tarjeta | `Días hasta la primera respuesta` | |
| Tarjeta | `Días hasta resolver` | |
| Gráfico de columnas agrupadas «Reportes por estado» | Eje X: `reportes[estado]`; eje Y: `Total reportes` | Ordenar por `Total reportes`, descendente |
| Gráfico de anillos «Reportes por prioridad» | Leyenda: `reportes[prioridad]`; valores: `Total reportes` | |
| Gráfico de líneas «Reportes creados y resueltos por mes» | Eje X: `Calendario[Mes]`; eje Y: `Total reportes` (renómbrala «Creados») y `Resueltos en el mes` | Filtro del visual: `Calendario[Ultimos 12 meses]` = Verdadero; ordenar por `Mes`, ascendente; en *Interacciones*, que las segmentaciones no lo filtren |
| Gráfico de barras agrupadas «Categorías con más reportes» | Eje Y: `categorias[nombre]`; eje X: `Total reportes` | Filtro del visual: `categorias[nombre]`, tipo *N superior*, superior 8 por `Total reportes` |
| Mapa (Azure Maps) «Reportes activos» | Latitud: `reportes[latitud]`; longitud: `reportes[longitud]`; información sobre herramientas: categoría, barrio, estado | Filtro del visual: `reportes[activo]` = SI. Si el visual de mapa no aparece, actívalo en *Opciones › Seguridad › Usar los objetos visuales de mapa*; en la latitud y longitud elige *No resumir* |

Para la tendencia mensual, las segmentaciones se desconectan de ese visual (*Formato › Editar interacciones*) igual que
en Metabase, donde la gráfica mensual mantiene su ventana fija de 12 meses.

## 5. Verificación

Con los datos demo, sin filtros, el reporte debe mostrar exactamente estas cifras (calculadas desde los CSV con las
mismas fórmulas y comparadas con `GET /api/admin/estadisticas` sobre la misma base: coinciden todas):

| Cifra | Valor esperado |
| --- | --- |
| Total de reportes | 200 |
| Por estado | PENDIENTE 50 · EN_REVISION 30 · EN_PROCESO 40 · RESUELTO 60 · RECHAZADO 20 |
| Porcentaje resuelto | 33 % |
| Días hasta la primera respuesta | 1,9 |
| Días hasta resolver | 24,8 |
| Reportes activos (puntos del mapa) | 120 |
| Por prioridad | MEDIA 116 · BAJA 44 · ALTA 40 |
| Categorías | Vía sin pavimentar 51 · Hueco 45 · Inundación 33 · Hundimiento 25 · Daño en alcantarillado 20 · Derrumbe 18 · Obstáculo en la vía 8 |

Creados y resueltos por mes (gráfico de líneas):

| Mes | 2025-11 | 2025-12 | 2026-01 | 2026-02 | 2026-03 | 2026-04 | 2026-05 | 2026-06 | 2026-07 | 2026-08 | 2026-09 | 2026-10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Creados | 7 | 3 | 10 | 5 | 7 | 7 | 15 | 19 | 9 | 26 | 29 | 63 |
| Resueltos | 1 | 5 | 3 | 7 | 4 | 4 | 1 | 5 | 9 | 4 | 7 | 10 |

Con el filtro Estado = RESUELTO: total 60 y porcentaje resuelto 100 %. Si una cifra no coincide, revisa primero el paso 1
(configuración regional y tipos) y la relación inactiva de `dia_resuelto`.

## 6. Actualizar los datos

Vuelve a exportar (`python3 exportar-powerbi.py`, o con `--db` y `--salida` para otra base o carpeta), reemplaza los
CSV y usa *Inicio › Actualizar*. Si cambias de carpeta, ajusta la ruta en *Transformar datos › Configuración de origen
de datos*.

Otra opción, sin archivos: *Obtener datos › Web* con `http://localhost:8080/api/reportes`, que es público y devuelve
JSON. Esa respuesta no trae las fechas de primera respuesta y resolución ya calculadas, así que los tiempos promedio
requieren más trabajo en Power Query; por eso esta guía usa los CSV.
