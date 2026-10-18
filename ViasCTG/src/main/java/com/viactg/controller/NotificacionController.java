package com.viactg.controller;

import com.viactg.dto.NotificacionResponse;
import com.viactg.mapper.NotificacionMapper;
import com.viactg.security.UsuarioPrincipal;
import com.viactg.service.NotificacionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {
    private final NotificacionService notificacionService;
    private final NotificacionMapper notificacionMapper;
    public NotificacionController(NotificacionService notificacionService, NotificacionMapper notificacionMapper) { this.notificacionService = notificacionService; this.notificacionMapper = notificacionMapper; }
    @GetMapping public List<NotificacionResponse> listar(@AuthenticationPrincipal UsuarioPrincipal principal) { return notificacionService.listarParaUsuario(principal.getId()).stream().map(notificacionMapper::toResponse).toList(); }
    @GetMapping("/sin-leer") public Map<String, Long> contarSinLeer(@AuthenticationPrincipal UsuarioPrincipal principal) { return Map.of("total", notificacionService.contarSinLeer(principal.getId())); }
    @PatchMapping("/{id}/leida") public NotificacionResponse marcarLeida(@PathVariable String id, @AuthenticationPrincipal UsuarioPrincipal principal) { return notificacionMapper.toResponse(notificacionService.marcarLeida(id, principal.getId())); }
    @PatchMapping("/leidas") public Map<String, Long> marcarTodasLeidas(@AuthenticationPrincipal UsuarioPrincipal principal) { return Map.of("marcadas", notificacionService.marcarTodasLeidas(principal.getId())); }
}
