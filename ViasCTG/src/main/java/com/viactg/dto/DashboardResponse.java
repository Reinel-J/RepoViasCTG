package com.viactg.dto;

import java.time.Instant;

/** URL firmada del dashboard de Metabase para el iframe del panel; deja de valer en {@code expiraEn}. */
public record DashboardResponse(String url, Instant expiraEn) {
}
