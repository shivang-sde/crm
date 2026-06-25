package com.shivang.crm.modules.account.service;

import java.util.Collections;
import java.util.List;
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
        return accountMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id, UUID tenantId) {
        Account account = accountRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
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
        Account account = accountRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));

        accountMapper.updateEntity(request, account);
        account.setUpdatedBy(userId);

        Account updated = accountRepository.save(account);
        return accountMapper.toResponse(updated);
    }

    public void deleteAccount(UUID id, UUID tenantId, UUID userId) {
        log.info("Deleting account {} for tenant {}", id, tenantId);
        Account account = accountRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
        accountRepository.delete(account);
    }

    @Transactional(readOnly = true)
    public Page<ContactResponse> listContacts(UUID accountId, UUID tenantId, int page, int size) {
        UUID currentUserId = UserUtil.currentUserId();
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
