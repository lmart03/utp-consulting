package com.utp.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
// Registra los @ConfigurationProperties de cada feature (gmail, calendar, jira, assistant, automation).
@ConfigurationPropertiesScan
public class UtpAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(UtpAssistantApplication.class, args);
	}

}
