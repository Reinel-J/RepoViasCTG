package com.viactg.controller;

import com.viactg.dto.DashboardResponse;
import com.viactg.service.MetabaseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERADOR')")
public class DashboardController {
    private final MetabaseService metabaseService;
    public DashboardController(MetabaseService metabaseService) { this.metabaseService = metabaseService; }
    @GetMapping public DashboardResponse obtener() { return metabaseService.urlDashboard(); }
}
