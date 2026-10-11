package com.viactg.service;

import com.mongodb.client.result.UpdateResult;
import com.viactg.exception.BusinessRuleException;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.Reporte;
import com.viactg.model.Seguimiento;
import com.viactg.model.Usuario;
import com.viactg.repository.ConfirmacionRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.SeguimientoRepository;
import com.viactg.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeguimientoServiceTests {
    private static final Instant AHORA = Instant.parse("2026-10-10T14:00:00Z");

    private SeguimientoRepository seguimientoRepository;
    private ReporteRepository reporteRepository;
    private ConfirmacionRepository confirmacionRepository;
    private UsuarioRepository usuarioRepository;
    private NotificacionService notificacionService;
    private CorreoService correoService;
    private MongoTemplate mongoTemplate;
    private SeguimientoService service;

    @BeforeEach
    void setUp() {
        seguimientoRepository = mock(SeguimientoRepository.class);
        reporteRepository = mock(ReporteRepository.class);
        confirmacionRepository = mock(ConfirmacionRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        notificacionService = mock(NotificacionService.class);
        correoService = mock(CorreoService.class);
        mongoTemplate = mock(MongoTemplate.class);
        service = new SeguimientoService(seguimientoRepository, reporteRepository, confirmacionRepository, usuarioRepository,
                notificacionService, correoService, mongoTemplate, 30, "http://localhost:5173/");
    }

    @Test
    void responderSobreUnReporteCerradoLanzaExcepcionYNoGuardaElVoto() {
        when(reporteRepository.findById("r1")).thenReturn(Optional.of(Reporte.builder().id("r1").estado(EstadoReporte.RESUELTO).build()));

        assertThatThrownBy(() -> service.responder("r1", "u1", true)).isInstanceOf(BusinessRuleException.class);
        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(Seguimiento.class));
    }

    @Test
    void responderHaceUpsertDelVotoDelUsuarioYDevuelveLosConteos() {
        when(reporteRepository.findById("r1")).thenReturn(Optional.of(Reporte.builder().id("r1").estado(EstadoReporte.EN_PROCESO).build()));
        when(seguimientoRepository.countByReporteIdAndArreglado("r1", true)).thenReturn(3L);
        when(seguimientoRepository.countByReporteIdAndArreglado("r1", false)).thenReturn(1L);
        when(seguimientoRepository.findByReporteIdAndUsuarioId("r1", "u1"))
                .thenReturn(Optional.of(Seguimiento.builder().arreglado(true).build()));

        SeguimientoService.Resumen resumen = service.responder("r1", "u1", true);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(query.capture(), update.capture(), eq(Seguimiento.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("reporteId", "r1").containsEntry("usuarioId", "u1");
        assertThat(update.getValue().getUpdateObject().toString()).contains("arreglado=true");
        assertThat(resumen.arreglado()).isEqualTo(3);
        assertThat(resumen.sigue()).isEqualTo(1);
        assertThat(resumen.miRespuesta()).isTrue();
    }

    @Test
    void recordatoriosBuscanReportesActivosSinCambiosNiAvisosEnElIntervalo() {
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of());

        service.enviarRecordatorios(AHORA);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Reporte.class));
        String filtro = query.getValue().getQueryObject().toString();
        assertThat(filtro).contains("PENDIENTE", "EN_REVISION", "EN_PROCESO", "fechaActualizacion", "fechaUltimoSeguimiento")
                .doesNotContain("RESUELTO", "RECHAZADO")
                .contains(AHORA.minus(Duration.ofDays(30)).toString());
    }

    @Test
    void recordatorioAvisaUnaVezAlAutorYAQuienesConfirmaronSaltandoCuentasInactivas() {
        Reporte reporte = Reporte.builder().id("r1").usuarioId("autor").descripcion("Hueco en la avenida").estado(EstadoReporte.EN_PROCESO).build();
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of(reporte));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Reporte.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));
        // El autor también confirmó su propio reporte: debe recibir un solo aviso.
        when(confirmacionRepository.findByReporteId("r1")).thenReturn(List.of(
                Confirmacion.builder().usuarioId("autor").build(), Confirmacion.builder().usuarioId("vecino").build(),
                Confirmacion.builder().usuarioId("inactivo").build()));
        when(usuarioRepository.findAllById(Set.of("autor", "vecino", "inactivo"))).thenReturn(List.of(
                usuario("autor", true), usuario("vecino", true), usuario("inactivo", false)));

        int recordados = service.enviarRecordatorios(AHORA);

        assertThat(recordados).isEqualTo(1);
        verify(notificacionService).crear(eq("autor"), eq("r1"), contains("tu reporte"));
        verify(notificacionService).crear(eq("vecino"), eq("r1"), contains("que confirmaste"));
        verify(notificacionService, never()).crear(eq("inactivo"), anyString(), anyString());
        verify(correoService).enviar(eq("vecino@correo.co"), anyString(), contains("http://localhost:5173/reportes/r1"));
    }

    @Test
    void recordatorioNoAvisaSiOtraEjecucionYaMarcoElReporte() {
        Reporte reporte = Reporte.builder().id("r1").usuarioId("autor").estado(EstadoReporte.PENDIENTE).build();
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of(reporte));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Reporte.class))).thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThat(service.enviarRecordatorios(AHORA)).isZero();
        verify(notificacionService, never()).crear(anyString(), anyString(), anyString());
    }

    private Usuario usuario(String id, boolean activo) {
        return Usuario.builder().id(id).nombre(id).email(id + "@correo.co").activo(activo).build();
    }
}
