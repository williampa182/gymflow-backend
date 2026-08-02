package com.gymflow.backend.dto.request;

import com.gymflow.backend.model.enums.Rol;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CambioRolRequest {

    @NotNull(message = "El rol es obligatorio")
    private Rol rol;
}
