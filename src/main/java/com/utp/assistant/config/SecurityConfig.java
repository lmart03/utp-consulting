package com.utp.assistant.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({GmailProperties.class, CalendarProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            OAuth2AuthorizationRequestResolver authorizationRequestResolver,
            @Value("${app.security.login-success-url}") String loginSuccessUrl) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // CSRF activo (la sesión viaja en cookie). TEMPORAL (solo desarrollo, para Postman):
                // /api/assistant/**, /api/calendar/** y /api/jira/** quedan sin CSRF y sin exigir login en el filtro.
                // Calendar igualmente necesita la sesión de Google (cookie JSESSIONID) para obtener el token.
                // Jira usa sus propias credenciales (API token) y no depende de Google.
                // Revertir antes de producción.
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        PathPatternRequestMatcher.withDefaults().matcher("/api/assistant/**"),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/calendar/**"),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/jira/**")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/oauth2/**", "/login/**", "/api/assistant/**",
                                "/api/calendar/**", "/api/jira/**").permitAll()
                        // Documentación OpenAPI / Swagger UI (solo lectura).
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                // Para la API REST se responde 401 en lugar de redirigir al login de Google.
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(
                                endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
                        .defaultSuccessUrl(loginSuccessUrl, true));
        return http.build();
    }

    /**
     * Añade access_type=offline y prompt=consent para que Google entregue refresh
     * token
     * (necesario para acceso en segundo plano en fases posteriores).
     */
    @Bean
    OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(builder -> builder.additionalParameters(params -> {
            params.put("access_type", "offline");
            params.put("prompt", "consent");
        }));
        return resolver;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
