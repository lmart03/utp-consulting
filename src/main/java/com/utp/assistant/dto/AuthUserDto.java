package com.utp.assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos no sensibles de la cuenta Google autenticada.")
public record AuthUserDto(
        @Schema(example = "true") boolean authenticated,
        @Schema(example = "Luis Enrique Martínez Quijandria") String name,
        @Schema(example = "lmartinezquijandria@gmail.com") String email,
        @Schema(description = "Indica si Google entregó refresh token (acceso en segundo plano).", example = "true")
        boolean refreshTokenAvailable) {
}
