package com.viactg.config;

import com.viactg.model.Categoria;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.Reporte;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import com.viactg.repository.CategoriaRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import com.viactg.service.AreaServicio;
import com.viactg.service.ReporteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatosDemoTests {
    private static final List<String> CATEGORIAS = List.of("Hueco", "Inundación", "Derrumbe");
    private static final AreaServicio AREA = AreaServicio.cartagena();

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ReporteRepository reporteRepository = mock(ReporteRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private DatosDemo datosDemo(boolean habilitado) {
        return new DatosDemo(mongoTemplate, reporteRepository, usuarioRepository, categoriaRepository, AREA, passwordEncoder,
                habilitado, "admin@viactg.co");
    }

    @BeforeEach
    void baseVacia() {
        when(reporteRepository.count()).thenReturn(0L);
        when(categoriaRepository.findByActivaTrue()).thenReturn(CATEGORIAS.stream()
                .map(nombre -> Categoria.builder().id("id-" + nombre).nombre(nombre).activa(true).build()).toList());
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("admin@viactg.co"))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").email("admin@viactg.co").rol(Rol.ADMIN).build()));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        // Como Mongo, insertAll asigna los id y devuelve lo insertado.
        when(mongoTemplate.insertAll(anyCollection())).thenAnswer(invocacion -> {
            Collection<?> documentos = invocacion.getArgument(0);
            documentos.forEach(documento -> {
                if (documento instanceof Usuario usuario) usuario.setId("u-" + usuario.getEmail());
            });
            return documentos;
        });
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, List<Object>> insertados() {
        ArgumentCaptor<Collection<Object>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(mongoTemplate, times(2)).insertAll(captor.capture());
        return captor.getAllValues().stream().collect(Collectors.toMap(lote -> lote.iterator().next().getClass(), List::copyOf));
    }

    private List<Reporte> reportesGenerados() {
        datosDemo(true).run();
        return insertados().get(Reporte.class).stream().map(Reporte.class::cast).toList();
    }

    @Test
    void conSeedDemoApagadoNoHaceNada() {
        datosDemo(false).run();

        verifyNoInteractions(mongoTemplate, reporteRepository, usuarioRepository, categoriaRepository, passwordEncoder);
    }

    @Test
    void conDatosExistentesNoDuplica() {
        when(reporteRepository.count()).thenReturn(20L);

        datosDemo(true).run();

        verify(mongoTemplate, never()).insertAll(any());
    }

    @Test
    void creaCincoCiudadanosVerificadosDelDominioDemo() {
        datosDemo(true).run();

        List<Usuario> usuarios = insertados().get(Usuario.class).stream().map(Usuario.class::cast).toList();
        assertThat(usuarios).hasSize(5).allSatisfy(usuario -> {
            assertThat(usuario.getEmail()).endsWith("@demo.viasctg.local");
            assertThat(usuario.isEmailVerificado()).isTrue();
            assertThat(usuario.getRol()).isEqualTo(Rol.CIUDADANO);
            assertThat(usuario.getPasswordHash()).isNotBlank();
        });
    }

    @Test
    void reutilizaLosCiudadanosDemoQueYaExisten() {
        when(usuarioRepository.findByEmail(anyString())).thenAnswer(invocacion -> Optional.of(
                Usuario.builder().id("existente").email(invocacion.getArgument(0)).rol(Rol.CIUDADANO).build()));

        datosDemo(true).run();

        ArgumentCaptor<Collection<Object>> captor = captorReportes();
        verify(mongoTemplate).insertAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(reporte -> assertThat(reporte).isInstanceOf(Reporte.class));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Collection<Object>> captorReportes() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    @Test
    void generaDoscientosReportesMarcadosConElRepartoDeEstadosPedido() {
        List<Reporte> reportes = reportesGenerados();

        assertThat(reportes).hasSize(200).allSatisfy(reporte -> assertThat(reporte.getDescripcion()).startsWith("[DEMO] "));
        Map<EstadoReporte, Long> porEstado = reportes.stream().collect(Collectors.groupingBy(Reporte::getEstado, Collectors.counting()));
        assertThat(porEstado).containsExactlyInAnyOrderEntriesOf(Map.of(EstadoReporte.PENDIENTE, 50L, EstadoReporte.EN_REVISION, 30L,
                EstadoReporte.EN_PROCESO, 40L, EstadoReporte.RESUELTO, 60L, EstadoReporte.RECHAZADO, 20L));
    }

    @Test
    void losReportesEstanDentroDelAreaYSuUbicacionEsLngLat() {
        assertThat(reportesGenerados()).allSatisfy(reporte -> {
            assertThat(AREA.contiene(reporte.getLatitud(), reporte.getLongitud())).isTrue();
            assertThat(reporte.getUbicacion().getX()).isEqualTo(reporte.getLongitud());
            assertThat(reporte.getUbicacion().getY()).isEqualTo(reporte.getLatitud());
            assertThat(reporte.getBarrioOsm()).isNotBlank();
        });
    }

    @Test
    void todasLasCategoriasExistenEnElCatalogo() {
        assertThat(reportesGenerados()).extracting(Reporte::getCategoriaId).isSubsetOf(CATEGORIAS);
    }

    @Test
    void losHistorialesSoloTienenTransicionesValidasConFechasCrecientes() {
        Instant ahora = Instant.now();

        assertThat(reportesGenerados()).allSatisfy(reporte -> {
            List<HistorialEstado> historial = reporte.getHistorialEstados();
            assertThat(reporte.getFechaCreacion()).isBetween(ahora.minus(Duration.ofDays(365)), ahora);
            EstadoReporte estado = EstadoReporte.PENDIENTE;
            Instant anterior = reporte.getFechaCreacion();
            for (HistorialEstado cambio : historial) {
                assertThat(cambio.getEstadoAnterior()).isEqualTo(estado);
                assertThat(ReporteService.esTransicionValida(cambio.getEstadoAnterior(), cambio.getEstadoNuevo())).isTrue();
                assertThat(cambio.getFecha()).isAfter(anterior).isBefore(ahora.plusSeconds(1));
                assertThat(cambio.getUsuarioAdminId()).isEqualTo("admin-1");
                estado = cambio.getEstadoNuevo();
                anterior = cambio.getFecha();
            }
            assertThat(estado).isEqualTo(reporte.getEstado());
            assertThat(reporte.getFechaActualizacion()).isEqualTo(anterior);
        });
    }

    @Test
    void soloUnModeradorFijaLaPrioridadDeLosPendientes() {
        Map<EstadoReporte, List<Reporte>> porEstado = reportesGenerados().stream().collect(Collectors.groupingBy(Reporte::getEstado));

        assertThat(porEstado.get(EstadoReporte.PENDIENTE)).allSatisfy(reporte -> assertThat(reporte.getHistorialEstados()).isEmpty())
                .extracting(reporte -> reporte.getPrioridad().name()).containsOnly("MEDIA");
        assertThat(porEstado.get(EstadoReporte.RESUELTO).stream().map(Reporte::getPrioridad).collect(Collectors.toSet())).hasSize(3);
    }

    @Test
    void losReportesSeConcentranEnVariosBarrios() {
        Map<String, Long> porBarrio = reportesGenerados().stream()
                .collect(Collectors.groupingBy(Reporte::getBarrioOsm, Collectors.counting()));

        assertThat(porBarrio.keySet()).containsExactlyInAnyOrderElementsOf(DatosDemo.ZONAS.stream().map(DatosDemo.Zona::barrio).toList());
        assertThat(porBarrio.values().stream().max(Long::compare).orElseThrow()).isGreaterThan(20);
    }
}
