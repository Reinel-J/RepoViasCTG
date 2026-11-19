# Iniciar sesión con Google

El login y el registro muestran **Continuar con Google** cuando el backend tiene un Client ID de Google. Sin él, el
botón no aparece y todo sigue funcionando con correo y contraseña.

## Cómo funciona

1. El navegador carga Google Identity Services y Google dibuja su botón. Al elegir una cuenta, Google le entrega al
   navegador un *ID token*: un JWT firmado por Google con el correo, el nombre y el identificador de la cuenta (`sub`).
2. El frontend lo envía a `POST /api/auth/google`. El backend comprueba la firma con las claves públicas de Google,
   que el emisor sea Google, que el token sea para **nuestro** Client ID (`aud`) y que no haya vencido.
3. Si el correo está verificado en Google, el backend responde con la misma sesión (JWT de ViaCTG) que el login con
   contraseña.

No hace falta un *client secret*: el Client ID es público (va en la página) y el backend no habla con Google en nombre
del usuario, solo verifica la firma del token. Por eso no hay URL de redirección que configurar.

### Qué cuenta se usa

| Situación | Resultado |
| --- | --- |
| Ya había entrado con esa cuenta de Google | Entra a su cuenta, aunque haya cambiado el correo en Google. |
| Existe una cuenta con ese correo, ya confirmada | Se vincula con Google. Conserva su contraseña, su rol y sus reportes. |
| Existe una cuenta con ese correo **sin confirmar** | Pudo crearla otra persona con un correo ajeno: se confirma, se descarta su contraseña y se cierran sus sesiones (igual que al restablecer la contraseña). |
| No existe | Se crea como ciudadano, con el correo confirmado y el nombre de Google. No tiene contraseña; si la quiere, la crea con «¿Olvidaste tu contraseña?». |
| La cuenta de Google no tiene el correo verificado | 403: no se puede entrar así. |
| El correo ya está vinculado a **otra** cuenta de Google | 403: entra con la contraseña o con la cuenta de Google de siempre. |
| La cuenta de ViaCTG está desactivada | 403. |

`GET /api/auth/google` devuelve `{"clientId": "..."}` (o `null`). El frontend lo consulta al abrir el login, así que
cambiar el Client ID no obliga a recompilar el frontend, basta con reiniciar el backend.

## Crear el Client ID (una sola vez)

1. Entra a [Google Cloud Console](https://console.cloud.google.com/) y crea un proyecto (por ejemplo, `ViaCTG`).
2. Abre **Google Auth Platform** (*APIs y servicios › Pantalla de consentimiento de OAuth* en la interfaz anterior):
   - **Branding**: nombre de la app (`ViaCTG`), correo de asistencia y, si lo tienes, el dominio.
   - **Audience**: tipo **External**. Mientras la app esté en *Testing* solo pueden entrar los usuarios de prueba que
     agregues ahí. Para abrirla a todos pulsa **Publish app**. Con los permisos básicos que se usan (`openid`,
     `email`, `profile`) Google no pide verificación.
3. En **Clients** pulsa **Create client** y elige el tipo **Web application**. En **Authorized JavaScript origins**
   agrega cada dirección desde la que se abre el sitio:
   - `http://localhost:5173` y `http://localhost` (en local, Google pide las dos).
   - `https://<DOMINIO>` en un servidor (ver [azure.md](azure.md)).

   **Authorized redirect URIs** queda vacío.
4. Copia el **Client ID** (termina en `.apps.googleusercontent.com`) en el `.env`:

   ```bash
   GOOGLE_CLIENT_ID=1234567890-abc123.apps.googleusercontent.com
   ```

5. Reinicia el backend: `docker compose up -d backend` (o `./mvnw spring-boot:run` con la variable exportada).

## Problemas frecuentes

| Síntoma | Causa y solución |
| --- | --- |
| No aparece el botón | `GOOGLE_CLIENT_ID` vacío o el backend no se reinició. `curl http://localhost:8080/api/auth/google` debe mostrar el Client ID. |
| Google muestra «Error 400: origin_mismatch» o «The given origin is not allowed» | La dirección del sitio no está en *Authorized JavaScript origins*. Agrégala exacta (esquema, dominio y puerto). Google tarda unos minutos en aplicar el cambio. |
| «Acceso bloqueado: la app no completó el proceso de verificación» / solo entran algunas personas | La app sigue en *Testing*: agrega a esa persona como usuario de prueba o publica la app. |
| Con el túnel de Cloudflare (`--profile tunel`) el botón falla | La dirección `https://….trycloudflare.com` cambia en cada arranque y no está autorizada. Agrégala en Google Cloud para esa demo o entra con correo y contraseña. |
| «No fue posible comprobar la sesión con Google» (503) | El backend no pudo descargar las claves de `https://www.googleapis.com/oauth2/v3/certs`: revisa la salida a internet del servidor. |
