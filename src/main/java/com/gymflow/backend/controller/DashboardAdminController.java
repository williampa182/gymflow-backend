package com.gymflow.backend.controller;

import com.gymflow.backend.dto.dashboard.AsistenciasSemanaStatsDTO;
import com.gymflow.backend.dto.dashboard.DashboardAdminStatsResponse;
import com.gymflow.backend.service.DashboardAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/admin")
@RequiredArgsConstructor
public class DashboardAdminController {

    private final DashboardAdminService dashboardAdminService;

    @GetMapping("/estadisticas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DashboardAdminStatsResponse> obtenerEstadisticas() {
        return ResponseEntity.ok(dashboardAdminService.obtenerEstadisticas());
    }

    @GetMapping("/asistencias-semana")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AsistenciasSemanaStatsDTO> obtenerAsistenciasSemana() {
        return ResponseEntity.ok(dashboardAdminService.obtenerAsistenciasSemana());
    }
}
