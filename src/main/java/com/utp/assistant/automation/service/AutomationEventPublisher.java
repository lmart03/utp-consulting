package com.utp.assistant.automation.service;

import com.utp.assistant.automation.dto.AutomationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Publicador de eventos de observabilidad en tiempo real sobre STOMP/WebSocket.
 * No ejecuta lógica de negocio; publica hacia /topic/automation y /topic/automation/{processedEmailId}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationEventPublisher {

    public static final String TOPIC_AUTOMATION = "/topic/automation";
    public static final String TOPIC_AUTOMATION_PREFIX = "/topic/automation/";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(AutomationEvent event) {
        if (event == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(TOPIC_AUTOMATION, event);
            if (event.processedEmailId() != null) {
                messagingTemplate.convertAndSend(TOPIC_AUTOMATION_PREFIX + event.processedEmailId(), event);
            }
        } catch (Exception ex) {
            log.warn("No se pudo publicar evento de observabilidad WebSocket (stage={}): {}",
                    event.stage(), ex.getMessage());
        }
    }
}
