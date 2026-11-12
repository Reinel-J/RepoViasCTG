package com.viactg.dto;
import java.time.Instant;
public record RegistroAuditoriaResponse(String id, Instant fecha, String actorId, String actorNombre, String accion, String entidad, String entidadId, String detalle) {}
