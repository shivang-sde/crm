package com.shivang.crm.modules.contact.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.contact.dto.ContactCreateRequest;
import com.shivang.crm.modules.contact.dto.ContactResponse;
import com.shivang.crm.modules.contact.dto.ContactUpdateRequest;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.mapper.ContactMapper;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.contact.repository.ContactSpecifications;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.shared.event.CanonicalCrmEvent;
import com.shivang.crm.shared.event.CanonicalCrmEventPublisher;
import com.shivang.crm.shared.exception.BusinessException;

import com.shivang.crm.util.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContactService {

    private final ContactRepository contactRepository;
    private final com.shivang.crm.modules.rbac.service.RecordScopeGuard recordScopeGuard;
    private final ContactMapper contactMapper;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;

    public ContactResponse createContact(UUID tenantId, UUID userId, ContactCreateRequest request) {
        log.info("Creating contact for tenant {}", tenantId);

        if (request.getEmail() != null && contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, request.getEmail().trim()).isPresent()) {
            throw new BusinessException("DUPLICATE_CONTACT", "A contact with the same email already exists");
        }

        if (request.getPhone() != null && contactRepository.findByTenantIdAndPhoneAndDeletedFalse(tenantId, request.getPhone().trim()).isPresent()) {
            throw new BusinessException("DUPLICATE_CONTACT", "A contact with the same phone already exists");
        }

        Contact contact = contactMapper.toEntity(request);
        contact.setTenantId(tenantId);
        contact.setCreatedBy(userId);
        contact.setUpdatedBy(userId);

        Contact saved = contactRepository.save(contact);

        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("source", "MANUAL");
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
            saved.getTenantId(),
            CanonicalCrmEvent.CONTACT_ENTITY_TYPE,
            CanonicalCrmEvent.CREATED_EVENT_TYPE,
            saved.getId(),
            eventMetadata
        );

        return contactMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ContactResponse getContactById(UUID id, UUID tenantId) {
        UUID currentUserId = UserUtil.currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, "contact", "read");

        Contact contact = contactRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Contact not found"));

        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, currentUserId, contact.getOwnerId(), contact.getCreatedBy());

        return contactMapper.toResponse(contact);
    }

    @Transactional(readOnly = true)
    public Page<ContactResponse> listContacts(UUID tenantId, UUID ownerUserId, String search, int page, int size) {
        UUID currentUserId = UserUtil.currentUserId();
        String accessScope = permissionEvaluatorService.getAccessScope(currentUserId, tenantId, "contact", "read");
        List<UUID> teamUserIds = "TEAM".equals(accessScope)
                ? permissionEvaluatorService.getTeamUserIds(currentUserId, tenantId)
                : Collections.emptyList();

        Specification<Contact> spec = ContactSpecifications.buildSpecification(
                tenantId,
                null,
                ownerUserId,
                search,
                accessScope,
                currentUserId,
                teamUserIds
        );
        Pageable pageable = PageRequest.of(page, size);
        return contactRepository.findAll(spec, pageable).map(contactMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public java.util.List<ContactResponse> searchContacts(UUID tenantId, String search, int limit) {
        return listContacts(tenantId, null, search, 0, limit).getContent();
    }

    public ContactResponse updateContact(UUID id, UUID tenantId, UUID userId, ContactUpdateRequest request) {
        log.info("Updating contact {} for tenant {}", id, tenantId);
        String scope = recordScopeGuard.requireScope(tenantId, userId, "contact", "write");
        Contact contact = contactRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Contact not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, userId, contact.getOwnerId(), contact.getCreatedBy());

        if (request.getEmail() != null && contactRepository
                .findByTenantIdAndEmailIgnoreCaseAndDeletedFalseAndIdNot(tenantId, request.getEmail().trim(), id)
                .isPresent()) {
            throw new BusinessException("DUPLICATE_CONTACT", "A contact with the same email already exists");
        }

        if (request.getPhone() != null && contactRepository
                .findByTenantIdAndPhoneAndDeletedFalseAndIdNot(tenantId, request.getPhone().trim(), id)
                .isPresent()) {
            throw new BusinessException("DUPLICATE_CONTACT", "A contact with the same phone already exists");
        }

        contactMapper.updateEntity(request, contact);
        contact.setUpdatedBy(userId);

        Contact updated = contactRepository.save(contact);

        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("source", "MANUAL");
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
            updated.getTenantId(),
            CanonicalCrmEvent.CONTACT_ENTITY_TYPE,
            CanonicalCrmEvent.UPDATED_EVENT_TYPE,
            updated.getId(),
            eventMetadata
        );

        return contactMapper.toResponse(updated);
    }

    public ContactResponse assignOwner(UUID id, UUID tenantId, UUID ownerUserId, UUID actorId) {
        String scope = recordScopeGuard.requireScope(tenantId, actorId, "contact", "assign");
        Contact contact = contactRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Contact not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, actorId, contact.getOwnerId(), contact.getCreatedBy());
        UUID previousOwnerId = contact.getOwnerId();
        contact.setOwnerId(ownerUserId);
        contact.setUpdatedBy(actorId);
        Contact updated = contactRepository.save(contact);

        if (!java.util.Objects.equals(previousOwnerId, ownerUserId)) {
            Map<String, Object> eventMetadata = new HashMap<>();
            if (previousOwnerId != null) {
                eventMetadata.put("previousOwnerId", previousOwnerId.toString());
            }
            if (ownerUserId != null) {
                eventMetadata.put("newOwnerId", ownerUserId.toString());
            }
            eventMetadata.put("actorId", actorId.toString());
            eventMetadata.put("actorType", "USER");
            canonicalCrmEventPublisher.publish(
                updated.getTenantId(),
                CanonicalCrmEvent.CONTACT_ENTITY_TYPE,
                CanonicalCrmEvent.OWNER_CHANGED_EVENT_TYPE,
                updated.getId(),
                eventMetadata
            );
        }

        return contactMapper.toResponse(updated);
    }

    public void deleteContact(UUID id, UUID tenantId, UUID userId) {
        log.info("Deleting contact {} for tenant {}", id, tenantId);
        String scope = recordScopeGuard.requireScope(tenantId, userId, "contact", "delete");
        Contact contact = contactRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Contact not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, userId, contact.getOwnerId(), contact.getCreatedBy());
        contactRepository.delete(contact);
    }
}
