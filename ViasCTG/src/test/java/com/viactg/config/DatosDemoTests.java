package com.viactg.config;

import com.viactg.model.Categoria;
import com.viactg.model.Comentario;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.Reporte;
import com.viactg.model.Rol;
import com.viactg.model.Seguimiento;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatosDemoTests {
    private static final List<String> CATEGORIAS = List.of("Hueco", "Inundación", "Derrumbe");
    private static final AreaServicio AREA = AreaServicio.cartagena();
    private static final Set<String> ATIENDEN = Set.of("admin-1", "u-moderador1@demo.viasctg.local", "u-moderador2@demo.viasctg.local",
            "u-moderador3@demo.viasctg.local");

    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ReporteRepository reporteRepository = mock(ReporteRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final org.springframework.context.ApplicationEventPublisher eventos = mock(org.springframework.context.ApplicationEventPublisher.class);

    private DatosDemo datosDemo(boolean habilitado) {
        return datosDemo(habilitado, 200);
    }

    private DatosDemo datosDemo(boolean habilitado, int reportes) {
        return new DatosDemo(mongoTemplate, reporteRepository, usuarioRepository, categoriaRepository, AREA, passwordEncoder,
                habilitado, reportes, "admin@viactg.co", eventos);
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
                if (documento instanceof Reporte reporte) reporte.setId("r-" + contador++);
            });
            return documentos;
        });
    }

    private int contador;

    // Todo lo insertado, agrupado por tipo de documento (se inserta por bloques, en varias llamadas por tipo).
    @SuppressWarnings("unchecked")
    private Map<Class<?>, List<Object>> insertados() {
        ArgumentCaptor<Collection<Object>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(mongoTemplate, atLeastOnce()).insertAll(captor.capture());
        return captor.getAllValues().stream().flatMap(Collection::stream).collect(Collectors.groupingBy(Object::getClass));
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> insertados(Class<T> tipo) {
        return (List<T>) (List<?>) insertados().getOrDefault(tipo, List.of());
    }

    private List<Reporte> reportesGenerados() {
        datosDemo(true).run();
        return insertados().get(Reporte.class).stream().map(Reporte.class::cast).toList();
    }

    @Test
    void conSeedDemoApagadoNoHaceNada() {
        datosDemo(false).run();

        verifyNoInteractions(mongoTemplate, reporteRepository, usuarioRepository, categoriaRepository, passwordEncoder, eventos);
    }

    @Test
    void conDatosExistentesNoDuplica() {
        when(reporteRepository.count()).thenReturn(20L);

        datosDemo(true).run();

        verify(mongoTemplate, never()).insertAll(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void trasInsertarPideReconstruirLaReplicaSqlCompleta() {
        reportesGenerados();

        verify(eventos).publishEvent(com.viactg.service.CambioReplicable.todo());
    }

    @Test
    void creaCincoCiudadanosYTresModeradoresVerificadosDelDominioDemo() {
        datosDemo(true).run();

        List<Usuario> usuarios = insertados(Usuario.class);
        assertThat(usuarios).hasSize(8).allSatisfy(usuario -> {
            assertThat(usuario.getEmail()).endsWith("@demo.viasctg.local");
            assertThat(usuario.isEmailVerificado()).isTrue();
            assertThat(usuario.getPasswordHash()).isNotBlank();
        });
        assertThat(usuarios).filteredOn(usuario -> usuario.getRol() == Rol.CIUDADANO).hasSize(5);
        assertThat(usuarios).filteredOn(usuario -> usuario.getRol() == Rol.MODERADOR).extracting(Usuario::getEmail)
                .containsExactly("moderador1@demo.viasctg.local", "moderador2@demo.viasctg.local", "moderador3@demo.viasctg.local");
        // Un solo hash para todas las cuentas: BCrypt es lento y con miles de cuentas tardaría minutos.
        verify(passwordEncoder).encode(anyString());
    }

    @Test
    void conMasReportesHayUnCiudadanoPorCadaCuarenta() {
        assertThat(DatosDemo.ciudadanosPara(200)).isEqualTo(5);
        assertThat(DatosDemo.ciudadanosPara(10_000)).isEqualTo(250);
    }

    @Test
    void reutilizaLosCiudadanosDemoQueYaExisten() {
        when(usuarioRepository.findByEmail(anyString())).thenAnswer(invocacion -> Optional.of(
                Usuario.builder().id("existente").email(invocacion.getArgument(0)).rol(Rol.CIUDADANO).build()));

        datosDemo(true).run();

        assertThat(insertados(Usuario.class)).isEmpty();
        assertThat(insertados(Reporte.class)).hasSize(200);
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
        List<Reporte> reportes = reportesGenerados();
        assertThat(reportes).extracting(reporte -> reporte.getCategoria().getNombre()).isSubsetOf(CATEGORIAS);
        assertThat(reportes).allSatisfy(reporte -> assertThat(reporte.getCategoria().getId()).isEqualTo("id-" + reporte.getCategoria().getNombre()));
    }

    @Test
    void laCategoriaOtroNoSeUsaEnLaDemo() {
        when(categoriaRepository.findByActivaTrue()).thenReturn(List.of(
                Categoria.builder().id("id-Hueco").nombre("Hueco").activa(true).build(),
                Categoria.builder().id("id-Otro").nombre("Otro").activa(true).build()));

        assertThat(reportesGenerados()).extracting(reporte -> reporte.getCategoria().getNombre()).containsOnly("Hueco");
    }

    @Test
    void losHistorialesSoloTienenTransicionesValidasConFechasCrecientes() {
        Instant ahora = Instant.now();

        assertThat(reportesGenerados()).allSatisfy(reporte -> {
            List<HistorialEstado> historial = reporte.getHistorialEstados();
            // Dentro de los 12 meses de la gráfica mensual de Estadísticas y del dashboard.
            assertThat(reporte.getFechaCreacion()).isBetween(DatosDemo.inicioVentana(ahora), ahora);
            EstadoReporte estado = EstadoReporte.PENDIENTE;
            Instant anterior = reporte.getFechaCreacion();
            for (HistorialEstado cambio : historial) {
                assertThat(cambio.getEstadoAnterior()).isEqualTo(estado);
                assertThat(ReporteService.esTransicionValida(cambio.getEstadoAnterior(), cambio.getEstadoNuevo())).isTrue();
                assertThat(cambio.getFecha()).isAfter(anterior).isBefore(ahora.plusSeconds(1));
                // Lo atiende el administrador o uno de los moderadores demo, el mismo en todo el historial.
                assertThat(cambio.getUsuarioAdminId()).isIn(ATIENDEN).isEqualTo(historial.get(0).getUsuarioAdminId());
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
    void losReportesCubrenDesdeElPrimerMesDeLaVentana() {
        Instant inicio = DatosDemo.inicioVentana(Instant.now());

        assertThat(reportesGenerados()).extracting(Reporte::getFechaCreacion)
                .anySatisfy(fecha -> assertThat(fecha).isBefore(inicio.plus(Duration.ofDays(31))));
    }

    @Test
    void losReportesSeConcentranEnVariosBarrios() {
        Map<String, Long> porBarrio = reportesGenerados().stream()
                .collect(Collectors.groupingBy(Reporte::getBarrioOsm, Collectors.counting()));

        assertThat(porBarrio.keySet()).containsExactlyInAnyOrderElementsOf(DatosDemo.ZONAS.stream().map(DatosDemo.Zona::barrio).toList());
        assertThat(porBarrio.values().stream().max(Long::compare).orElseThrow()).isGreaterThan(20);
    }

    // Los puntos de calle se comprobaron en tierra: un reporte lejos de ellos podría caer en el mar o la ciénaga.
    @Test
    void cadaReporteQuedaJuntoAUnaCalleDeSuBarrio() {
        Map<String, DatosDemo.Zona> zonas = DatosDemo.ZONAS.stream().collect(Collectors.toMap(DatosDemo.Zona::barrio, zona -> zona));

        assertThat(reportesGenerados()).allSatisfy(reporte -> {
            assertThat(reporte.getUbicacion().getX()).isEqualTo(reporte.getLongitud());
            assertThat(reporte.getUbicacion().getY()).isEqualTo(reporte.getLatitud());
            assertThat(Arrays.stream(zonas.get(reporte.getBarrioOsm()).calles()))
                    .as("%s (%s, %s)", reporte.getBarrioOsm(), reporte.getLatitud(), reporte.getLongitud())
                    .anyMatch(calle -> Math.abs(reporte.getLatitud() - calle[0]) <= DatosDemo.DISPERSION
                            && Math.abs(reporte.getLongitud() - calle[1]) <= DatosDemo.DISPERSION);
        });
    }
    @Test
    void laParticipacionRespetaLasReglasDeLaApi() {
        datosDemo(true).run();
        Map<String, Reporte> reportes = insertados(Reporte.class).stream().collect(Collectors.toMap(Reporte::getId, reporte -> reporte));
        Instant ahora = Instant.now().plusSeconds(1);

        List<Comentario> comentarios = insertados(Comentario.class);
        List<Confirmacion> confirmaciones = insertados(Confirmacion.class);
        List<Seguimiento> votos = insertados(Seguimiento.class);
        assertThat(comentarios).isNotEmpty().allSatisfy(comentario -> {
            Reporte reporte = reportes.get(comentario.getReporteId());
            assertThat(comentario.getUsuarioId()).isNotEqualTo(reporte.getUsuarioId());
            assertThat(comentario.getFecha()).isAfter(reporte.getFechaCreacion()).isBefore(ahora);
        });
        // Nadie confirma su propio reporte ni lo confirma dos veces; tampoco después de cerrado.
        assertThat(confirmaciones).isNotEmpty().allSatisfy(confirmacion -> {
            Reporte reporte = reportes.get(confirmacion.getReporteId());
            assertThat(confirmacion.getUsuarioId()).isNotEqualTo(reporte.getUsuarioId());
            assertThat(confirmacion.getFecha()).isAfter(reporte.getFechaCreacion());
            if (!reporte.getEstado().esActivo()) assertThat(confirmacion.getFecha()).isBeforeOrEqualTo(reporte.getFechaActualizacion());
        });
        assertThat(confirmaciones.stream().map(c -> c.getReporteId() + "|" + c.getUsuarioId()).distinct()).hasSameSizeAs(confirmaciones);
        // Los votos solo existen en reportes activos y hay uno por persona.
        assertThat(votos).isNotEmpty().allSatisfy(voto -> assertThat(reportes.get(voto.getReporteId()).getEstado().esActivo()).isTrue());
        assertThat(votos.stream().map(v -> v.getReporteId() + "|" + v.getUsuarioId()).distinct()).hasSameSizeAs(votos);
    }

    @Test
    void conDiezMilReportesLaBaseSuperaLosDiecinueveMilDocumentosInsertandoPorBloques() {
        datosDemo(true, 10_000).run();

        Map<Class<?>, List<Object>> insertados = insertados();
        int total = insertados.values().stream().mapToInt(List::size).sum();
        assertThat(insertados.get(Reporte.class)).hasSize(10_000);
        assertThat(insertados.get(Usuario.class)).hasSize(253);
        assertThat(total).isGreaterThan(19_000);
        ArgumentCaptor<Collection<Object>> captor = captorReportes();
        verify(mongoTemplate, atLeastOnce()).insertAll(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(lote -> assertThat(lote).hasSizeLessThanOrEqualTo(DatosDemo.TAMANO_LOTE));
        verify(eventos).publishEvent(com.viactg.service.CambioReplicable.todo());
    }

    @Test
    void conUnTotalQueNoEsMultiploDeCienNoPierdeReportes() {
        datosDemo(true, 1_234).run();

        assertThat(insertados(Reporte.class)).hasSize(1_234);
    }
}
