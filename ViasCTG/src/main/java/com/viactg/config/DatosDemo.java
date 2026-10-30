package com.viactg.config;

import com.viactg.model.Categoria;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.PrioridadReporte;
import com.viactg.model.Reporte;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import com.viactg.repository.CategoriaRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import com.viactg.service.AreaServicio;
import com.viactg.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Datos de demostración para el dashboard de Metabase (SEED_DEMO=true): unos 200 reportes de los últimos 12 meses y 5
 * ciudadanos, solo si hay menos de 20 reportes. Se insertan en bloque, sin geocodificar ni notificar. Se reconocen por
 * la descripción que empieza con "[DEMO]" y los correos @demo.viasctg.local (el README explica cómo borrarlos).
 * Corre al final, después de las categorías iniciales y del administrador inicial.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DatosDemo implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);
    static final String MARCA = "[DEMO]";
    static final String DOMINIO = "@demo.viasctg.local";
    static final int TOTAL_REPORTES = 200;
    static final int UMBRAL_REPORTES = 20;
    // Los reportes caen en los mismos 12 meses de la gráfica mensual (EstadisticasService): desde el día 1 del mes de
    // hace 11 meses, hora de Colombia.
    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final int MESES = 12;
    private static final int CIUDADANOS = 5;
    private static final List<EstadoReporte> FLUJO = List.of(EstadoReporte.PENDIENTE, EstadoReporte.EN_REVISION,
            EstadoReporte.EN_PROCESO, EstadoReporte.RESUELTO);
    // Reparto pedido: 25 % pendientes, 15 % en revisión, 20 % en proceso, 30 % resueltos y 10 % rechazados.
    // EnumMap: orden fijo, para que con la misma semilla salgan siempre los mismos datos.
    private static final Map<EstadoReporte, Integer> PORCENTAJES = new EnumMap<>(Map.of(EstadoReporte.PENDIENTE, 25,
            EstadoReporte.EN_REVISION, 15, EstadoReporte.EN_PROCESO, 20, EstadoReporte.RESUELTO, 30, EstadoReporte.RECHAZADO, 10));

    /** Barrios reales donde se concentran los daños, para que el mapa de calor tenga zonas; el peso es su proporción. */
    record Zona(String barrio, double latitud, double longitud, int peso) {
    }

    static final List<Zona> ZONAS = List.of(
            new Zona("Centro Histórico", 10.4236, -75.5505, 14),
            new Zona("Getsemaní", 10.4205, -75.5465, 8),
            new Zona("Manga", 10.4125, -75.5375, 10),
            new Zona("Bocagrande", 10.3985, -75.5560, 9),
            new Zona("El Bosque", 10.3980, -75.5180, 12),
            new Zona("Olaya Herrera", 10.4165, -75.4925, 16),
            new Zona("El Pozón", 10.4085, -75.4660, 15),
            new Zona("Nelson Mandela", 10.3725, -75.4835, 11),
            new Zona("Crespo", 10.4440, -75.5145, 5));

    private static final List<String> PLANTILLAS = List.of(
            "%s en %s: afecta el paso de carros y motos.",
            "%s en %s, lleva varias semanas sin atención.",
            "%s sobre la vía principal de %s; de noche casi no se ve.",
            "%s en %s, frente a un colegio.",
            "%s en %s: cuando llueve empeora.");
    private static final List<String> MOTIVOS_RECHAZO = List.of(
            "Reporte duplicado de otro caso abierto.",
            "No corresponde a la vía pública.",
            "No se encontró el daño en la ubicación indicada.");

    private final MongoTemplate mongoTemplate;
    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final AreaServicio areaServicio;
    private final PasswordEncoder passwordEncoder;
    private final boolean habilitado;
    private final String adminEmail;

    public DatosDemo(MongoTemplate mongoTemplate, ReporteRepository reporteRepository, UsuarioRepository usuarioRepository,
                     CategoriaRepository categoriaRepository, AreaServicio areaServicio, PasswordEncoder passwordEncoder,
                     @Value("${app.seed.demo:false}") boolean habilitado, @Value("${app.admin.email:}") String adminEmail) {
        this.mongoTemplate = mongoTemplate;
        this.reporteRepository = reporteRepository;
        this.usuarioRepository = usuarioRepository;
        this.categoriaRepository = categoriaRepository;
        this.areaServicio = areaServicio;
        this.passwordEncoder = passwordEncoder;
        this.habilitado = habilitado;
        this.adminEmail = adminEmail;
    }

    @Override
    public void run(String... args) {
        if (!habilitado) return;
        long existentes = reporteRepository.count();
        if (existentes >= UMBRAL_REPORTES) {
            log.info("SEED_DEMO: ya hay {} reportes; no se crean datos de demostración", existentes);
            return;
        }
        List<String> categorias = categoriaRepository.findByActivaTrue().stream().map(Categoria::getNombre).toList();
        if (categorias.isEmpty()) {
            log.warn("SEED_DEMO: no hay categorías activas; no se crean datos de demostración");
            return;
        }
        Instant ahora = Instant.now();
        Random random = new Random(2026);
        List<String> ciudadanos = asegurarCiudadanos(ahora);
        String adminId = adminEmail.isBlank() ? null : usuarioRepository.findByEmail(UsuarioService.normalizarEmail(adminEmail))
                .filter(usuario -> usuario.getRol() == Rol.ADMIN).map(Usuario::getId).orElse(null);
        List<Reporte> reportes = generarReportes(random, ahora, categorias, ciudadanos, adminId);
        mongoTemplate.insertAll(reportes);
        log.info("SEED_DEMO: se crearon {} reportes de demostración", reportes.size());
    }

    /** Los ciudadanos demo que falten; nadie conoce su contraseña (aleatoria), así que no se puede iniciar sesión con ellos. */
    private List<String> asegurarCiudadanos(Instant ahora) {
        List<String> ids = new ArrayList<>();
        List<Usuario> nuevos = new ArrayList<>();
        for (int numero = 1; numero <= CIUDADANOS; numero++) {
            String email = "ciudadano" + numero + DOMINIO;
            String nombre = "Ciudadano demo " + numero;
            usuarioRepository.findByEmail(email).ifPresentOrElse(existente -> ids.add(existente.getId()), () -> nuevos.add(
                    Usuario.builder().nombre(nombre).email(email).passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .rol(Rol.CIUDADANO).activo(true).emailVerificado(true).fechaRegistro(ahora.minus(Duration.ofDays(400)))
                            .versionToken(0).build()));
        }
        if (!nuevos.isEmpty()) {
            mongoTemplate.insertAll(nuevos).forEach(usuario -> ids.add(usuario.getId()));
        }
        return ids;
    }

    List<Reporte> generarReportes(Random random, Instant ahora, List<String> categorias, List<String> ciudadanos, String adminId) {
        List<EstadoReporte> estados = new ArrayList<>();
        PORCENTAJES.forEach((estado, porcentaje) -> estados.addAll(Collections.nCopies(TOTAL_REPORTES * porcentaje / 100, estado)));
        Collections.shuffle(estados, random);
        // Medio día de margen para no caer justo en el borde de la ventana.
        double diasVentana = Duration.between(inicioVentana(ahora), ahora).toMinutes() / (24 * 60.0) - 0.5;
        List<Reporte> reportes = new ArrayList<>(estados.size());
        for (EstadoReporte estado : estados) {
            reportes.add(generarReporte(random, ahora, diasVentana, estado, categorias, ciudadanos, adminId));
        }
        return reportes;
    }

    static Instant inicioVentana(Instant ahora) {
        return YearMonth.from(ahora.atZone(ZONA)).minusMonths(MESES - 1).atDay(1).atStartOfDay(ZONA).toInstant();
    }

    private Reporte generarReporte(Random random, Instant ahora, double diasVentana, EstadoReporte estadoFinal, List<String> categorias,
                                   List<String> ciudadanos, String adminId) {
        Zona zona = elegirZona(random);
        double[] punto = puntoEn(random, zona);
        String categoria = elegirCategoria(random, categorias);
        List<EstadoReporte> secuencia = secuenciaHasta(random, estadoFinal);
        Instant creado = ahora.minus(Duration.ofMinutes((long) (edadEnDias(random, estadoFinal, diasVentana) * 24 * 60)));
        List<HistorialEstado> historial = historial(random, ahora, creado, secuencia, adminId);
        Instant actualizado = historial.isEmpty() ? creado : historial.get(historial.size() - 1).getFecha();
        return Reporte.builder()
                .usuarioId(ciudadanos.isEmpty() ? null : ciudadanos.get(random.nextInt(ciudadanos.size())))
                .categoriaId(categoria)
                .direccionOsm(zona.barrio())
                .barrioOsm(zona.barrio())
                .descripcion(MARCA + " " + PLANTILLAS.get(random.nextInt(PLANTILLAS.size())).formatted(categoria, zona.barrio()))
                .latitud(punto[0])
                .longitud(punto[1])
                .ubicacion(new GeoJsonPoint(punto[1], punto[0]))
                .estado(estadoFinal)
                .prioridad(estadoFinal == EstadoReporte.PENDIENTE ? PrioridadReporte.MEDIA : prioridad(random))
                .fechaCreacion(creado)
                .fechaActualizacion(actualizado)
                .historialEstados(historial)
                .build();
    }

    private static Zona elegirZona(Random random) {
        int total = ZONAS.stream().mapToInt(Zona::peso).sum();
        int tirada = random.nextInt(total);
        for (Zona zona : ZONAS) {
            tirada -= zona.peso();
            if (tirada < 0) return zona;
        }
        return ZONAS.get(0);
    }

    // Unos 300 m alrededor del centro del barrio; si cayera fuera del área de servicio, se vuelve a sortear.
    private double[] puntoEn(Random random, Zona zona) {
        for (int intento = 0; intento < 20; intento++) {
            double latitud = zona.latitud() + random.nextGaussian() * 0.0027;
            double longitud = zona.longitud() + random.nextGaussian() * 0.0027;
            if (areaServicio.contiene(latitud, longitud)) return new double[]{latitud, longitud};
        }
        return new double[]{zona.latitud(), zona.longitud()};
    }

    // Las primeras categorías del catálogo pesan más, para que el top de categorías no salga parejo.
    private static String elegirCategoria(Random random, List<String> categorias) {
        int n = categorias.size();
        int tirada = random.nextInt(n * (n + 1) / 2);
        for (int i = 0; i < n; i++) {
            tirada -= n - i;
            if (tirada < 0) return categorias.get(i);
        }
        return categorias.get(0);
    }

    /** Estados por los que pasa el reporte, empezando en PENDIENTE; un rechazo puede llegar desde cualquier estado activo. */
    private static List<EstadoReporte> secuenciaHasta(Random random, EstadoReporte estadoFinal) {
        if (estadoFinal != EstadoReporte.RECHAZADO) return FLUJO.subList(0, FLUJO.indexOf(estadoFinal) + 1);
        double tirada = random.nextDouble();
        int etapa = tirada < 0.5 ? 0 : tirada < 0.85 ? 1 : 2;
        List<EstadoReporte> secuencia = new ArrayList<>(FLUJO.subList(0, etapa + 1));
        secuencia.add(EstadoReporte.RECHAZADO);
        return secuencia;
    }

    // Los pendientes son recientes y los resueltos pueden venir de cualquier mes del último año.
    private static double edadEnDias(Random random, EstadoReporte estado, double diasVentana) {
        double[] rango = switch (estado) {
            case PENDIENTE -> new double[]{0.05, 40};
            case EN_REVISION -> new double[]{1, 90};
            case EN_PROCESO -> new double[]{3, 200};
            case RESUELTO -> new double[]{5, diasVentana};
            case RECHAZADO -> new double[]{1, diasVentana};
        };
        double maximo = Math.min(rango[1], diasVentana);
        return rango[0] + random.nextDouble() * (maximo - rango[0]);
    }

    /** Un cambio por cada paso de la secuencia, con fechas crecientes entre la creación y ahora. */
    private static List<HistorialEstado> historial(Random random, Instant ahora, Instant creado, List<EstadoReporte> secuencia, String adminId) {
        List<Double> esperas = new ArrayList<>();
        for (int i = 1; i < secuencia.size(); i++) {
            esperas.add(esperaEnHoras(random, secuencia.get(i)));
        }
        double disponibles = Duration.between(creado, ahora).toMinutes() / 60.0 * 0.9;
        double total = esperas.stream().mapToDouble(Double::doubleValue).sum();
        double escala = total > disponibles ? disponibles / total : 1;
        List<HistorialEstado> historial = new ArrayList<>();
        Instant fecha = creado;
        for (int i = 1; i < secuencia.size(); i++) {
            long minutos = Math.max(1, (long) (esperas.get(i - 1) * escala * 60));
            fecha = fecha.plus(Duration.ofMinutes(minutos));
            EstadoReporte nuevo = secuencia.get(i);
            historial.add(new HistorialEstado(UUID.randomUUID().toString(), adminId, secuencia.get(i - 1), nuevo,
                    comentario(random, nuevo), fecha));
        }
        return historial;
    }

    private static double esperaEnHoras(Random random, EstadoReporte nuevo) {
        double[] dias = switch (nuevo) {
            case EN_REVISION -> new double[]{0.1, 4};
            case EN_PROCESO -> new double[]{1, 15};
            case RESUELTO -> new double[]{2, 30};
            default -> new double[]{0.5, 5};
        };
        return (dias[0] + random.nextDouble() * (dias[1] - dias[0])) * 24;
    }

    private static String comentario(Random random, EstadoReporte nuevo) {
        return switch (nuevo) {
            case EN_REVISION -> "Recibido: lo verificamos en terreno.";
            case EN_PROCESO -> "Programado con la cuadrilla de mantenimiento vial.";
            case RESUELTO -> "Reparación terminada.";
            case RECHAZADO -> MOTIVOS_RECHAZO.get(random.nextInt(MOTIVOS_RECHAZO.size()));
            case PENDIENTE -> null;
        };
    }

    private static PrioridadReporte prioridad(Random random) {
        int tirada = random.nextInt(100);
        return tirada < 25 ? PrioridadReporte.BAJA : tirada < 70 ? PrioridadReporte.MEDIA : PrioridadReporte.ALTA;
    }
}
