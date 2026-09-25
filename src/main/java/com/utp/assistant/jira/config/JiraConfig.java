package com.utp.assistant.jira.config;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class JiraConfig {

    /**
     * Cliente HTTP para Jira Cloud REST API v3 con Basic Auth (email + API token).
     * Si faltan credenciales se crea sin Authorization; JiraService responde 503 antes de usarlo.
     */
    @Bean
    RestClient jiraRestClient(JiraProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (properties.isConfigured()) {
            builder.baseUrl(properties.normalizedBaseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader(properties.email(), properties.apiToken()));
        }
        return builder.build();
    }

    /** "Basic " + base64(email:apiToken), según la autenticación básica de Jira Cloud. */
    public static String basicAuthHeader(String email, String apiToken) {
        String credentials = email.strip() + ":" + apiToken.strip();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
