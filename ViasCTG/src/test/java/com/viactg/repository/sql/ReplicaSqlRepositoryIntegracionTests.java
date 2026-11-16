package com.viactg.repository.sql;

import com.viactg.model.Barrio;
import com.viactg.model.Calle;
import com.viactg.model.Categoria;
import com.viactg.model.CategoriaReporte;
import com.viactg.model.Comentario;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.PrioridadReporte;
import com.viactg.model.RegistroAuditoria;
import com.viactg.model.Reporte;
import com.viactg.model.Rol;
import com.viactg.model.Seguimiento;
import com.viactg.model.Usuario;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El SQL de la réplica contra un PostgreSQL real: esquema de Flyway, upserts, llaves foráneas, reemplazo de hijos y
 * vistas. Solo corre si se define SQL_TEST_URL (por ejemplo jdbc:postgresql://localhost:5432/viasctg_sql_test) con
 * SQL_TEST_USUARIO y SQL_TEST_PASSWORD; en la CI, sin PostgreSQL, se omite. Borra y recrea el esquema de esa base:
 * nunca la apuntes a la base de la aplicación.
 */
@EnabledIfEnvironmentVariable(named = "SQL_TEST_URL", matches = "jdbc:postgresql:.+_test.*")
class ReplicaSqlRepositoryIntegracionTests {
    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static ReplicaSqlRepository repositorio;

    private static final Instant AHORA = Instant.parse("2026-11-16T15:00:00Z");

    @BeforeAll
    static void esquemaLimpio() {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(System.getenv("SQL_TEST_URL"));
        dataSource.setUsername(System.getenv().getOrDefault("SQL_TEST_USUARIO", "viasctg"));
        dataSource.setPassword(System.getenv("SQL_TEST_PASSWORD"));
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
        repositorio = new ReplicaSqlRepository(jdbc);
    }

    @AfterAll
    static void cerrar() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void vaciar() {
        repositorio.vaciar();
    }

    private static Usuario usuario(String id, Rol rol) {
        return Usuario.builder().id(id).nombre("Persona " + id).email(id + "@correo.com").passwordHash("$2a$10$secreto")
                .rol(rol).activo(true).emailVerificado(true).fechaRegistro(AHORA).build();
    }

    private static Reporte reporte(String id, String autor, String categoria, EstadoReporte estado) {
        return Reporte.builder().id(id).usuarioId(autor).categoria(new CategoriaReporte(categoria, "x")).descripcion("Hueco " + id)
                .latitud(10.42).longitud(-75.55).estado(estado).prioridad(PrioridadReporte.ALTA).fechaCreacion(AHORA)
                .fechaActualizacion(AHORA).fotos(new ArrayList<>(List.of("/uploads/a.jpg", "/uploads/b.jpg"))).build();
    }

    private void catalogoBase() {
        repositorio.guardarUsuarios(List.of(usuario("u1", Rol.CIUDADANO), usuario("mod1", Rol.MODERADOR)));
        repositorio.guardarCategorias(List.of(Categoria.builder().id("c1").nombre("Hueco").descripcion("Bache").activa(true).build()));
    }

    @Test
    void elEsquemaTieneTodasLasTablasYNoGuardaContrasenas() {
        assertThat(repositorio.contarFilas().keySet()).containsExactlyElementsOf(ReplicaSqlRepository.TABLAS);
        List<String> columnas = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'usuarios'", String.class);
        assertThat(columnas).doesNotContain("password_hash", "passwordhash");
    }

    @Test
    void unReporteSeGuardaConSusFotosEHistorialYSeActualizaSinDuplicar() {
        catalogoBase();
        Reporte reporte = reporte("r1", "u1", "c1", EstadoReporte.RESUELTO);
        reporte.getHistorialEstados().add(new HistorialEstado("h1", "mod1", EstadoReporte.PENDIENTE, EstadoReporte.EN_REVISION, null, AHORA));
        reporte.getHistorialEstados().add(new HistorialEstado("h2", "mod1", EstadoReporte.EN_REVISION, EstadoReporte.RESUELTO, "Listo", AHORA.plusSeconds(86_400)));

        repositorio.guardarReportes(List.of(reporte));
        reporte.getFotos().remove(1);
        reporte.setDescripcion("Hueco grande");
        repositorio.guardarReportes(List.of(reporte));

        Map<String, Long> filas = repositorio.contarFilas();
        assertThat(filas).containsEntry("reportes", 1L).containsEntry("reporte_fotos", 1L).containsEntry("historial_estados", 2L);
        assertThat(jdbc.queryForObject("SELECT descripcion FROM reportes WHERE id = 'r1'", String.class)).isEqualTo("Hueco grande");
        Map<String, Object> detalle = jdbc.queryForMap("SELECT categoria, autor, dias_resolucion FROM v_reportes_detalle WHERE id = 'r1'");
        assertThat(detalle).containsEntry("categoria", "Hueco").containsEntry("autor", "Persona u1");
        assertThat(((Number) detalle.get("dias_resolucion")).doubleValue()).isEqualTo(1.0);
        assertThat(jdbc.queryForObject("SELECT resueltos FROM v_actividad_moderadores WHERE usuario_id = 'mod1'", Long.class)).isEqualTo(1L);
    }

    @Test
    void lasReferenciasQueFaltanQuedanEnNullYLosHijosHuerfanosSeOmiten() {
        catalogoBase();
        repositorio.guardarReportes(List.of(reporte("r1", "no-existe", "no-existe", EstadoReporte.PENDIENTE)));
        repositorio.guardarComentarios(List.of(
                Comentario.builder().id("k1").reporteId("r1").usuarioId("u1").texto("Confirmo").fecha(AHORA).build(),
                Comentario.builder().id("k2").reporteId("r-sin-copiar").usuarioId("u1").texto("Huérfano").fecha(AHORA).build()));

        Map<String, Object> fila = jdbc.queryForMap("SELECT usuario_id, categoria_id FROM reportes WHERE id = 'r1'");
        assertThat(fila.get("usuario_id")).isNull();
        assertThat(fila.get("categoria_id")).isNull();
        assertThat(repositorio.contarFilas()).containsEntry("comentarios", 1L);
    }

    @Test
    void confirmacionesYVotosSeReemplazanPorReporte() {
        catalogoBase();
        repositorio.guardarReportes(List.of(reporte("r1", "u1", "c1", EstadoReporte.EN_PROCESO)));
        Confirmacion confirmacion = Confirmacion.builder().id("cf1").reporteId("r1").usuarioId("mod1").fecha(AHORA).build();
        Seguimiento voto = Seguimiento.builder().id("s1").reporteId("r1").usuarioId("mod1").arreglado(true).fecha(AHORA).build();

        repositorio.reemplazarConfirmaciones("r1", List.of(confirmacion));
        repositorio.reemplazarSeguimientos("r1", List.of(voto));
        voto.setArreglado(false);
        repositorio.reemplazarSeguimientos("r1", List.of(voto));
        repositorio.reemplazarConfirmaciones("r1", List.of());

        assertThat(repositorio.contarFilas()).containsEntry("confirmaciones", 0L).containsEntry("seguimientos", 1L);
        assertThat(jdbc.queryForObject("SELECT arreglado FROM seguimientos WHERE id = 's1'", Boolean.class)).isFalse();
    }

    @Test
    void barriosConCallesYBorradoEnCascada() {
        Barrio manga = Barrio.builder().id("b1").nombre("Manga").localidad("Histórica")
                .calles(new ArrayList<>(List.of(new Calle("ca1", "Calle Real", "130001"), new Calle("ca2", "Avenida Miramar", "130001")))).build();
        repositorio.guardarBarrios(List.of(manga));
        manga.getCalles().remove(0);
        repositorio.guardarBarrios(List.of(manga));
        assertThat(repositorio.contarFilas()).containsEntry("calles", 1L);

        repositorio.eliminarBarrio("b1");

        assertThat(repositorio.contarFilas()).containsEntry("barrios", 0L).containsEntry("calles", 0L);
    }

    @Test
    void laVistaPorCategoriaCalculaElPorcentajeResueltoSinRechazados() {
        catalogoBase();
        repositorio.guardarReportes(List.of(reporte("r1", "u1", "c1", EstadoReporte.RESUELTO), reporte("r2", "u1", "c1", EstadoReporte.PENDIENTE),
                reporte("r3", "u1", "c1", EstadoReporte.RECHAZADO)));
        repositorio.insertarAuditoria(List.of(RegistroAuditoria.builder().id("a1").fecha(AHORA).actorId("sistema").actorNombre("Sistema")
                .accion("REPORTE_CERRADO_POR_COMUNIDAD").entidad("REPORTE").entidadId("r1").build()));
        repositorio.insertarAuditoria(List.of(RegistroAuditoria.builder().id("a1").fecha(AHORA).accion("REPETIDA").build()));

        Map<String, Object> fila = jdbc.queryForMap("SELECT total, resueltos, rechazados, porcentaje_resuelto FROM v_reportes_por_categoria WHERE categoria_id = 'c1'");
        assertThat(fila).containsEntry("total", 3L).containsEntry("resueltos", 1L).containsEntry("rechazados", 1L);
        assertThat(((Number) fila.get("porcentaje_resuelto")).doubleValue()).isEqualTo(50.0);
        assertThat(repositorio.contarFilas()).containsEntry("auditoria", 1L);
        assertThat(repositorio.existe("reportes", "r2")).isTrue();
        assertThat(repositorio.existe("reportes", "r9")).isFalse();
    }
}
