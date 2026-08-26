package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;

import lombok.RequiredArgsConstructor;

/**
 * Controlled ONE-HOP related-record projections for workflow context.
 *
 * Every lookup is tenant-scoped (findByIdAndTenantId); a related ID belonging
 * to another tenant resolves as absent. Projections are fixed and small — this
 * is not a graph traversal engine.
 */
@Component
@RequiredArgsConstructor
public class WorkflowRelatedRecordResolver {

    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final DealRepository dealRepository;
    private final LeadRepository leadRepository;

    public Optional<Map<String, Object>> account(UUID tenantId, UUID accountId) {
        if (accountId == null) return Optional.empty();
        return accountRepository.findByIdAndTenantId(accountId, tenantId).map(this::accountProjection);
    }

    public Optional<Map<String, Object>> contact(UUID tenantId, UUID contactId) {
        if (contactId == null) return Optional.empty();
        return contactRepository.findByIdAndTenantId(contactId, tenantId).map(this::contactProjection);
    }

    public Optional<Map<String, Object>> deal(UUID tenantId, UUID dealId) {
        if (dealId == null) return Optional.empty();
        return dealRepository.findByIdAndTenantId(dealId, tenantId).map(this::dealProjection);
    }

    public Optional<Map<String, Object>> lead(UUID tenantId, UUID leadId) {
        if (leadId == null) return Optional.empty();
        return leadRepository.findByIdAndTenantId(leadId, tenantId).map(this::leadProjection);
    }

    /**
     * Polymorphic projection for activity entities (Task / Meeting / Call)
     * whose related record is addressed by entityType + entityId. Only the
     * core CRM types LEAD / CONTACT / ACCOUNT / DEAL are resolvable.
     */
    public Optional<Map<String, Object>> related(String entityType, UUID tenantId, UUID entityId) {
        if (entityType == null || entityId == null) return Optional.empty();
        return switch (entityType.trim().toUpperCase()) {
            case "LEAD" -> lead(tenantId, entityId).map(map -> withType(map, "LEAD"));
            case "CONTACT" -> contact(tenantId, entityId).map(map -> withType(map, "CONTACT"));
            case "ACCOUNT" -> account(tenantId, entityId).map(map -> withType(map, "ACCOUNT"));
            case "DEAL" -> deal(tenantId, entityId).map(map -> withType(map, "DEAL"));
            default -> Optional.empty();
        };
    }

    private Map<String, Object> withType(Map<String, Object> projection, String type) {
        Map<String, Object> result = new LinkedHashMap<>(projection);
        result.put("type", type);
        return result;
    }

    private Map<String, Object> accountProjection(Account account) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", account.getId());
        map.put("name", account.getName());
        map.put("ownerId", account.getOwnerId());
        map.put("industry", account.getIndustry());
        map.put("customFields", account.getCustomData() == null ? Map.of() : account.getCustomData());
        return map;
    }

    private Map<String, Object> contactProjection(Contact contact) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", contact.getId());
        // Consistent aggregate label so entity.related.name resolves for every
        // related record type, matching the declared builder vocabulary.
        map.put("name", contactDisplayName(contact.getFirstName(), contact.getLastName()));
        map.put("firstName", contact.getFirstName());
        map.put("lastName", contact.getLastName());
        map.put("email", contact.getEmail());
        map.put("ownerId", contact.getOwnerId());
        map.put("customFields", contact.getCustomData() == null ? Map.of() : contact.getCustomData());
        return map;
    }

    private Map<String, Object> dealProjection(Deal deal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", deal.getId());
        map.put("name", deal.getName());
        map.put("stage", deal.getStageName());
        map.put("ownerId", deal.getOwnerId());
        map.put("customFields", deal.getCustomData() == null ? Map.of() : deal.getCustomData());
        return map;
    }

    private Map<String, Object> leadProjection(Lead lead) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", lead.getId());
        String fullName = lead.getFullName();
        map.put("name", fullName);
        map.put("fullName", fullName);
        map.put("status", lead.getStatus() == null ? null : lead.getStatus().getName());
        map.put("ownerId", lead.getOwnerId());
        map.put("customFields", lead.getCustomData() == null ? Map.of() : lead.getCustomData());
        return map;
    }

    private String contactDisplayName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (first + " " + last).trim();
    }
}
