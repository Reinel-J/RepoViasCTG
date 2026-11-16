package com.viactg.config;

import com.viactg.model.CategoriaReporte;
import com.viactg.model.Comentario;
import com.viactg.model.Confirmacion;
import com.viactg.model.EstadoReporte;
import com.viactg.model.HistorialEstado;
import com.viactg.model.PrioridadReporte;
import com.viactg.model.Reporte;
import com.viactg.model.Rol;
import com.viactg.model.Seguimiento;
import com.viactg.model.Usuario;
import com.viactg.repository.CategoriaRepository;
import com.viactg.repository.ReporteRepository;
import com.viactg.repository.UsuarioRepository;
import com.viactg.service.AreaServicio;
import com.viactg.service.CambioReplicable;
import com.viactg.service.CategoriaService;
import com.viactg.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Datos de demostración (SEED_DEMO=true), solo si hay menos de 20 reportes. Crea SEED_DEMO_REPORTES reportes (200 por
 * defecto) de los últimos 12 meses, con ciudadanos y moderadores de demostración, comentarios, confirmaciones y votos
 * «¿ya lo arreglaron?». Con SEED_DEMO_REPORTES=10000 la base queda con más de 20 000 documentos.
 * <p>
 * Se insertan en bloques de {@value #TAMANO_LOTE}, sin geocodificar ni notificar, y al final la réplica SQL se
 * reconstruye una sola vez. Todo se reconoce: los reportes empiezan con "[DEMO]" y las cuentas usan
 * {@value #DOMINIO} (el README explica cómo borrarlos). La semilla es fija: siempre salen los mismos datos. Corre al
 * final, después de las categorías iniciales y del administrador inicial.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DatosDemo implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);
    static final String MARCA = "[DEMO]";
    static final String DOMINIO = "@demo.viasctg.local";
    static final int UMBRAL_REPORTES = 20;
    static final int TAMANO_LOTE = 1_000;
    /** Un ciudadano de demostración por cada tantos reportes (como mínimo {@value #CIUDADANOS_MINIMOS}). */
    static final int REPORTES_POR_CIUDADANO = 40;
    static final int CIUDADANOS_MINIMOS = 5;
    static final int MODERADORES = 3;
    // Los reportes caen en los mismos 12 meses de la gráfica mensual (EstadisticasService): desde el día 1 del mes de
    // hace 11 meses, hora de Colombia.
    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    static final int MESES = 12;
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
    private static final List<String> COMENTARIOS = List.of(
            "Yo paso por aquí todos los días y sigue igual.",
            "Esta mañana un motociclista casi se cae por esto.",
            "Ya van varias semanas así, ojalá lo arreglen pronto.",
            "Con la lluvia de anoche empeoró bastante.",
            "Confirmo, está justo como dice el reporte.",
            "Los vecinos pusieron una señal improvisada para avisar.",
            "Hoy vi a una cuadrilla revisando la zona.",
            "Por aquí pasan buses del SITM, es urgente.",
            "Gracias por reportarlo, yo también lo había visto.",
            "De noche no se ve nada, hay que tener mucho cuidado.");
    private static final List<String> NOMBRES = List.of("Ana", "Luis", "María", "Carlos", "Valentina", "Andrés", "Daniela",
            "Jorge", "Camila", "Santiago", "Laura", "Miguel", "Paola", "Julián", "Sofía", "Diego", "Natalia", "Felipe");
    private static final List<String> APELLIDOS = List.of("Pérez", "Gómez", "Rodríguez", "Martínez", "Herrera", "Castro",
            "Romero", "Díaz", "Torres", "Mendoza", "Vargas", "Rojas", "Jiménez", "Morales", "Ortiz", "Cabarcas");

    /** Lo que creó una ejecución, para el registro y las pruebas. */
    record Resultado(int usuarios, int reportes, int comentarios, int confirmaciones, int seguimientos) {
        int total() { return usuarios + reportes + comentarios + confirmaciones + seguimientos; }
    }

    /** Cuentas de demostración: los ciudadanos crean y participan; los moderadores (y el admin) atienden. */
    record Cuentas(List<String> ciudadanos, List<String> moderadores, int creadas) {
    }

    private final MongoTemplate mongoTemplate;
    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final AreaServicio areaServicio;
    private final PasswordEncoder passwordEncoder;
    private final boolean habilitado;
    private final int totalReportes;
    private final String adminEmail;
    private final ApplicationEventPublisher eventos;

    public DatosDemo(MongoTemplate mongoTemplate, ReporteRepository reporteRepository, UsuarioRepository usuarioRepository,
                     CategoriaRepository categoriaRepository, AreaServicio areaServicio, PasswordEncoder passwordEncoder,
                     @Value("${app.seed.demo:false}") boolean habilitado, @Value("${app.seed.demo-reportes:200}") int totalReportes,
                     @Value("${app.admin.email:}") String adminEmail, ApplicationEventPublisher eventos) {
        this.mongoTemplate = mongoTemplate;
        this.reporteRepository = reporteRepository;
        this.usuarioRepository = usuarioRepository;
        this.categoriaRepository = categoriaRepository;
        this.areaServicio = areaServicio;
        this.passwordEncoder = passwordEncoder;
        this.habilitado = habilitado;
        this.totalReportes = Math.max(1, totalReportes);
        this.adminEmail = adminEmail;
        this.eventos = eventos;
    }

    @Override
    public void run(String... args) {
        if (!habilitado) return;
        long existentes = reporteRepository.count();
        if (existentes >= UMBRAL_REPORTES) {
            log.info("SEED_DEMO: ya hay {} reportes; no se crean datos de demostración", existentes);
            return;
        }
        // «Otro» no se usa en la demo: sus reportes no dirían qué daño es.
        List<CategoriaReporte> categorias = categoriaRepository.findByActivaTrue().stream()
                .filter(categoria -> !CategoriaService.NOMBRE_OTRO.equalsIgnoreCase(categoria.getNombre()))
                .map(CategoriaReporte::de).toList();
        if (categorias.isEmpty()) {
            log.warn("SEED_DEMO: no hay categorías activas; no se crean datos de demostración");
            return;
        }
        long inicio = System.currentTimeMillis();
        Instant ahora = Instant.now();
        Random random = new Random(2026);
        Cuentas cuentas = asegurarCuentas(ahora);
        String adminId = adminEmail.isBlank() ? null : usuarioRepository.findByEmail(UsuarioService.normalizarEmail(adminEmail))
                .filter(usuario -> usuario.getRol() == Rol.ADMIN).map(Usuario::getId).orElse(null);
        List<String> atienden = new ArrayList<>(cuentas.moderadores());
        if (adminId != null) atienden.add(adminId);

        List<Reporte> reportes = insertar(generarReportes(random, ahora, categorias, cuentas.ciudadanos(), atienden));
        List<Comentario> comentarios = insertar(generarComentarios(random, ahora, reportes, cuentas.ciudadanos()));
        List<Confirmacion> confirmaciones = insertar(generarConfirmaciones(random, ahora, reportes, cuentas.ciudadanos()));
        List<Seguimiento> seguimientos = insertar(generarSeguimientos(random, ahora, reportes, cuentas.ciudadanos()));
        Resultado resultado = new Resultado(cuentas.creadas(), reportes.size(), comentarios.size(), confirmaciones.size(),
                seguimientos.size());

        // Inserción en bloque: la réplica SQL se reconstruye completa en vez de copiar documento por documento.
        eventos.publishEvent(CambioReplicable.todo());
        log.info("SEED_DEMO: {} documentos en {} ms ({} usuarios, {} reportes, {} comentarios, {} confirmaciones, {} votos)",
                resultado.total(), System.currentTimeMillis() - inicio, resultado.usuarios(), resultado.reportes(),
                resultado.comentarios(), resultado.confirmaciones(), resultado.seguimientos());
    }

    // insertAll asigna los id; por bloques, para no armar un solo mensaje gigante con miles de documentos.
    private <T> List<T> insertar(List<T> documentos) {
        List<T> insertados = new ArrayList<>(documentos.size());
        for (int desde = 0; desde < documentos.size(); desde += TAMANO_LOTE) {
            insertados.addAll(mongoTemplate.insertAll(documentos.subList(desde, Math.min(desde + TAMANO_LOTE, documentos.size()))));
        }
        return insertados;
    }

    // ------------------------------------------------------------------ cuentas

    static int ciudadanosPara(int reportes) {
        return Math.max(CIUDADANOS_MINIMOS, reportes / REPORTES_POR_CIUDADANO);
    }

    /**
     * Las cuentas demo que falten (las que existen se reutilizan). Nadie conoce su contraseña (aleatoria), así que no se
     * puede iniciar sesión con ellas.
     */
    private Cuentas asegurarCuentas(Instant ahora) {
        List<String> ciudadanos = new ArrayList<>();
        List<String> moderadores = new ArrayList<>();
        List<Usuario> nuevos = new ArrayList<>();
        int cantidad = ciudadanosPara(totalReportes);
        // Un solo hash para todas: BCrypt es lento a propósito y miles de hashes tardarían minutos.
        String hash = passwordEncoder.encode(UUID.randomUUID().toString());
        for (int numero = 1; numero <= cantidad + MODERADORES; numero++) {
            boolean esModerador = numero > cantidad;
            int indice = esModerador ? numero - cantidad : numero;
            String email = (esModerador ? "moderador" : "ciudadano") + indice + DOMINIO;
            String nombre = esModerador ? "Moderador demo " + indice
                    : NOMBRES.get(indice % NOMBRES.size()) + " " + APELLIDOS.get((indice / NOMBRES.size()) % APELLIDOS.size()) + " (demo " + indice + ")";
            List<String> destino = esModerador ? moderadores : ciudadanos;
            usuarioRepository.findByEmail(email).ifPresentOrElse(existente -> destino.add(existente.getId()), () -> nuevos.add(
                    Usuario.builder().nombre(nombre).email(email).passwordHash(hash)
                            .rol(esModerador ? Rol.MODERADOR : Rol.CIUDADANO).activo(true).emailVerificado(true)
                            .fechaRegistro(ahora.minus(Duration.ofDays(400))).versionToken(0).build()));
        }
        for (Usuario usuario : insertar(nuevos)) {
            (usuario.getRol() == Rol.MODERADOR ? moderadores : ciudadanos).add(usuario.getId());
        }
        return new Cuentas(ciudadanos, moderadores, nuevos.size());
    }

    // ------------------------------------------------------------------ reportes

    List<Reporte> generarReportes(Random random, Instant ahora, List<CategoriaReporte> categorias, List<String> ciudadanos,
                                  List<String> atienden) {
        List<EstadoReporte> estados = new ArrayList<>(totalReportes);
        PORCENTAJES.forEach((estado, porcentaje) -> estados.addAll(Collections.nCopies(totalReportes * porcentaje / 100, estado)));
        // Lo que no reparte el redondeo queda pendiente.
        while (estados.size() < totalReportes) estados.add(EstadoReporte.PENDIENTE);
        Collections.shuffle(estados, random);
        // Medio día de margen para no caer justo en el borde de la ventana.
        double diasVentana = Duration.between(inicioVentana(ahora), ahora).toMinutes() / (24 * 60.0) - 0.5;
        List<Reporte> reportes = new ArrayList<>(estados.size());
        for (EstadoReporte estado : estados) {
            reportes.add(generarReporte(random, ahora, diasVentana, estado, categorias, ciudadanos, atienden));
        }
        return reportes;
    }

    static Instant inicioVentana(Instant ahora) {
        return YearMonth.from(ahora.atZone(ZONA)).minusMonths(MESES - 1).atDay(1).atStartOfDay(ZONA).toInstant();
    }

    private Reporte generarReporte(Random random, Instant ahora, double diasVentana, EstadoReporte estadoFinal,
                                   List<CategoriaReporte> categorias, List<String> ciudadanos, List<String> atienden) {
        Zona zona = elegirZona(random);
        double[] punto = puntoEn(random, zona);
        CategoriaReporte categoria = elegirCategoria(random, categorias);
        List<EstadoReporte> secuencia = secuenciaHasta(random, estadoFinal);
        Instant creado = ahora.minus(Duration.ofMinutes((long) (edadEnDias(random, estadoFinal, diasVentana) * 24 * 60)));
        // Un mismo moderador atiende el caso de principio a fin (o nadie, si no hay quién).
        String moderador = atienden.isEmpty() ? null : atienden.get(random.nextInt(atienden.size()));
        List<HistorialEstado> historial = historial(random, ahora, creado, secuencia, moderador);
        Instant actualizado = historial.isEmpty() ? creado : historial.get(historial.size() - 1).getFecha();
        return Reporte.builder()
                .usuarioId(ciudadanos.isEmpty() ? null : ciudadanos.get(random.nextInt(ciudadanos.size())))
                .categoria(categoria)
                .direccionOsm(zona.barrio())
                .barrioOsm(zona.barrio())
                .descripcion(MARCA + " " + PLANTILLAS.get(random.nextInt(PLANTILLAS.size())).formatted(categoria.getNombre(), zona.barrio()))
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

    // ------------------------------------------------------------------ participación

    /** Hasta 2 comentarios por reporte (en promedio ~0,45), de otros ciudadanos, mientras el caso estuvo abierto. */
    List<Comentario> generarComentarios(Random random, Instant ahora, List<Reporte> reportes, List<String> ciudadanos) {
        List<Comentario> comentarios = new ArrayList<>();
        for (Reporte reporte : reportes) {
            int cantidad = cantidad(random, 0.65, 0.90);
            for (String autor : otrosCiudadanos(random, reporte, ciudadanos, cantidad)) {
                comentarios.add(Comentario.builder().reporteId(reporte.getId()).usuarioId(autor)
                        .texto(COMENTARIOS.get(random.nextInt(COMENTARIOS.size())))
                        .fecha(momentoAbierto(random, reporte, ahora)).editado(false).build());
            }
        }
        return comentarios;
    }

    /** Hasta 3 confirmaciones por reporte (en promedio ~0,5), cada una de un ciudadano distinto del autor. */
    List<Confirmacion> generarConfirmaciones(Random random, Instant ahora, List<Reporte> reportes, List<String> ciudadanos) {
        List<Confirmacion> confirmaciones = new ArrayList<>();
        for (Reporte reporte : reportes) {
            int cantidad = cantidad(random, 0.70, 0.88, 0.96);
            for (String usuario : otrosCiudadanos(random, reporte, ciudadanos, cantidad)) {
                confirmaciones.add(Confirmacion.builder().reporteId(reporte.getId()).usuarioId(usuario)
                        .fecha(momentoAbierto(random, reporte, ahora)).build());
            }
        }
        return confirmaciones;
    }

    /**
     * Votos «¿ya lo arreglaron?», solo en reportes que siguen activos (la API no admite votos en los cerrados). En los que
     * están en proceso hay más votos y la mayoría dice que ya quedó; en los demás casi todos dicen que sigue igual.
     */
    List<Seguimiento> generarSeguimientos(Random random, Instant ahora, List<Reporte> reportes, List<String> ciudadanos) {
        List<Seguimiento> votos = new ArrayList<>();
        for (Reporte reporte : reportes) {
            if (!reporte.getEstado().esActivo()) continue;
            boolean enProceso = reporte.getEstado() == EstadoReporte.EN_PROCESO;
            int cantidad = enProceso ? cantidad(random, 0.25, 0.55, 0.80) : cantidad(random, 0.75, 0.95);
            double probabilidadArreglado = enProceso ? 0.65 : 0.10;
            for (String usuario : otrosCiudadanos(random, reporte, ciudadanos, cantidad)) {
                votos.add(Seguimiento.builder().reporteId(reporte.getId()).usuarioId(usuario)
                        .arreglado(random.nextDouble() < probabilidadArreglado).fecha(momentoAbierto(random, reporte, ahora)).build());
            }
        }
        return votos;
    }

    // Cuántos: 0 si la tirada cae bajo el primer umbral, 1 bajo el segundo, y así; el máximo es la cantidad de umbrales.
    private static int cantidad(Random random, double... umbrales) {
        double tirada = random.nextDouble();
        for (int i = 0; i < umbrales.length; i++) {
            if (tirada < umbrales[i]) return i;
        }
        return umbrales.length;
    }

    // Ciudadanos distintos entre sí y del autor (uno por reporte en confirmaciones y votos, como exige la API).
    private static Collection<String> otrosCiudadanos(Random random, Reporte reporte, List<String> ciudadanos, int cantidad) {
        Set<String> elegidos = new LinkedHashSet<>();
        int disponibles = (int) ciudadanos.stream().filter(id -> !id.equals(reporte.getUsuarioId())).count();
        int objetivo = Math.min(cantidad, disponibles);
        while (elegidos.size() < objetivo) {
            String candidato = ciudadanos.get(random.nextInt(ciudadanos.size()));
            if (!candidato.equals(reporte.getUsuarioId())) elegidos.add(candidato);
        }
        return elegidos;
    }

    // Un instante entre la creación del reporte y su cierre (o ahora, si sigue abierto).
    private static Instant momentoAbierto(Random random, Reporte reporte, Instant ahora) {
        Instant fin = reporte.getEstado().esActivo() ? ahora : reporte.getFechaActualizacion();
        long minutos = Math.max(1, Duration.between(reporte.getFechaCreacion(), fin).toMinutes());
        return reporte.getFechaCreacion().plus(Duration.ofMinutes(1 + (long) (random.nextDouble() * (minutos - 1))));
    }

    // ------------------------------------------------------------------ utilidades de los reportes

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
    private static CategoriaReporte elegirCategoria(Random random, List<CategoriaReporte> categorias) {
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
    private static List<HistorialEstado> historial(Random random, Instant ahora, Instant creado, List<EstadoReporte> secuencia,
                                                   String moderador) {
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
            historial.add(new HistorialEstado(UUID.randomUUID().toString(), moderador, secuencia.get(i - 1), nuevo,
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
