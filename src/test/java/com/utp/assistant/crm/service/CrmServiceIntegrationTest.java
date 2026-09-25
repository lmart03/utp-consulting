package com.utp.assistant.crm.service;

import com.utp.assistant.crm.dto.ProspectRequest;
import com.utp.assistant.crm.dto.ProspectResponse;
import com.utp.assistant.crm.entity.DataSource;
import com.utp.assistant.crm.entity.ProspectStatus;
import com.utp.assistant.crm.exception.InvalidProspectException;
import com.utp.assistant.crm.repository.ProspectInteractionRepository;
import com.utp.assistant.crm.repository.ProspectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CRM con PostgreSQL real en un schema aislado ("crm_test"). Requiere: docker compose up -d */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:${DB_PORT:2340}/${DB_NAME:utp_assistant}?currentSchema=crm_test",
        "spring.jpa.properties.hibernate.default_schema=crm_test",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "app.gemini.api-key=",
        "app.jira.api-token=",
        "assistant.polling.enabled=false"
})
class CrmServiceIntegrationTest {

    @Autowired
    private CrmService crmService;
    @Autowired
    private ProspectRepository prospectRepository;
    @Autowired
    private ProspectInteractionRepository interactionRepository;

    @BeforeEach
    void setUp() {
        interactionRepository.deleteAll();
        prospectRepository.deleteAll();
    }

    @Test
    void createsContactWithGeminiDataAndRealSenderEmail() {
        CrmService.UpsertResult result = crmService.upsertFromEmail(command("g1",
                "\"Carla Mendoza\" <carla.mendoza@andinalogistics.com>",
                "Carla Mendoza", "otro@correo-falso.com", "Andina Logistics", "+51 987 654 321", "INTERESTED", "Pide estimación.", false));

        ProspectResponse prospect = result.prospect();
        assertThat(result.created()).isTrue();
        assertThat(prospect.email()).isEqualTo("carla.mendoza@andinalogistics.com");
        assertThat(prospect.name()).isEqualTo("Carla Mendoza");
        assertThat(prospect.nameSource()).isEqualTo(DataSource.GEMINI);
        assertThat(prospect.company()).isEqualTo("Andina Logistics");
        assertThat(prospect.status()).isEqualTo(ProspectStatus.INTERESTED);
        assertThat(prospect.missingFields()).isEmpty();
        assertThat(prospect.inferredFields()).isEmpty();
    }

    @Test
    void infersMissingDataAndReportsWhatIsMissing() {
        ProspectResponse fromHeader = crmService.upsertFromEmail(command("g1", "Pedro Ruiz <pruiz@gmail.com>",
                null, null, null, null, null, null, false)).prospect();

        assertThat(fromHeader.name()).isEqualTo("Pedro Ruiz");
        assertThat(fromHeader.nameSource()).isEqualTo(DataSource.FROM_HEADER);
        assertThat(fromHeader.company()).isNull();
        assertThat(fromHeader.status()).isEqualTo(ProspectStatus.CONTACTED);
        assertThat(fromHeader.missingFields()).containsExactly("company", "phone");
        assertThat(fromHeader.inferredFields()).containsExactly("name");

        ProspectResponse fromAddress = crmService.upsertFromEmail(command("g2", "laura.gomez@andinalogistics.com",
                null, null, null, null, null, null, false)).prospect();

        assertThat(fromAddress.name()).isEqualTo("Laura Gomez");
        assertThat(fromAddress.nameSource()).isEqualTo(DataSource.EMAIL_ADDRESS);
        assertThat(fromAddress.company()).isEqualTo("Andinalogistics");
        assertThat(fromAddress.companySource()).isEqualTo(DataSource.EMAIL_DOMAIN);
        assertThat(fromAddress.inferredFields()).containsExactly("name", "company");
    }

    @Test
    void laterEmailsUpdateTheSameContactWithoutLosingDataOrGoingBackInStatus() {
        crmService.upsertFromEmail(command("g1", "carla@andinalogistics.com",
                null, null, null, "+51 999 111 222", "INTERESTED", "Primer contacto.", true));

        CrmService.UpsertResult second = crmService.upsertFromEmail(command("g2", "\"Carla Mendoza\" <carla@andinalogistics.com>",
                "Carla Mendoza", null, "Andina Logistics", null, "CONTACTED", "Envía requisitos.", false));

        ProspectResponse prospect = second.prospect();
        assertThat(second.created()).isFalse();
        assertThat(prospectRepository.count()).isEqualTo(1);
        assertThat(prospect.name()).isEqualTo("Carla Mendoza");
        assertThat(prospect.company()).isEqualTo("Andina Logistics");
        assertThat(prospect.companySource()).isEqualTo(DataSource.GEMINI);
        assertThat(prospect.phone()).isEqualTo("+51 999 111 222");
        assertThat(prospect.status()).isEqualTo(ProspectStatus.MEETING_SCHEDULED);
        assertThat(prospect.interactionCount()).isEqualTo(2);
        assertThat(prospect.notes()).isEqualTo("Envía requisitos.");
        assertThat(crmService.detail(prospect.id()).interactions()).hasSize(2);
    }

    @Test
    void retryingTheSameEmailIsIdempotent() {
        CrmContactCommand command = command("g1", "carla@andinalogistics.com", "Carla", null, null, null, "INTERESTED", "Nota", false);

        crmService.upsertFromEmail(command);
        CrmService.UpsertResult retry = crmService.upsertFromEmail(command);

        assertThat(retry.alreadyApplied()).isTrue();
        assertThat(retry.prospect().interactionCount()).isEqualTo(1);
        assertThat(interactionRepository.count()).isEqualTo(1);
    }

    @Test
    void manualDataIsNotOverwrittenByInferencesAndSearchWorks() {
        crmService.upsertManual(new ProspectRequest("carla@andinalogistics.com", "Carla M.", "Andina Logistics SAC",
                null, ProspectStatus.CLIENT, "Alta manual"));
        ProspectResponse afterEmail = crmService.upsertFromEmail(command("g1", "\"C. Mendoza\" <carla@andinalogistics.com>",
                null, null, null, null, "INTERESTED", null, false)).prospect();

        assertThat(afterEmail.name()).isEqualTo("Carla M.");
        assertThat(afterEmail.company()).isEqualTo("Andina Logistics SAC");
        assertThat(afterEmail.status()).isEqualTo(ProspectStatus.CLIENT);
        assertThat(crmService.list("andina", 10)).hasSize(1);
        assertThat(crmService.list("otra", 10)).isEmpty();
    }

    @Test
    void rejectsEmailsWithoutAValidSender() {
        assertThatThrownBy(() -> crmService.upsertFromEmail(command("g1", "sin-email", null, "tampoco", null, null, null, null, false)))
                .isInstanceOf(InvalidProspectException.class);
    }

    private static CrmContactCommand command(String gmailId, String from, String name, String email, String company,
                                             String phone, String status, String notes, boolean meeting) {
        return new CrmContactCommand(from, gmailId, "Asunto " + gmailId, name, email, company, phone, status, notes, meeting);
    }
}
