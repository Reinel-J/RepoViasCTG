package com.viactg.service;

import com.mongodb.client.result.UpdateResult;
import com.viactg.exception.BusinessRuleException;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.Reporte;
import com.viactg.model.Seguimiento;
import com.viactg.model.Usuario;
import com.viactg.repository.ConfirmacionRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.SeguimientoRepository;
import com.viactg.repository.UsuarioRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private ReporteService reporteService;
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
        reporteService = mock(ReporteService.class);
        service = servicio(true);
    }

    // Cierre por la comunidad con los valores por defecto: 3 votos, 75 % y 72 horas de gracia.
    private SeguimientoService servicio(boolean cierreHabilitado) {
        return new SeguimientoService(seguimientoRepository, reporteRepository, confirmacionRepository, usuarioRepository,
                notificacionService, correoService, mongoTemplate, reporteService, 30, "http://localhost:5173/",
                cierreHabilitado, 3, 0.75, 72);
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

    // --- Cierre por la comunidad ---

    private static final Instant EN_PROCESO_DESDE = AHORA.minus(Duration.ofDays(5));

    private Reporte enProceso(Instant cierreDesde, Instant vetadoEn) {
        HistorialEstado aProceso = new HistorialEstado("h1", "moderador", EstadoReporte.EN_REVISION, EstadoReporte.EN_PROCESO, null, EN_PROCESO_DESDE);
        Reporte reporte = Reporte.builder().id("r1").usuarioId("autor").descripcion("Hueco en la avenida").estado(EstadoReporte.EN_PROCESO)
                .historialEstados(new ArrayList<>(List.of(aProceso))).cierreComunitarioDesde(cierreDesde).cierreComunitarioVetadoEn(vetadoEn).build();
        when(reporteRepository.findById("r1")).thenReturn(Optional.of(reporte));
        return reporte;
    }

    private void votos(Seguimiento... votos) {
        when(seguimientoRepository.findByReporteId("r1")).thenReturn(List.of(votos));
    }

    private static Seguimiento voto(String usuarioId, boolean arreglado, Instant fecha) {
        return Seguimiento.builder().reporteId("r1").usuarioId(usuarioId).arreglado(arreglado).fecha(fecha).build();
    }

    private static Seguimiento votoReciente(String usuarioId, boolean arreglado) {
        return voto(usuarioId, arreglado, EN_PROCESO_DESDE.plus(Duration.ofDays(1)));
    }

    private void marcarDevuelve(long modificados) {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Reporte.class)))
                .thenReturn(UpdateResult.acknowledged(modificados, modificados, null));
    }

    private Update unicaActualizacionDelReporte(ArgumentCaptor<Query> query) {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(Reporte.class));
        return update.getValue();
    }

    @Test
    void unVotoQueCompletaLaReglaProponeElCierreYAvisaALosModeradores() {
        enProceso(null, null);
        // El autor cuenta doble: autor + un vecino = 3 votos «arreglado».
        votos(votoReciente("autor", true), votoReciente("vecino", true));
        marcarDevuelve(1);
        when(usuarioRepository.findByRolInAndActivoTrue(any())).thenReturn(List.of(usuario("moderadora", true)));

        service.responder("r1", "vecino", true);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        Update update = unicaActualizacionDelReporte(query);
        // Solo marca si sigue en proceso y nadie lo marcó antes.
        assertThat(query.getValue().getQueryObject()).containsEntry("estado", EstadoReporte.EN_PROCESO).containsEntry("cierreComunitarioDesde", null);
        assertThat(update.getUpdateObject().get("$set", Document.class)).containsKey("cierreComunitarioDesde");
        verify(notificacionService).crear(eq("moderadora"), eq("r1"), contains("Se cerrará solo el"));
    }

    @Test
    void siOtraPeticionYaLoPropusoNoSeAvisaDosVeces() {
        enProceso(null, null);
        votos(votoReciente("autor", true), votoReciente("vecino", true));
        marcarDevuelve(0);

        service.responder("r1", "vecino", true);

        verify(notificacionService, never()).crear(anyString(), anyString(), anyString());
    }

    @Test
    void unVotoSigueIgualQueRompeLaReglaQuitaLaPropuesta() {
        enProceso(AHORA, null);
        votos(votoReciente("v1", true), votoReciente("v2", true), votoReciente("v3", true),
                votoReciente("v4", false), votoReciente("v5", false));

        service.responder("r1", "v5", false);

        Update update = unicaActualizacionDelReporte(ArgumentCaptor.forClass(Query.class));
        assertThat(update.getUpdateObject().get("$unset", Document.class)).containsKey("cierreComunitarioDesde");
    }

    @Test
    void losVotosAnterioresAEnProcesoNoCuentan() {
        enProceso(null, null);
        Instant antes = EN_PROCESO_DESDE.minus(Duration.ofDays(2));
        votos(voto("v1", true, antes), voto("v2", true, antes), voto("v3", true, antes));

        service.responder("r1", "v3", true);

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Reporte.class));
    }

    @Test
    void trasMantenerloAbiertoSoloCuentanLosVotosPosteriores() {
        Instant veto = EN_PROCESO_DESDE.plus(Duration.ofDays(2));
        enProceso(null, veto);
        // Tres votos entre EN_PROCESO y el veto, y solo uno después: no alcanza.
        votos(votoReciente("v1", true), votoReciente("v2", true), votoReciente("v3", true),
                voto("v4", true, veto.plus(Duration.ofHours(1))));

        service.responder("r1", "v4", true);

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Reporte.class));
    }

    @Test
    void conElCierreApagadoLosVotosNoProponenNada() {
        service = servicio(false);
        enProceso(AHORA, null);
        votos(votoReciente("autor", true), votoReciente("vecino", true));

        SeguimientoService.Resumen resumen = service.responder("r1", "vecino", true);

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Reporte.class));
        assertThat(resumen.cierrePrevisto()).isNull();
        assertThat(service.aplicarCierresComunitarios(AHORA)).isZero();
        verify(mongoTemplate, never()).find(any(Query.class), eq(Reporte.class));
    }

    @Test
    void elResumenMuestraLosVotosComoLosCuentaLaRegla() {
        enProceso(null, null);
        Instant antes = EN_PROCESO_DESDE.minus(Duration.ofDays(2));
        // El autor vale doble y el «sigue igual» de antes de EN_PROCESO no cuenta: 2 personas, 3 votos para la regla.
        votos(votoReciente("autor", true), votoReciente("vecino", true), voto("viejo", false, antes));

        SeguimientoService.ProgresoCierre progreso = service.resumen("r1", null).progresoCierre();

        assertThat(progreso).isEqualTo(new SeguimientoService.ProgresoCierre(3, 0, 3, 0.75));
    }

    @Test
    void sinCierreHabilitadoOFueraDeEnProcesoNoHayProgreso() {
        service = servicio(false);
        enProceso(null, null);
        assertThat(service.resumen("r1", null).progresoCierre()).isNull();

        service = servicio(true);
        when(reporteRepository.findById("r1")).thenReturn(Optional.of(Reporte.builder().id("r1").estado(EstadoReporte.EN_REVISION).build()));
        assertThat(service.resumen("r1", null).progresoCierre()).isNull();
    }

    @Test
    void elResumenIndicaCuandoSeCerraraSolo() {
        enProceso(AHORA, null);

        assertThat(service.resumen("r1", null).cierrePrevisto()).isEqualTo(AHORA.plus(Duration.ofHours(72)));
    }

    @Test
    void laTareaCierraLasPropuestasConLaGraciaVencidaQueSiguenCumpliendo() {
        Reporte reporte = enProceso(AHORA.minus(Duration.ofHours(73)), null);
        votos(votoReciente("autor", true), votoReciente("vecino", true), votoReciente("otro", false));
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of(reporte));

        assertThat(service.aplicarCierresComunitarios(AHORA)).isEqualTo(1);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Reporte.class));
        assertThat(query.getValue().getQueryObject().toString()).contains("EN_PROCESO", "cierreComunitarioDesde", "$lte",
                AHORA.minus(Duration.ofHours(72)).toString());
        verify(reporteService).cerrarPorLaComunidad(eq("r1"), contains("3 votos dicen que ya está arreglado y 1 que sigue igual"));
    }

    @Test
    void laTareaNoCierraSiAlVencerLaGraciaYaNoCumpleLaRegla() {
        Reporte reporte = enProceso(AHORA.minus(Duration.ofHours(80)), null);
        votos(votoReciente("autor", true), votoReciente("vecino", true), votoReciente("v2", false), votoReciente("v3", false));
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of(reporte));

        assertThat(service.aplicarCierresComunitarios(AHORA)).isZero();

        verify(reporteService, never()).cerrarPorLaComunidad(anyString(), anyString());
        Update update = unicaActualizacionDelReporte(ArgumentCaptor.forClass(Query.class));
        assertThat(update.getUpdateObject().get("$unset", Document.class)).containsKey("cierreComunitarioDesde");
    }

    @Test
    void siUnModeradorCambioElEstadoAntesLaTareaSigueConLosDemas() {
        Reporte reporte = enProceso(AHORA.minus(Duration.ofHours(73)), null);
        votos(votoReciente("autor", true), votoReciente("vecino", true));
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of(reporte));
        doThrow(new BusinessRuleException("El reporte cambió de estado")).when(reporteService).cerrarPorLaComunidad(anyString(), anyString());

        assertThat(service.aplicarCierresComunitarios(AHORA)).isZero();
    }

    @Test
    void mantenerAbiertoQuitaLaPropuestaYGuardaElMomentoDelVeto() {
        enProceso(AHORA, null);

        service.mantenerAbierto("r1");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        Update update = unicaActualizacionDelReporte(query);
        assertThat(query.getValue().getQueryObject()).containsEntry("estado", EstadoReporte.EN_PROCESO);
        assertThat(update.getUpdateObject().get("$unset", Document.class)).containsKey("cierreComunitarioDesde");
        assertThat(update.getUpdateObject().get("$set", Document.class)).containsKey("cierreComunitarioVetadoEn");
    }

    @Test
    void mantenerAbiertoUnReporteQueNoEstaEnProcesoFalla() {
        when(reporteRepository.findById("r1")).thenReturn(Optional.of(Reporte.builder().id("r1").estado(EstadoReporte.RESUELTO).build()));

        assertThatThrownBy(() -> service.mantenerAbierto("r1")).isInstanceOf(BusinessRuleException.class);
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Reporte.class));
    }

    private Usuario usuario(String id, boolean activo) {
        return Usuario.builder().id(id).nombre(id).email(id + "@correo.co").activo(activo).build();
    }
}
