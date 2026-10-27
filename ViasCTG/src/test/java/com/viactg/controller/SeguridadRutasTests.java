package com.viactg.controller;

import com.viactg.model.Barrio;
import com.viactg.model.Categoria;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import com.viactg.security.JwtService;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.ReporteService;
import com.viactg.service.SeguimientoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Qué rutas son públicas, cuáles exigen sesión y cuáles un rol, según SecurityConfig y los @PreAuthorize. */
class SeguridadRutasTests extends ControllerTestBase {
    private static final String SIN_SESION = "Debes iniciar sesión para realizar esta acción, o tu sesión ya venció";
    private static final String SIN_PERMISO = "No tienes permiso para realizar esta acción";

    @BeforeEach
    void respuestasDeLosServicios() {
        when(reporteService.buscarPorId("r1")).thenReturn(reporte("r1", "ciudadano"));
        when(reporteService.calcularResumen(any())).thenReturn(new ReporteService.Resumen(0, Map.of(), 0, 0));
        when(reporteService.cambiarEstadoReporte(eq("r1"), any(), anyString(), any(), any())).thenReturn(reporte("r1", "ciudadano"));
        when(seguimientoService.resumen(eq("r1"), any())).thenReturn(new SeguimientoService.Resumen("r1", 0, 0, null, null, null));
        when(seguimientoService.mantenerAbierto("r1")).thenReturn(new SeguimientoService.Resumen("r1", 0, 0, null, null, null));
        when(seguimientoService.ejecutarTareaDiaria(any())).thenReturn(new SeguimientoService.Ejecucion(0, 0));
        Barrio barrio = Barrio.builder().id("b1").nombre("Manga").localidad("Histórica").build();
        when(barrioService.buscarPorId("b1")).thenReturn(barrio);
        when(barrioService.crear(any())).thenReturn(barrio);
        when(barrioService.actualizar(eq("b1"), any())).thenReturn(barrio);
        when(barrioService.agregarCalle(eq("b1"), any())).thenReturn(barrio);
        when(barrioService.eliminarCalle("b1", "c1")).thenReturn(barrio);
        Categoria categoria = Categoria.builder().id("c1").nombre("Hueco").activa(true).build();
        when(categoriaService.buscarPorId("c1")).thenReturn(categoria);
        when(categoriaService.crear(any())).thenReturn(categoria);
        when(categoriaService.actualizar(eq("c1"), any())).thenReturn(categoria);
        when(categoriaService.cambiarEstado(eq("c1"), anyBoolean())).thenReturn(categoria);
        when(usuarioService.cambiarRol(eq("u1"), any(), anyString())).thenReturn(usuario(Rol.CIUDADANO));
        when(usuarioService.cambiarActivo(eq("u1"), anyBoolean(), anyString())).thenReturn(usuario(Rol.CIUDADANO));
    }

    static Stream<String> rutasPublicas() {
        return Stream.of("/api/reportes", "/api/reportes?estado=PENDIENTE&limite=5", "/api/reportes/resumen",
                "/api/reportes/r1", "/api/reportes/cercanos?lat=10.42&lng=-75.54",
                "/api/reportes/posibles-duplicados?lat=10.42&lng=-75.54", "/api/reportes/r1/comentarios",
                "/api/reportes/r1/confirmaciones", "/api/reportes/r1/seguimiento", "/api/barrios", "/api/barrios/b1",
                "/api/categorias", "/api/categorias/c1", "/api/geocoding/inverso?lat=10.42&lng=-75.54");
    }

