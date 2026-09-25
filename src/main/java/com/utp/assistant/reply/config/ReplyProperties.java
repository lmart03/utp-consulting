package com.utp.assistant.reply.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Borradores de respuesta generados por IA (prefijo "app.reply"). El bot nunca envía solo: el borrador queda
 * guardado y una persona lo revisa y lo envía desde el dashboard.
 *
 * @param enabled   si es false la automatización no genera borradores
 * @param signature firma con la que Gemini cierra la respuesta
 */
@ConfigurationProperties(prefix = "app.reply")
public record ReplyProperties(boolean enabled, String signature) {

    public ReplyProperties {
        signature = signature == null || signature.isBlank() ? "Equipo UTP Consult" : signature.strip();
    }
}
