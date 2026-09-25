package com.utp.assistant.scheduler;

import com.utp.assistant.service.EmailAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Polling de Gmail. fixedDelay: el siguiente ciclo empieza delay-ms después de que termina el anterior,
 * así los ciclos no se solapan. Se desactiva con assistant.polling.enabled=false.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "assistant.polling", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EmailPollingScheduler {

    private final EmailAutomationService automationService;

    @Scheduled(fixedDelayString = "${assistant.polling.delay-ms:10000}",
            initialDelayString = "${assistant.polling.delay-ms:10000}")
    public void pollGmail() {
        automationService.runCycle();
    }
}
