package com.utp.assistant.crm.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.utp.assistant.crm.dto.ProspectDetailResponse;
import com.utp.assistant.crm.dto.ProspectInteractionResponse;
import com.utp.assistant.crm.dto.ProspectRequest;
import com.utp.assistant.crm.dto.ProspectResponse;
import com.utp.assistant.crm.entity.DataSource;
import com.utp.assistant.crm.entity.Prospect;
import com.utp.assistant.crm.entity.ProspectInteraction;
import com.utp.assistant.crm.entity.ProspectStatus;
import com.utp.assistant.crm.exception.InvalidProspectException;
import com.utp.assistant.crm.exception.ProspectNotFoundException;
import com.utp.assistant.crm.repository.ProspectInteractionRepository;
import com.utp.assistant.crm.repository.ProspectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRM propio en PostgreSQL. Upsert de contactos por email con reglas de negocio del lado del servidor:
 * <ul>
 *   <li>El email real sale del header From; el que proponga Gemini se ignora si no coincide.</li>
 *   <li>Datos faltantes se infieren del From (nombre) o del dominio corporativo (empresa); el teléfono nunca.</li>
 *   <li>Un dato vacío nunca borra uno existente, y uno inferido nunca pisa uno real (Gemini/manual).</li>
 *   <li>El estado comercial no retrocede; si el correo pide reunión pasa a MEETING_SCHEDULED.</li>
 *   <li>Idempotente por gmailMessageId: reintentar el mismo correo no duplica historial ni contadores.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrmService {

    /** Resultado del upsert. alreadyApplied: ese correo ya se había registrado (reintento). */
    public record UpsertResult(ProspectResponse prospect, boolean created, boolean alreadyApplied) {
    }

    private static final Map<DataSource, Integer> PRIORITY = Map.of(
            DataSource.MANUAL, 4,
            DataSource.GEMINI, 3,
            DataSource.FROM_HEADER, 2,
            DataSource.EMAIL_ADDRESS, 1,
            DataSource.EMAIL_DOMAIN, 1);

    private final ProspectRepository prospectRepository;
    private final ProspectInteractionRepository interactionRepository;

    /** Acción actualizar_contacto_crm de la automatización. */
    @Transactional
    public UpsertResult upsertFromEmail(CrmContactCommand command) {
        if (command.gmailMessageId() != null) {
            Optional<ProspectInteraction> previous = interactionRepository.findFirstByGmailMessageId(command.gmailMessageId());
            if (previous.isPresent()) {
                log.info("El correo {} ya estaba registrado en el CRM; no se duplica", command.gmailMessageId());
                return new UpsertResult(ProspectResponse.from(previous.get().getProspect()), false, true);
            }
        }

        String email = resolveEmail(command);
        Optional<Prospect> existing = prospectRepository.findByEmail(email);
        Prospect prospect = existing.orElseGet(Prospect::new);
        prospect.setEmail(email);
        ProspectStatus before = prospect.getStatus();

        applyName(prospect, clean(command.name()), DataSource.GEMINI);
        applyName(prospect, ContactInference.displayName(command.from()), DataSource.FROM_HEADER);
        applyName(prospect, ContactInference.nameFromAddress(email), DataSource.EMAIL_ADDRESS);
        applyCompany(prospect, clean(command.company()), DataSource.GEMINI);
        applyCompany(prospect, ContactInference.companyFromDomain(email), DataSource.EMAIL_DOMAIN);
        if (clean(command.phone()) != null) {
            prospect.setPhone(clean(command.phone()));
        }

        ProspectStatus requested = ProspectStatus.parseOrNull(command.status());
        if (command.meetingRequested()) {
            requested = ProspectStatus.merge(requested, ProspectStatus.MEETING_SCHEDULED);
        }
        prospect.setStatus(ProspectStatus.merge(before, requested == null && before == null ? ProspectStatus.CONTACTED : requested));

        String notes = clean(command.notes());
        if (notes != null) {
            prospect.setNotes(notes);
        }
        prospect.setLastSubject(truncate(command.subject(), 1000));
        prospect.setLastGmailMessageId(command.gmailMessageId());
        prospect.setLastContactAt(OffsetDateTime.now(ZoneOffset.UTC));
        prospect.setInteractionCount(prospect.getInteractionCount() + 1);
        Prospect saved = prospectRepository.save(prospect);

        saveInteraction(saved, command.gmailMessageId(), command.subject(), before, notes, DataSource.GEMINI);
        log.info("CRM: contacto {} {} (estado {} → {})", saved.getEmail(), existing.isPresent() ? "actualizado" : "creado",
                before, saved.getStatus());
        return new UpsertResult(ProspectResponse.from(saved), existing.isEmpty(), false);
    }

    /** Alta/actualización manual (Swagger). Los datos manuales tienen prioridad y el estado se aplica tal cual. */
    @Transactional
    public UpsertResult upsertManual(ProspectRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (!ContactInference.isValidEmail(email)) {
            throw new InvalidProspectException("El email '" + request.email() + "' no es válido.");
        }
        Optional<Prospect> existing = prospectRepository.findByEmail(email);
        Prospect prospect = existing.orElseGet(Prospect::new);
        prospect.setEmail(email);
        ProspectStatus before = prospect.getStatus();

        applyName(prospect, clean(request.name()), DataSource.MANUAL);
        applyName(prospect, ContactInference.nameFromAddress(email), DataSource.EMAIL_ADDRESS);
        applyCompany(prospect, clean(request.company()), DataSource.MANUAL);
        applyCompany(prospect, ContactInference.companyFromDomain(email), DataSource.EMAIL_DOMAIN);
        if (clean(request.phone()) != null) {
            prospect.setPhone(clean(request.phone()));
        }
        prospect.setStatus(request.status() != null ? request.status() : before != null ? before : ProspectStatus.NEW);
        if (clean(request.notes()) != null) {
            prospect.setNotes(clean(request.notes()));
        }
        prospect.setLastContactAt(OffsetDateTime.now(ZoneOffset.UTC));
        prospect.setInteractionCount(prospect.getInteractionCount() + 1);
        Prospect saved = prospectRepository.save(prospect);

        saveInteraction(saved, null, "Registro manual", before, clean(request.notes()), DataSource.MANUAL);
        return new UpsertResult(ProspectResponse.from(saved), existing.isEmpty(), false);
    }

    @Transactional(readOnly = true)
    public List<ProspectResponse> list(String search, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        String term = clean(search);
        List<Prospect> prospects = term == null
                ? prospectRepository.findLatest(page)
                : prospectRepository.search("%" + term.toLowerCase(Locale.ROOT) + "%", page);
        return prospects.stream().map(ProspectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProspectDetailResponse detail(Long id) {
        Prospect prospect = prospectRepository.findById(id)
                .orElseThrow(() -> new ProspectNotFoundException("No existe el prospecto " + id + "."));
        List<ProspectInteractionResponse> interactions = interactionRepository.findByProspectIdOrderByCreatedAtDesc(id)
                .stream().map(ProspectInteractionResponse::from).toList();
        return new ProspectDetailResponse(ProspectResponse.from(prospect), interactions);
    }

    /** El header From manda; el email de Gemini solo se usa si el From no trae una dirección válida. */
    private static String resolveEmail(CrmContactCommand command) {
        String fromHeader = ContactInference.senderEmail(command.from());
        String proposed = clean(command.email()) == null ? null : command.email().strip().toLowerCase(Locale.ROOT);
        if (ContactInference.isValidEmail(fromHeader)) {
            if (proposed != null && !proposed.equals(fromHeader)) {
                log.warn("Gemini propuso el email {} pero el remitente real es {}; se usa el remitente", proposed, fromHeader);
            }
            return fromHeader;
        }
        if (ContactInference.isValidEmail(proposed)) {
            return proposed;
        }
        throw new InvalidProspectException("El correo no tiene un remitente con email válido para registrar en el CRM.");
    }

    private static void applyName(Prospect prospect, String candidate, DataSource source) {
        if (shouldReplace(prospect.getName(), prospect.getNameSource(), candidate, source)) {
            prospect.setName(truncate(candidate, 255));
            prospect.setNameSource(source);
        }
    }

    private static void applyCompany(Prospect prospect, String candidate, DataSource source) {
        if (shouldReplace(prospect.getCompany(), prospect.getCompanySource(), candidate, source)) {
            prospect.setCompany(truncate(candidate, 255));
            prospect.setCompanySource(source);
        }
    }

    /**
     * Nunca con vacíos. Reemplaza si el candidato es de mayor prioridad; entre iguales solo los datos reales
     * (Gemini/manual) se actualizan con el más reciente.
     */
    private static boolean shouldReplace(String current, DataSource currentSource, String candidate, DataSource candidateSource) {
        if (candidate == null) {
            return false;
        }
        if (current == null || current.isBlank() || currentSource == null) {
            return true;
        }
        int candidatePriority = PRIORITY.get(candidateSource);
        int currentPriority = PRIORITY.get(currentSource);
        return candidatePriority > currentPriority
                || (candidatePriority == currentPriority && !candidateSource.isInferred() && !candidate.equals(current));
    }

    private void saveInteraction(Prospect prospect, String gmailMessageId, String subject, ProspectStatus before,
                                 String notes, DataSource source) {
        ProspectInteraction interaction = new ProspectInteraction();
        interaction.setProspect(prospect);
        interaction.setGmailMessageId(gmailMessageId);
        interaction.setSubject(truncate(subject, 1000));
        interaction.setStatusBefore(before);
        interaction.setStatusAfter(prospect.getStatus());
        interaction.setNotes(notes);
        interaction.setSource(source);
        interactionRepository.save(interaction);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
