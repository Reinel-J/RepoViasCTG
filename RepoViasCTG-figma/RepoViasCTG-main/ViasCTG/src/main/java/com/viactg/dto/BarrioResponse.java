package com.viactg.dto;

import java.util.List;

public record BarrioResponse(String id, String nombre, String localidad, List<CalleResponse> calles) {
}
