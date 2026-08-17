package com.shivang.crm.modules.lead.service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.dto.AccountCreateRequest;
import com.shivang.crm.modules.account.dto.AccountResponse;
import com.shivang.crm.modules.account.service.AccountService;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.contact.dto.ContactCreateRequest;
import com.shivang.crm.modules.contact.dto.ContactResponse;
import com.shivang.crm.modules.contact.service.ContactService;
import com.shivang.crm.modules.lead.dto.LeadConvertRequest;
import com.shivang.crm.modules.lead.dto.LeadConvertResponse;
import com.shivang.crm.modules.lead.dto.LeadCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadResponse;
import com.shivang.crm.modules.lead.dto.LeadUpdateRequest;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.entity.LeadStatus;
import com.shivang.crm.modules.lead.mapper.LeadMapper;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.lead.repository.LeadSpecifications;
import com.shivang.crm.modules.lead.repository.LeadStatusRepository;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.util.UserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadStatusRepository leadStatusRepository;
    private final LeadMapper leadMapper;

    private final AccountService accountService;
    private final ContactService contactService;

    private final ActivityService activityService;
    private final EntityHistoryService HistoryService;

    private final UserRepository userRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;

     /**
     * because service beans are singleton beans created at application startup, before any request exists.
     * Get the user ID inside the method, not in the constructor.
     */


    /**
     * Create a new lead
     */
    public LeadResponse createLead(UUID tenantId, UUID userId, LeadCreateRequest request) {
        log.info("Creating lead for tenant: {}", tenantId);

        // Map request to entity
        Lead lead = leadMapper.toEntity(request);
        lead.setTenantId(tenantId);
        lead.setCreatedBy(userId);
        lead.setOwnerId(request.getOwnerUserId() != null ? request.getOwnerUserId() : userId); // Default owner to creator if not provided
        lead.setUpdatedBy(userId);

        // Set status if not provided
        if (lead.getStatus() == null && request.getStatusId() != null) {
            LeadStatus status = leadStatusRepository.findByIdAndTenantId(request.getStatusId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Status not found"));
            lead.setStatus(status);
        } else {
            // If status is not provided, set to default status for the tenant
            LeadStatus defaultStatus = leadStatusRepository.findDefaultStatusByTenant(tenantId)
                .orElseThrow(() -> new RuntimeException("Default status not found for Lead."));
            lead.setStatus(defaultStatus);
        }

        if(request.getEmail() != null && existsWithEmail(request.getEmail(), tenantId)) {
            
                throw new BusinessException("DUPLICATE", "A lead with this email already exists");
            
        }
        if(request.getPhone() != null && existsWithPhone(request.getPhone(), tenantId)) {
            
                throw new BusinessException("DUPLICATE", "A lead with this phone number already exists");
            
        }

        // Save lead
        Lead savedLead = leadRepository.save(lead);

        // Log activity
        HistoryService.logEntityCreated(tenantId, savedLead.getId(), "LEAD", userId);
        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("source", "MANUAL");
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publishLeadCreated(
            savedLead.getTenantId(),
            savedLead.getId(),
            eventMetadata
        );

        return leadMapper.toResponse(savedLead);
    }

    public LeadResponse createLeadInternal(UUID tenantId, UUID createdBy, LeadCreateRequest request) {
        return createLeadInternal(tenantId, createdBy, request, Map.of("source", "INTERNAL"));
    }

    public LeadResponse createLeadInternal(
        UUID tenantId,
        UUID createdBy,
        LeadCreateRequest request,
        Map<String, Object> eventMetadata
    ) {
        log.info("Creating ingestion lead for tenant: {} by system actor: {}", tenantId, createdBy);

        Lead lead = leadMapper.toEntity(request);
        lead.setTenantId(tenantId);
        lead.setCreatedBy(createdBy);
        lead.setOwnerId(null);
        lead.setUpdatedBy(createdBy);

        if (lead.getStatus() == null && request.getStatusId() != null) {
            LeadStatus status = leadStatusRepository.findByIdAndTenantId(request.getStatusId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Status not found"));
            lead.setStatus(status);
        } else {
            LeadStatus defaultStatus = leadStatusRepository.findDefaultStatusByTenant(tenantId)
                .orElseThrow(() -> new RuntimeException("Default status not found for Lead."));
            lead.setStatus(defaultStatus);
        }

        if (request.getEmail() != null && existsWithEmail(request.getEmail(), tenantId)) {
            throw new BusinessException("DUPLICATE", "A lead with this email already exists");
        }
        if (request.getPhone() != null && existsWithPhone(request.getPhone(), tenantId)) {
            throw new BusinessException("DUPLICATE", "A lead with this phone number already exists");
        }

        Lead savedLead = leadRepository.save(lead);
        HistoryService.logEntityCreated(tenantId, savedLead.getId(), "LEAD", createdBy);
        Map<String, Object> enrichedEventMetadata = new HashMap<>();
        if (eventMetadata != null) enrichedEventMetadata.putAll(eventMetadata);
        enrichedEventMetadata.put("actorId", createdBy.toString());
        enrichedEventMetadata.put("actorType", "SYSTEM");
        canonicalCrmEventPublisher.publishLeadCreated(
            savedLead.getTenantId(),
            savedLead.getId(),
            enrichedEventMetadata
        );
        return leadMapper.toResponse(savedLead);
    }

    /**
     * Get lead by ID with tenant isolation
     */
    @Transactional(readOnly = true)
    public LeadResponse getLeadById(UUID id, UUID tenantId) {
        log.info("Fetching lead: {} for tenant: {}", id, tenantId);

        Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Lead not found"));

        return leadMapper.toResponse(lead);
    }

    /**
     * List leads with filtering
     */
    @Transactional(readOnly = true)
    public Page<LeadResponse> listLeads(
            UUID tenantId,
            UUID statusId,
            UUID sourceId,
            UUID ownerUserId,
            String searchTerm,
            Boolean isConverted,
            int page,
            int size) {

        log.info("Listing leads for tenant: {} with filters", tenantId);

        UUID currentUserId = UserUtil.currentUserId();

        String accessScope = permissionEvaluatorService.getAccessScope(currentUserId, tenantId, "lead", "read");
        log.info("Access scope for user {} on leads: {}", currentUserId, accessScope);
        
        List<UUID> teamUserIds =
        "TEAM".equals(accessScope)
                ? userRepository.findTeamUserIdsByManagerAndTenant(
                        tenantId,
                        currentUserId
                )
                : Collections.emptyList();

        Specification<Lead> spec =
        LeadSpecifications.buildSpecification(
                tenantId,
                statusId,
                sourceId,
                ownerUserId,
                searchTerm,
                isConverted,
                accessScope,
                currentUserId,
                teamUserIds
        );

        Pageable pageable = PageRequest.of(page, size);
        Page<Lead> leads = leadRepository.findAll(spec, pageable);

        return leads.map(leadMapper::toResponse);
    }

    /**
     * Update a lead
     */
    public LeadResponse updateLead(UUID id, UUID tenantId, UUID userId, LeadUpdateRequest request) {
        log.info("Updating lead: {} for tenant: {}", id, tenantId);

        Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Lead not found"));

        if (request.getEmail() != null) {
    leadRepository
        .findByTenantIdAndEmailAndIdNot(
            tenantId,
            request.getEmail(),
            id
        )
        .ifPresent(existing -> {
            throw new RuntimeException("Email already exists");
        });
}
        if (request.getPhone() != null) {
    leadRepository
        .findByTenantIdAndPhoneAndIdNot(
            tenantId,
            request.getPhone(),
            id
        )
        .ifPresent(existing -> {
            throw new RuntimeException("Phone number already exists");
        });
}

        // Store old values for activity logging
        Map<String, Object> oldValues = new HashMap<>();
        if (request.getStatusId() != null && !request.getStatusId().equals(lead.getStatus().getId())) {
            oldValues.put("oldStatus", lead.getStatus().getName());
        }
        if (request.getOwnerUserId() != null && !request.getOwnerUserId().equals(lead.getOwnerId())) {
            oldValues.put("oldOwner", lead.getOwnerId());
        }

        // Update entity
        leadMapper.updateEntity(request, lead);
        lead.setUpdatedBy(userId);

        // Update status if needed
        if (request.getStatusId() != null) {
            LeadStatus status = leadStatusRepository.findByIdAndTenantId(request.getStatusId(), tenantId)
                .orElseThrow(() -> new RuntimeException("Status not found"));
            lead.setStatus(status);
        }

        Lead updatedLead = leadRepository.save(lead);

        // Log activity
        if (!oldValues.isEmpty()) {
            HistoryService.logEntityUpdated(tenantId, updatedLead.getId(), "LEAD", userId, oldValues);
        }

        return leadMapper.toResponse(updatedLead);
    }

    /**
     * Assign lead to a user
     */
    public LeadResponse assignLead(UUID id, UUID tenantId, UUID ownerUserId, UUID userId) {
        log.info("Assigning lead: {} to user: {} for tenant: {}", id, ownerUserId, tenantId);

        Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Lead not found"));

        UUID oldOwner = lead.getOwnerId();
        lead.setOwnerId(ownerUserId);
        lead.setUpdatedBy(userId);

        Lead updatedLead = leadRepository.save(lead);

        // Log activity
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("oldOwner", oldOwner);
        metadata.put("newOwner", ownerUserId);
        HistoryService.logEntityUpdated(
            tenantId, updatedLead.getId(), "LEAD", userId, metadata
        );

        return leadMapper.toResponse(updatedLead);
    }

    /**
     * Change lead status
     */
    public LeadResponse changeStatus(UUID id, UUID tenantId, UUID statusId, UUID userId) {
        log.info("Changing status of lead: {} to: {} for tenant: {}", id, statusId, tenantId);

        Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Lead not found"));

        LeadStatus newStatus = leadStatusRepository.findByIdAndTenantId(statusId, tenantId)
            .orElseThrow(() -> new RuntimeException("Status not found"));

        String oldStatusName = lead.getStatus().getName();
        lead.setStatus(newStatus);
        lead.setUpdatedBy(userId);

        Lead updatedLead = leadRepository.save(lead);

        // Log activity
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("oldStatus", oldStatusName);
        metadata.put("newStatus", newStatus.getName());
        HistoryService.logEntityUpdated(
            tenantId, updatedLead.getId(), "LEAD", userId, metadata
        );

        return leadMapper.toResponse(updatedLead);
    }

    /**
     * Delete a lead (soft delete can be added later)
     * for now keeo only soft delete, but we can add hard delete if needed.
     * add different delete methods for soft and hard delete, and use soft delete by default.
     */
    public void deleteLead(UUID id, UUID tenantId, UUID userId) {
        log.info("Deleting lead: {} for tenant: {}", id, tenantId);

        Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Lead not found"));

        lead.softDelete(userId);
        lead.setUpdatedBy(userId);
        leadRepository.save(lead);
    }

    @Transactional
    public LeadConvertResponse convertLead(UUID id, UUID tenantId, UUID userId, LeadConvertRequest request) {
        log.info("Converting lead: {} for tenant: {}", id, tenantId);


        Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("Lead not found"));

        if (Boolean.TRUE.equals(lead.getIsConverted())) {
            throw new BusinessException("ALREADY_CONVERTED", "Lead has already been converted");
        }

        if (request == null) {
            request = LeadConvertRequest.builder().build();
        }

        AccountResponse accountResponse = null;
        ContactResponse contactResponse = null;

        if (request.getAccountId() != null) {
            accountResponse = accountService.getAccountById(request.getAccountId(), tenantId);
        }

        if (request.getContactId() != null) {
            contactResponse = contactService.getContactById(request.getContactId(), tenantId);
            if (accountResponse == null) {
                accountResponse = accountService.getAccountById(contactResponse.getAccountId(), tenantId);
            } else if (contactResponse.getAccountId() != null
                && !contactResponse.getAccountId().equals(accountResponse.getId())) {
                throw new BusinessException("CONTACT_ACCOUNT_MISMATCH", "Selected contact does not belong to the selected account");
            }
        }

        if (accountResponse == null) {
            AccountCreateRequest accountRequest = AccountCreateRequest.builder()
                .name(lead.getCompany() != null && !lead.getCompany().isBlank() ? lead.getCompany() : lead.getFullName())
                .ownerUserId(lead.getOwnerId())
                .leadId(lead.getId())
                .build();
            accountResponse = accountService.createAccount(tenantId, userId, accountRequest);
        }

        if (contactResponse == null) {
            ContactCreateRequest contactRequest = ContactCreateRequest.builder()
                .accountId(accountResponse.getId())
                .firstName(lead.getFirstName())
                .lastName(lead.getLastName())
                .email(lead.getEmail())
                .phone(lead.getPhone())
                .ownerUserId(lead.getOwnerId())
                .leadId(lead.getId())
                .build();
            contactResponse = contactService.createContact(tenantId, userId, contactRequest);
        }

        lead.setIsConverted(true);
        lead.setConvertedAt(Instant.now());
        lead.setConvertedAccountId(accountResponse.getId());
        lead.setConvertedContactId(contactResponse.getId());
        lead.setUpdatedBy(userId);
        leadRepository.save(lead);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("accountId", accountResponse.getId());
        metadata.put("contactId", contactResponse.getId());

        HistoryService.logEntityUpdated(tenantId, lead.getId(), "LEAD", userId, metadata);
        activityService.logActivity(tenantId, lead.getId(), "LEAD", "CONVERTED", "Lead converted to account and contact", userId, metadata);

        if (request.getAccountId() == null) {
            activityService.logActivity(tenantId, accountResponse.getId(), "ACCOUNT", "ACCOUNT_CREATED_FROM_LEAD", "Account created from lead", userId, Map.of("leadId", lead.getId()));
        }
        if (request.getContactId() == null) {
            activityService.logActivity(tenantId, contactResponse.getId(), "CONTACT", "CONTACT_CREATED_FROM_LEAD", "Contact created from lead", userId, Map.of("leadId", lead.getId()));
        }

        return LeadConvertResponse.builder()
            .leadId(lead.getId())
            .accountId(accountResponse.getId())
            .contactId(contactResponse.getId())
            .build();
    }

    /**
     * Get recent unconverted leads
     */
    @Transactional(readOnly = true)
    public List<LeadResponse> getRecentUnconvertedLeads(UUID tenantId, int limit) {
        List<Lead> leads = leadRepository.findByTenantIdAndIsConvertedFalseOrderByCreatedAtDesc(tenantId, PageRequest.of(0, limit));
        return leadMapper.toResponseList(leads);
    }


    // duplicate check for email and phone can be added in create and update methods. For create, we can check if any active lead exists with the same email or phone. For update, we need to exclude the current lead from the check.

    /**
     * Check if lead exists with email
     */
    @Transactional(readOnly = true)
    public boolean existsWithEmail(String email, UUID tenantId) {
        return leadRepository.findActiveLeadByEmailAndTenant(email, tenantId).isPresent();
    }


    /**
     * Check if lead exists with phone
     */
    @Transactional(readOnly = true)
    public boolean existsWithPhone(String phone, UUID tenantId) {
        return leadRepository.findActiveLeadByPhoneAndTenant(phone, tenantId).isPresent();
    }
}
