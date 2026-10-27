# ViaCTG — Pruebas unitarias con Mockito

Esta rama agrega pruebas unitarias con **JUnit 5 y Mockito** para dos servicios del backend que aún no tenían cobertura.

| Servicio | Clase de prueba | Qué cubre |
| --- | --- | --- |
| `BarrioService` | `BarrioServiceTests` | Crear, buscar, actualizar y eliminar barrios; agregar y eliminar calles (incluidas listas inmutables o nulas); y el error `ResourceNotFoundException` cuando no existen. |
| `CorreoService` | `CorreoServiceTests` | Que no se envíe nada sin SMTP configurado; el envío con destinatario, asunto y cuerpo correctos; el remitente por defecto; y que un fallo de envío no afecte a quien llama. |

## Pruebas HTTP de controladores (`@WebMvcTest`)

En `ViasCTG/src/test/java/com/viactg/controller/` se prueban los 12 controladores sobre MockMvc con la seguridad real
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
| `ComentarioControllerTests`, `SeguimientoControllerTests`, `NotificacionControllerTests`, `GeocodingControllerTests`, `CatalogoControllerTests` | Usuario tomado del token, validaciones en español, límite por IP de la geocodificación, 405, y que los errores del driver de Mongo o las excepciones inesperadas no expongan detalles técnicos. |
