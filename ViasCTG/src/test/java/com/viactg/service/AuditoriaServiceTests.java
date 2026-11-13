package com.viactg.service;

import com.viactg.dto.PaginaResponse;
import com.viactg.dto.RegistroAuditoriaResponse;
import com.viactg.model.RegistroAuditoria;
import com.viactg.model.Usuario;
import com.viactg.repository.UsuarioRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditoriaServiceTests {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AuditoriaService service = new AuditoriaService(mongoTemplate, usuarioRepository);

    @Test
    void registrarGuardaTodosLosDatosDeLaAccion() {
        Usuario actor = Usuario.builder().id("admin").nombre("Ana Admin").build();
        when(usuarioRepository.findById("admin")).thenReturn(Optional.of(actor));

        service.registrar("admin", "REPORTE_ESTADO_CAMBIADO", "REPORTE", "r1", "PENDIENTE → EN_REVISION");

        ArgumentCaptor<RegistroAuditoria> registro = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(mongoTemplate).insert(registro.capture());
        assertThat(registro.getValue())
                .extracting(RegistroAuditoria::getActorId, RegistroAuditoria::getActorNombre,
                        RegistroAuditoria::getAccion, RegistroAuditoria::getEntidad,
                        RegistroAuditoria::getEntidadId, RegistroAuditoria::getDetalle)
                .containsExactly("admin", "Ana Admin", "REPORTE_ESTADO_CAMBIADO", "REPORTE", "r1",
                        "PENDIENTE → EN_REVISION");
        assertThat(registro.getValue().getFecha()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void registrarResuelveElNombreDelActorSinExponerFallosDeAuditoria() {
        when(usuarioRepository.findById("eliminado")).thenReturn(Optional.empty());

        service.registrar(AuditoriaService.ACTOR_SISTEMA, "TAREA", "SISTEMA", "seguimiento", "Ejecutada");
        service.registrar("eliminado", "TAREA", "SISTEMA", "seguimiento", "Ejecutada");

        ArgumentCaptor<RegistroAuditoria> registros = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(mongoTemplate, org.mockito.Mockito.times(2)).insert(registros.capture());
        assertThat(registros.getAllValues()).extracting(RegistroAuditoria::getActorNombre)
                .containsExactly("Sistema", "Usuario eliminado");
        verify(usuarioRepository, never()).findById(AuditoriaService.ACTOR_SISTEMA);
    }

    @Test
    void registrarNoPropagaUnaFallaDeMongo() {
        when(usuarioRepository.findById("admin")).thenReturn(Optional.empty());
        when(mongoTemplate.insert(any(RegistroAuditoria.class))).thenThrow(new IllegalStateException("Mongo no disponible"));

        assertThatCode(() -> service.registrar("admin", "TAREA", "SISTEMA", "seguimiento", "Ejecutada"))
                .doesNotThrowAnyException();
    }

    @Test
    void listarSinFiltrosPaginaOrdenadaYCalculaElTotalDePaginas() {
        RegistroAuditoria registro = RegistroAuditoria.builder().id("a1").fecha(Instant.parse("2026-11-12T08:00:00Z"))
                .actorId("admin").actorNombre("Ana").accion("TAREA").entidad("SISTEMA")
                .entidadId("seguimiento").detalle("Ejecutada").build();
        when(mongoTemplate.count(any(Query.class), eq(RegistroAuditoria.class))).thenReturn(101L);
        when(mongoTemplate.find(any(Query.class), eq(RegistroAuditoria.class))).thenReturn(List.of(registro));

        PaginaResponse<RegistroAuditoriaResponse> pagina = service.listar(null, null, null, null, null, 2, 50);

        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(consulta.capture(), eq(RegistroAuditoria.class));
        assertThat(consulta.getValue().getQueryObject()).isEmpty();
        assertThat(consulta.getValue().getSortObject()).isEqualTo(new Document("fecha", -1));
        assertThat(consulta.getValue().getSkip()).isEqualTo(100);
        assertThat(consulta.getValue().getLimit()).isEqualTo(50);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
        assertThat(pagina.contenido()).extracting(RegistroAuditoriaResponse::id).containsExactly("a1");
    }

    @Test
    void listarAgregaLosCriteriosDeTodosLosFiltros() {
        Instant desde = Instant.parse("2026-11-01T00:00:00Z");
        Instant hasta = Instant.parse("2026-11-12T23:59:59Z");
        when(mongoTemplate.count(any(Query.class), eq(RegistroAuditoria.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(RegistroAuditoria.class))).thenReturn(List.of());

        service.listar("REPORTE", "REPORTE_CERRADO_POR_COMUNIDAD", "sistema", desde, hasta, 0, 20);

        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(consulta.capture(), eq(RegistroAuditoria.class));
        assertThat(consulta.getValue().getQueryObject()).containsEntry("entidad", "REPORTE")
                .containsEntry("accion", "REPORTE_CERRADO_POR_COMUNIDAD")
                .containsEntry("actorId", "sistema");
        assertThat(consulta.getValue().getQueryObject().get("fecha", Document.class))
                .containsEntry("$gte", desde).containsEntry("$lte", hasta);
    }
}
