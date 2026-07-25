package com.gymflow.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador de diagnóstico temporal para verificar el comportamiento de X-Forwarded-For en Railway.
 * Solo está activo cuando app.debug-headers.enabled=true (por defecto false).
 */
@RestController
@RequestMapping("/api/v1/debug")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.debug-headers", name = "enabled", havingValue = "true")
public class DiagnosticHeaderController {

    /**
     * Endpoint de diagnóstico que devuelve información de conexión y todos los headers.
     * 
     * @param request HttpServletRequest para obtener información de conexión y headers
     * @return Map con remoteAddr, remoteHost, remotePort y todos los headers
     */
    @GetMapping("/headers")
    public Map<String, Object> getDebugHeaders(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        // Información de conexión
        result.put("remoteAddr", request.getRemoteAddr());
        result.put("remoteHost", request.getRemoteHost());
        result.put("remotePort", request.getRemotePort());
        
        // Todos los headers. Los nombres de header HTTP son case-insensitive
        // por spec, pero getHeaderNames() los devuelve tal cual llegaron del
        // cliente (varía según el cliente/proxy). Normalizamos a minúsculas
        // para que el resultado sea determinístico y comparable.
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName.toLowerCase(), headerValue);
        }
        result.put("headers", headers);
        
        return result;
    }
}