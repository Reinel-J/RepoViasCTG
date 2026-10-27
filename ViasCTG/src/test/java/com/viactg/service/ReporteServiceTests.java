package com.viactg.service;

import com.viactg.dto.ReporteActualizarRequest;
import com.viactg.dto.ReporteCrearRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.TooManyRequestsException;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.Reporte;
import com.viactg.repository.ConfirmacionRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import com.viactg.security.LimitadorIntentos;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReporteServiceTests {
    private final ReporteRepository reporteRepository = mock(ReporteRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final GeocodingService geocodingService = mock(GeocodingService.class);
    private final NotificacionService notificacionService = mock(NotificacionService.class);
    private final CategoriaService categoriaService = mock(CategoriaService.class);
    private final ConfirmacionRepository confirmacionRepository = mock(ConfirmacionRepository.class);

    private ReporteService crearServicio() {
        return crearServicio("/tmp/viasctg-test-uploads");
    }

    private ReporteService crearServicio(String uploads) {
        return new ReporteService(reporteRepository, usuarioRepository, usuarioService, mongoTemplate, geocodingService,
                notificacionService, categoriaService, confirmacionRepository, new LimitadorIntentos(),
                AreaServicio.cartagena(), uploads);
    }

    private static Reporte pendiente(String id, String autor) {
        return Reporte.builder().id(id).usuarioId(autor).descripcion("Hueco grande frente al colegio")
                .latitud(10.391).longitud(-75.479).estado(EstadoReporte.PENDIENTE)
                .fechaCreacion(Instant.now()).fechaActualizacion(Instant.now()).build();
    }

    @Test
    void listarCercanosConstruyeLaConsultaGeoespacialYDevuelveElResultadoDeMongo() {
        Reporte esperado = Reporte.builder().id("reporte-1").build();
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of(esperado));

        List<Reporte> resultado = crearServicio().listarCercanos(10.391, -75.479, 2, EstadoReporte.PENDIENTE);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Reporte.class));
        Document consulta = queryCaptor.getValue().getQueryObject();
        assertThat(resultado).containsExactly(esperado);
        assertThat(consulta.toString()).contains("$nearSphere", "$maxDistance", "-75.479", "10.391", "PENDIENTE");
        assertThat(queryCaptor.getValue().getLimit()).isEqualTo(ReporteService.MAX_RESULTADOS_CERCANOS);
    }

    @Test
    void listarCercanosRechazaRadiosNoPositivosOMayoresAlMaximo() {
        ReporteService service = crearServicio();

        assertThatThrownBy(() -> service.listarCercanos(10.391, -75.479, 0, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("El radio de búsqueda debe ser mayor a cero");
        assertThatThrownBy(() -> service.listarCercanos(10.391, -75.479, -1, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("El radio de búsqueda debe ser mayor a cero");
        assertThatThrownBy(() -> service.listarCercanos(10.391, -75.479, 51, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("50 km");
    }

    @Test
    void listarOrdenaDelMasRecienteAlMasAntiguoYAplicaElLimite() {
        crearServicio().listar(EstadoReporte.RESUELTO, 3);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Reporte.class));
        Query consulta = queryCaptor.getValue();
        assertThat(consulta.getSortObject()).isEqualTo(new Document("fechaCreacion", -1));
        assertThat(consulta.getQueryObject().get("estado")).isEqualTo(EstadoReporte.RESUELTO);
        assertThat(consulta.getLimit()).isEqualTo(3);
    }

    @Test
    void listarDeUsuarioFiltraPorAutorYEstadoDelMasRecienteAlMasAntiguo() {
        Reporte propio = pendiente("r-1", "u-1");
        when(mongoTemplate.find(any(Query.class), eq(Reporte.class))).thenReturn(List.of(propio));

        List<Reporte> resultado = crearServicio().listarDeUsuario("u-1", EstadoReporte.PENDIENTE);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Reporte.class));
        Query consulta = queryCaptor.getValue();
        assertThat(resultado).containsExactly(propio);
        assertThat(consulta.getQueryObject().get("usuarioId")).isEqualTo("u-1");
        assertThat(consulta.getQueryObject().get("estado")).isEqualTo(EstadoReporte.PENDIENTE);
        assertThat(consulta.getSortObject()).isEqualTo(new Document("fechaCreacion", -1));
    }

    @Test
    void listarDeUsuarioSinEstadoNoFiltraPorEstado() {
        crearServicio().listarDeUsuario("u-1", null);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Reporte.class));
        assertThat(queryCaptor.getValue().getQueryObject()).containsOnlyKeys("usuarioId");
    }

    @Test
    void crearNormalizaLaCategoriaYGuardaElReportePendienteConPrioridadMedia() {
        when(usuarioRepository.existsById("u-1")).thenReturn(true);
        when(categoriaService.normalizarNombre(" inundacion ")).thenReturn("Inundación");
        when(reporteRepository.save(any(Reporte.class))).thenAnswer(invocacion -> {
            Reporte reporte = invocacion.getArgument(0);
            reporte.setId("r-1");
            return reporte;
        });

        Reporte creado = crearServicio().crear(new ReporteCrearRequest(" inundacion ", "  Calle inundada tras la lluvia  ",
                10.40, -75.50), "u-1");

        assertThat(creado.getCategoriaId()).isEqualTo("Inundación");
        assertThat(creado.getDescripcion()).isEqualTo("Calle inundada tras la lluvia");
        assertThat(creado.getEstado()).isEqualTo(EstadoReporte.PENDIENTE);
        assertThat(creado.getPrioridad()).isEqualTo(com.viactg.model.PrioridadReporte.MEDIA);
        assertThat(creado.getUbicacion().getX()).isEqualTo(-75.50);
        assertThat(creado.getUbicacion().getY()).isEqualTo(10.40);
    }

    @Test
    void crearRechazaUbicacionesFueraDeCartagena() {
        when(usuarioRepository.existsById("u-1")).thenReturn(true);

        assertThatThrownBy(() -> crearServicio().crear(new ReporteCrearRequest("Hueco", "Hueco en la vía principal", 4.711, -74.072), "u-1"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("fuera del área de servicio");
        verify(reporteRepository, never()).save(any());
    }

    @Test
    void crearLimitaLaCantidadDeReportesPorUsuarioYHora() {
        when(usuarioRepository.existsById("u-1")).thenReturn(true);
        when(categoriaService.normalizarNombre(anyString())).thenReturn("Hueco");
        when(reporteRepository.save(any(Reporte.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        ReporteService service = crearServicio();
        ReporteCrearRequest request = new ReporteCrearRequest("Hueco", "Hueco en la vía principal", 10.40, -75.50);

        for (int i = 0; i < ReporteService.MAX_REPORTES_POR_HORA; i++) service.crear(request, "u-1");

        assertThatThrownBy(() -> service.crear(request, "u-1")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void actualizarConLasMismasCoordenadasNoBorraLaDireccionNiVuelveAGeocodificar() {
        // Instancias distintas de Double con el mismo valor: antes se comparaban con != y siempre "cambiaban".
        Reporte reporte = pendiente("r-1", "u-1");
        reporte.setLatitud(Double.valueOf(10.391));
        reporte.setLongitud(Double.valueOf(-75.479));
        when(reporteRepository.findById("r-1")).thenReturn(Optional.of(reporte));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(reporte);

        crearServicio().actualizar("r-1", new ReporteActualizarRequest("Nueva descripción del hueco",
                Double.valueOf(10.391), Double.valueOf(-75.479)), "u-1");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(Reporte.class));
        Document cambios = update.getValue().getUpdateObject();
        assertThat(cambios.get("$set", Document.class)).containsKey("descripcion").doesNotContainKey("ubicacion");
        assertThat(cambios).doesNotContainKey("$unset");
        // La edición solo aplica si el reporte sigue pendiente: no pisa un cambio de estado simultáneo.
        assertThat(query.getValue().getQueryObject().get("estado")).isEqualTo(EstadoReporte.PENDIENTE);
    }

    @Test
    void actualizarConOtraUbicacionLimpiaLaDireccionParaResolverlaDeNuevo() {
        when(reporteRepository.findById("r-1")).thenReturn(Optional.of(pendiente("r-1", "u-1")));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(pendiente("r-1", "u-1"));

        crearServicio().actualizar("r-1", new ReporteActualizarRequest("Nueva descripción del hueco", 10.400, -75.500), "u-1");

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Reporte.class));
        Document cambios = update.getValue().getUpdateObject();
        assertThat(cambios.get("$set", Document.class)).containsKeys("latitud", "longitud", "ubicacion");
        assertThat(cambios.get("$unset", Document.class)).containsKeys("direccionOsm", "barrioOsm");
    }

    @Test
    void actualizarFallaSiUnModeradorCambioElEstadoMientrasTanto() {
        when(reporteRepository.findById("r-1")).thenReturn(Optional.of(pendiente("r-1", "u-1")));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> crearServicio().actualizar("r-1",
                new ReporteActualizarRequest("Nueva descripción del hueco", 10.391, -75.479), "u-1"))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Solo se pueden editar reportes pendientes");
    }

    @Test
    void subirFotosAgregaLasFotosSoloSiQuedaCupoDeFormaAtomica(@TempDir Path uploads) {
        Reporte reporte = pendiente("r-1", "u-1");
        reporte.setFotos(new ArrayList<>(List.of("/uploads/existente.jpg")));
        when(reporteRepository.findById("r-1")).thenReturn(Optional.of(reporte));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(reporte);

        crearServicio(uploads.toString()).subirFotos("r-1", List.of(jpeg("calle.jpg")), "u-1");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(Reporte.class));
        // Con 1 foto nueva, el arreglo debe tener como máximo 2: la posición 2 no puede existir.
        assertThat(query.getValue().getQueryObject().get("fotos.2")).isEqualTo(new Document("$exists", false));
        assertThat(update.getValue().getUpdateObject()).containsKey("$push");
        assertThat(archivosEn(uploads)).hasSize(1);
    }

    @Test
    void subirFotosBorraLosArchivosSiOtraSubidaLlenoElCupo(@TempDir Path uploads) {
        when(reporteRepository.findById("r-1")).thenReturn(Optional.of(pendiente("r-1", "u-1")));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> crearServicio(uploads.toString()).subirFotos("r-1", List.of(jpeg("a.jpg"), jpeg("b.jpg")), "u-1"))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Un reporte puede tener como máximo 3 fotos");
        assertThat(archivosEn(uploads)).isEmpty();
    }

    @Test
    void cambiarEstadoInsertaHistorialEnLaActualizacionAtomica() {
        Reporte reporte = Reporte.builder().id("reporte-1").estado(EstadoReporte.PENDIENTE)
                .fechaCreacion(Instant.now()).fechaActualizacion(Instant.now()).build();
        Reporte actualizado = Reporte.builder().id("reporte-1").estado(EstadoReporte.EN_REVISION)
                .historialEstados(List.of()).build();
        when(usuarioService.esAdministradorOModerador("admin-1")).thenReturn(true);
        when(reporteRepository.findById("reporte-1")).thenReturn(Optional.of(reporte));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(actualizado);

        Reporte resultado = crearServicio().cambiarEstadoReporte("reporte-1", EstadoReporte.EN_REVISION, "admin-1", "En revisión", null);

        assertThat(resultado.getEstado()).isEqualTo(EstadoReporte.EN_REVISION);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Reporte.class));
        assertThat(update.getValue().getUpdateObject()).containsKey("$push");
    }

    @Test
    void cambiarEstadoNotificaAlAutorDelReporte() {
        Reporte reporte = Reporte.builder().id("reporte-1").usuarioId("autor-1").estado(EstadoReporte.PENDIENTE).build();
        Reporte actualizado = Reporte.builder().id("reporte-1").usuarioId("autor-1").descripcion("Hueco grande")
                .estado(EstadoReporte.EN_REVISION).build();
        when(usuarioService.esAdministradorOModerador("admin-1")).thenReturn(true);
        when(reporteRepository.findById("reporte-1")).thenReturn(Optional.of(reporte));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(actualizado);

        crearServicio().cambiarEstadoReporte("reporte-1", EstadoReporte.EN_REVISION, "admin-1", null, null);

        verify(notificacionService).crear("autor-1", "reporte-1", "Tu reporte \"Hueco grande\" cambió a en revisión");
        verify(confirmacionRepository, never()).findByReporteId(anyString());
    }

    @Test
    void cerrarPorLaComunidadResuelveDesdeEnProcesoSinModeradorYNotifica() {
        Reporte reporte = Reporte.builder().id("reporte-1").usuarioId("autor-1").estado(EstadoReporte.EN_PROCESO).build();
        Reporte actualizado = Reporte.builder().id("reporte-1").usuarioId("autor-1").descripcion("Hueco grande")
                .estado(EstadoReporte.RESUELTO).build();
        when(reporteRepository.findById("reporte-1")).thenReturn(Optional.of(reporte));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(actualizado);
        when(confirmacionRepository.findByReporteId("reporte-1")).thenReturn(List.of(
                Confirmacion.builder().reporteId("reporte-1").usuarioId("vecina-1").build()));

        crearServicio().cerrarPorLaComunidad("reporte-1", "Resuelto por la comunidad: 3 voto(s)");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(Reporte.class));
        // Solo si sigue en proceso: si un moderador lo cambió a la vez, su decisión prevalece.
        assertThat(query.getValue().getQueryObject()).containsEntry("estado", EstadoReporte.EN_PROCESO);
        Document cambios = update.getValue().getUpdateObject();
        assertThat(cambios.get("$set", Document.class)).containsEntry("estado", EstadoReporte.RESUELTO);
        assertThat(cambios.get("$unset", Document.class)).containsKey("cierreComunitarioDesde");
        HistorialEstado historial = (HistorialEstado) cambios.get("$push", Document.class).get("historialEstados");
        assertThat(historial.getUsuarioAdminId()).isNull();
        assertThat(historial.getComentarioAdmin()).isEqualTo("Resuelto por la comunidad: 3 voto(s)");
        verify(usuarioService, never()).esAdministradorOModerador(any());
        verify(notificacionService).crear("autor-1", "reporte-1", "Tu reporte \"Hueco grande\" cambió a resuelto");
        verify(notificacionService).crear(eq("vecina-1"), eq("reporte-1"), anyString());
    }

    @Test
    void soloUnReporteEnProcesoPuedeCerrarsePorLaComunidad() {
        when(reporteRepository.findById("reporte-1")).thenReturn(Optional.of(
                Reporte.builder().id("reporte-1").estado(EstadoReporte.EN_REVISION).build()));

        assertThatThrownBy(() -> crearServicio().cerrarPorLaComunidad("reporte-1", "x")).isInstanceOf(BusinessRuleException.class);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class));
    }

    @Test
    void alResolverseTambienSeNotificaAQuienesConfirmaron() {
        Reporte reporte = Reporte.builder().id("reporte-1").usuarioId("autor-1").estado(EstadoReporte.EN_PROCESO).build();
        Reporte actualizado = Reporte.builder().id("reporte-1").usuarioId("autor-1").descripcion("Hueco grande")
                .estado(EstadoReporte.RESUELTO).build();
        when(usuarioService.esAdministradorOModerador("admin-1")).thenReturn(true);
        when(reporteRepository.findById("reporte-1")).thenReturn(Optional.of(reporte));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Reporte.class)))
                .thenReturn(actualizado);
        when(confirmacionRepository.findByReporteId("reporte-1")).thenReturn(List.of(
                Confirmacion.builder().reporteId("reporte-1").usuarioId("vecina-1").build(),
                Confirmacion.builder().reporteId("reporte-1").usuarioId("vecino-2").build()));

        crearServicio().cambiarEstadoReporte("reporte-1", EstadoReporte.RESUELTO, "admin-1", null, null);

        verify(notificacionService).crear("autor-1", "reporte-1", "Tu reporte \"Hueco grande\" cambió a resuelto");
        verify(notificacionService).crear("vecina-1", "reporte-1", "El daño que confirmaste (\"Hueco grande\") quedó resuelto");
        verify(notificacionService).crear("vecino-2", "reporte-1", "El daño que confirmaste (\"Hueco grande\") quedó resuelto");
    }

    private static MultipartFile jpeg(String nombre) {
        byte[] contenido = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
        return new MockMultipartFile("archivos", nombre, "image/jpeg", contenido);
    }

    private static List<Path> archivosEn(Path carpeta) {
        try (Stream<Path> archivos = Files.list(carpeta)) {
            return archivos.toList();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
