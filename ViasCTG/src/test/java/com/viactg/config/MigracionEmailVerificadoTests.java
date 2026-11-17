package com.viactg.config;

import com.mongodb.client.result.UpdateResult;
import com.viactg.model.Usuario;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigracionEmailVerificadoTests {
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);

    @Test
    void marcaComoVerificadasSoloLasCuentasQueNoTienenElCampo() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Usuario.class)))
                .thenReturn(UpdateResult.acknowledged(3, 3L, null));

        new MigracionEmailVerificado(mongoTemplate).run(null);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(Usuario.class));
        // Una cuenta nueva sin confirmar tiene emailVerificado=false: no debe quedar verificada por esta migración.
        assertThat(query.getValue().getQueryObject()).containsEntry("emailVerificado", new Document("$exists", false));
        assertThat(update.getValue().getUpdateObject().get("$set", Document.class)).containsEntry("emailVerificado", true);
    }
}
