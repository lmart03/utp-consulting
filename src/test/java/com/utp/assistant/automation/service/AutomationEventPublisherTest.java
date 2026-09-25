package com.utp.assistant.automation.service;

import java.time.OffsetDateTime;
import java.util.Map;

import com.utp.assistant.automation.dto.AutomationEvent;
import com.utp.assistant.automation.dto.AutomationEventStatus;
import com.utp.assistant.automation.dto.AutomationStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AutomationEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private AutomationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AutomationEventPublisher(messagingTemplate);
    }

    @Test
    void publish_sendsToGeneralAndSpecificTopic_whenProcessedEmailIdPresent() {
        AutomationEvent event = AutomationEvent.builder()
                .processedEmailId(25L)
                .gmailMessageId("msg-123")
                .subject("Reunión NovaTech")
                .from("cliente@novatech.com")
                .stage(AutomationStage.AI_ANALYSIS_COMPLETED)
                .status(AutomationEventStatus.SUCCESS)
                .message("Gemini detectó 2 acciones")
                .elapsedMs(1500L)
                .metadata(Map.of("toolsDetected", 2))
                .build();

        publisher.publish(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/automation"), eq(event));
        verify(messagingTemplate).convertAndSend(eq("/topic/automation/25"), eq(event));
    }

    @Test
    void publish_sendsOnlyToGeneralTopic_whenProcessedEmailIdIsNull() {
        AutomationEvent event = AutomationEvent.builder()
                .gmailMessageId("msg-456")
                .stage(AutomationStage.EMAIL_DETECTED)
                .status(AutomationEventStatus.SUCCESS)
                .message("Nuevo correo detectado")
                .build();

        publisher.publish(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/automation"), eq(event));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/automation/null"), any(Object.class));
    }

    @Test
    void publish_doesNotThrow_whenMessagingTemplateFails() {
        doThrow(new MessagingException("Broker disconnected"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        AutomationEvent event = AutomationEvent.builder()
                .processedEmailId(99L)
                .stage(AutomationStage.PROCESS_COMPLETED)
                .status(AutomationEventStatus.SUCCESS)
                .build();

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }

    @Test
    void publish_doesNothing_whenEventIsNull() {
        publisher.publish(null);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
