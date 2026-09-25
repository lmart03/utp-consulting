package com.utp.assistant.crm.service;

import com.utp.assistant.crm.entity.ProspectStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactInferenceTest {

    @Test
    void extractsRealSenderEmailAndDisplayName() {
        String from = "\"Carla Mendoza\" <Carla.Mendoza@AndinaLogistics.com>";

        assertThat(ContactInference.senderEmail(from)).isEqualTo("carla.mendoza@andinalogistics.com");
        assertThat(ContactInference.displayName(from)).isEqualTo("Carla Mendoza");
        assertThat(ContactInference.displayName("carla@andinalogistics.com")).isNull();
    }

    @Test
    void infersNameFromAddressWhenThereIsNoSignatureOrDisplayName() {
        assertThat(ContactInference.nameFromAddress("carla.mendoza@x.com")).isEqualTo("Carla Mendoza");
        assertThat(ContactInference.nameFromAddress("jperez_22@x.com")).isEqualTo("Jperez");
        assertThat(ContactInference.nameFromAddress("ventas@x.com")).isEqualTo("Ventas");
    }

    @Test
    void infersCompanyOnlyFromCorporateDomains() {
        assertThat(ContactInference.companyFromDomain("carla@andinalogistics.com.pe")).isEqualTo("Andinalogistics");
        assertThat(ContactInference.companyFromDomain("carla@gmail.com")).isNull();
        assertThat(ContactInference.companyFromDomain("carla@hotmail.com")).isNull();
    }

    @Test
    void statusNeverGoesBackButDiscardedIsExplicitAndCanBeReopened() {
        assertThat(ProspectStatus.merge(ProspectStatus.MEETING_SCHEDULED, ProspectStatus.CONTACTED)).isEqualTo(ProspectStatus.MEETING_SCHEDULED);
        assertThat(ProspectStatus.merge(ProspectStatus.CONTACTED, ProspectStatus.INTERESTED)).isEqualTo(ProspectStatus.INTERESTED);
        assertThat(ProspectStatus.merge(ProspectStatus.CLIENT, ProspectStatus.DISCARDED)).isEqualTo(ProspectStatus.DISCARDED);
        assertThat(ProspectStatus.merge(ProspectStatus.DISCARDED, ProspectStatus.INTERESTED)).isEqualTo(ProspectStatus.INTERESTED);
        assertThat(ProspectStatus.merge(null, null)).isEqualTo(ProspectStatus.NEW);
        assertThat(ProspectStatus.parseOrNull("interested")).isEqualTo(ProspectStatus.INTERESTED);
        assertThat(ProspectStatus.parseOrNull("otro")).isNull();
    }
}
