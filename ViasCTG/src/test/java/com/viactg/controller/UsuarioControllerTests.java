package com.viactg.controller;

import com.jayway.jsonpath.JsonPath;
import com.viactg.dto.UsuarioActualizacionRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import com.viactg.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsuarioControllerTests extends ControllerTestBase {

    @Test
    void perfilDevuelveAlUsuarioDelTokenSinElHashDeLaContrasena() throws Exception {
        Usuario usuario = usuario(Rol.CIUDADANO);
        usuario.setPasswordHash("$2a$10$hashQueNuncaDebeSalir");
        when(usuarioService.buscarPorId("ciudadano")).thenReturn(usuario);

        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ciudadano"))
                .andExpect(jsonPath("$.email").value("ciudadano@viactg.co"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.versionToken").doesNotExist());
    }

    @Test
    void actualizarPerfilValidaNombreYTelefono() throws Exception {
        mvc.perform(put("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"   \",\"telefono\":\"llámame\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.nombre").value("El nombre no puede quedar en blanco"))
                .andExpect(jsonPath("$.validationErrors.telefono").value("El teléfono no tiene un formato válido"));

        verifyNoInteractions(usuarioService);
    }

    @Test
    void actualizarPerfilConTelefonoVacioLoBorra() throws Exception {
        when(usuarioService.actualizarPerfil(any(), any())).thenReturn(usuario(Rol.CIUDADANO));

        mvc.perform(put("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"\"}"))
                .andExpect(status().isOk());

        verify(usuarioService).actualizarPerfil("ciudadano", new UsuarioActualizacionRequest(null, ""));
    }

    @Test
    void cambiarContrasenaDevuelveUnTokenNuevoQueSobreviveALaRevocacion() throws Exception {
        Usuario usuario = usuario(Rol.CIUDADANO);
        String tokenAnterior = bearer(usuario);
        Usuario actualizado = usuario(Rol.CIUDADANO);
        actualizado.setVersionToken(1);
        when(authService.cambiarPassword("ciudadano", "actual123", "nuevaClave1")).thenReturn(actualizado);

        String cuerpo = mvc.perform(put("/api/usuarios/me/password").header(HttpHeaders.AUTHORIZATION, tokenAnterior)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"actual123\",\"passwordNueva\":\"nuevaClave1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        String tokenNuevo = JsonPath.read(cuerpo, "$.token");
        assertThat(jwtService.esValido(tokenNuevo, new UsuarioPrincipal(actualizado))).isTrue();
        assertThat(jwtService.esValido(tokenAnterior.substring(7), new UsuarioPrincipal(actualizado))).isFalse();
    }

    @Test
    void cambiarContrasenaConLaActualIncorrectaDevuelve409() throws Exception {
        when(authService.cambiarPassword("ciudadano", "equivocada", "nuevaClave1"))
                .thenThrow(new BusinessRuleException("La contraseña actual no es correcta"));

        mvc.perform(put("/api/usuarios/me/password").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"equivocada\",\"passwordNueva\":\"nuevaClave1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));
    }

    @Test
    void cerrarSesionesResponde204() throws Exception {
        mvc.perform(post("/api/usuarios/me/cerrar-sesiones").header(HttpHeaders.AUTHORIZATION, bearer(Rol.CIUDADANO)))
                .andExpect(status().isNoContent());

        verify(authService).cerrarTodasLasSesiones("ciudadano");
    }

    @Test
    void cambiarRolPasaAlAdministradorQueLoSolicita() throws Exception {
        when(usuarioService.cambiarRol("u1", Rol.MODERADOR, "admin")).thenReturn(usuario(Rol.MODERADOR));

        mvc.perform(patch("/api/usuarios/u1/rol").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"MODERADOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("MODERADOR"));
    }

    @Test
    void cambiarRolSinRolOConUnoInexistenteDevuelve400() throws Exception {
        mvc.perform(patch("/api/usuarios/u1/rol").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.rol").value("El rol es obligatorio"));
        mvc.perform(patch("/api/usuarios/u1/rol").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"SUPERADMIN\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioService);
    }

    @Test
    void cambiarActivoExigeElParametro() throws Exception {
        mvc.perform(patch("/api/usuarios/u1/activo").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el parámetro obligatorio 'activo'"));
    }

    @Test
    void desactivarseASiMismoDevuelveElErrorDelServicio() throws Exception {
        when(usuarioService.cambiarActivo("admin", false, "admin"))
                .thenThrow(new BusinessRuleException("No puedes desactivar tu propia cuenta"));

        mvc.perform(patch("/api/usuarios/admin/activo").param("activo", "false").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No puedes desactivar tu propia cuenta"));
    }

    @Test
    void listarDevuelveLosUsuarios() throws Exception {
        when(usuarioService.listar()).thenReturn(List.of(usuario(Rol.CIUDADANO), usuario(Rol.MODERADOR)));

        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(Rol.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].rol").value("MODERADOR"));
    }
}
