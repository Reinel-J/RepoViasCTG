package com.viactg.service;

import com.viactg.dto.UsuarioActualizacionRequest;
import com.viactg.dto.UsuarioRegistroRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.InvalidRequestException;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import com.viactg.repository.UsuarioRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceTests {
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final UsuarioService service = new UsuarioService(usuarioRepository, passwordEncoder, mongoTemplate);

    private Update capturarActualizacion() {
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Usuario.class));
        return update.getValue();
    }

    private void devolverAlActualizar(Usuario usuario) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Usuario.class)))
                .thenReturn(usuario);
    }

    @Test
    void unAdministradorNoPuedeCambiarSuPropioRolNiDesactivarse() {
        assertThatThrownBy(() -> service.cambiarRol("admin-1", Rol.CIUDADANO, "admin-1"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.cambiarActivo("admin-1", false, "admin-1"))
                .isInstanceOf(BusinessRuleException.class);
        verify(usuarioRepository, never()).save(any());
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Usuario.class));
    }

    @Test
    void cambiarRolSoloModificaElCampoRol() {
        devolverAlActualizar(Usuario.builder().id("u-1").rol(Rol.MODERADOR).activo(true).build());

        assertThat(service.cambiarRol("u-1", Rol.MODERADOR, "admin-1").getRol()).isEqualTo(Rol.MODERADOR);

        // Actualización parcial: no reescribe el documento, así no pisa un cambio de contraseña simultáneo.
        Document cambios = capturarActualizacion().getUpdateObject();
        assertThat(cambios.get("$set", Document.class)).containsOnlyKeys("rol");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizarPerfilNoTocaRolNiEstadoYPermiteBorrarElTelefono() {
        devolverAlActualizar(Usuario.builder().id("u-1").nombre("Ana").build());

        service.actualizarPerfil("u-1", new UsuarioActualizacionRequest(" Ana ", ""));

        Document set = capturarActualizacion().getUpdateObject().get("$set", Document.class);
        assertThat(set).containsOnlyKeys("nombre", "telefono");
        assertThat(set.get("nombre")).isEqualTo("Ana");
        assertThat(set.get("telefono")).isNull();
    }

    @Test
    void actualizarPasswordSubeLaVersionDelTokenEnLaMismaOperacion() {
        when(passwordEncoder.encode("Nueva12345")).thenReturn("hash-nuevo");
        devolverAlActualizar(Usuario.builder().id("u-1").versionToken(1).build());

        service.actualizarPassword("u-1", "Nueva12345");

        Document cambios = capturarActualizacion().getUpdateObject();
        assertThat(cambios.get("$set", Document.class).get("passwordHash")).isEqualTo("hash-nuevo");
        assertThat(cambios.get("$inc", Document.class).get("versionToken")).isEqualTo(1);
    }

    @Test
    void asegurarAdministradorCreaLaCuentaSiNoExiste() {
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ClaveAdmin123")).thenReturn("hash-admin");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Usuario creado = service.asegurarAdministrador(" Admin@Test.com ", "ClaveAdmin123", "Administrador");

        assertThat(creado.getEmail()).isEqualTo("admin@test.com");
        assertThat(creado.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(creado.isActivo()).isTrue();
        assertThat(creado.isEmailVerificado()).isTrue();
        assertThat(creado.getPasswordHash()).isEqualTo("hash-admin");
    }

    @Test
    void asegurarAdministradorNoCambiaLaContrasenaDeUnAdministradorExistente() {
        Usuario admin = Usuario.builder().id("u-1").email("admin@test.com").passwordHash("hash-original")
                .rol(Rol.ADMIN).activo(true).emailVerificado(true).build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        Usuario resultado = service.asegurarAdministrador("admin@test.com", "OtraClave123", "Administrador");

        assertThat(resultado.getPasswordHash()).isEqualTo("hash-original");
        verify(passwordEncoder, never()).encode(any());
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Usuario.class));
    }

    @Test
    void asegurarAdministradorAlPromoverUnaCuentaCiudadanaReemplazaSuContrasenaYCierraSusSesiones() {
        // Alguien se registró antes con el correo del administrador: no debe quedar como ADMIN con su contraseña.
        Usuario intruso = Usuario.builder().id("u-9").email("admin@test.com").passwordHash("hash-del-intruso")
                .rol(Rol.CIUDADANO).activo(true).build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(intruso));
        when(passwordEncoder.encode("ClaveAdmin123")).thenReturn("hash-admin");
        devolverAlActualizar(Usuario.builder().id("u-9").rol(Rol.ADMIN).passwordHash("hash-admin").build());

        service.asegurarAdministrador("admin@test.com", "ClaveAdmin123", "Administrador");

        Document cambios = capturarActualizacion().getUpdateObject();
        assertThat(cambios.get("$set", Document.class))
                .containsEntry("rol", Rol.ADMIN).containsEntry("activo", true).containsEntry("passwordHash", "hash-admin")
                .containsEntry("emailVerificado", true);
        assertThat(cambios.get("$inc", Document.class).get("versionToken")).isEqualTo(1);
    }

    @Test
    void asegurarAdministradorVerificaElCorreoDeUnAdministradorSinVerificarSinTocarSuContrasena() {
        Usuario admin = Usuario.builder().id("u-1").email("admin@test.com").passwordHash("hash-original")
                .rol(Rol.ADMIN).activo(true).emailVerificado(false).build();
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        devolverAlActualizar(admin);

        service.asegurarAdministrador("admin@test.com", "OtraClave123", "Administrador");

        Document cambios = capturarActualizacion().getUpdateObject();
        assertThat(cambios.get("$set", Document.class)).containsEntry("emailVerificado", true).doesNotContainKey("passwordHash");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void registrarCreaLaCuentaSinVerificar() {
        when(usuarioRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Usuario creado = service.registrar(new UsuarioRegistroRequest("Ana", "Ana@Test.com", "Correcta123", null));

        assertThat(creado.isEmailVerificado()).isFalse();
        assertThat(creado.getRol()).isEqualTo(Rol.CIUDADANO);
    }

    @Test
    void restablecerPasswordVerificaElCorreoYCierraLasSesiones() {
        when(passwordEncoder.encode("Nueva12345")).thenReturn("hash-nuevo");
        devolverAlActualizar(new Usuario());

        service.restablecerPassword("u-1", "Nueva12345");

        Document cambios = capturarActualizacion().getUpdateObject();
        assertThat(cambios.get("$set", Document.class)).containsEntry("passwordHash", "hash-nuevo").containsEntry("emailVerificado", true);
        assertThat(cambios.get("$inc", Document.class).get("versionToken")).isEqualTo(1);
    }

    @Test
    void lasContrasenasDeMasDe72BytesSeRechazanConUnMensajeClaro() {
        assertThatCode(() -> UsuarioService.validarLongitudPassword("a".repeat(72))).doesNotThrowAnyException();
        // 40 eñes son 40 caracteres pero 80 bytes en UTF-8.
        assertThatThrownBy(() -> UsuarioService.validarLongitudPassword("ñ".repeat(40)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("72");
    }
}
