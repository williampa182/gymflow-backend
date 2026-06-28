package com.gymflow.backend.dto.response;

import com.gymflow.backend.model.enums.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String tipo;
    private Long id;
    private String nombre;
    private String email;
    private Rol rol;
}