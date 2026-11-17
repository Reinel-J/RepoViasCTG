package com.viactg.dto;

/** Resultado de ejecutar la tarea diaria a mano: recordatorios enviados y reportes cerrados por la comunidad. */
public record RecordatoriosResponse(int reportesRecordados, int reportesCerrados) {
}
