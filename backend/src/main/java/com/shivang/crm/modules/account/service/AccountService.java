package com.shivang.crm.modules.account.service;

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

import com.shivang.crm.modules.account.dto.AccountCreateRequest;
import com.shivang.crm.modules.account.dto.AccountResponse;
import com.shivang.crm.modules.account.dto.AccountUpdateRequest;
import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.mapper.AccountMapper;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.account.repository.AccountSpecifications;
import com.shivang.crm.modules.contact.dto.ContactResponse;
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
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final ContactRepository contactRepository;
    private final ContactMapper contactMapper;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final com.shivang.crm.modules.rbac.service.RecordScopeGuard recordScopeGuard;
    private final CanonicalCrmEventPublisher canonicalCrmEventPublisher;

    public AccountResponse createAccount(UUID tenantId, UUID userId, AccountCreateRequest request) {
        log.info("Creating account for tenant {}", tenantId);

        String normalizedName = request.getName() != null ? request.getName().trim() : null;
        if (normalizedName != null && accountRepository.findByTenantIdAndNameIgnoreCaseAndDeletedFalse(tenantId, normalizedName).isPresent()) {
            throw new BusinessException("DUPLICATE_ACCOUNT", "An account with the same name already exists");
        }

        Account account = accountMapper.toEntity(request);
        account.setTenantId(tenantId);
        account.setCreatedBy(userId);
        account.setUpdatedBy(userId);

        Account saved = accountRepository.save(account);

        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("source", "MANUAL");
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
            saved.getTenantId(),
            CanonicalCrmEvent.ACCOUNT_ENTITY_TYPE,
            CanonicalCrmEvent.CREATED_EVENT_TYPE,
            saved.getId(),
            eventMetadata
        );

        return accountMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id, UUID tenantId) {
        UUID currentUserId = UserUtil.currentUserId();
        String scope = recordScopeGuard.requireScope(tenantId, currentUserId, "account", "read");

        Account account = accountRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));

        recordScopeGuard.assertWithinOwnerCreatorScope(
                scope, tenantId, currentUserId, account.getOwnerId(), account.getCreatedBy());

        return accountMapper.toResponse(account);
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccounts(UUID tenantId, UUID ownerUserId, String search, int page, int size) {
        UUID currentUserId = UserUtil.currentUserId();
        String accessScope = permissionEvaluatorService.getAccessScope(currentUserId, tenantId, "account", "read");
        List<UUID> teamUserIds = "TEAM".equals(accessScope)
                ? permissionEvaluatorService.getTeamUserIds(currentUserId, tenantId)
                : Collections.emptyList();

        Specification<Account> spec = AccountSpecifications.buildSpecification(
                tenantId,
                ownerUserId,
                search,
                accessScope,
                currentUserId,
                teamUserIds
        );
        Pageable pageable = PageRequest.of(page, size);
        return accountRepository.findAll(spec, pageable).map(accountMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public java.util.List<AccountResponse> searchAccounts(UUID tenantId, String search, int limit) {
        return listAccounts(tenantId, null, search, 0, limit).getContent();
    }

    public AccountResponse updateAccount(UUID id, UUID tenantId, UUID userId, AccountUpdateRequest request) {
        log.info("Updating account {} for tenant {}", id, tenantId);
        String writeScope = recordScopeGuard.requireScope(tenantId, userId, "account", "write");
        Account account = accountRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                writeScope, tenantId, userId, account.getOwnerId(), account.getCreatedBy());

        String normalizedName = request.getName() != null ? request.getName().trim() : null;
        if (normalizedName != null && accountRepository
                .findByTenantIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(tenantId, normalizedName, id)
                .isPresent()) {
            throw new BusinessException("DUPLICATE_ACCOUNT", "An account with the same name already exists");
        }

        accountMapper.updateEntity(request, account);
        account.setUpdatedBy(userId);

        Account updated = accountRepository.save(account);

        Map<String, Object> eventMetadata = new HashMap<>();
        eventMetadata.put("source", "MANUAL");
        eventMetadata.put("actorId", userId.toString());
        eventMetadata.put("actorType", "USER");
        canonicalCrmEventPublisher.publish(
            updated.getTenantId(),
            CanonicalCrmEvent.ACCOUNT_ENTITY_TYPE,
            CanonicalCrmEvent.UPDATED_EVENT_TYPE,
            updated.getId(),
            eventMetadata
        );

        return accountMapper.toResponse(updated);
    }

    public AccountResponse assignOwner(UUID id, UUID tenantId, UUID ownerUserId, UUID actorId) {
        String assignScope = recordScopeGuard.requireScope(tenantId, actorId, "account", "assign");
        Account account = accountRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                assignScope, tenantId, actorId, account.getOwnerId(), account.getCreatedBy());
        UUID previousOwnerId = account.getOwnerId();
        account.setOwnerId(ownerUserId);
        account.setUpdatedBy(actorId);
        Account updated = accountRepository.save(account);

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
                CanonicalCrmEvent.ACCOUNT_ENTITY_TYPE,
                CanonicalCrmEvent.OWNER_CHANGED_EVENT_TYPE,
                updated.getId(),
                eventMetadata
            );
        }

        return accountMapper.toResponse(updated);
    }

    public void deleteAccount(UUID id, UUID tenantId, UUID userId) {
        log.info("Deleting account {} for tenant {}", id, tenantId);
        String deleteScope = recordScopeGuard.requireScope(tenantId, userId, "account", "delete");
        Account account = accountRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                deleteScope, tenantId, userId, account.getOwnerId(), account.getCreatedBy());
        accountRepository.delete(account);
    }

    @Transactional(readOnly = true)
    public Page<ContactResponse> listContacts(UUID accountId, UUID tenantId, int page, int size) {
        UUID currentUserId = UserUtil.currentUserId();
        // RBAC-7: nested-resource rule — the parent account must be within the
        // caller's account:read scope before any child contacts are exposed.
        String parentScope = recordScopeGuard.requireScope(tenantId, currentUserId, "account", "read");
        Account parent = accountRepository.findByIdAndTenantId(accountId, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        recordScopeGuard.assertWithinOwnerCreatorScope(
                parentScope, tenantId, currentUserId, parent.getOwnerId(), parent.getCreatedBy());

        String accessScope = permissionEvaluatorService.getAccessScope(currentUserId, tenantId, "contact", "read");
        List<UUID> teamUserIds = "TEAM".equals(accessScope)
                ? permissionEvaluatorService.getTeamUserIds(currentUserId, tenantId)
                : Collections.emptyList();

        Specification<Contact> spec = ContactSpecifications.buildSpecification(
                tenantId,
                accountId,
                null,
                null,
                accessScope,
                currentUserId,
                teamUserIds
        );

        Pageable pageable = PageRequest.of(page, size);
        return contactRepository.findAll(spec, pageable)
            .map(contactMapper::toResponse);
    }
}
