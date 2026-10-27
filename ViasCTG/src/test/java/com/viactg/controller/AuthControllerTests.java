package com.viactg.controller;

import com.jayway.jsonpath.JsonPath;
import com.viactg.dto.UsuarioRegistroRequest;
import com.viactg.exception.BusinessRuleException;
import com.viactg.exception.ForbiddenOperationException;
import com.viactg.exception.InvalidRequestException;
import com.viactg.exception.TooManyRequestsException;
import com.viactg.model.Rol;
import com.viactg.model.Usuario;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTests extends ControllerTestBase {
    private static final RequestPostProcessor DESDE_IP = peticion -> {
        peticion.setRemoteAddr("203.0.113.7");
        return peticion;
    };

    private static Usuario ana() {
        Usuario usuario = usuario(Rol.CIUDADANO);
        usuario.setEmail("ana@correo.com");
        usuario.setPasswordHash("$2a$10$hashQueNuncaDebeSalir");
        return usuario;
    }

    @Test
    void registroDevuelve201SinTokenHastaQueSeConfirmeElCorreo() throws Exception {
        Usuario creada = ana();
        creada.setEmailVerificado(false);
        when(authService.registrar(any(), eq("203.0.113.7"))).thenReturn(creada);

        mvc.perform(post("/api/auth/registro").with(DESDE_IP).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":" Ana ","email":"ana@correo.com","password":"secreta123","telefono":"300 123 4567"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.email").value("ana@correo.com"))
                .andExpect(jsonPath("$.emailVerificado").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<UsuarioRegistroRequest> captor = ArgumentCaptor.forClass(UsuarioRegistroRequest.class);
        verify(authService).registrar(captor.capture(), eq("203.0.113.7"));
        assertThat(captor.getValue().password()).isEqualTo("secreta123");
    }

    @Test
    void verificarElCorreoIniciaSesionConUnTokenValido() throws Exception {
        when(authService.verificarEmail("enlace-123")).thenReturn(ana());

        String cuerpo = mvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"enlace-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.emailVerificado").value(true))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(jwtService.extraerEmail(JsonPath.read(cuerpo, "$.token"))).isEqualTo("ana@correo.com");
    }

    @Test
    void verificarConUnEnlaceInvalidoDevuelve409YSinTokenDevuelve400() throws Exception {
        when(authService.verificarEmail("usado")).thenThrow(new BusinessRuleException("El enlace no es válido, ya se usó o venció."));

        mvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"usado\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El enlace no es válido, ya se usó o venció."));
        mvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.token").value("El enlace de confirmación está incompleto"));
    }

    @Test
    void reenviarLaVerificacionResponde202YPasaLaIp() throws Exception {
        mvc.perform(post("/api/auth/reenviar-verificacion").with(DESDE_IP).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@correo.com\"}"))
                .andExpect(status().isAccepted());

        verify(authService).reenviarVerificacion("ana@correo.com", "203.0.113.7");
    }

    @Test
    void loginSinConfirmarElCorreoDevuelve403ConElMotivo() throws Exception {
        when(authService.login(anyString(), anyString(), anyString())).thenThrow(new ForbiddenOperationException(
                "Confirma tu correo antes de iniciar sesión: abre el enlace que te enviamos a ana@correo.com. Si no te llegó, pide uno nuevo."));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@correo.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(startsWith("Confirma tu correo antes de iniciar sesión")));
    }

    @Test
    void registroConDatosInvalidosDevuelve400ConUnMensajePorCampo() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":"","email":"no-es-un-correo","password":"corta","telefono":"abc"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La solicitud contiene campos inválidos"))
                .andExpect(jsonPath("$.validationErrors.nombre").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.validationErrors.email").value("El correo electrónico no tiene un formato válido"))
                .andExpect(jsonPath("$.validationErrors.password").value("La contraseña debe tener entre 8 y 72 caracteres"))
                .andExpect(jsonPath("$.validationErrors.telefono").value("El teléfono no tiene un formato válido"));

        verifyNoInteractions(authService);
    }

    @Test
    void loginPasaLaIpDelClienteAlServicio() throws Exception {
        when(authService.login("ana@correo.com", "secreta123", "203.0.113.7")).thenReturn(ana());

        mvc.perform(post("/api/auth/login").with(DESDE_IP).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@correo.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").value("ciudadano"));
    }

    @Test
    void loginConCredencialesIncorrectasDevuelve401SinDecirQueFallo() throws Exception {
        when(authService.login(anyString(), anyString(), anyString())).thenThrow(new BadCredentialsException("detalle interno"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@correo.com\",\"password\":\"equivocada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("El correo o la contraseña no son correctos, o la cuenta está desactivada"));
    }

    @Test
    void loginBloqueadoPorDemasiadosIntentosDevuelve429() throws Exception {
        when(authService.login(anyString(), anyString(), anyString()))
                .thenThrow(new TooManyRequestsException("Demasiados intentos. Espera unos minutos"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@correo.com\",\"password\":\"secreta123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Demasiados intentos. Espera unos minutos"));
    }

    @Test
    void unJsonMalFormadoDevuelve400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("El cuerpo de la solicitud falta o no es un JSON válido")));

        verifyNoInteractions(authService);
    }

    @Test
    void unCuerpoQueNoEsJsonDevuelve415() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("ana@correo.com"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("El tipo de contenido de la solicitud no es compatible con esta operación"));
    }

    @Test
    void unaContrasenaDeMasDe72BytesDevuelve400() throws Exception {
        when(authService.registrar(any(), anyString())).thenThrow(new InvalidRequestException(
                "La contraseña es demasiado larga: usa como máximo 72 caracteres (las letras con tilde y la ñ cuentan doble)"));

        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@correo.com\",\"password\":\"" + "ñ".repeat(40) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("La contraseña es demasiado larga")));
    }

    @Test
    void recuperarResponde202YPasaLaIp() throws Exception {
        mvc.perform(post("/api/auth/recuperar").with(DESDE_IP).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@correo.com\"}"))
                .andExpect(status().isAccepted());

        verify(authService).solicitarRecuperacion("ana@correo.com", "203.0.113.7");
    }

    @Test
    void restablecerResponde204() throws Exception {
        mvc.perform(post("/api/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc123\",\"passwordNueva\":\"nuevaClave1\"}"))
                .andExpect(status().isNoContent());

        verify(authService).restablecer("abc123", "nuevaClave1");
    }

    @Test
    void restablecerSinTokenNiContrasenaValidaDevuelve400() throws Exception {
        mvc.perform(post("/api/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\" \",\"passwordNueva\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.token").value("El enlace de recuperación está incompleto"))
                .andExpect(jsonPath("$.validationErrors.passwordNueva").value("La contraseña nueva debe tener entre 8 y 72 caracteres"));

        verifyNoInteractions(authService);
    }
}
