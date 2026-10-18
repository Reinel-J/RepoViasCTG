package com.viactg.service;

import com.viactg.model.Notificacion;
import com.viactg.repository.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificacionServiceTests {
    private final NotificacionRepository notificacionRepository = mock(NotificacionRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final NotificacionService service = new NotificacionService(notificacionRepository, mongoTemplate);

    @Test
    void contarSinLeerCuentaSoloLasNoLeidasDelUsuarioEnLaBaseDeDatos() {
        when(mongoTemplate.count(any(Query.class), eq(Notificacion.class))).thenReturn(4L);

        long total = service.contarSinLeer("u-1");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(queryCaptor.capture(), eq(Notificacion.class));
        assertThat(total).isEqualTo(4L);
        assertThat(queryCaptor.getValue().getQueryObject().get("usuarioId")).isEqualTo("u-1");
        assertThat(queryCaptor.getValue().getQueryObject().get("leida")).isEqualTo(false);
    }
}