    static Stream<Arguments> rutasConSesion() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/reportes/mios"),
                Arguments.of(HttpMethod.POST, "/api/reportes"),
                Arguments.of(HttpMethod.PUT, "/api/reportes/r1"),
                Arguments.of(HttpMethod.POST, "/api/reportes/r1/fotos"),
                Arguments.of(HttpMethod.POST, "/api/reportes/r1/comentarios"),
                Arguments.of(HttpMethod.PUT, "/api/comentarios/k1"),
                Arguments.of(HttpMethod.DELETE, "/api/comentarios/k1"),
                Arguments.of(HttpMethod.POST, "/api/reportes/r1/confirmaciones"),
                Arguments.of(HttpMethod.DELETE, "/api/reportes/r1/confirmaciones/me"),
                Arguments.of(HttpMethod.POST, "/api/reportes/r1/seguimiento"),
                Arguments.of(HttpMethod.GET, "/api/notificaciones"),
                Arguments.of(HttpMethod.GET, "/api/notificaciones/sin-leer"),
                Arguments.of(HttpMethod.PATCH, "/api/notificaciones/n1/leida"),
                Arguments.of(HttpMethod.PATCH, "/api/notificaciones/leidas"),
                Arguments.of(HttpMethod.GET, "/api/usuarios/me"),
                Arguments.of(HttpMethod.PUT, "/api/usuarios/me"),
                Arguments.of(HttpMethod.PUT, "/api/usuarios/me/password"),
                Arguments.of(HttpMethod.POST, "/api/usuarios/me/cerrar-sesiones"),
                Arguments.of(HttpMethod.GET, "/api/usuarios"),
                Arguments.of(HttpMethod.PATCH, "/api/usuarios/u1/rol"),
                Arguments.of(HttpMethod.GET, "/api/admin/reportes/pendientes"),
                Arguments.of(HttpMethod.PATCH, "/api/admin/reportes/r1/estado"),
                Arguments.of(HttpMethod.GET, "/api/admin/estadisticas"),
                Arguments.of(HttpMethod.POST, "/api/barrios"),
                Arguments.of(HttpMethod.DELETE, "/api/barrios/b1"),
                Arguments.of(HttpMethod.POST, "/api/categorias"),
                Arguments.of(HttpMethod.PATCH, "/api/categorias/c1/estado?activa=false"));
    }

    // Con cuerpo válido: @Valid se resuelve antes que @PreAuthorize, así que sin él la respuesta sería 400.
    static Stream<Arguments> rutasDeModeracion() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/admin/reportes/pendientes", null),
                Arguments.of(HttpMethod.PATCH, "/api/admin/reportes/r1/estado", "{\"nuevoEstado\":\"EN_REVISION\"}"),
                Arguments.of(HttpMethod.GET, "/api/admin/reportes/r1/historial", null),
                Arguments.of(HttpMethod.GET, "/api/admin/reportes/seguimiento", null),
                Arguments.of(HttpMethod.POST, "/api/admin/reportes/r1/mantener-abierto", null),
                Arguments.of(HttpMethod.GET, "/api/admin/estadisticas", null));
    }

    static Stream<Arguments> rutasSoloAdmin() {
        String barrio = "{\"nombre\":\"Manga\",\"localidad\":\"Histórica\"}";
        String categoria = "{\"nombre\":\"Hueco\"}";
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/admin/reportes/seguimiento/recordatorios", null),
                Arguments.of(HttpMethod.POST, "/api/barrios", barrio),
                Arguments.of(HttpMethod.PUT, "/api/barrios/b1", barrio),
                Arguments.of(HttpMethod.POST, "/api/barrios/b1/calles", "{\"nombre\":\"Calle Real\",\"codigoPostal\":\"130001\"}"),
                Arguments.of(HttpMethod.DELETE, "/api/barrios/b1/calles/c1", null),
                Arguments.of(HttpMethod.DELETE, "/api/barrios/b1", null),
                Arguments.of(HttpMethod.POST, "/api/categorias", categoria),
                Arguments.of(HttpMethod.PUT, "/api/categorias/c1", categoria),
                Arguments.of(HttpMethod.PATCH, "/api/categorias/c1/estado?activa=false", null),
                Arguments.of(HttpMethod.GET, "/api/usuarios", null),
                Arguments.of(HttpMethod.PATCH, "/api/usuarios/u1/rol", "{\"rol\":\"MODERADOR\"}"),
                Arguments.of(HttpMethod.PATCH, "/api/usuarios/u1/activo?activo=false", null));
    }

    private static MockHttpServletRequestBuilder peticion(HttpMethod metodo, String ruta, String cuerpo) {
        MockHttpServletRequestBuilder builder = request(metodo, ruta);
        return cuerpo == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private void sinTocarLosServicios() {
        verifyNoInteractions(reporteService, seguimientoService, estadisticasService, barrioService, categoriaService,
                usuarioService, comentarioService, confirmacionService, notificacionService, authService);
    }

    @ParameterizedTest(name = "GET {0}")
    @MethodSource("rutasPublicas")
    void lasConsultasPublicasRespondenSinSesion(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rutasConSesion")
    void sinSesionLasRutasProtegidasResponden401ConErrorResponse(HttpMethod metodo, String ruta) throws Exception {
        mvc.perform(request(metodo, ruta))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(SIN_SESION))
                .andExpect(jsonPath("$.validationErrors").isEmpty());

        sinTocarLosServicios();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource({"rutasDeModeracion", "rutasSoloAdmin"})
    void unCiudadanoNoEntraAModeracionNiAdministracion(HttpMethod metodo, String ruta, String cuerpo) throws Exception {
        mvc.perform(peticion(metodo, ruta, cuerpo).header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(SIN_PERMISO));

        sinTocarLosServicios();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rutasDeModeracion")
    void unModeradorPuedeModerar(HttpMethod metodo, String ruta, String cuerpo) throws Exception {
        mvc.perform(peticion(metodo, ruta, cuerpo).header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR)))
                .andExpect(status().is2xxSuccessful());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rutasSoloAdmin")
    void unModeradorNoAdministraCatalogosNiUsuarios(HttpMethod metodo, String ruta, String cuerpo) throws Exception {
        mvc.perform(peticion(metodo, ruta, cuerpo).header(HttpHeaders.AUTHORIZATION, bearer(Rol.MODERADOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(SIN_PERMISO));

        sinTocarLosServicios();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource({"rutasDeModeracion", "rutasSoloAdmin"})
    void unAdministradorEntraATodo(HttpMethod metodo, String ruta, String cuerpo) throws Exception {
        mvc.perform(peticion(metodo, ruta, cuerpo).header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().is2xxSuccessful());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource({"rutasDeModeracion", "rutasSoloAdmin"})
    void sinPermisoLaRespuestaEs403AunqueElCuerpoSeaInvalido(HttpMethod metodo, String ruta, String cuerpo) throws Exception {
        // Sin las reglas por URL de SecurityConfig, @Valid se resolvía antes que @PreAuthorize y respondía 400 con
        // los errores de cada campo de un formulario que esta persona no puede usar.
        mvc.perform(peticion(metodo, ruta, cuerpo == null ? null : "{}").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(SIN_PERMISO))
                .andExpect(jsonPath("$.validationErrors").isEmpty());

        sinTocarLosServicios();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/uploads//foto.jpg", "/uploads/%2e%2e/application.properties", "/uploads/foto.jpg;x=1"})
    void unaUrlQueRechazaElFirewallDevuelve400EnLugarDePedirSesion(String ruta) throws Exception {
        mvc.perform(get(java.net.URI.create(ruta)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("La dirección solicitada no es válida"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer no-es-un-jwt", "Bearer ", "Basic YW5hOmNsYXZl", "bearer minusculas"})
    void unaCabeceraAuthorizationInvalidaCuentaComoSinSesion(String cabecera) throws Exception {
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, cabecera))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SIN_SESION));
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() throws Exception {
        Usuario usuario = usuario(Rol.ADMIN);
        bearer(usuario);
        JwtService otraClave = new JwtService("b3Ryby1zZWNyZXRvLXF1ZS1uby1lcy1lbC1kZS1sYS1hcGk=", 60_000);
        String falsificado = otraClave.generarToken(new UsuarioPrincipal(usuario));

        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + falsificado))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(usuarioService);
    }

    @Test
    void unTokenVencidoSeRechaza() throws Exception {
        Usuario usuario = usuario(Rol.CIUDADANO);
        bearer(usuario);
        String vencido = new JwtService(SECRETO_JWT, -1_000).generarToken(new UsuarioPrincipal(usuario));

        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SIN_SESION));
    }

    @Test
    void unTokenVencidoNoImpideVerLasRutasPublicas() throws Exception {
        String vencido = new JwtService(SECRETO_JWT, -1_000).generarToken(new UsuarioPrincipal(usuario(Rol.CIUDADANO)));

        mvc.perform(get("/api/reportes").header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isOk());
    }

    @Test
    void desactivarUnaCuentaInvalidaSusTokensAlInstante() throws Exception {
        Usuario usuario = usuario(Rol.ADMIN);
        String token = bearer(usuario);
        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());

        usuario.setActivo(false);

        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void cerrarSesionesInvalidaLosTokensDeVersionesAnteriores() throws Exception {
        Usuario usuario = usuario(Rol.CIUDADANO);
        String anterior = bearer(usuario);

        usuario.setVersionToken(1);

        mvc.perform(get("/api/reportes/mios").header(HttpHeaders.AUTHORIZATION, anterior)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reportes/mios").header(HttpHeaders.AUTHORIZATION, bearer(usuario))).andExpect(status().isOk());
    }

    @Test
    void elTokenDeUnUsuarioEliminadoSeRechaza() throws Exception {
        Usuario usuario = usuario(Rol.CIUDADANO);
        String token = bearer(usuario);
        when(userDetailsService.loadUserByUsername(usuario.getEmail())).thenThrow(new UsernameNotFoundException("Usuario no encontrado"));

        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    @Test
    void elRolSeLeeDeLaBaseDeDatosYNoDelToken() throws Exception {
        Usuario usuario = usuario(Rol.ADMIN);
        String token = bearer(usuario);

        usuario.setRol(Rol.CIUDADANO);

        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
    }

    @Test
    void lasRutasDeAutenticacionNoExigenSesion() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/api/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@correo.com\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void lasFotosSonPublicas() throws Exception {
        mvc.perform(get("/uploads/no-existe.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La dirección solicitada no existe"));
    }

    @Test
    void elPreflightDeCorsSeAceptaDesdeElOrigenConfigurado() throws Exception {
        mvc.perform(options("/api/reportes").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void elPreflightDeCorsSeRechazaDesdeOtroOrigen() throws Exception {
        mvc.perform(options("/api/reportes").header(HttpHeaders.ORIGIN, "https://sitio-ajeno.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
