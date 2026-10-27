package com.viactg.config;

import com.viactg.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Las cuentas creadas antes de la verificación por correo no tienen el campo {@code emailVerificado}: se marcan como
 * verificadas para no dejarlas fuera. Solo toca documentos sin el campo, así que en los arranques siguientes no hace nada.
 */
@Component
public class MigracionEmailVerificado implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MigracionEmailVerificado.class);
    private final MongoTemplate mongoTemplate;

    public MigracionEmailVerificado(MongoTemplate mongoTemplate) { this.mongoTemplate = mongoTemplate; }

    @Override
    public void run(ApplicationArguments args) {
        long marcadas = mongoTemplate.updateMulti(Query.query(Criteria.where("emailVerificado").exists(false)),
                new Update().set("emailVerificado", true), Usuario.class).getModifiedCount();
        if (marcadas > 0) {
            log.info("{} cuenta(s) anteriores a la verificación por correo quedaron marcadas como verificadas", marcadas);
        }
    }
}
