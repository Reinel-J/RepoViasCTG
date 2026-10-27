package com.viactg.controller;

import com.viactg.config.SecurityConfig;
import com.viactg.mapper.BarrioMapper;
import com.viactg.mapper.CategoriaMapper;
import com.viactg.mapper.ComentarioMapper;
import com.viactg.mapper.ConfirmacionMapper;
import com.viactg.mapper.NotificacionMapper;
import com.viactg.mapper.ReporteMapper;
import com.viactg.mapper.UsuarioMapper;
import com.viactg.model.EstadoReporte;
import com.viactg.model.PrioridadReporte;
import com.viactg.model.Reporte;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import com.viactg.security.JwtService;
import com.viactg.security.LimitadorIntentos;
import com.viactg.security.MongoUserDetailsService;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.AreaServicio;
import com.viactg.service.AuthService;
import com.viactg.service.BarrioService;
import com.viactg.service.CategoriaService;
import com.viactg.service.ComentarioService;
import com.viactg.service.ConfirmacionService;
import com.viactg.service.DuplicadoService;
import com.viactg.service.EstadisticasService;
import com.viactg.service.GeocodingService;
import com.viactg.service.NotificacionService;
import com.viactg.service.ReporteService;
import com.viactg.service.SeguimientoService;
import com.viactg.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Locale;

import static org.mockito.Mockito.when;

/**
 * Base de las pruebas HTTP: levanta todos los controladores con la cadena de seguridad real (SecurityConfig, filtro
 * JWT, JwtService y GlobalExceptionHandler) y los mappers reales; los servicios son mocks. Todas las subclases
 * comparten la misma configuración, así que Spring reutiliza un único contexto.
 */
@WebMvcTest(properties = {
        "app.jwt.secret=" + ControllerTestBase.SECRETO_JWT,
        "app.jwt.expiration-ms=60000",
        "app.cors.allowed-origins=http://localhost:5173"
})
@Import({SecurityConfig.class, JwtService.class, BarrioMapper.class, CategoriaMapper.class, ComentarioMapper.class,
        ConfirmacionMapper.class, NotificacionMapper.class, ReporteMapper.class, UsuarioMapper.class})
abstract class ControllerTestBase {
    static final String SECRETO_JWT = "dmlhY3RnLXNlY3JldG8tZGUtcHJ1ZWJhcy0zMi1ieXRlcw==";

    @Autowired protected MockMvc mvc;
    @Autowired protected JwtService jwtService;

    @MockBean protected MongoUserDetailsService userDetailsService;
    @MockBean protected LimitadorIntentos limitador;
    @MockBean protected AreaServicio areaServicio;
    @MockBean protected AuthService authService;
    @MockBean protected BarrioService barrioService;
    @MockBean protected CategoriaService categoriaService;
    @MockBean protected ComentarioService comentarioService;
    @MockBean protected ConfirmacionService confirmacionService;
    @MockBean protected DuplicadoService duplicadoService;
    @MockBean protected EstadisticasService estadisticasService;
    @MockBean protected GeocodingService geocodingService;
    @MockBean protected NotificacionService notificacionService;
    @MockBean protected ReporteService reporteService;
    @MockBean protected SeguimientoService seguimientoService;
    @MockBean protected UsuarioService usuarioService;

    /** Usuario activo cuyo id es el nombre del rol en minúsculas (por ejemplo, "ciudadano"). */
    protected static Usuario usuario(Rol rol) {
        String id = rol.name().toLowerCase(Locale.ROOT);
        return Usuario.builder().id(id).nombre("Usuario " + id).email(id + "@viactg.co").rol(rol).activo(true)
                .emailVerificado(true).versionToken(0).fechaRegistro(Instant.parse("2026-10-01T12:00:00Z")).build();
    }

    /** Registra al usuario en el UserDetailsService simulado y devuelve la cabecera Authorization con su JWT. */
    protected String bearer(Usuario usuario) {
        when(userDetailsService.loadUserByUsername(usuario.getEmail())).thenReturn(new UsuarioPrincipal(usuario));
        return "Bearer " + jwtService.generarToken(new UsuarioPrincipal(usuario));
    }

    protected String bearer(Rol rol) { return bearer(usuario(rol)); }

    protected static Reporte reporte(String id, String autorId) {
        Instant creado = Instant.parse("2026-10-20T15:00:00Z");
        return Reporte.builder().id(id).usuarioId(autorId).categoriaId("Hueco").descripcion("Hueco profundo frente al colegio")
                .latitud(10.42).longitud(-75.54).estado(EstadoReporte.PENDIENTE).prioridad(PrioridadReporte.MEDIA)
                .fechaCreacion(creado).fechaActualizacion(creado).build();
    }
}
