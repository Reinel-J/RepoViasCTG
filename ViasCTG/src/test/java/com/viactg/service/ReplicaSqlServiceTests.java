package com.viactg.service;

import com.viactg.dto.EstadoReplicaResponse;
import com.viactg.exception.ServicioNoDisponibleException;
import com.viactg.model.Categoria;
import com.viactg.model.CategoriaReporte;
import com.viactg.model.Comentario;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.Reporte;
import com.viactg.model.Usuario;
import com.viactg.repository.sql.ReplicaSqlRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReplicaSqlServiceTests {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ReplicaSqlRepository repositorio = mock(ReplicaSqlRepository.class);
    private final Flyway flyway = mock(Flyway.class);
    private final TransactionTemplate transacciones = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> proveedor(T valor) {
        ObjectProvider<T> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(valor);
        return proveedor;
    }

    private ReplicaSqlService habilitada() {
        return new ReplicaSqlService(mongoTemplate, proveedor(repositorio), proveedor(flyway), proveedor(transacciones));
    }

    private ReplicaSqlService deshabilitada() {
        return new ReplicaSqlService(mongoTemplate, proveedor(null), proveedor(null), proveedor(null));
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void coleccionesVacias() {
        when(mongoTemplate.stream(any(Query.class), any(Class.class))).thenAnswer(invocacion -> Stream.empty());
        when(repositorio.contarFilas()).thenReturn(Map.of());
        when(mongoTemplate.getCollectionName(any())).thenReturn("coleccion");
        agregacionesDeMongo();
    }

    private static Reporte reporte(String id) {
        Reporte reporte = Reporte.builder().id(id).usuarioId("u1").categoria(new CategoriaReporte("c1", "Hueco"))
                .descripcion("Hueco frente al colegio").latitud(10.4).longitud(-75.5).estado(EstadoReporte.EN_REVISION)
                .fechaCreacion(Instant.parse("2026-11-01T12:00:00Z")).build();
        reporte.getHistorialEstados().add(new HistorialEstado("h1", "mod1", EstadoReporte.PENDIENTE, EstadoReporte.EN_REVISION, null,
                Instant.parse("2026-11-02T12:00:00Z")));
        return reporte;
    }

    @Test
    void deshabilitadaNoHaceNadaYNoSePuedeReconstruir() {
        ReplicaSqlService replica = deshabilitada();

        replica.alArrancar();
        replica.alCambiar(CambioReplicable.reporte("r1"));

        assertThat(replica.habilitada()).isFalse();
        assertThatThrownBy(replica::solicitarReconstruccion).isInstanceOf(ServicioNoDisponibleException.class);
        verifyNoInteractions(repositorio, flyway);
    }

    @Test
    void reconstruirMigraElEsquemaVaciaYCopiaLasColeccionesEnOrdenDeDependencia() {
        Usuario usuario = Usuario.builder().id("u1").nombre("Ana").email("ana@correo.com").build();
        when(mongoTemplate.stream(any(Query.class), eq(Usuario.class))).thenAnswer(invocacion -> Stream.of(usuario));
        when(mongoTemplate.stream(any(Query.class), eq(Reporte.class))).thenAnswer(invocacion -> Stream.of(reporte("r1")));
        ReplicaSqlService replica = habilitada();

        replica.reconstruir();

        var orden = inOrder(flyway, repositorio);
        orden.verify(flyway).migrate();
        orden.verify(repositorio).vaciar();
        orden.verify(repositorio).guardarUsuarios(List.of(usuario));
        orden.verify(repositorio).guardarReportes(anyList());
        orden.verify(repositorio).contarFilas();
        EstadoReplicaResponse estado = replica.estado();
        assertThat(estado.pendienteReconstruir()).isFalse();
        assertThat(estado.ultimaReconstruccion()).isNotNull();
        assertThat(estado.ultimoError()).isNull();
    }

    @Test
    void copiaPorLotesSinCargarTodaLaColeccion() {
        List<Reporte> muchos = new ArrayList<>();
        for (int i = 0; i < ReplicaSqlService.TAMANO_LOTE * 2 + 3; i++) muchos.add(reporte("r" + i));
        when(mongoTemplate.stream(any(Query.class), eq(Reporte.class))).thenAnswer(invocacion -> muchos.stream());
        List<Integer> tamanos = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocacion -> tamanos.add(invocacion.<List<?>>getArgument(0).size()))
                .when(repositorio).guardarReportes(anyList());

        habilitada().reconstruir();

        assertThat(tamanos).containsExactly(ReplicaSqlService.TAMANO_LOTE, ReplicaSqlService.TAMANO_LOTE, 3);
    }

    @Test
    void antesDeLaPrimeraReconstruccionLosCambiosSueltosSeIgnoran() {
        habilitada().aplicar(CambioReplicable.reporte("r1"));

        verifyNoInteractions(repositorio);
    }

    @Test
    void unReporteSeCopiaJuntoConLoQueReferencia() {
        ReplicaSqlService replica = habilitada();
        replica.reconstruir();
        Reporte reporte = reporte("r1");
        when(mongoTemplate.findById("r1", Reporte.class)).thenReturn(reporte);
        when(repositorio.existe(anyString(), anyString())).thenReturn(false);
        Usuario autor = Usuario.builder().id("u1").build();
        Usuario moderador = Usuario.builder().id("mod1").build();
        Categoria hueco = Categoria.builder().id("c1").nombre("Hueco").activa(true).build();
        when(mongoTemplate.findById("u1", Usuario.class)).thenReturn(autor);
        when(mongoTemplate.findById("mod1", Usuario.class)).thenReturn(moderador);
        when(mongoTemplate.findById("c1", Categoria.class)).thenReturn(hueco);

        replica.aplicar(CambioReplicable.reporte("r1"));

        verify(repositorio).guardarUsuarios(List.of(autor));
        verify(repositorio).guardarUsuarios(List.of(moderador));
        verify(repositorio).guardarCategorias(List.of(hueco));
        verify(repositorio).guardarReportes(List.of(reporte));
        assertThat(replica.estado().ultimaSincronizacion()).isNotNull();
    }

    @Test
    void unDocumentoQueYaNoExisteSeBorraDeLaReplica() {
        ReplicaSqlService replica = habilitada();
        replica.reconstruir();

        replica.aplicar(CambioReplicable.comentario("k1"));
        replica.aplicar(CambioReplicable.barrio("b1"));

        verify(repositorio).eliminarComentario("k1");
        verify(repositorio).eliminarBarrio("b1");
        verify(repositorio, never()).guardarComentarios(anyList());
    }

    @Test
    void lasConfirmacionesDeUnReporteSeReemplazanCompletas() {
        ReplicaSqlService replica = habilitada();
        replica.reconstruir();
        when(repositorio.existe(anyString(), anyString())).thenReturn(true);
        when(mongoTemplate.find(any(Query.class), eq(com.viactg.model.Confirmacion.class))).thenReturn(List.of());

        replica.aplicar(CambioReplicable.confirmaciones("r1"));

        verify(repositorio).reemplazarConfirmaciones("r1", List.of());
    }

    @Test
    void unErrorDePostgresQuedaRegistradoYPideReconstruir() {
        ReplicaSqlService replica = habilitada();
        replica.reconstruir();
        when(mongoTemplate.findById("k1", Comentario.class)).thenReturn(Comentario.builder().id("k1").reporteId("r1").usuarioId("u1").build());
        when(repositorio.existe(anyString(), anyString())).thenReturn(true);
        doThrow(new DataAccessResourceFailureException("Connection refused")).when(repositorio).guardarComentarios(anyList());

        replica.aplicar(CambioReplicable.comentario("k1"));
        replica.aplicar(CambioReplicable.comentario("k2"));

        EstadoReplicaResponse estado = replica.estado();
        assertThat(estado.ultimoError()).isEqualTo("Connection refused");
        assertThat(estado.pendienteReconstruir()).isTrue();
        // Tras el error ya no se aplican cambios sueltos: la reconstrucción completa los recogerá.
        verify(mongoTemplate, never()).findById("k2", Comentario.class);
    }

    @Test
    void siPostgresNoRespondeAlReconstruirLaApiSigueYQuedaPendiente() {
        doThrow(new DataAccessResourceFailureException("Connection refused")).when(flyway).migrate();
        ReplicaSqlService replica = habilitada();

        replica.reconstruir();

        assertThat(replica.estado().pendienteReconstruir()).isTrue();
        assertThat(replica.estado().ultimoError()).contains("Connection refused");
        verify(repositorio, never()).vaciar();
    }

    // Cada suma de elementos embebidos (calles, fotos, historial) da 7.
    @SuppressWarnings("unchecked")
    private void agregacionesDeMongo() {
        com.mongodb.client.MongoCollection<org.bson.Document> coleccion = mock(com.mongodb.client.MongoCollection.class);
        com.mongodb.client.AggregateIterable<org.bson.Document> resultado = mock(com.mongodb.client.AggregateIterable.class);
        when(mongoTemplate.getCollection(anyString())).thenReturn(coleccion);
        when(coleccion.aggregate(anyList())).thenReturn(resultado);
        when(resultado.first()).thenReturn(new org.bson.Document("total", 7));
    }

    @Test
    void elEstadoComparaMongoConPostgresPorTabla() {
        agregacionesDeMongo();
        when(mongoTemplate.count(any(Query.class), eq(Reporte.class))).thenReturn(20_000L);
        when(repositorio.contarFilas()).thenReturn(Map.of("reportes", 19_998L, "calles", 7L));

        EstadoReplicaResponse estado = habilitada().estado();

        assertThat(estado.habilitada()).isTrue();
        assertThat(estado.conectada()).isTrue();
        assertThat(estado.tablas()).extracting(EstadoReplicaResponse.Tabla::nombre).containsExactlyElementsOf(ReplicaSqlRepository.TABLAS);
        assertThat(estado.tablas()).contains(new EstadoReplicaResponse.Tabla("reportes", 20_000L, 19_998L),
                new EstadoReplicaResponse.Tabla("calles", 7L, 7L));
    }

    @Test
    void siPostgresNoRespondeElEstadoLoMuestraDesconectadoSinFallar() {
        agregacionesDeMongo();
        when(repositorio.contarFilas()).thenThrow(new DataAccessResourceFailureException("Connection refused"));

        EstadoReplicaResponse estado = habilitada().estado();

        assertThat(estado.conectada()).isFalse();
        assertThat(estado.tablas()).allSatisfy(tabla -> assertThat(tabla.sql()).isNull());
    }

    @Test
    void deshabilitadaElEstadoSoloMuestraMongo() {
        agregacionesDeMongo();

        EstadoReplicaResponse estado = deshabilitada().estado();

        assertThat(estado.habilitada()).isFalse();
        assertThat(estado.pendienteReconstruir()).isFalse();
        assertThat(estado.tablas()).allSatisfy(tabla -> assertThat(tabla.sql()).isNull());
    }
}
