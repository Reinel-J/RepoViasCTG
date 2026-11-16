package com.viactg.config;

import com.viactg.repository.sql.ReplicaSqlRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Conexión a la base secundaria (PostgreSQL), solo con {@code app.sql.habilitada=true}. Se configura aquí y no con la
 * autoconfiguración de Spring Boot porque esa exige la base al arrancar: con esta, si PostgreSQL está caído la API
 * arranca igual (el pool no se conecta hasta que se usa) y ReplicaSqlService corre Flyway y lo reintenta después.
 */
@Configuration
@ConditionalOnProperty(name = "app.sql.habilitada", havingValue = "true")
public class ReplicaSqlConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource replicaDataSource(@Value("${app.sql.url}") String url, @Value("${app.sql.usuario}") String usuario,
                                              @Value("${app.sql.password:}") String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("replica-sql");
        config.setJdbcUrl(url);
        config.setUsername(usuario);
        config.setPassword(password);
        // Una sola tarea escribe a la vez; unas pocas conexiones más para consultar el estado desde el panel.
        config.setMaximumPoolSize(4);
        config.setConnectionTimeout(5_000);
        // -1: no intentar conectar al crear el pool, así el arranque no depende de PostgreSQL.
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate replicaJdbcTemplate(DataSource replicaDataSource) {
        return new JdbcTemplate(replicaDataSource);
    }

    @Bean
    public TransactionTemplate replicaTransacciones(DataSource replicaDataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(replicaDataSource));
    }

    /** Migraciones del esquema (src/main/resources/db/migration). Las ejecuta ReplicaSqlService, no el arranque. */
    @Bean
    public Flyway replicaFlyway(DataSource replicaDataSource) {
        return Flyway.configure().dataSource(replicaDataSource).locations("classpath:db/migration").load();
    }

    @Bean
    public ReplicaSqlRepository replicaSqlRepository(JdbcTemplate replicaJdbcTemplate) {
        return new ReplicaSqlRepository(replicaJdbcTemplate);
    }
}
