# ViaCTG — Pruebas unitarias con Mockito

Esta rama agrega pruebas unitarias con **JUnit 5 y Mockito** para dos servicios del backend que aún no tenían cobertura.
| `BarrioService` | `BarrioServiceTests` | Crear, buscar, actualizar y eliminar barrios; agregar y eliminar calles (incluidas listas inmutables o nulas); y el error `ResourceNotFoundException` cuando no existen. |
| `CorreoService` | `CorreoServiceTests` | Que no se envíe nada sin SMTP configurado; el envío con destinatario, asunto y cuerpo correctos; el remitente por defecto; y que un fallo de envío no afecte a quien llama. |
