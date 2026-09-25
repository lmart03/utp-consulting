package com.utp.assistant.assistant.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    /**
     * Cliente oficial de Google Gen AI (Gemini Developer API). Solo se crea si hay API key,
     * para que la aplicación (y Gmail) siga arrancando sin ella. Spring invoca close() al apagar.
     */
    @Bean
    @ConditionalOnExpression("'${app.gemini.api-key:}'.trim().length() > 0")
    Client geminiClient(GeminiProperties properties) {
        return Client.builder()
                .apiKey(properties.apiKey())
                .httpOptions(HttpOptions.builder()
                        .timeout(properties.timeoutMs())
                        // Pocos reintentos por modelo: ante saturación conviene pasar pronto al modelo de respaldo.
                        .retryOptions(HttpRetryOptions.builder().attempts(properties.retryAttempts()).build())
                        .build())
                .build();
    }
}
